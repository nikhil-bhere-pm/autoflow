package com.autoflow.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class RoomMigration1To2Test {

    data class ColumnInfo(
        val cid: Int,
        val name: String,
        val type: String,
        val notNull: Boolean,
        val dfltValue: String?,
        val pk: Int
    )

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

        // 3. Setup queries
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

    private fun getTableColumns(conn: Connection, tableName: String): List<ColumnInfo> {
        val columns = mutableListOf<ColumnInfo>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA table_info(`$tableName`)").use { rs ->
                while (rs.next()) {
                    columns.add(
                        ColumnInfo(
                            cid = rs.getInt("cid"),
                            name = rs.getString("name"),
                            type = rs.getString("type"),
                            notNull = rs.getInt("notnull") == 1,
                            dfltValue = rs.getString("dflt_value"),
                            pk = rs.getInt("pk")
                        )
                    )
                }
            }
        }
        return columns
    }

    @Test
    fun testRoomMigration1To2_onRealSqlite() {
        val fileV1 = findSchemaFile(1)
        val fileV2 = findSchemaFile(2)
        val jsonV1 = fileV1.readText()
        val jsonV2 = fileV2.readText()

        val v1CreateStatements = extractCreateStatements(jsonV1)
        assertTrue("v1 must have CREATE statements", v1CreateStatements.isNotEmpty())

        val v2CreateStatements = extractCreateStatements(jsonV2)
        assertTrue("v2 must have CREATE statements", v2CreateStatements.isNotEmpty())

        // 1. Establish in-memory SQLite connection for migrated DB
        val migratedConn = DriverManager.getConnection("jdbc:sqlite::memory:")

        // 2. Execute v1 schema CREATE statements
        migratedConn.createStatement().use { stmt ->
            for (sql in v1CreateStatements) {
                stmt.execute(sql)
            }
        }

        // 3. Insert 3 sample transactions:
        // tx1: EXPENSE
        // tx2: REFUND
        // tx3: soft-deleted
        val insertTxSql = """
            INSERT INTO transactions (
                id, amount_paise, record_type, date_time, merchant_name, category,
                account_number, bank_name, reference, transaction_hash, source,
                review_status, uncertainty_bits, created_at, updated_at, is_deleted
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        migratedConn.prepareStatement(insertTxSql).use { stmt ->
            // tx1: EXPENSE
            stmt.setLong(1, 1L)
            stmt.setLong(2, 50000L)
            stmt.setString(3, "EXPENSE")
            stmt.setLong(4, 1700000000000L)
            stmt.setString(5, "Starbucks")
            stmt.setString(6, "Food")
            stmt.setString(7, "XX1234")
            stmt.setString(8, "HDFC")
            stmt.setString(9, "REF123")
            stmt.setString(10, "HASH1")
            stmt.setString(11, "SMS_AUTO")
            stmt.setString(12, "CONFIRMED")
            stmt.setInt(13, 0)
            stmt.setLong(14, 1700000000000L)
            stmt.setLong(15, 1700000000000L)
            stmt.setInt(16, 0)
            stmt.executeUpdate()

            // tx2: REFUND
            stmt.setLong(1, 2L)
            stmt.setLong(2, 20000L)
            stmt.setString(3, "REFUND")
            stmt.setLong(4, 1700000001000L)
            stmt.setString(5, "Starbucks")
            stmt.setString(6, "Others")
            stmt.setString(7, "XX1234")
            stmt.setString(8, "HDFC")
            stmt.setString(9, "REF123")
            stmt.setString(10, "HASH2")
            stmt.setString(11, "SMS_AUTO")
            stmt.setString(12, "CONFIRMED")
            stmt.setInt(13, 0)
            stmt.setLong(14, 1700000001000L)
            stmt.setLong(15, 1700000001000L)
            stmt.setInt(16, 0)
            stmt.executeUpdate()

            // tx3: soft-deleted
            stmt.setLong(1, 3L)
            stmt.setLong(2, 15000L)
            stmt.setString(3, "EXPENSE")
            stmt.setLong(4, 1700000002000L)
            stmt.setString(5, "Unknown Merchant")
            stmt.setString(6, "Others")
            stmt.setString(7, null)
            stmt.setString(8, null)
            stmt.setString(9, null)
            stmt.setString(10, "HASH3")
            stmt.setString(11, "SMS_AUTO")
            stmt.setString(12, "NEEDS_REVIEW")
            stmt.setInt(13, 0)
            stmt.setLong(14, 1700000002000L)
            stmt.setLong(15, 1700000002000L)
            stmt.setInt(16, 1)
            stmt.executeUpdate()
        }

        // 4. Execute MIGRATION_1_2_SQL
        migratedConn.createStatement().use { stmt ->
            for (sql in AutoFlowDatabase.MIGRATION_1_2_SQL) {
                stmt.execute(sql)
            }
        }

        // 5. Assert: 3 rows still exist with original values intact and categorySource = 'AUTO'
        migratedConn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM transactions ORDER BY id ASC").use { rs ->
                // Row 1
                assertTrue("Row 1 must exist", rs.next())
                assertEquals(1L, rs.getLong("id"))
                assertEquals(50000L, rs.getLong("amount_paise"))
                assertEquals("EXPENSE", rs.getString("record_type"))
                assertEquals("Starbucks", rs.getString("merchant_name"))
                assertEquals("Food", rs.getString("category"))
                assertEquals("AUTO", rs.getString("categorySource"))
                assertEquals(0, rs.getInt("is_deleted"))

                // Row 2
                assertTrue("Row 2 must exist", rs.next())
                assertEquals(2L, rs.getLong("id"))
                assertEquals(20000L, rs.getLong("amount_paise"))
                assertEquals("REFUND", rs.getString("record_type"))
                assertEquals("Starbucks", rs.getString("merchant_name"))
                assertEquals("Others", rs.getString("category"))
                assertEquals("AUTO", rs.getString("categorySource"))
                assertEquals(0, rs.getInt("is_deleted"))

                // Row 3
                assertTrue("Row 3 must exist", rs.next())
                assertEquals(3L, rs.getLong("id"))
                assertEquals(15000L, rs.getLong("amount_paise"))
                assertEquals("EXPENSE", rs.getString("record_type"))
                assertEquals("Unknown Merchant", rs.getString("merchant_name"))
                assertEquals("Others", rs.getString("category"))
                assertEquals("AUTO", rs.getString("categorySource"))
                assertEquals(1, rs.getInt("is_deleted"))

                assertTrue("Only 3 rows should exist", !rs.next())
            }
        }

        // 6. Assert: Table merchant_categories exists and accepts insert
        migratedConn.createStatement().use { stmt ->
            stmt.execute("INSERT INTO merchant_categories (merchantKey, category, updatedAt) VALUES ('starbucks', 'Food', 1700000005000)")
            stmt.executeQuery("SELECT * FROM merchant_categories WHERE merchantKey = 'starbucks'").use { rs ->
                assertTrue(rs.next())
                assertEquals("starbucks", rs.getString("merchantKey"))
                assertEquals("Food", rs.getString("category"))
                assertEquals(1700000005000L, rs.getLong("updatedAt"))
            }
        }

        // 7. Establish reference v2 database from 2.json
        val referenceV2Conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        referenceV2Conn.createStatement().use { stmt ->
            for (sql in v2CreateStatements) {
                stmt.execute(sql)
            }
        }

        // Compare PRAGMA table_info for transactions and merchant_categories
        val tables = listOf("transactions", "merchant_categories")
        for (table in tables) {
            val migratedCols = getTableColumns(migratedConn, table)
            val refCols = getTableColumns(referenceV2Conn, table)

            println("\n=== PRAGMA table_info comparison for table: $table ===")
            println(String.format("%-5s | %-20s | %-10s | %-8s | %-15s | %-5s", "cid", "name", "type", "notnull", "default", "pk"))
            println("-".repeat(75))
            for (c in migratedCols) {
                println(String.format("%-5d | %-20s | %-10s | %-8b | %-15s | %-5d", c.cid, c.name, c.type, c.notNull, c.dfltValue, c.pk))
            }

            assertEquals("Column count must match for $table", refCols.size, migratedCols.size)
            val migByName = migratedCols.associateBy { it.name }
            val refByName = refCols.associateBy { it.name }

            assertEquals("Set of column names must match for $table", refByName.keys, migByName.keys)
            for (colName in refByName.keys) {
                val ref = refByName[colName]!!
                val mig = migByName[colName]!!
                assertEquals("Column type mismatch for $colName in $table", ref.type, mig.type)
                assertEquals("Column notNull mismatch for $colName in $table", ref.notNull, mig.notNull)
                assertEquals("Column pk mismatch for $colName in $table", ref.pk, mig.pk)
                if (ref.dfltValue != null) {
                    val normRef = ref.dfltValue.trim('\'')
                    val normMig = mig.dfltValue?.trim('\'')
                    assertEquals("Column default value mismatch for $colName in $table", normRef, normMig)
                }
            }
        }

        // 8. Add a row with categorySource = 'USER' and a soft-deleted AUTO row to test retro-update protection
        val insertV2TxSql = """
            INSERT INTO transactions (
                id, amount_paise, record_type, date_time, merchant_name, category,
                categorySource, account_number, bank_name, reference, transaction_hash,
                source, review_status, uncertainty_bits, created_at, updated_at, is_deleted
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        migratedConn.prepareStatement(insertV2TxSql).use { stmt ->
            // tx4: USER row (user manually categorized Starbucks as Transport)
            stmt.setLong(1, 4L)
            stmt.setLong(2, 40000L)
            stmt.setString(3, "EXPENSE")
            stmt.setLong(4, 1700000003000L)
            stmt.setString(5, "Starbucks")
            stmt.setString(6, "Transport")
            stmt.setString(7, "USER")
            stmt.setString(8, "XX1234")
            stmt.setString(9, "HDFC")
            stmt.setString(10, "REF456")
            stmt.setString(11, "HASH4")
            stmt.setString(12, "SMS_AUTO")
            stmt.setString(13, "CONFIRMED")
            stmt.setInt(14, 0)
            stmt.setLong(15, 1700000003000L)
            stmt.setLong(16, 1700000003000L)
            stmt.setInt(17, 0)
            stmt.executeUpdate()

            // tx5: soft-deleted AUTO row (deleted Starbucks expense)
            stmt.setLong(1, 5L)
            stmt.setLong(2, 30000L)
            stmt.setString(3, "EXPENSE")
            stmt.setLong(4, 1700000004000L)
            stmt.setString(5, "Starbucks")
            stmt.setString(6, "Others")
            stmt.setString(7, "AUTO")
            stmt.setString(8, "XX1234")
            stmt.setString(9, "HDFC")
            stmt.setString(10, "REF789")
            stmt.setString(11, "HASH5")
            stmt.setString(12, "SMS_AUTO")
            stmt.setString(13, "CONFIRMED")
            stmt.setInt(14, 0)
            stmt.setLong(15, 1700000004000L)
            stmt.setLong(16, 1700000004000L)
            stmt.setInt(17, 1)
            stmt.executeUpdate()
        }

        // 9. Test real DAO queries on migrated database using TransactionDao.QUERY_GET_ALL_AUTO:
        // Assert: getAllAutoCategorizedTransactions query never returns USER rows or soft-deleted rows
        val autoCategorizedRows = mutableListOf<Long>()
        migratedConn.createStatement().use { stmt ->
            stmt.executeQuery(com.autoflow.app.data.db.dao.TransactionDao.QUERY_GET_ALL_AUTO).use { rs ->
                while (rs.next()) {
                    autoCategorizedRows.add(rs.getLong("id"))
                }
            }
        }
        assertEquals("Must return only active AUTO rows (tx1 and tx2)", listOf(1L, 2L), autoCategorizedRows)
        assertTrue("Must NOT return USER row (tx4)", !autoCategorizedRows.contains(4L))
        assertTrue("Must NOT return soft-deleted rows (tx3, tx5)", !autoCategorizedRows.contains(3L) && !autoCategorizedRows.contains(5L))

        // Simulate retro-update logic: update matching auto rows to 'Food'
        val updateSql = "UPDATE transactions SET category = ?, updated_at = ? WHERE id = ?"
        migratedConn.prepareStatement(updateSql).use { stmt ->
            for (id in autoCategorizedRows) {
                if (id != 1L) { // tx2 is the other Starbucks row
                    stmt.setString(1, "Food")
                    stmt.setLong(2, 1700000009000L)
                    stmt.setLong(3, id)
                    stmt.executeUpdate()
                }
            }
        }

        // Assert: USER row and deleted rows were NEVER modified
        migratedConn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, category, categorySource, is_deleted FROM transactions ORDER BY id ASC").use { rs ->
                val rows = mutableMapOf<Long, Triple<String, String, Int>>()
                while (rs.next()) {
                    rows[rs.getLong("id")] = Triple(
                        rs.getString("category"),
                        rs.getString("categorySource"),
                        rs.getInt("is_deleted")
                    )
                }

                // tx1: Food / AUTO / 0
                assertEquals(Triple("Food", "AUTO", 0), rows[1L])
                // tx2: retro-updated to Food / AUTO / 0
                assertEquals(Triple("Food", "AUTO", 0), rows[2L])
                // tx3: soft-deleted row untouched
                assertEquals(Triple("Others", "AUTO", 1), rows[3L])
                // tx4: USER row strictly UNTOUCHED (still Transport / USER / 0)
                assertEquals(Triple("Transport", "USER", 0), rows[4L])
                // tx5: soft-deleted row strictly UNTOUCHED (still Others / AUTO / 1)
                assertEquals(Triple("Others", "AUTO", 1), rows[5L])
            }
        }

        // 10. Query (b): upsert SQL for MerchantCategoryDao
        // INSERT OR REPLACE INTO merchant_categories (merchantKey, category, updatedAt) VALUES (?, ?, ?)
        val upsertSql = "INSERT OR REPLACE INTO merchant_categories (merchantKey, category, updatedAt) VALUES (?, ?, ?)"
        migratedConn.prepareStatement(upsertSql).use { stmt ->
            // Update starbucks to Groceries
            stmt.setString(1, "starbucks")
            stmt.setString(2, "Groceries")
            stmt.setLong(3, 1700000006000L)
            stmt.executeUpdate()
        }

        // Verify conflict update worked and row count is still 1
        migratedConn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM merchant_categories").use { rs ->
                assertTrue(rs.next())
                assertEquals("Conflict update must not duplicate row", 1, rs.getInt(1))
            }
        }

        // Query (c): getCategory
        // SELECT category FROM merchant_categories WHERE merchantKey = :key LIMIT 1
        migratedConn.prepareStatement("SELECT category FROM merchant_categories WHERE merchantKey = ? LIMIT 1").use { stmt ->
            stmt.setString(1, "starbucks")
            stmt.executeQuery().use { rs ->
                assertTrue(rs.next())
                assertEquals("Groceries", rs.getString(1))
            }
        }

        migratedConn.close()
        referenceV2Conn.close()
    }
}
