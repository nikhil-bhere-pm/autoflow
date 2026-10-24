package com.autoflow.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

class RoomMigration4To5Test {

    data class ColumnInfo(
        val cid: Int,
        val name: String,
        val type: String,
        val notNull: Boolean,
        val dfltValue: String?,
        val pk: Int
    )

    data class TransactionSnapshot(
        val id: Long,
        val amountPaise: Long,
        val recordType: String,
        val dateTime: Long,
        val merchantName: String,
        val category: String,
        val categorySource: String,
        val paymentMode: String,
        val note: String?,
        val accountNumber: String?,
        val bankName: String?,
        val reference: String?,
        val transactionHash: String,
        val source: String,
        val reviewStatus: String,
        val uncertaintyBits: Int,
        val createdAt: Long,
        val updatedAt: Long,
        val isDeleted: Int
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

    private fun readAllTransactions(conn: Connection): List<TransactionSnapshot> {
        val list = mutableListOf<TransactionSnapshot>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM transactions ORDER BY id ASC").use { rs ->
                while (rs.next()) {
                    list.add(
                        TransactionSnapshot(
                            id = rs.getLong("id"),
                            amountPaise = rs.getLong("amount_paise"),
                            recordType = rs.getString("record_type"),
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
                            source = rs.getString("source"),
                            reviewStatus = rs.getString("review_status"),
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

    @Test
    fun testRoomMigration4To5_onRealBackupFile() {
        // Copy ../backups/v4_pre_ui6.db to a temp file, never opening the original
        val backupCandidates = listOf(
            File("../backups/v4_pre_ui6.db"),
            File("backups/v4_pre_ui6.db"),
            File("../../backups/v4_pre_ui6.db")
        )
        val originalBackup = backupCandidates.firstOrNull { it.exists() }
            ?: error("v4_pre_ui6.db backup not found in candidates: ${backupCandidates.map { it.absolutePath }}")

        val tempDb = Files.createTempFile("autoflow_migration_test_", ".db").toFile()
        tempDb.deleteOnExit()
        originalBackup.copyTo(tempDb, overwrite = true)

        val jdbcUrl = "jdbc:sqlite:${tempDb.absolutePath}"
        val conn = DriverManager.getConnection(jdbcUrl)

        // Verify pre-migration state: exactly 5 transactions
        val preMigrationTxs = readAllTransactions(conn)
        assertEquals("Pre-migration backup must contain exactly 5 transactions", 5, preMigrationTxs.size)

        // Execute MIGRATION_4_5_SQL
        conn.createStatement().use { stmt ->
            for (sql in AutoFlowDatabase.MIGRATION_4_5_SQL) {
                stmt.execute(sql)
            }
        }

        // Verify post-migration state: all 5 transactions must remain byte-for-byte identical
        val postMigrationTxs = readAllTransactions(conn)
        assertEquals("Post-migration must still contain exactly 5 transactions", 5, postMigrationTxs.size)
        assertEquals(preMigrationTxs, postMigrationTxs)

        // Verify new tables exist and are empty
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM goals").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1))
            }
            stmt.executeQuery("SELECT COUNT(*) FROM goal_entries").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1))
            }
        }

        conn.close()
        tempDb.delete()
    }

    @Test
    fun testRoomMigration4To5_matchesSchema5Definition() {
        val fileV4 = findSchemaFile(4)
        val fileV5 = findSchemaFile(5)
        val jsonV4 = fileV4.readText()
        val jsonV5 = fileV5.readText()

        val v4CreateStatements = extractCreateStatements(jsonV4)
        val v5CreateStatements = extractCreateStatements(jsonV5)

        val migratedConn = DriverManager.getConnection("jdbc:sqlite::memory:")
        migratedConn.createStatement().use { stmt ->
            for (sql in v4CreateStatements) {
                stmt.execute(sql)
            }
            for (sql in AutoFlowDatabase.MIGRATION_4_5_SQL) {
                stmt.execute(sql)
            }
        }

        val referenceConn = DriverManager.getConnection("jdbc:sqlite::memory:")
        referenceConn.createStatement().use { stmt ->
            for (sql in v5CreateStatements) {
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

        migratedConn.close()
        referenceConn.close()
    }
}
