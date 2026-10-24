package com.autoflow.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class RoomMigration3To4Test {

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
    fun testRoomMigration3To4_onRealSqlite() {
        val fileV3 = findSchemaFile(3)
        val fileV4 = findSchemaFile(4)
        val jsonV3 = fileV3.readText()
        val jsonV4 = fileV4.readText()

        val v3CreateStatements = extractCreateStatements(jsonV3)
        assertTrue("v3 must have CREATE statements", v3CreateStatements.isNotEmpty())

        val v4CreateStatements = extractCreateStatements(jsonV4)
        assertTrue("v4 must have CREATE statements", v4CreateStatements.isNotEmpty())

        // 1. Establish in-memory SQLite connection for migrated DB
        val migratedConn = DriverManager.getConnection("jdbc:sqlite::memory:")

        // 2. Execute v3 schema CREATE statements
        migratedConn.createStatement().use { stmt ->
            for (sql in v3CreateStatements) {
                stmt.execute(sql)
            }
        }

        // 3. Insert sample rows into v3 schema
        val insertTxSql = """
            INSERT INTO transactions (
                id, amount_paise, record_type, date_time, merchant_name, category,
                categorySource, payment_mode, note, account_number, bank_name, reference, transaction_hash,
                source, review_status, uncertainty_bits, created_at, updated_at, is_deleted
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        migratedConn.prepareStatement(insertTxSql).use { stmt ->
            // Row 1: Active transaction
            stmt.setLong(1, 1L)
            stmt.setLong(2, 45000L)
            stmt.setString(3, "EXPENSE")
            stmt.setLong(4, 1700000000000L)
            stmt.setString(5, "Zomato")
            stmt.setString(6, "Food")
            stmt.setString(7, "AUTO")
            stmt.setString(8, "UPI")
            stmt.setString(9, "Dinner with team")
            stmt.setString(10, "XX1234")
            stmt.setString(11, "HDFC")
            stmt.setString(12, "REF100")
            stmt.setString(13, "HASH100")
            stmt.setString(14, "SMS_AUTO")
            stmt.setString(15, "CONFIRMED")
            stmt.setInt(16, 0)
            stmt.setLong(17, 1700000000000L)
            stmt.setLong(18, 1700000000000L)
            stmt.setInt(19, 0)
            stmt.executeUpdate()

            // Row 2: Soft deleted transaction
            stmt.setLong(1, 2L)
            stmt.setLong(2, 12000L)
            stmt.setString(3, "EXPENSE")
            stmt.setLong(4, 1700000001000L)
            stmt.setString(5, "Old Cafe")
            stmt.setString(6, "Food")
            stmt.setString(7, "USER")
            stmt.setString(8, "CASH")
            stmt.setString(9, null)
            stmt.setString(10, null)
            stmt.setString(11, null)
            stmt.setString(12, null)
            stmt.setString(13, "HASH200")
            stmt.setString(14, "MANUAL_CASH")
            stmt.setString(15, "CONFIRMED")
            stmt.setInt(16, 0)
            stmt.setLong(17, 1700000001000L)
            stmt.setLong(18, 1700000001000L)
            stmt.setInt(19, 1)
            stmt.executeUpdate()
        }

        // Insert sample merchant category rule
        migratedConn.prepareStatement("INSERT INTO merchant_categories (merchantKey, category, updatedAt) VALUES (?, ?, ?)").use { stmt ->
            stmt.setString(1, "zomato")
            stmt.setString(2, "Food")
            stmt.setLong(3, 1700000000000L)
            stmt.executeUpdate()
        }

        // 4. Run production migration statements (MIGRATION_3_4_SQL)
        migratedConn.createStatement().use { stmt ->
            for (sql in AutoFlowDatabase.MIGRATION_3_4_SQL) {
                stmt.execute(sql)
            }
        }

        // 5. Build reference DB directly from schema 4.json
        val referenceConn = DriverManager.getConnection("jdbc:sqlite::memory:")
        referenceConn.createStatement().use { stmt ->
            for (sql in v4CreateStatements) {
                stmt.execute(sql)
            }
        }

        // 6. Compare PRAGMA table_info for all tables
        val tables = listOf("transactions", "merchant_categories", "goals", "goal_contributions", "refund_matches", "custom_categories")
        for (table in tables) {
            val migratedCols = getTableColumns(migratedConn, table)
            val referenceCols = getTableColumns(referenceConn, table)
            assertEquals("Columns count mismatch for table $table", referenceCols.size, migratedCols.size)
            for (i in referenceCols.indices) {
                val refCol = referenceCols[i]
                val migCol = migratedCols[i]
                assertEquals("Column name mismatch in $table at $i", refCol.name, migCol.name)
                assertEquals("Column type mismatch in $table at ${refCol.name}", refCol.type, migCol.type)
                assertEquals("Column notNull mismatch in $table at ${refCol.name}", refCol.notNull, migCol.notNull)
                assertEquals("Column pk mismatch in $table at ${refCol.name}", refCol.pk, migCol.pk)
            }
        }

        // 7. Verify all old rows unchanged
        migratedConn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM transactions WHERE id = 1").use { rs ->
                assertTrue("Row 1 must exist", rs.next())
                assertEquals(45000L, rs.getLong("amount_paise"))
                assertEquals("Zomato", rs.getString("merchant_name"))
                assertEquals("Food", rs.getString("category"))
                assertEquals("AUTO", rs.getString("categorySource"))
                assertEquals("UPI", rs.getString("payment_mode"))
                assertEquals("Dinner with team", rs.getString("note"))
                assertEquals(0, rs.getInt("is_deleted"))
            }

            stmt.executeQuery("SELECT * FROM transactions WHERE id = 2").use { rs ->
                assertTrue("Row 2 must exist", rs.next())
                assertEquals(12000L, rs.getLong("amount_paise"))
                assertEquals("Old Cafe", rs.getString("merchant_name"))
                assertEquals("Food", rs.getString("category"))
                assertEquals("USER", rs.getString("categorySource"))
                assertEquals("CASH", rs.getString("payment_mode"))
                assertEquals(1, rs.getInt("is_deleted"))
            }

            stmt.executeQuery("SELECT * FROM merchant_categories WHERE merchantKey = 'zomato'").use { rs ->
                assertTrue("Merchant category rule must exist", rs.next())
                assertEquals("Food", rs.getString("category"))
            }

            // 8. Verify new table custom_categories is empty
            stmt.executeQuery("SELECT COUNT(*) FROM custom_categories").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1))
            }
        }

        migratedConn.close()
        referenceConn.close()
    }
}
