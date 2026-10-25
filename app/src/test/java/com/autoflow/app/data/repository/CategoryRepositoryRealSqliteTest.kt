package com.autoflow.app.data.repository

import com.autoflow.app.data.db.AutoFlowDatabase
import com.autoflow.app.data.db.dao.CustomCategoryDao
import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.CustomCategoryEntity
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.domain.category.CategoryItem
import com.autoflow.app.domain.category.CategoryOperationResult
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.stats.categoryBreakdown
import com.autoflow.app.domain.stats.fiveMonthSpendingSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.YearMonth
import java.time.ZoneId

class CategoryRepositoryRealSqliteTest {

    private lateinit var conn: Connection
    private lateinit var customCategoryDao: SqliteCustomCategoryDao
    private lateinit var transactionDao: SqliteTransactionDao
    private lateinit var merchantCategoryDao: SqliteMerchantCategoryDao
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var transactionRepository: TransactionRepository

    private val jdbcTransactionRunner: suspend (suspend () -> Unit) -> Unit = { block ->
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

    private fun findSchemaFile(version: Int): File {
        val candidates = listOf(
            File("schemas/com.autoflow.app.data.db.AutoFlowDatabase/$version.json"),
            File("app/schemas/com.autoflow.app.data.db.AutoFlowDatabase/$version.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Schema file for version $version not found")
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

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val jsonV4 = findSchemaFile(4).readText()
        val statements = extractCreateStatements(jsonV4)
        conn.createStatement().use { stmt ->
            for (sql in statements) {
                stmt.execute(sql)
            }
        }

        customCategoryDao = SqliteCustomCategoryDao(conn)
        transactionDao = SqliteTransactionDao(conn)
        merchantCategoryDao = SqliteMerchantCategoryDao(conn)

        categoryRepository = CategoryRepository(
            customCategoryDao = customCategoryDao,
            transactionDao = transactionDao,
            merchantCategoryDao = merchantCategoryDao,
            runInTransaction = jdbcTransactionRunner
        )

        transactionRepository = TransactionRepository(
            transactionDao = transactionDao,
            merchantCategoryDao = merchantCategoryDao,
            customCategoryDao = customCategoryDao,
            runInTransaction = jdbcTransactionRunner
        )
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) {
            conn.close()
        }
    }

    // =========================================================================
    // 6(b) ADD TESTS
    // =========================================================================

    @Test
    fun testAddCategory_valid() = runBlocking {
        val res = categoryRepository.addCategory(
            name = "Coffee",
            iconKey = "COFFEE",
            colorKey = "ORANGE"
        )
        assertTrue(res is CategoryOperationResult.Success)
        val item = (res as CategoryOperationResult.Success).category
        assertNotNull(item)
        assertEquals("Coffee", item!!.name)
        assertEquals("COFFEE", item.iconKey)
        assertEquals("ORANGE", item.colorKey)

        val fromDb = customCategoryDao.getByNameKey("coffee")
        assertNotNull(fromDb)
        assertEquals("coffee", fromDb!!.nameKey)
        assertEquals("Coffee", fromDb.name)
        assertEquals("COFFEE", fromDb.iconKey)
        assertEquals("ORANGE", fromDb.colorKey)
    }

    @Test
    fun testAddCategory_duplicateWithDifferentCaseAndSpaces_rejected() = runBlocking {
        val res1 = categoryRepository.addCategory("Pet Care", "PAW", "GREEN")
        assertTrue(res1 is CategoryOperationResult.Success)

        // Try adding with different casing and extra spaces
        val res2 = categoryRepository.addCategory("   pet   care  ", "PAW", "BLUE")
        assertEquals(CategoryOperationResult.NameNotUnique, res2)
    }

    @Test
    fun testAddCategory_builtInName_rejected() = runBlocking {
        val builtInNames = listOf(
            "Food", "food", "  GROCERIES  ", "bills", "Travel", "Others",
            "unknown", "Refund", "Cash", "Not counted"
        )
        for (name in builtInNames) {
            val res = categoryRepository.addCategory(name, "HOME", "BLUE")
            assertEquals("Expected $name to be rejected with NameNotUnique", CategoryOperationResult.NameNotUnique, res)
        }
    }

    @Test
    fun testAddCategory_thirtyFirst_rejected() = runBlocking {
        for (i in 1..30) {
            val res = categoryRepository.addCategory("Custom $i", "TAG" /* wait, valid icon */.let { "BOOK" }, "BLUE")
            assertTrue("Expected item $i to be added successfully", res is CategoryOperationResult.Success)
        }

        assertEquals(30, customCategoryDao.count())

        val res31 = categoryRepository.addCategory("Custom 31", "BOOK", "BLUE")
        assertEquals(CategoryOperationResult.TooManyCategories, res31)
    }

    @Test
    fun testAddCategory_primaryKeyBackstop_mapsToNameNotUnique() = runBlocking {
        // Direct insertion simulating race condition
        conn.prepareStatement("INSERT INTO custom_categories (name_key, name, icon_key, color_key, created_at) VALUES (?, ?, ?, ?, ?)").use { stmt ->
            stmt.setString(1, "gym")
            stmt.setString(2, "Gym")
            stmt.setString(3, "TOOL")
            stmt.setString(4, "RED")
            stmt.setLong(5, 1000L)
            stmt.executeUpdate()
        }

        // Now test inserting the same key when DAO cache or pre-check might race
        val res = categoryRepository.addCategory("Gym", "TOOL", "RED")
        assertEquals(CategoryOperationResult.NameNotUnique, res)
    }

    @Test
    fun testAddCategory_validationRules() = runBlocking {
        assertEquals(CategoryOperationResult.NameBlank, categoryRepository.addCategory("   ", "BOOK", "BLUE"))
        assertEquals(CategoryOperationResult.NameTooLong, categoryRepository.addCategory("This name is way too long for a category", "BOOK", "BLUE"))
        assertEquals(CategoryOperationResult.NameInvalidCharacters, categoryRepository.addCategory("Books @ Library!", "BOOK", "BLUE"))
        assertEquals(CategoryOperationResult.InvalidIcon, categoryRepository.addCategory("Books", "NON_EXISTENT_ICON", "BLUE"))
        assertEquals(CategoryOperationResult.InvalidColor, categoryRepository.addCategory("Books", "BOOK", "NON_EXISTENT_COLOR"))
    }

    // =========================================================================
    // 6(c) RENAME TESTS ON REAL SQLITE
    // =========================================================================

    @Test
    fun testRenameCategory_onRealSqlite_transactionsAndRulesFollow() = runBlocking {
        // 1. Add custom category
        val addRes = categoryRepository.addCategory("Books", "BOOK", "BLUE")
        assertTrue(addRes is CategoryOperationResult.Success)

        // 2. Seed transactions (one active, one soft-deleted) and a merchant rule
        conn.prepareStatement("""
            INSERT INTO transactions (id, amount_paise, record_type, date_time, merchant_name, category, categorySource, payment_mode, transaction_hash, source, review_status, uncertainty_bits, created_at, updated_at, is_deleted)
            VALUES (?, ?, 'EXPENSE', 1000, 'Kindle', ?, 'USER', 'CREDIT', ?, 'SMS_AUTO', 'CONFIRMED', 0, 1000, 1000, ?)
        """).use { stmt ->
            // Active transaction
            stmt.setLong(1, 10L)
            stmt.setLong(2, 50000L)
            stmt.setString(3, "Books")
            stmt.setString(4, "HASH_10")
            stmt.setInt(5, 0)
            stmt.executeUpdate()

            // Soft-deleted transaction
            stmt.setLong(1, 11L)
            stmt.setLong(2, 30000L)
            stmt.setString(3, "Books")
            stmt.setString(4, "HASH_11")
            stmt.setInt(5, 1)
            stmt.executeUpdate()
        }

        conn.prepareStatement("INSERT INTO merchant_categories (merchantKey, category, updatedAt) VALUES ('kindle', 'Books', 1000)").use {
            it.executeUpdate()
        }

        // 3. Rename category "Books" -> "Reading"
        val renameRes = categoryRepository.updateCategory(
            currentName = "Books",
            newName = "Reading",
            iconKey = "BOOK",
            colorKey = "PINK"
        )
        assertTrue(renameRes is CategoryOperationResult.Success)

        // 4. Verify in real SQLite: custom_categories row updated
        assertNull(customCategoryDao.getByNameKey("books"))
        val newCat = customCategoryDao.getByNameKey("reading")
        assertNotNull(newCat)
        assertEquals("reading", newCat!!.nameKey)
        assertEquals("Reading", newCat.name)
        assertEquals("PINK", newCat.colorKey)

        // 5. Verify transactions (both active and soft-deleted) updated
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT category, is_deleted FROM transactions WHERE id = 10").use { rs ->
                assertTrue(rs.next())
                assertEquals("Reading", rs.getString("category"))
                assertEquals(0, rs.getInt("is_deleted"))
            }
            stmt.executeQuery("SELECT category, is_deleted FROM transactions WHERE id = 11").use { rs ->
                assertTrue(rs.next())
                assertEquals("Reading", rs.getString("category"))
                assertEquals(1, rs.getInt("is_deleted"))
            }

            // 6. Verify merchant_categories updated
            stmt.executeQuery("SELECT category FROM merchant_categories WHERE merchantKey = 'kindle'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Reading", rs.getString("category"))
            }
        }
    }

    @Test
    fun testRenameCategory_caseOnlyRenameAllowed() = runBlocking {
        categoryRepository.addCategory("reading", "BOOK", "BLUE")

        // Rename reading -> Reading (changes capitalization only)
        val res = categoryRepository.updateCategory("reading", "Reading", "BOOK", "TEAL")
        assertTrue(res is CategoryOperationResult.Success)

        val updated = customCategoryDao.getByNameKey("reading")
        assertNotNull(updated)
        assertEquals("Reading", updated!!.name)
        assertEquals("TEAL", updated.colorKey)
    }

    @Test
    fun testRenameCategory_renameToAnotherCategory_rejected() = runBlocking {
        categoryRepository.addCategory("Books", "BOOK", "BLUE")
        categoryRepository.addCategory("Games", "TOOL", "PURPLE")

        val res = categoryRepository.updateCategory("Books", "Games", "BOOK", "BLUE")
        assertEquals(CategoryOperationResult.NameNotUnique, res)
    }

    @Test
    fun testRenameCategory_renameBuiltIn_rejected() = runBlocking {
        val res = categoryRepository.updateCategory("Food", "Yum", "HOME", "ORANGE")
        assertEquals(CategoryOperationResult.BuiltInCategoryReadOnly, res)
    }

    @Test
    fun testRenameCategory_nonExistent_rejected() = runBlocking {
        val res = categoryRepository.updateCategory("NonExistent", "NewName", "HOME", "ORANGE")
        assertEquals(CategoryOperationResult.CategoryNotFound, res)
    }

    // =========================================================================
    // 6(d) DELETE TESTS ON REAL SQLITE
    // =========================================================================

    @Test
    fun testDeleteCategory_onRealSqlite() = runBlocking {
        // 1. Add category
        categoryRepository.addCategory("Subscriptions", "PHONE", "GOLD")

        // 2. Seed active & soft-deleted transactions, and a merchant category rule
        conn.prepareStatement("""
            INSERT INTO transactions (id, amount_paise, record_type, date_time, merchant_name, category, categorySource, payment_mode, transaction_hash, source, review_status, uncertainty_bits, created_at, updated_at, is_deleted)
            VALUES (?, ?, 'EXPENSE', 1000, 'Netflix', 'Subscriptions', 'USER', 'CREDIT', ?, 'SMS_AUTO', 'CONFIRMED', 0, 1000, 1000, ?)
        """).use { stmt ->
            // Active
            stmt.setLong(1, 20L)
            stmt.setLong(2, 79900L)
            stmt.setString(3, "HASH_20")
            stmt.setInt(4, 0)
            stmt.executeUpdate()

            // Soft-deleted
            stmt.setLong(1, 21L)
            stmt.setLong(2, 19900L)
            stmt.setString(3, "HASH_21")
            stmt.setInt(4, 1)
            stmt.executeUpdate()
        }

        conn.prepareStatement("INSERT INTO merchant_categories (merchantKey, category, updatedAt) VALUES ('netflix', 'Subscriptions', 1000)").use {
            it.executeUpdate()
        }

        // Check count of non-deleted transactions in category
        assertEquals(1, categoryRepository.countTransactionsInCategory("Subscriptions"))

        // 3. Delete category
        val delRes = categoryRepository.deleteCategory("Subscriptions")
        assertTrue(delRes is CategoryOperationResult.Success)

        // 4. Verify transactions moved to 'Others' with categorySource 'AUTO' (including soft-deleted)
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT category, categorySource, is_deleted FROM transactions WHERE id = 20").use { rs ->
                assertTrue(rs.next())
                assertEquals("Others", rs.getString("category"))
                assertEquals("AUTO", rs.getString("categorySource"))
                assertEquals(0, rs.getInt("is_deleted"))
            }
            stmt.executeQuery("SELECT category, categorySource, is_deleted FROM transactions WHERE id = 21").use { rs ->
                assertTrue(rs.next())
                assertEquals("Others", rs.getString("category"))
                assertEquals("AUTO", rs.getString("categorySource"))
                assertEquals(1, rs.getInt("is_deleted"))
            }

            // 5. Verify merchant_categories rule deleted
            stmt.executeQuery("SELECT COUNT(*) FROM merchant_categories WHERE category = 'Subscriptions'").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1))
            }

            // 6. Verify custom_categories row deleted
            stmt.executeQuery("SELECT COUNT(*) FROM custom_categories WHERE name_key = 'subscriptions'").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1))
            }
        }

        // 7. Verify the name can be added again afterwards
        val reAddRes = categoryRepository.addCategory("Subscriptions", "PHONE", "GOLD")
        assertTrue(reAddRes is CategoryOperationResult.Success)
    }

    @Test
    fun testDeleteCategory_builtInRejected() = runBlocking {
        val res = categoryRepository.deleteCategory("Food")
        assertEquals(CategoryOperationResult.BuiltInCategoryReadOnly, res)
    }

    @Test
    fun testDeleteCategory_notFoundRejected() = runBlocking {
        val res = categoryRepository.deleteCategory("NonExistent")
        assertEquals(CategoryOperationResult.CategoryNotFound, res)
    }

    // =========================================================================
    // 6(e) ATOMICITY ON PRODUCTION CODE (RENAME & DELETE)
    // =========================================================================

    @Test
    fun testAtomicity_rename_whenLastWriteFails_nothingChanged() = runBlocking {
        categoryRepository.addCategory("Coffee", "COFFEE", "ORANGE")

        // Seed transaction and rule
        conn.prepareStatement("""
            INSERT INTO transactions (id, amount_paise, record_type, date_time, merchant_name, category, categorySource, payment_mode, transaction_hash, source, review_status, uncertainty_bits, created_at, updated_at, is_deleted)
            VALUES (30, 45000, 'EXPENSE', 1000, 'Cafe', 'Coffee', 'USER', 'UPI', 'HASH3', 'SMS_AUTO', 'CONFIRMED', 0, 1000, 1000, 0)
        """).use { it.executeUpdate() }

        conn.prepareStatement("INSERT INTO merchant_categories (merchantKey, category, updatedAt) VALUES ('cafe', 'Coffee', 1000)").use {
            it.executeUpdate()
        }

        // Cause the merchant category update write to fail
        merchantCategoryDao.failOnUpdate = true

        try {
            categoryRepository.updateCategory(
                currentName = "Coffee",
                newName = "Beverages",
                iconKey = "COFFEE",
                colorKey = "BLUE"
            )
            fail("Expected SQLException from failing last write")
        } catch (e: SQLException) {
            // Expected
        }

        // Verify everything in real SQLite was rolled back
        val fromDb = customCategoryDao.getByNameKey("coffee")
        assertNotNull("Coffee must still exist", fromDb)
        assertEquals("Coffee", fromDb!!.name)
        assertNull("Beverages must not exist", customCategoryDao.getByNameKey("beverages"))

        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT category FROM transactions WHERE id = 30").use { rs ->
                assertTrue(rs.next())
                assertEquals("Coffee", rs.getString("category"))
            }
            stmt.executeQuery("SELECT category FROM merchant_categories WHERE merchantKey = 'cafe'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Coffee", rs.getString("category"))
            }
        }
    }

    @Test
    fun testAtomicity_delete_whenLastWriteFails_nothingChanged() = runBlocking {
        categoryRepository.addCategory("Pets", "PAW", "GREEN")

        // Seed transaction and rule
        conn.prepareStatement("""
            INSERT INTO transactions (id, amount_paise, record_type, date_time, merchant_name, category, categorySource, payment_mode, transaction_hash, source, review_status, uncertainty_bits, created_at, updated_at, is_deleted)
            VALUES (40, 25000, 'EXPENSE', 1000, 'Vet', 'Pets', 'USER', 'UPI', 'HASH4', 'SMS_AUTO', 'CONFIRMED', 0, 1000, 1000, 0)
        """).use { it.executeUpdate() }

        conn.prepareStatement("INSERT INTO merchant_categories (merchantKey, category, updatedAt) VALUES ('vet', 'Pets', 1000)").use {
            it.executeUpdate()
        }

        // Cause deleteByKey in customCategoryDao to fail
        customCategoryDao.failOnDelete = true

        try {
            categoryRepository.deleteCategory("Pets")
            fail("Expected SQLException from failing last write")
        } catch (e: SQLException) {
            // Expected
        }

        // Verify everything in real SQLite was rolled back
        val fromDb = customCategoryDao.getByNameKey("pets")
        assertNotNull("Pets must still exist", fromDb)

        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT category, categorySource FROM transactions WHERE id = 40").use { rs ->
                assertTrue(rs.next())
                assertEquals("Pets", rs.getString("category"))
                assertEquals("USER", rs.getString("categorySource"))
            }
            stmt.executeQuery("SELECT category FROM merchant_categories WHERE merchantKey = 'vet'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Pets", rs.getString("category"))
            }
        }
    }

    // =========================================================================
    // 6(g) updateTransaction WITH CUSTOM & UNKNOWN CATEGORY
    // =========================================================================

    @Test
    fun testUpdateTransaction_withCustomCategory_succeeds() = runBlocking {
        categoryRepository.addCategory("Books", "BOOK", "BLUE")

        // Seed a transaction
        conn.prepareStatement("""
            INSERT INTO transactions (id, amount_paise, record_type, date_time, merchant_name, category, categorySource, payment_mode, transaction_hash, source, review_status, uncertainty_bits, created_at, updated_at, is_deleted)
            VALUES (50, 60000, 'EXPENSE', 1000, 'Amazon Books', 'Food', 'AUTO', 'UPI', 'HASH5', 'SMS_AUTO', 'CONFIRMED', 0, 1000, 1000, 0)
        """).use { it.executeUpdate() }

        val res = transactionRepository.updateTransaction(
            id = 50L,
            merchantName = "Amazon Books",
            category = "Books",
            dateTime = 1500L,
            paymentMode = PaymentMode.UPI,
            note = null
        )

        assertTrue("Expected update to succeed with custom category Books", res is UpdateTransactionResult.Success)
        val updated = (res as UpdateTransactionResult.Success).transaction
        assertEquals("Books", updated.category)

        // Verify in real SQLite
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT category, categorySource FROM transactions WHERE id = 50").use { rs ->
                assertTrue(rs.next())
                assertEquals("Books", rs.getString("category"))
                assertEquals("USER", rs.getString("categorySource"))
            }
            // Merchant rule also saved with custom category
            stmt.executeQuery("SELECT category FROM merchant_categories WHERE merchantKey = 'amazon books'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Books", rs.getString("category"))
            }
        }
    }

    @Test
    fun testUpdateTransaction_withUnknownCategory_fails() = runBlocking {
        // Seed a transaction
        conn.prepareStatement("""
            INSERT INTO transactions (id, amount_paise, record_type, date_time, merchant_name, category, categorySource, payment_mode, transaction_hash, source, review_status, uncertainty_bits, created_at, updated_at, is_deleted)
            VALUES (60, 60000, 'EXPENSE', 1000, 'Mystery Shop', 'Food', 'AUTO', 'UPI', 'HASH6', 'SMS_AUTO', 'CONFIRMED', 0, 1000, 1000, 0)
        """).use { it.executeUpdate() }

        val res = transactionRepository.updateTransaction(
            id = 60L,
            merchantName = "Mystery Shop",
            category = "InventedCategory123",
            dateTime = 1500L,
            paymentMode = PaymentMode.UPI,
            note = null
        )

        assertEquals(UpdateTransactionResult.CategoryNotFound, res)
    }

    // =========================================================================
    // SECTION 5: STATS WITH CUSTOM CATEGORY
    // =========================================================================

    @Test
    fun testCategoryBreakdown_andMonthlyStats_withCustomCategory() {
        val txCustom = FinancialTransaction(
            id = 1L,
            amountPaise = 30000L, // 300 Rs
            recordType = RecordType.EXPENSE,
            dateTime = 1700000000000L,
            merchantName = "Book Store",
            category = "Books", // Custom category
            categorySource = "USER",
            paymentMode = "UPI",
            note = null,
            accountNumber = null,
            bankName = null,
            reference = null,
            transactionHash = "H1",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyReasons = emptySet(),
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            isDeleted = false
        )

        val txFood = txCustom.copy(
            id = 2L,
            amountPaise = 70000L, // 700 Rs
            category = "Food",
            transactionHash = "H2"
        )

        val breakdown = categoryBreakdown(listOf(txCustom, txFood))
        assertEquals(2, breakdown.size)
        val foodItem = breakdown.first { it.category == "Food" }
        val booksItem = breakdown.first { it.category == "Books" }

        assertEquals(70000L, foodItem.totalPaise)
        assertEquals(70, foodItem.percent)
        assertEquals(30000L, booksItem.totalPaise)
        assertEquals(30, booksItem.percent)

        // Monthly series includes custom category spend
        val series = fiveMonthSpendingSeries(
            records = listOf(txCustom, txFood),
            selectedMonth = YearMonth.of(2023, 11),
            zone = ZoneId.of("UTC")
        )
        val targetMonth = series.last() // 2023-11
        assertEquals(100000L, targetMonth.totalExpensePaise)
        assertEquals(100000L, targetMonth.netSpendPaise)
    }

    // =========================================================================
    // SQLITE DAO WRAPPERS FOR REAL SQLITE TEST
    // =========================================================================

    class SqliteCustomCategoryDao(private val conn: Connection) : CustomCategoryDao {
        var failOnDelete = false

        override fun getAll(): Flow<List<CustomCategoryEntity>> = kotlinx.coroutines.flow.flow {
            emit(getAllSync())
        }

        override suspend fun getAllSync(): List<CustomCategoryEntity> {
            val list = mutableListOf<CustomCategoryEntity>()
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT name_key, name, icon_key, color_key, created_at FROM custom_categories ORDER BY created_at ASC").use { rs ->
                    while (rs.next()) {
                        list.add(
                            CustomCategoryEntity(
                                nameKey = rs.getString("name_key"),
                                name = rs.getString("name"),
                                iconKey = rs.getString("icon_key"),
                                colorKey = rs.getString("color_key"),
                                createdAt = rs.getLong("created_at")
                            )
                        )
                    }
                }
            }
            return list
        }

        override suspend fun getByNameKey(nameKey: String): CustomCategoryEntity? {
            conn.prepareStatement("SELECT name_key, name, icon_key, color_key, created_at FROM custom_categories WHERE name_key = ? LIMIT 1").use { stmt ->
                stmt.setString(1, nameKey)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return CustomCategoryEntity(
                            nameKey = rs.getString("name_key"),
                            name = rs.getString("name"),
                            iconKey = rs.getString("icon_key"),
                            colorKey = rs.getString("color_key"),
                            createdAt = rs.getLong("created_at")
                        )
                    }
                }
            }
            return null
        }

        override suspend fun count(): Int {
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM custom_categories").use { rs ->
                    if (rs.next()) return rs.getInt(1)
                }
            }
            return 0
        }

        override suspend fun insert(entity: CustomCategoryEntity) {
            conn.prepareStatement("INSERT INTO custom_categories (name_key, name, icon_key, color_key, created_at) VALUES (?, ?, ?, ?, ?)").use { stmt ->
                stmt.setString(1, entity.nameKey)
                stmt.setString(2, entity.name)
                stmt.setString(3, entity.iconKey)
                stmt.setString(4, entity.colorKey)
                stmt.setLong(5, entity.createdAt)
                stmt.executeUpdate()
            }
        }

        override suspend fun update(entity: CustomCategoryEntity) {
            conn.prepareStatement("UPDATE custom_categories SET name = ?, icon_key = ?, color_key = ? WHERE name_key = ?").use { stmt ->
                stmt.setString(1, entity.name)
                stmt.setString(2, entity.iconKey)
                stmt.setString(3, entity.colorKey)
                stmt.setString(4, entity.nameKey)
                stmt.executeUpdate()
            }
        }

        override suspend fun updateCategoryValues(
            oldKey: String,
            newKey: String,
            newName: String,
            newIcon: String,
            newColor: String
        ): Int {
            conn.prepareStatement("UPDATE custom_categories SET name_key = ?, name = ?, icon_key = ?, color_key = ? WHERE name_key = ?").use { stmt ->
                stmt.setString(1, newKey)
                stmt.setString(2, newName)
                stmt.setString(3, newIcon)
                stmt.setString(4, newColor)
                stmt.setString(5, oldKey)
                return stmt.executeUpdate()
            }
        }

        override suspend fun deleteByKey(nameKey: String): Int {
            if (failOnDelete) {
                throw SQLException("Simulated failure deleting custom category")
            }
            conn.prepareStatement("DELETE FROM custom_categories WHERE name_key = ?").use { stmt ->
                stmt.setString(1, nameKey)
                return stmt.executeUpdate()
            }
        }
    }

    class SqliteMerchantCategoryDao(private val conn: Connection) : MerchantCategoryDao {
        var failOnUpdate = false

        override suspend fun getByKey(key: String): MerchantCategoryEntity? {
            conn.prepareStatement("SELECT merchantKey, category, updatedAt FROM merchant_categories WHERE merchantKey = ? LIMIT 1").use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return MerchantCategoryEntity(
                            merchantKey = rs.getString("merchantKey"),
                            category = rs.getString("category"),
                            updatedAt = rs.getLong("updatedAt")
                        )
                    }
                }
            }
            return null
        }

        override suspend fun getCategory(key: String): String? = getByKey(key)?.category

        override suspend fun upsert(entity: MerchantCategoryEntity) {
            conn.prepareStatement("INSERT OR REPLACE INTO merchant_categories (merchantKey, category, updatedAt) VALUES (?, ?, ?)").use { stmt ->
                stmt.setString(1, entity.merchantKey)
                stmt.setString(2, entity.category)
                stmt.setLong(3, entity.updatedAt)
                stmt.executeUpdate()
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

        override suspend fun updateCategory(oldCategory: String, newCategory: String): Int {
            if (failOnUpdate) {
                throw SQLException("Simulated failure updating merchant_categories")
            }
            conn.prepareStatement("UPDATE merchant_categories SET category = ? WHERE category = ?").use { stmt ->
                stmt.setString(1, newCategory)
                stmt.setString(2, oldCategory)
                return stmt.executeUpdate()
            }
        }

        override suspend fun deleteByCategory(category: String): Int {
            conn.prepareStatement("DELETE FROM merchant_categories WHERE category = ?").use { stmt ->
                stmt.setString(1, category)
                return stmt.executeUpdate()
            }
        }
    }

    class SqliteTransactionDao(private val conn: Connection) : TransactionDao {
        override suspend fun insert(transaction: TransactionEntity): Long {
            conn.prepareStatement("""
                INSERT INTO transactions (id, amount_paise, record_type, date_time, merchant_name, category, categorySource, payment_mode, note, account_number, bank_name, reference, transaction_hash, source, review_status, uncertainty_bits, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """).use { stmt ->
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

        override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> {
            return transactions.map { insert(it) }
        }

        override suspend fun update(transaction: TransactionEntity) {
            conn.prepareStatement("""
                UPDATE transactions 
                SET amount_paise = ?, record_type = ?, date_time = ?, merchant_name = ?, category = ?, categorySource = ?, payment_mode = ?, note = ?, account_number = ?, bank_name = ?, reference = ?, transaction_hash = ?, source = ?, review_status = ?, uncertainty_bits = ?, created_at = ?, updated_at = ?, is_deleted = ?
                WHERE id = ?
            """).use { stmt ->
                stmt.setLong(1, transaction.amountPaise)
                stmt.setString(2, transaction.recordType.name)
                stmt.setLong(3, transaction.dateTime)
                stmt.setString(4, transaction.merchantName)
                stmt.setString(5, transaction.category)
                stmt.setString(6, transaction.categorySource)
                stmt.setString(7, transaction.paymentMode)
                stmt.setString(8, transaction.note)
                stmt.setString(9, transaction.accountNumber)
                stmt.setString(10, transaction.bankName)
                stmt.setString(11, transaction.reference)
                stmt.setString(12, transaction.transactionHash)
                stmt.setString(13, transaction.source.name)
                stmt.setString(14, transaction.reviewStatus.name)
                stmt.setInt(15, transaction.uncertaintyBits)
                stmt.setLong(16, transaction.createdAt)
                stmt.setLong(17, transaction.updatedAt)
                stmt.setInt(18, transaction.isDeleted)
                stmt.setLong(19, transaction.id)
                stmt.executeUpdate()
            }
        }

        override suspend fun getById(id: Long): TransactionEntity? {
            conn.prepareStatement("SELECT * FROM transactions WHERE id = ? LIMIT 1").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
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
                }
            }
            return null
        }

        override suspend fun getByHash(hash: String): TransactionEntity? = null
        override suspend fun findDuplicateCandidate(reference: String, recordType: RecordType, amountPaise: Long, minDateTime: Long, maxDateTime: Long): TransactionEntity? = null
        override suspend fun findExpensesByReference(reference: String): List<TransactionEntity> = emptyList()
        override suspend fun findExpensesByMerchant(merchantName: String, minDateTime: Long, maxDateTime: Long): List<TransactionEntity> = emptyList()
        override fun getAllActiveTransactions(): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getTransactionsForPeriod(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getTransactionsByReviewStatus(status: ReviewStatus): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getUnreviewedCount(): Flow<Int> = flowOf(0)
        override suspend fun softDelete(id: Long, updatedAt: Long) {}
        override suspend fun undelete(id: Long, updatedAt: Long) {}

        override suspend fun updateCategory(oldCategory: String, newCategory: String): Int {
            conn.prepareStatement("UPDATE transactions SET category = ? WHERE category = ?").use { stmt ->
                stmt.setString(1, newCategory)
                stmt.setString(2, oldCategory)
                return stmt.executeUpdate()
            }
        }

        override suspend fun reassignCategoryToOthers(category: String, now: Long): Int {
            conn.prepareStatement("UPDATE transactions SET category = 'Others', categorySource = 'AUTO', updated_at = ? WHERE category = ?").use { stmt ->
                stmt.setLong(1, now)
                stmt.setString(2, category)
                return stmt.executeUpdate()
            }
        }

        override suspend fun countActiveTransactionsInCategory(category: String): Int {
            conn.prepareStatement("SELECT COUNT(*) FROM transactions WHERE is_deleted = 0 AND category = ?").use { stmt ->
                stmt.setString(1, category)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) return rs.getInt(1)
                }
            }
            return 0
        }

        override suspend fun getAllAutoCategorizedTransactions(): List<TransactionEntity> {
            val list = mutableListOf<TransactionEntity>()
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM transactions WHERE is_deleted = 0 AND categorySource = 'AUTO'").use { rs ->
                    while (rs.next()) {
                        list.add(
                            TransactionEntity(
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
                        )
                    }
                }
            }
            return list
        }

        override fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())

        override fun searchTransactions(
            textPattern: String,
            exactPaise: Long?,
            digitPattern: String?,
            limit: Int
        ): Flow<List<TransactionEntity>> = flowOf(emptyList())
    }
}
