package com.autoflow.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class RoomMigration5To6Test {

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
    fun testMigration5To6_dataPreservationAndRebuild() {
        val fileV5 = findSchemaFile(5)
        val v5CreateStatements = extractCreateStatements(fileV5.readText())

        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { stmt ->
            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON")

            for (sql in v5CreateStatements) {
                stmt.execute(sql)
            }

            // Seed v5 data: 2 goals
            stmt.execute(
                """
                INSERT INTO goals (id, name, name_key, kind, target_paise, created_at, updated_at)
                VALUES (1, 'Emergency Fund', 'emergency fund', 'SAVE', 50000000, 1000, 1000)
                """.trimIndent()
            )
            stmt.execute(
                """
                INSERT INTO goals (id, name, name_key, kind, target_paise, created_at, updated_at)
                VALUES (2, 'Avoid Dining Out', 'avoid dining out', 'AVOID', 10000000, 2000, 2000)
                """.trimIndent()
            )

            // Seed entries: SET_ASIDE with note, SET_ASIDE with null note, AVOIDED with blank note, USED entry
            stmt.execute(
                """
                INSERT INTO goal_entries (id, goal_id, type, amount_paise, note, occurred_at, linked_transaction_id, is_deleted, created_at, updated_at)
                VALUES (1, 1, 'SET_ASIDE', 500000, 'Bonus deposited', 1100, NULL, 0, 1100, 1100)
                """.trimIndent()
            )
            stmt.execute(
                """
                INSERT INTO goal_entries (id, goal_id, type, amount_paise, note, occurred_at, linked_transaction_id, is_deleted, created_at, updated_at)
                VALUES (2, 1, 'SET_ASIDE', 200000, NULL, 1200, NULL, 0, 1200, 1200)
                """.trimIndent()
            )
            stmt.execute(
                """
                INSERT INTO goal_entries (id, goal_id, type, amount_paise, note, occurred_at, linked_transaction_id, is_deleted, created_at, updated_at)
                VALUES (3, 2, 'AVOIDED', 150000, '   ', 2100, NULL, 0, 2100, 2100)
                """.trimIndent()
            )
            stmt.execute(
                """
                INSERT INTO goal_entries (id, goal_id, type, amount_paise, note, occurred_at, linked_transaction_id, is_deleted, created_at, updated_at)
                VALUES (4, 1, 'USED', 100000, 'Spent on repair', 1300, 999, 0, 1300, 1300)
                """.trimIndent()
            )
        }

        // Run MIGRATION_5_6
        conn.createStatement().use { stmt ->
            // SQLite migrations should execute foreign_keys pragma management if needed
            for (sql in AutoFlowDatabase.MIGRATION_5_6_SQL) {
                stmt.execute(sql)
            }
        }

        // Verify goals table
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, name, name_key, target_paise, deadline_at, is_deleted, created_at, updated_at FROM goals ORDER BY id ASC").use { rs ->
                assertTrue("Goal 1 must exist", rs.next())
                assertEquals(1L, rs.getLong("id"))
                assertEquals("Emergency Fund", rs.getString("name"))
                assertEquals("emergency fund", rs.getString("name_key"))
                assertEquals(50000000L, rs.getLong("target_paise"))
                assertNull(rs.getObject("deadline_at"))
                assertEquals(0, rs.getInt("is_deleted"))
                assertEquals(1000L, rs.getLong("created_at"))
                assertEquals(1000L, rs.getLong("updated_at"))

                assertTrue("Goal 2 must exist", rs.next())
                assertEquals(2L, rs.getLong("id"))
                assertEquals("Avoid Dining Out", rs.getString("name"))
                assertEquals("avoid dining out", rs.getString("name_key"))
                assertEquals(10000000L, rs.getLong("target_paise"))
                assertNull(rs.getObject("deadline_at"))
                assertEquals(0, rs.getInt("is_deleted"))

                assertFalse("No more goals", rs.next())
            }
        }

        // Verify entries table
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, goal_id, amount_paise, note, occurred_at, is_deleted FROM goal_entries ORDER BY id ASC").use { rs ->
                // Entry 1: SET_ASIDE with note kept
                assertTrue("Entry 1 must exist", rs.next())
                assertEquals(1L, rs.getLong("id"))
                assertEquals(1L, rs.getLong("goal_id"))
                assertEquals(500000L, rs.getLong("amount_paise"))
                assertEquals("Bonus deposited", rs.getString("note"))
                assertEquals(1100L, rs.getLong("occurred_at"))
                assertEquals(0, rs.getInt("is_deleted"))

                // Entry 2: SET_ASIDE with null note becomes 'Saved'
                assertTrue("Entry 2 must exist", rs.next())
                assertEquals(2L, rs.getLong("id"))
                assertEquals(1L, rs.getLong("goal_id"))
                assertEquals(200000L, rs.getLong("amount_paise"))
                assertEquals("Saved", rs.getString("note"))

                // Entry 3: AVOIDED with blank note becomes 'Saved'
                assertTrue("Entry 3 must exist", rs.next())
                assertEquals(3L, rs.getLong("id"))
                assertEquals(2L, rs.getLong("goal_id"))
                assertEquals(150000L, rs.getLong("amount_paise"))
                assertEquals("Saved", rs.getString("note"))

                // Entry 4: USED must be DROPPED!
                assertFalse("Entry 4 (USED) must be dropped and no more entries should exist", rs.next())
            }
        }

        // Verify foreign key cascade delete
        conn.createStatement().use { stmt ->
            stmt.execute("PRAGMA foreign_keys = ON")
            stmt.execute("DELETE FROM goals WHERE id = 2")
            stmt.executeQuery("SELECT COUNT(*) FROM goal_entries WHERE goal_id = 2").use { rs ->
                assertTrue(rs.next())
                assertEquals("Cascade delete must remove goal_entries for deleted goal", 0, rs.getInt(1))
            }
        }

        conn.close()
    }

    @Test
    fun testRoomMigration5To6_matchesSchema6Definition() {
        val fileV5 = findSchemaFile(5)
        val fileV6 = findSchemaFile(6)
        val jsonV5 = fileV5.readText()
        val jsonV6 = fileV6.readText()

        val v5CreateStatements = extractCreateStatements(jsonV5)
        val v6CreateStatements = extractCreateStatements(jsonV6)

        val migratedConn = DriverManager.getConnection("jdbc:sqlite::memory:")
        migratedConn.createStatement().use { stmt ->
            for (sql in v5CreateStatements) {
                stmt.execute(sql)
            }
            for (sql in AutoFlowDatabase.MIGRATION_5_6_SQL) {
                stmt.execute(sql)
            }
        }

        val referenceConn = DriverManager.getConnection("jdbc:sqlite::memory:")
        referenceConn.createStatement().use { stmt ->
            for (sql in v6CreateStatements) {
                stmt.execute(sql)
            }
        }

        val tables = listOf("transactions", "refund_matches", "merchant_categories", "custom_categories", "goals", "goal_entries")
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

        // Compare index definitions for goals and goal_entries
        fun getIndexNames(conn: Connection, tableName: String): Set<String> {
            val set = mutableSetOf<String>()
            conn.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA index_list(`$tableName`)").use { rs ->
                    while (rs.next()) {
                        set.add(rs.getString("name"))
                    }
                }
            }
            return set
        }

        for (table in listOf("goals", "goal_entries")) {
            val migIndexes = getIndexNames(migratedConn, table)
            val refIndexes = getIndexNames(referenceConn, table)
            assertEquals("Index mismatch for table $table", refIndexes, migIndexes)
        }

        migratedConn.close()
        referenceConn.close()
    }
}
