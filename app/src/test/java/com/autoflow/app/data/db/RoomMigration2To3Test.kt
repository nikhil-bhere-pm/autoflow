package com.autoflow.app.data.db

import com.autoflow.app.data.db.dao.TransactionDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class RoomMigration2To3Test {

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
    fun testRoomMigration2To3_onRealSqlite() {
        val fileV2 = findSchemaFile(2)
        val fileV3 = findSchemaFile(3)
        val jsonV2 = fileV2.readText()
        val jsonV3 = fileV3.readText()

        val v2CreateStatements = extractCreateStatements(jsonV2)
        assertTrue("v2 must have CREATE statements", v2CreateStatements.isNotEmpty())

        val v3CreateStatements = extractCreateStatements(jsonV3)
        assertTrue("v3 must have CREATE statements", v3CreateStatements.isNotEmpty())

        // 1. Establish in-memory SQLite connection for migrated DB
        val migratedConn = DriverManager.getConnection("jdbc:sqlite::memory:")

        // 2. Execute v2 schema CREATE statements
        migratedConn.createStatement().use { stmt ->
            for (sql in v2CreateStatements) {
                stmt.execute(sql)
            }
        }

        // 3. Insert sample rows into v2 schema (transactions has categorySource, but no payment_mode or note)
        val insertTxSql = """
            INSERT INTO transactions (
                id, amount_paise, record_type, date_time, merchant_name, category,
                categorySource, account_number, bank_name, reference, transaction_hash,
                source, review_status, uncertainty_bits, created_at, updated_at, is_deleted
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        migratedConn.prepareStatement(insertTxSql).use { stmt ->
            // Row 1: SMS row (SMS_AUTO)
            stmt.setLong(1, 1L)
            stmt.setLong(2, 45000L)
            stmt.setString(3, "EXPENSE")
            stmt.setLong(4, 1700000000000L)
            stmt.setString(5, "Zomato")
            stmt.setString(6, "Food")
            stmt.setString(7, "AUTO")
            stmt.setString(8, "XX1234")
            stmt.setString(9, "HDFC")
            stmt.setString(10, "REF100")
            stmt.setString(11, "HASH100")
            stmt.setString(12, "SMS_AUTO")
            stmt.setString(13, "CONFIRMED")
            stmt.setInt(14, 0)
            stmt.setLong(15, 1700000000000L)
            stmt.setLong(16, 1700000000000L)
            stmt.setInt(17, 0)
            stmt.executeUpdate()

            // Row 2: Manual cash row (MANUAL_CASH) -> must be backfilled to 'CASH'
            stmt.setLong(1, 2L)
            stmt.setLong(2, 20000L)
            stmt.setString(3, "EXPENSE")
            stmt.setLong(4, 1700000001000L)
            stmt.setString(5, "Local Chai")
            stmt.setString(6, "Food")
            stmt.setString(7, "USER")
            stmt.setString(8, null)
            stmt.setString(9, null)
            stmt.setString(10, null)
            stmt.setString(11, "HASH200")
            stmt.setString(12, "MANUAL_CASH")
            stmt.setString(13, "CONFIRMED")
            stmt.setInt(14, 0)
            stmt.setLong(15, 1700000001000L)
            stmt.setLong(16, 1700000001000L)
            stmt.setInt(17, 0)
            stmt.executeUpdate()

            // Row 3: Soft-deleted row
            stmt.setLong(1, 3L)
            stmt.setLong(2, 10000L)
            stmt.setString(3, "EXPENSE")
            stmt.setLong(4, 1700000002000L)
            stmt.setString(5, "Deleted Merchant")
            stmt.setString(6, "Others")
            stmt.setString(7, "AUTO")
            stmt.setString(8, null)
            stmt.setString(9, null)
            stmt.setString(10, null)
            stmt.setString(11, "HASH300")
            stmt.setString(12, "SMS_AUTO")
            stmt.setString(13, "CONFIRMED")
            stmt.setInt(14, 0)
            stmt.setLong(15, 1700000002000L)
            stmt.setLong(16, 1700000002000L)
            stmt.setInt(17, 1)
            stmt.executeUpdate()
        }

        // 4. Execute MIGRATION_2_3_SQL
        migratedConn.createStatement().use { stmt ->
            for (sql in AutoFlowDatabase.MIGRATION_2_3_SQL) {
                stmt.execute(sql)
            }
        }

        // 5. Assert: 3 rows still exist, data intact, payment_mode and note columns verified
        migratedConn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM transactions ORDER BY id ASC").use { rs ->
                // Row 1: SMS row -> defaults to UNKNOWN, note is null
                assertTrue("Row 1 must exist", rs.next())
                assertEquals(1L, rs.getLong("id"))
                assertEquals(45000L, rs.getLong("amount_paise"))
                assertEquals("Zomato", rs.getString("merchant_name"))
                assertEquals("UNKNOWN", rs.getString("payment_mode"))
                assertNull(rs.getString("note"))
                assertEquals(0, rs.getInt("is_deleted"))

                // Row 2: Manual cash row -> backfilled to CASH, note is null
                assertTrue("Row 2 must exist", rs.next())
                assertEquals(2L, rs.getLong("id"))
                assertEquals(20000L, rs.getLong("amount_paise"))
                assertEquals("Local Chai", rs.getString("merchant_name"))
                assertEquals("CASH", rs.getString("payment_mode"))
                assertNull(rs.getString("note"))
                assertEquals(0, rs.getInt("is_deleted"))

                // Row 3: Soft-deleted row -> intact, payment_mode = UNKNOWN, note is null
                assertTrue("Row 3 must exist", rs.next())
                assertEquals(3L, rs.getLong("id"))
                assertEquals(10000L, rs.getLong("amount_paise"))
                assertEquals("UNKNOWN", rs.getString("payment_mode"))
                assertNull(rs.getString("note"))
                assertEquals(1, rs.getInt("is_deleted"))

                assertTrue("Only 3 rows should exist", !rs.next())
            }
        }

        // 6. Establish reference v3 database from 3.json
        val referenceV3Conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        referenceV3Conn.createStatement().use { stmt ->
            for (sql in v3CreateStatements) {
                stmt.execute(sql)
            }
        }

        // 7. Compare PRAGMA table_info for transactions and merchant_categories
        val tables = listOf("transactions", "merchant_categories")
        for (table in tables) {
            val migratedCols = getTableColumns(migratedConn, table)
            val refCols = getTableColumns(referenceV3Conn, table)

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

        // 8. Range query test: run the exact TransactionDao.QUERY_GET_IN_RANGE SQL against migrated DB
        // Insert transactions across bounds:
        // Range: [start = 2000, end = 4000)
        val rangeStart = 2000L
        val rangeEnd = 4000L

        val insertV3Sql = """
            INSERT INTO transactions (
                id, amount_paise, record_type, date_time, merchant_name, category,
                categorySource, payment_mode, note, account_number, bank_name, reference,
                transaction_hash, source, review_status, uncertainty_bits, created_at, updated_at, is_deleted
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        migratedConn.prepareStatement(insertV3Sql).use { stmt ->
            // tx10: at exact start (2000) -> MUST be returned
            stmt.setLong(1, 10L); stmt.setLong(2, 1000L); stmt.setString(3, "EXPENSE"); stmt.setLong(4, rangeStart)
            stmt.setString(5, "Start Merchant"); stmt.setString(6, "Food"); stmt.setString(7, "AUTO")
            stmt.setString(8, "UPI"); stmt.setString(9, null); stmt.setString(10, null); stmt.setString(11, null)
            stmt.setString(12, null); stmt.setString(13, "HASH_10"); stmt.setString(14, "SMS_AUTO")
            stmt.setString(15, "CONFIRMED"); stmt.setInt(16, 0); stmt.setLong(17, rangeStart); stmt.setLong(18, rangeStart)
            stmt.setInt(19, 0)
            stmt.executeUpdate()

            // tx11: inside range (3000) -> MUST be returned
            stmt.setLong(1, 11L); stmt.setLong(2, 2000L); stmt.setString(3, "EXPENSE"); stmt.setLong(4, 3000L)
            stmt.setString(5, "Mid Merchant"); stmt.setString(6, "Food"); stmt.setString(7, "AUTO")
            stmt.setString(8, "UPI"); stmt.setString(9, null); stmt.setString(10, null); stmt.setString(11, null)
            stmt.setString(12, null); stmt.setString(13, "HASH_11"); stmt.setString(14, "SMS_AUTO")
            stmt.setString(15, "CONFIRMED"); stmt.setInt(16, 0); stmt.setLong(17, 3000L); stmt.setLong(18, 3000L)
            stmt.setInt(19, 0)
            stmt.executeUpdate()

            // tx12: at exact end (4000) -> MUST NOT be returned (half-open range [start, end))
            stmt.setLong(1, 12L); stmt.setLong(2, 3000L); stmt.setString(3, "EXPENSE"); stmt.setLong(4, rangeEnd)
            stmt.setString(5, "End Merchant"); stmt.setString(6, "Food"); stmt.setString(7, "AUTO")
            stmt.setString(8, "UPI"); stmt.setString(9, null); stmt.setString(10, null); stmt.setString(11, null)
            stmt.setString(12, null); stmt.setString(13, "HASH_12"); stmt.setString(14, "SMS_AUTO")
            stmt.setString(15, "CONFIRMED"); stmt.setInt(16, 0); stmt.setLong(17, rangeEnd); stmt.setLong(18, rangeEnd)
            stmt.setInt(19, 0)
            stmt.executeUpdate()

            // tx13: inside range (3500) but soft-deleted -> MUST NOT be returned
            stmt.setLong(1, 13L); stmt.setLong(2, 4000L); stmt.setString(3, "EXPENSE"); stmt.setLong(4, 3500L)
            stmt.setString(5, "Deleted Inside"); stmt.setString(6, "Food"); stmt.setString(7, "AUTO")
            stmt.setString(8, "UPI"); stmt.setString(9, null); stmt.setString(10, null); stmt.setString(11, null)
            stmt.setString(12, null); stmt.setString(13, "HASH_13"); stmt.setString(14, "SMS_AUTO")
            stmt.setString(15, "CONFIRMED"); stmt.setInt(16, 0); stmt.setLong(17, 3500L); stmt.setLong(18, 3500L)
            stmt.setInt(19, 1)
            stmt.executeUpdate()
        }

        // Run range query using exact TransactionDao.QUERY_GET_IN_RANGE string
        // Replace named parameters :startTime and :endTime with ? for JDBC
        val rangeQueryJdbc = TransactionDao.QUERY_GET_IN_RANGE
            .replace(":startTime", "?")
            .replace(":endTime", "?")

        val returnedIds = mutableListOf<Long>()
        migratedConn.prepareStatement(rangeQueryJdbc).use { stmt ->
            stmt.setLong(1, rangeStart)
            stmt.setLong(2, rangeEnd)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    returnedIds.add(rs.getLong("id"))
                }
            }
        }

        // Must return tx11 (3000) and tx10 (2000) in descending order
        assertEquals(listOf(11L, 10L), returnedIds)
        assertTrue("Row at exact end (12) must NOT be returned", !returnedIds.contains(12L))
        assertTrue("Soft-deleted row (13) must NOT be returned", !returnedIds.contains(13L))

        // 9. Update test: execute update against migrated DB
        val updateSql = """
            UPDATE transactions 
            SET payment_mode = ?, note = ?, date_time = ?, updated_at = ?
            WHERE id = ?
        """.trimIndent()

        migratedConn.prepareStatement(updateSql).use { stmt ->
            stmt.setString(1, "CREDIT")
            stmt.setString(2, "Dinner with friends")
            stmt.setLong(3, 2500L)
            stmt.setLong(4, 5000L)
            stmt.setLong(5, 10L)
            val updatedCount = stmt.executeUpdate()
            assertEquals(1, updatedCount)
        }

        migratedConn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT payment_mode, note, date_time FROM transactions WHERE id = 10").use { rs ->
                assertTrue(rs.next())
                assertEquals("CREDIT", rs.getString("payment_mode"))
                assertEquals("Dinner with friends", rs.getString("note"))
                assertEquals(2500L, rs.getLong("date_time"))
            }
        }
    }
}
