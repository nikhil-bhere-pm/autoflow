package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.db.AutoFlowDatabase.Companion.MIGRATION_2_3_SQL
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Proves that TransactionRepository.updateTransaction is truly atomic (all-or-nothing)
 * against a real SQLite database (sqlite-jdbc, migrated from v2 to v3).
 *
 * In production on Android, Room provides AutoFlowDatabase.runInTransaction { ... }.
 * In a host-JVM unit test, Android Room's SQLite framework driver cannot run without Android OS,
 * so TransactionRepository accepts a transaction runner lambda `runInTransaction`.
 * Here, we pass the real transaction block through a JDBC transaction wrapper:
 *   conn.autoCommit = false
 *   try { block(); conn.commit() }
 *   catch (e) { conn.rollback(); throw e }
 *   finally { conn.autoCommit = true }
 *
 * All writes execute real SQLite statements against real tables. When write 2 fails,
 * real SQLite rollback executes, ensuring zero partial writes survive.
 */
class TransactionRepositoryRealSqliteAtomicityTest {

    private lateinit var conn: Connection
    private lateinit var transactionDao: SqliteTransactionDao
    private lateinit var merchantCategoryDao: SqliteMerchantCategoryDao
    private lateinit var repository: TransactionRepository

    @Before
    fun setup() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")

        // Initialize schema at v2 and migrate to v3
        val v2SchemaFile = findSchemaFile(2)
        val v2CreateStatements = extractCreateStatements(v2SchemaFile.readText())
        conn.createStatement().use { stmt ->
            for (sql in v2CreateStatements) {
                stmt.execute(sql)
            }
            for (sql in MIGRATION_2_3_SQL) {
                stmt.execute(sql)
            }
        }

        transactionDao = SqliteTransactionDao(conn)
        merchantCategoryDao = SqliteMerchantCategoryDao(conn)

        // Real JDBC transaction wrapper
        val jdbcTransactionRunner: suspend (suspend () -> Unit) -> Unit = { block ->
            conn.autoCommit = false
            try {
                block()
                conn.commit()
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }

        repository = TransactionRepository(
            transactionDao = transactionDao,
            merchantCategoryDao = merchantCategoryDao,
            runInTransaction = jdbcTransactionRunner
        )
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) {
            conn.close()
        }
    }

    @Test
    fun updateTransaction_whenSecondWriteFails_firstWriteIsRolledBackInRealSqlite() = runBlocking {
        // 1. Seed initial transaction into real SQLite
        val initialTx = TransactionEntity(
            id = 1L,
            amountPaise = 50000L,
            recordType = RecordType.EXPENSE,
            dateTime = 1700000000000L,
            merchantName = "Starbucks",
            category = "Food",
            categorySource = "AUTO",
            paymentMode = PaymentMode.UPI,
            note = null,
            accountNumber = "XX1234",
            bankName = "HDFC",
            reference = "REF100",
            transactionHash = "HASH100",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyBits = 0,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            isDeleted = 0
        )
        transactionDao.insert(initialTx)

        // Verify initial state in real SQLite
        val (initCat, initCatSrc, initMode, initNote, initDate) = queryTxRow(1L)
        assertEquals("Food", initCat)
        assertEquals("AUTO", initCatSrc)
        assertEquals("UPI", initMode)
        assertNull(initNote)
        assertEquals(1700000000000L, initDate)

        // 2. Configure second write (merchantCategoryDao.upsert) to fail
        merchantCategoryDao.failOnUpsert = true

        // 3. Call real production updateTransaction
        try {
            repository.updateTransaction(
                id = 1L,
                merchantName = "Starbucks",
                category = "Coffee",              // Changed
                dateTime = 1700000050000L,         // Changed
                paymentMode = PaymentMode.CREDIT,  // Changed
                note = "Latte with team",          // Changed
                now = 1700000060000L
            )
            fail("Expected SQLException from failing second write inside transaction")
        } catch (e: SQLException) {
            // Expected
        }

        // 4. PROOF OF ATOMICITY: Query real SQLite directly with raw SQL.
        // Even though transactionDao.update() executed first, conn.rollback() was called
        // when write 2 failed, so every single field in SQLite MUST remain unchanged!
        val (catAfter, catSrcAfter, modeAfter, noteAfter, dateAfter) = queryTxRow(1L)
        assertEquals("Food", catAfter)
        assertEquals("AUTO", catSrcAfter)
        assertEquals("UPI", modeAfter)
        assertNull(noteAfter)
        assertEquals(1700000000000L, dateAfter)

        // And no merchant_categories row exists
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM merchant_categories WHERE merchantKey = 'starbucks'").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1))
            }
        }
    }

    @Test
    fun updateTransaction_whenSecondWriteSucceeds_bothWritesAreCommittedInRealSqlite() = runBlocking {
        // 1. Seed initial transaction
        val initialTx = TransactionEntity(
            id = 2L,
            amountPaise = 30000L,
            recordType = RecordType.EXPENSE,
            dateTime = 1700000000000L,
            merchantName = "Swiggy",
            category = "Others",
            categorySource = "AUTO",
            paymentMode = PaymentMode.UNKNOWN,
            note = null,
            accountNumber = "XX5678",
            bankName = "ICICI",
            reference = "REF200",
            transactionHash = "HASH200",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyBits = 0,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            isDeleted = 0
        )
        transactionDao.insert(initialTx)

        // 2. Both writes succeed
        merchantCategoryDao.failOnUpsert = false

        val result = repository.updateTransaction(
            id = 2L,
            merchantName = "Swiggy",
            category = "Food",
            dateTime = 1700000020000L,
            paymentMode = PaymentMode.UPI,
            note = "Dinner order",
            now = 1700000030000L
        )

        assertTrue(result is UpdateTransactionResult.Success)

        // 3. Verify in real SQLite: both writes were committed
        val (cat, catSrc, mode, note, date) = queryTxRow(2L)
        assertEquals("Food", cat)
        assertEquals("USER", catSrc)
        assertEquals("UPI", mode)
        assertEquals("Dinner order", note)
        assertEquals(1700000020000L, date)

        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT category FROM merchant_categories WHERE merchantKey = 'swiggy'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Food", rs.getString(1))
            }
        }
    }

    private data class TxRowSnapshot(
        val category: String,
        val categorySource: String,
        val paymentMode: String,
        val note: String?,
        val dateTime: Long
    )

    private fun queryTxRow(id: Long): TxRowSnapshot {
        conn.prepareStatement(
            "SELECT category, categorySource, payment_mode, note, date_time FROM transactions WHERE id = ?"
        ).use { stmt ->
            stmt.setLong(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) error("Row id=$id not found in SQLite")
                return TxRowSnapshot(
                    category = rs.getString("category"),
                    categorySource = rs.getString("categorySource"),
                    paymentMode = rs.getString("payment_mode"),
                    note = rs.getString("note"),
                    dateTime = rs.getLong("date_time")
                )
            }
        }
    }

    private fun findSchemaFile(version: Int): File {
        val candidates = listOf(
            File("schemas/com.autoflow.app.data.db.AutoFlowDatabase/$version.json"),
            File("app/schemas/com.autoflow.app.data.db.AutoFlowDatabase/$version.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Schema file for version $version not found in: ${candidates.map { it.absolutePath }}")
    }

    private fun extractCreateStatements(jsonContent: String): List<String> {
        val statements = mutableListOf<String>()

        val entityRegex = Regex("""\{\s*"tableName":\s*"([^"]+)",\s*"createSql":\s*"([^"]+)".*?"indices":\s*\[(.*?)\],\s*"foreignKeys"""", RegexOption.DOT_MATCHES_ALL)
        for (match in entityRegex.findAll(jsonContent)) {
            val tableName = match.groupValues[1]
            val tableSql = match.groupValues[2]
                .replace("${'$'}{TABLE_NAME}", tableName)
                .replace("\\\"", "\"")
            statements.add(tableSql)

            val indicesBlock = match.groupValues[3]
            val indexSqlRegex = Regex(""""createSql":\s*"(CREATE (?:UNIQUE )?INDEX[^"]+)"""")
            for (idxMatch in indexSqlRegex.findAll(indicesBlock)) {
                val indexSql = idxMatch.groupValues[1]
                    .replace("${'$'}{TABLE_NAME}", tableName)
                    .replace("\\\"", "\"")
                statements.add(indexSql)
            }
        }

        val setupQueriesRegex = Regex(""""setupQueries":\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        val setupMatch = setupQueriesRegex.find(jsonContent)
        if (setupMatch != null) {
            val queryRegex = Regex(""""([^"]+)"""")
            for (qMatch in queryRegex.findAll(setupMatch.groupValues[1])) {
                val sql = qMatch.groupValues[1].replace("\\\"", "\"")
                statements.add(sql)
            }
        }

        return statements
    }

    /**
     * Real SQLite implementation of TransactionDao executing production queries on SQLiteConnection.
     */
    private class SqliteTransactionDao(private val conn: Connection) : TransactionDao {

        override suspend fun insert(transaction: TransactionEntity): Long {
            val insertSql = """
                INSERT INTO transactions (
                    id, amount_paise, record_type, date_time, merchant_name, category,
                    categorySource, payment_mode, note, account_number, bank_name, reference,
                    transaction_hash, source, review_status, uncertainty_bits, created_at, updated_at, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            conn.prepareStatement(insertSql).use { stmt ->
                stmt.setLong(1, transaction.id)
                stmt.setLong(2, transaction.amountPaise)
                stmt.setString(3, transaction.recordType.name)
                stmt.setLong(4, transaction.dateTime)
                stmt.setString(5, transaction.merchantName)
                stmt.setString(6, transaction.category)
                stmt.setString(7, transaction.categorySource)
                stmt.setString(8, transaction.paymentMode)
                stmt.setString(9, transaction.note)
                stmt.setString(10, transaction.accountNumber)
                stmt.setString(11, transaction.bankName)
                stmt.setString(12, transaction.reference)
                stmt.setString(13, transaction.transactionHash)
                stmt.setString(14, transaction.source.name)
                stmt.setString(15, transaction.reviewStatus.name)
                stmt.setInt(16, transaction.uncertaintyBits)
                stmt.setLong(17, transaction.createdAt)
                stmt.setLong(18, transaction.updatedAt)
                stmt.setInt(19, transaction.isDeleted)
                stmt.executeUpdate()
            }
            return transaction.id
        }

        override suspend fun update(transaction: TransactionEntity) {
            // Uses the exact production SQL generated by Room and defined in TransactionDao.SQL_UPDATE
            conn.prepareStatement(TransactionDao.SQL_UPDATE).use { stmt ->
                stmt.setLong(1, transaction.id)
                stmt.setLong(2, transaction.amountPaise)
                stmt.setString(3, transaction.recordType.name)
                stmt.setLong(4, transaction.dateTime)
                stmt.setString(5, transaction.merchantName)
                stmt.setString(6, transaction.category)
                stmt.setString(7, transaction.categorySource)
                stmt.setString(8, transaction.paymentMode)
                stmt.setString(9, transaction.note)
                stmt.setString(10, transaction.accountNumber)
                stmt.setString(11, transaction.bankName)
                stmt.setString(12, transaction.reference)
                stmt.setString(13, transaction.transactionHash)
                stmt.setString(14, transaction.source.name)
                stmt.setString(15, transaction.reviewStatus.name)
                stmt.setInt(16, transaction.uncertaintyBits)
                stmt.setLong(17, transaction.createdAt)
                stmt.setLong(18, transaction.updatedAt)
                stmt.setInt(19, transaction.isDeleted)
                stmt.setLong(20, transaction.id)
                stmt.executeUpdate()
            }
        }

        override suspend fun getById(id: Long): TransactionEntity? {
            conn.prepareStatement("SELECT * FROM transactions WHERE id = ? LIMIT 1").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) mapRow(rs) else null
                }
            }
        }

        override suspend fun getAllAutoCategorizedTransactions(): List<TransactionEntity> {
            val list = mutableListOf<TransactionEntity>()
            conn.createStatement().use { stmt ->
                stmt.executeQuery(TransactionDao.QUERY_GET_ALL_AUTO).use { rs ->
                    while (rs.next()) {
                        list.add(mapRow(rs))
                    }
                }
            }
            return list
        }

        private fun mapRow(rs: ResultSet): TransactionEntity {
            return TransactionEntity(
                id = rs.getLong("id"),
                amountPaise = rs.getLong("amount_paise"),
                recordType = RecordType.valueOf(rs.getString("record_type")),
                dateTime = rs.getLong("date_time"),
                merchantName = rs.getString("merchant_name"),
                category = rs.getString("category"),
                categorySource = rs.getString("categorySource"),
                paymentMode = rs.getString("payment_mode"),
                note = rs.getString("note"),
                accountNumber = rs.getString("account_number"),
                bankName = rs.getString("bank_name"),
                reference = rs.getString("reference"),
                transactionHash = rs.getString("transaction_hash"),
                source = TransactionSource.valueOf(rs.getString("source")),
                reviewStatus = ReviewStatus.valueOf(rs.getString("review_status")),
                uncertaintyBits = rs.getInt("uncertainty_bits"),
                createdAt = rs.getLong("created_at"),
                updatedAt = rs.getLong("updated_at"),
                isDeleted = rs.getInt("is_deleted")
            )
        }

        override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> = error("Not needed")
        override suspend fun getByHash(hash: String): TransactionEntity? = error("Not needed")
        override suspend fun findDuplicateCandidate(reference: String, recordType: RecordType, amountPaise: Long, minDateTime: Long, maxDateTime: Long): TransactionEntity? = error("Not needed")
        override suspend fun findExpensesByReference(reference: String): List<TransactionEntity> = error("Not needed")
        override suspend fun findExpensesByMerchant(merchantName: String, minDateTime: Long, maxDateTime: Long): List<TransactionEntity> = error("Not needed")
        override fun getAllActiveTransactions(): Flow<List<TransactionEntity>> = error("Not needed")
        override fun getTransactionsForPeriod(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = error("Not needed")
        override fun getTransactionsByReviewStatus(status: ReviewStatus): Flow<List<TransactionEntity>> = error("Not needed")
        override fun getUnreviewedCount(): Flow<Int> = error("Not needed")
        override suspend fun softDelete(id: Long, updatedAt: Long) = error("Not needed")
        override fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = error("Not needed")
    }

    /**
     * Real SQLite implementation of MerchantCategoryDao.
     */
    private class SqliteMerchantCategoryDao(private val conn: Connection) : MerchantCategoryDao {
        var failOnUpsert: Boolean = false

        override suspend fun upsert(entity: MerchantCategoryEntity) {
            if (failOnUpsert) {
                // Violates NOT NULL PRIMARY KEY constraint or throws real SQLException
                throw SQLException("Simulated SQLite write failure on merchant_categories (disk/constraint failure)")
            }
            val sql = "INSERT OR REPLACE INTO merchant_categories (merchantKey, category, updatedAt) VALUES (?, ?, ?)"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entity.merchantKey)
                stmt.setString(2, entity.category)
                stmt.setLong(3, entity.updatedAt)
                stmt.executeUpdate()
            }
        }

        override suspend fun getByKey(key: String): MerchantCategoryEntity? {
            conn.prepareStatement("SELECT merchantKey, category, updatedAt FROM merchant_categories WHERE merchantKey = ? LIMIT 1").use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) {
                        MerchantCategoryEntity(
                            merchantKey = rs.getString("merchantKey"),
                            category = rs.getString("category"),
                            updatedAt = rs.getLong("updatedAt")
                        )
                    } else null
                }
            }
        }

        override suspend fun getCategory(key: String): String? {
            conn.prepareStatement("SELECT category FROM merchant_categories WHERE merchantKey = ? LIMIT 1").use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("category") else null
                }
            }
        }

        override suspend fun getAll(): List<MerchantCategoryEntity> {
            val list = mutableListOf<MerchantCategoryEntity>()
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT merchantKey, category, updatedAt FROM merchant_categories").use { rs ->
                    while (rs.next()) {
                        list.add(
                            MerchantCategoryEntity(
                                merchantKey = rs.getString("merchantKey"),
                                category = rs.getString("category"),
                                updatedAt = rs.getLong("updatedAt")
                            )
                        )
                    }
                }
            }
            return list
        }
    }
}
