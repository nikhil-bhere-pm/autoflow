package com.autoflow.app.data.repository

import com.autoflow.app.domain.search.TransactionSearchHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class TransactionSearchRealSqliteTest {

    private lateinit var conn: Connection

    @Before
    fun setup() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val schemaFile = findSchemaFile(6)
        val createStatements = extractCreateStatements(schemaFile.readText())
        conn.createStatement().use { stmt ->
            for (sql in createStatements) {
                stmt.execute(sql)
            }
        }
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) {
            conn.close()
        }
    }

    private fun insertTx(
        id: Long,
        amountPaise: Long,
        merchant: String,
        category: String,
        note: String? = null,
        dateTime: Long = 1000L + id,
        isDeleted: Int = 0
    ) {
        val sql = """
            INSERT INTO transactions (
                id, amount_paise, record_type, date_time, merchant_name, category,
                categorySource, payment_mode, note, transaction_hash, source,
                review_status, uncertainty_bits, created_at, updated_at, is_deleted
            ) VALUES (?, ?, 'EXPENSE', ?, ?, ?, 'AUTO', 'UPI', ?, ?, 'SMS_AUTO', 'CONFIRMED', 0, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            ps.setLong(1, id)
            ps.setLong(2, amountPaise)
            ps.setLong(3, dateTime)
            ps.setString(4, merchant)
            ps.setString(5, category)
            ps.setString(6, note)
            ps.setString(7, "hash_$id")
            ps.setLong(8, dateTime)
            ps.setLong(9, dateTime)
            ps.setInt(10, isDeleted)
            ps.executeUpdate()
        }
    }

    private fun runSearch(query: String, limit: Int = 200): List<Long> {
        val textPattern = TransactionSearchHelper.buildTextPattern(query)
        val exactPaise = TransactionSearchHelper.parseRupeesToPaise(query)
        val digitPattern = TransactionSearchHelper.extractDigitsPattern(query)

        val sql = """
            SELECT id FROM transactions 
            WHERE is_deleted = 0 
              AND (
                merchant_name LIKE ? ESCAPE '\' 
                OR (note IS NOT NULL AND note LIKE ? ESCAPE '\') 
                OR category LIKE ? ESCAPE '\' 
                OR (? IS NOT NULL AND amount_paise = ?) 
                OR (? IS NOT NULL AND CAST(amount_paise AS TEXT) LIKE ? ESCAPE '\')
              )
            ORDER BY date_time DESC, id DESC 
            LIMIT ?
        """.trimIndent()

        val results = mutableListOf<Long>()
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, textPattern)
            ps.setString(2, textPattern)
            ps.setString(3, textPattern)
            if (exactPaise != null) {
                ps.setLong(4, exactPaise)
                ps.setLong(5, exactPaise)
            } else {
                ps.setNull(4, java.sql.Types.BIGINT)
                ps.setNull(5, java.sql.Types.BIGINT)
            }
            if (digitPattern != null) {
                ps.setString(6, digitPattern)
                ps.setString(7, digitPattern)
            } else {
                ps.setNull(6, java.sql.Types.VARCHAR)
                ps.setNull(7, java.sql.Types.VARCHAR)
            }
            ps.setInt(8, limit)

            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(rs.getLong("id"))
                }
            }
        }
        return results
    }

    @Test
    fun testSearchByMerchant_caseInsensitive() {
        insertTx(id = 1, amountPaise = 25000L, merchant = "Swiggy", category = "Food")
        insertTx(id = 2, amountPaise = 15000L, merchant = "Zomato", category = "Food")

        val results = runSearch("swiggy")
        assertEquals(listOf(1L), results)

        val resultsCaps = runSearch("SWIGGY")
        assertEquals(listOf(1L), resultsCaps)
    }

    @Test
    fun testSearchByNote_caseInsensitive() {
        insertTx(id = 1, amountPaise = 50000L, merchant = "Cafe", category = "Food", note = "Dinner with team")
        insertTx(id = 2, amountPaise = 20000L, merchant = "Cafe", category = "Food", note = "Coffee")

        val results = runSearch("dinner")
        assertEquals(listOf(1L), results)
    }

    @Test
    fun testSearchByCategory_caseInsensitive() {
        insertTx(id = 1, amountPaise = 30000L, merchant = "Pharmacy", category = "Health")
        insertTx(id = 2, amountPaise = 20000L, merchant = "Uber", category = "Transport")

        val results = runSearch("health")
        assertEquals(listOf(1L), results)
    }

    @Test
    fun testSearchByAmount_rupeeParsing_320_and_320_00() {
        insertTx(id = 1, amountPaise = 32000L, merchant = "Shop A", category = "Groceries") // ₹320.00
        insertTx(id = 2, amountPaise = 45000L, merchant = "Shop B", category = "Groceries") // ₹450.00

        // typing "320" matches 32000 paise (₹320.00)
        val results320 = runSearch("320")
        assertTrue(results320.contains(1L))
        assertFalse(results320.contains(2L))

        // typing "320.00" matches 32000 paise (₹320.00)
        val results320Dot00 = runSearch("320.00")
        assertEquals(listOf(1L), results320Dot00)
    }

    @Test
    fun testSearch_escapePercentAndUnderscore() {
        insertTx(id = 1, amountPaise = 10000L, merchant = "50% Off Store", category = "Shopping")
        insertTx(id = 2, amountPaise = 10000L, merchant = "500 Off Store", category = "Shopping")
        insertTx(id = 3, amountPaise = 5000L, merchant = "Tea_Stall", category = "Food")
        insertTx(id = 4, amountPaise = 5000L, merchant = "Tea1Stall", category = "Food")

        // Searching for literal 50% must not match 500
        val resultsPercent = runSearch("50%")
        assertEquals(listOf(1L), resultsPercent)

        // Searching for literal Tea_ must not match Tea1Stall
        val resultsUnderscore = runSearch("Tea_")
        assertEquals(listOf(3L), resultsUnderscore)
    }

    @Test
    fun testSearch_deletedTransactionsExcluded() {
        insertTx(id = 1, amountPaise = 20000L, merchant = "Active Mart", category = "Groceries", isDeleted = 0)
        insertTx(id = 2, amountPaise = 20000L, merchant = "Deleted Mart", category = "Groceries", isDeleted = 1)

        val results = runSearch("Mart")
        assertEquals(listOf(1L), results)
    }

    @Test
    fun testSearch_limit200() {
        for (i in 1..250) {
            insertTx(id = i.toLong(), amountPaise = 10000L, merchant = "Target Shop $i", category = "Shopping", dateTime = i.toLong() * 1000)
        }

        val results = runSearch("Target Shop")
        assertEquals(200, results.size)
        // Newest first
        assertEquals(250L, results.first())
        assertEquals(51L, results.last())
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

        return statements
    }
}
