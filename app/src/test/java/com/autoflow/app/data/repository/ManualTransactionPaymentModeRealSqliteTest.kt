package com.autoflow.app.data.repository

import com.autoflow.app.data.db.AutoFlowDatabase.Companion.MIGRATION_3_4_SQL
import com.autoflow.app.data.model.PaymentMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Proves that manual expenses added with chosen payment modes (CASH, CREDIT, BANK, UPI)
 * are stored accurately in a real SQLite database (migrated to schema v4).
 */
class ManualTransactionPaymentModeRealSqliteTest {

    private lateinit var conn: Connection

    @Before
    fun setup() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")

        // Load schema v3 and migrate to v4
        val v3SchemaFile = findSchemaFile(3)
        val v3CreateStatements = extractCreateStatements(v3SchemaFile.readText())
        conn.createStatement().use { stmt ->
            for (sql in v3CreateStatements) {
                stmt.execute(sql)
            }
            for (sql in MIGRATION_3_4_SQL) {
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

    @Test
    fun testManualExpenseStoresChosenPaymentModesInRealSqlite() {
        val testModes = listOf(
            PaymentMode.CASH,
            PaymentMode.CREDIT,
            PaymentMode.BANK,
            PaymentMode.UPI
        )

        val insertSql = """
            INSERT INTO transactions (
                amount_paise, record_type, date_time, merchant_name, category,
                categorySource, payment_mode, note, transaction_hash, source,
                review_status, uncertainty_bits, created_at, updated_at, is_deleted
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        var idCounter = 1L
        for (mode in testModes) {
            val hash = "cash_${System.currentTimeMillis()}_$mode"
            conn.prepareStatement(insertSql).use { stmt ->
                stmt.setLong(1, 15000L) // 150.00
                stmt.setString(2, "EXPENSE")
                stmt.setLong(3, 1700000000000L + idCounter)
                stmt.setString(4, "Tea Stall $mode")
                stmt.setString(5, "Food")
                stmt.setString(6, "USER")
                stmt.setString(7, mode)
                stmt.setString(8, "Note for $mode")
                stmt.setString(9, hash)
                stmt.setString(10, "MANUAL_CASH")
                stmt.setString(11, "CONFIRMED")
                stmt.setInt(12, 0)
                stmt.setLong(13, 1700000000000L)
                stmt.setLong(14, 1700000000000L)
                stmt.setInt(15, 0)
                stmt.executeUpdate()
            }

            // Query back from real SQLite
            conn.prepareStatement("SELECT payment_mode, merchant_name, source FROM transactions WHERE transaction_hash = ?").use { stmt ->
                stmt.setString(1, hash)
                stmt.executeQuery().use { rs ->
                    assertEquals(true, rs.next())
                    assertEquals(mode, rs.getString("payment_mode"))
                    assertEquals("Tea Stall $mode", rs.getString("merchant_name"))
                    assertEquals("MANUAL_CASH", rs.getString("source"))
                }
            }
            idCounter++
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
}
