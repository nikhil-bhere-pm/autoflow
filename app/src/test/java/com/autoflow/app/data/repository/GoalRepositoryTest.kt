package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.GoalAggregateRaw
import com.autoflow.app.data.db.dao.GoalDao
import com.autoflow.app.data.db.entity.GoalEntity
import com.autoflow.app.data.db.entity.GoalEntryEntity
import com.autoflow.app.domain.goals.Goal
import com.autoflow.app.domain.goals.GoalError
import com.autoflow.app.domain.goals.GoalResult
import com.autoflow.app.domain.goals.MAX_AMOUNT_PAISE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.time.LocalDate
import java.time.ZoneId

class GoalRepositoryTest {

    private lateinit var conn: Connection
    private lateinit var goalDao: SqliteGoalDao
    private lateinit var repository: GoalRepository
    private val zoneId = ZoneId.of("Asia/Kolkata")

    // Helper: start of today in fixed zone
    private val todayStart: Long = LocalDate.now(zoneId)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    private val now: Long = todayStart + 12 * 3600 * 1000L // 12:00 PM today

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
        val jsonV6 = findSchemaFile(6).readText()
        val statements = extractCreateStatements(jsonV6)
        conn.createStatement().use { stmt ->
            stmt.execute("PRAGMA foreign_keys = ON")
            for (sql in statements) {
                stmt.execute(sql)
            }
        }

        goalDao = SqliteGoalDao(conn)
        repository = DefaultGoalRepository(
            goalDao = goalDao,
            zoneId = zoneId
        )
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) {
            conn.close()
        }
    }

    // --- Goal Creation & Validation ---

    @Test
    fun createGoal_success_assignsFieldsCorrectly() = runBlocking {
        val result = repository.createGoal(
            name = "  New Laptop  ",
            targetPaise = 15000000L,
            deadlineAt = todayStart + 86400000L * 30, // 30 days from now
            now = now
        )

        assertTrue(result is GoalResult.Success)
        val goal = (result as GoalResult.Success).data
        assertTrue(goal.id > 0)
        assertEquals("New Laptop", goal.name)
        assertEquals("new laptop", goal.nameKey)
        assertEquals(15000000L, goal.targetPaise)
        assertEquals(todayStart + 86400000L * 30, goal.deadlineAt)
        assertFalse(goal.isDeleted)
        assertEquals(now, goal.createdAt)
    }

    @Test
    fun createGoal_validation_blankName() = runBlocking {
        val res = repository.createGoal("   ", 100000L, null, now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.BlankName, (res as GoalResult.Error).error)
    }

    @Test
    fun createGoal_validation_nameTooLong() = runBlocking {
        val longName = "A".repeat(31)
        val res = repository.createGoal(longName, 100000L, null, now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.NameTooLong(31), (res as GoalResult.Error).error)
    }

    @Test
    fun createGoal_validation_badAmount() = runBlocking {
        val resZero = repository.createGoal("Car", 0L, null, now)
        assertTrue(resZero is GoalResult.Error)
        assertEquals(GoalError.BadAmount(0L), (resZero as GoalResult.Error).error)

        val resNeg = repository.createGoal("Car", -500L, null, now)
        assertTrue(resNeg is GoalResult.Error)
        assertEquals(GoalError.BadAmount(-500L), (resNeg as GoalResult.Error).error)
    }

    @Test
    fun createGoal_validation_overLimit() = runBlocking {
        val res = repository.createGoal("Car", MAX_AMOUNT_PAISE + 1L, null, now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.OverLimit(MAX_AMOUNT_PAISE + 1L), (res as GoalResult.Error).error)
    }

    @Test
    fun createGoal_validation_duplicateNameNormalized() = runBlocking {
        val res1 = repository.createGoal("Bike Fund", 500000L, null, now)
        assertTrue(res1 is GoalResult.Success)

        // Case-insensitive duplicate
        val res2 = repository.createGoal("bike fund", 600000L, null, now)
        assertTrue(res2 is GoalResult.Error)
        assertEquals(GoalError.NameTaken("bike fund"), (res2 as GoalResult.Error).error)

        // Whitespace-normalized duplicate
        val res3 = repository.createGoal("  BIKE    FUND  ", 600000L, null, now)
        assertTrue(res3 is GoalResult.Error)
        assertEquals(GoalError.NameTaken("BIKE    FUND"), (res3 as GoalResult.Error).error)
    }

    @Test
    fun createGoal_deadline_pastRejected() = runBlocking {
        val yesterday = todayStart - 1000L
        val res = repository.createGoal("Bike", 500000L, yesterday, now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.PastDeadline(yesterday, todayStart), (res as GoalResult.Error).error)
    }

    @Test
    fun createGoal_deadline_todayAndFutureAllowed() = runBlocking {
        // Today at 00:00:00 is valid
        val resToday = repository.createGoal("Bike 1", 500000L, todayStart, now)
        assertTrue(resToday is GoalResult.Success)

        // Future deadline is valid
        val resFuture = repository.createGoal("Bike 2", 500000L, todayStart + 86400000L, now)
        assertTrue(resFuture is GoalResult.Success)
    }

    // --- Goal Update & Validation ---

    @Test
    fun updateGoal_success() = runBlocking {
        val created = (repository.createGoal("Old Name", 100000L, null, now) as GoalResult.Success).data
        val updated = repository.updateGoal(
            id = created.id,
            name = "New Name",
            targetPaise = 200000L,
            deadlineAt = todayStart + 86400000L,
            now = now + 1000L
        )

        assertTrue(updated is GoalResult.Success)
        val data = (updated as GoalResult.Success).data
        assertEquals("New Name", data.name)
        assertEquals(200000L, data.targetPaise)
        assertEquals(todayStart + 86400000L, data.deadlineAt)
        assertEquals(now + 1000L, data.updatedAt)
    }

    @Test
    fun updateGoal_validation_duplicateNameWithOtherGoal() = runBlocking {
        repository.createGoal("Goal A", 100000L, null, now)
        val goalB = (repository.createGoal("Goal B", 200000L, null, now) as GoalResult.Success).data

        val res = repository.updateGoal(goalB.id, "goal a", 300000L, null, now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.NameTaken("goal a"), (res as GoalResult.Error).error)

        // Updating to same name is allowed
        val resSame = repository.updateGoal(goalB.id, "Goal B", 400000L, null, now)
        assertTrue(resSame is GoalResult.Success)
    }

    @Test
    fun updateGoal_deadlineRules() = runBlocking {
        val pastDeadline = todayStart - 86400000L
        val goal = (repository.createGoal("Goal with Deadline", 100000L, todayStart, now) as GoalResult.Success).data

        // Changing to a past date must be rejected
        val resChangedToPast = repository.updateGoal(goal.id, "Goal with Deadline", 100000L, pastDeadline, now)
        assertTrue(resChangedToPast is GoalResult.Error)
        assertEquals(GoalError.PastDeadline(pastDeadline, todayStart), (resChangedToPast as GoalResult.Error).error)

        // Keeping the existing deadline unchanged is ALLOWED
        val resUnchanged = repository.updateGoal(goal.id, "Goal with Deadline Updated", 120000L, todayStart, now)
        assertTrue(resUnchanged is GoalResult.Success)
    }

    @Test
    fun updateGoal_onDeletedGoal_fails() = runBlocking {
        val goal = (repository.createGoal("Trip", 100000L, null, now) as GoalResult.Success).data
        repository.deleteGoal(goal.id, now)

        val res = repository.updateGoal(goal.id, "New Trip", 200000L, null, now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.GoalDeleted(goal.id), (res as GoalResult.Error).error)
    }

    // --- Delete, Name Reuse, and Undelete ---

    @Test
    fun deleteGoal_softDeletes_andAllowsImmediateNameReuse() = runBlocking {
        val goal = (repository.createGoal("Emergency", 500000L, null, now) as GoalResult.Success).data

        // Add an entry before delete
        val entry = (repository.addSaved(goal.id, 100000L, "First save", now, now) as GoalResult.Success).data

        // Delete goal
        val delRes = repository.deleteGoal(goal.id, now)
        assertTrue(delRes is GoalResult.Success)

        // Name is immediately reusable!
        val newGoalRes = repository.createGoal("Emergency", 700000L, null, now)
        assertTrue("Name must be immediately reusable after soft delete", newGoalRes is GoalResult.Success)

        // Entries of the deleted goal are kept in storage
        val storedEntry = repository.getEntryById(entry.id)
        assertNotNull("Entries of deleted goal must be kept", storedEntry)
        assertEquals(100000L, storedEntry?.amountPaise)
    }

    @Test
    fun undeleteGoal_successWhenNameAvailable() = runBlocking {
        val goal = (repository.createGoal("Vacation", 500000L, null, now) as GoalResult.Success).data
        repository.deleteGoal(goal.id, now)

        val undelRes = repository.undeleteGoal(goal.id, now + 100L)
        assertTrue(undelRes is GoalResult.Success)

        val restored = repository.getGoalById(goal.id)
        assertNotNull(restored)
        assertFalse(restored!!.isDeleted)
        assertEquals("vacation", restored.nameKey)
    }

    @Test
    fun undeleteGoal_collision_returnsNameTaken_andChangesNothing() = runBlocking {
        val goal1 = (repository.createGoal("Vacation", 500000L, null, now) as GoalResult.Success).data
        repository.deleteGoal(goal1.id, now)

        // Another goal takes the name while goal1 is deleted
        repository.createGoal("Vacation", 800000L, null, now)

        // Attempting to undelete goal1 must fail with NameTaken and change nothing
        val undelRes = repository.undeleteGoal(goal1.id, now + 100L)
        assertTrue(undelRes is GoalResult.Error)
        assertEquals(GoalError.NameTaken("Vacation"), (undelRes as GoalResult.Error).error)

        // Goal 1 is still deleted and unchanged
        val stillDeleted = repository.getGoalById(goal1.id)
        assertNotNull(stillDeleted)
        assertTrue(stillDeleted!!.isDeleted)
    }

    // --- Saved Entries (Add, Edit, Delete, Undelete) ---

    @Test
    fun addSaved_success() = runBlocking {
        val goal = (repository.createGoal("Gadget", 100000L, null, now) as GoalResult.Success).data
        val res = repository.addSaved(goal.id, 25000L, "  Saved cash  ", now, now)

        assertTrue(res is GoalResult.Success)
        val entry = (res as GoalResult.Success).data
        assertTrue(entry.id > 0)
        assertEquals(goal.id, entry.goalId)
        assertEquals(25000L, entry.amountPaise)
        assertEquals("Saved cash", entry.note)
        assertEquals(now, entry.occurredAt)
        assertFalse(entry.isDeleted)
    }

    @Test
    fun addSaved_validations() = runBlocking {
        val goal = (repository.createGoal("Gadget", 100000L, null, now) as GoalResult.Success).data

        // Blank note
        val resBlankNote = repository.addSaved(goal.id, 1000L, "   ", now, now)
        assertTrue(resBlankNote is GoalResult.Error)
        assertEquals(GoalError.BlankNote, (resBlankNote as GoalResult.Error).error)

        // Note too long (> 100 chars)
        val resLongNote = repository.addSaved(goal.id, 1000L, "N".repeat(101), now, now)
        assertTrue(resLongNote is GoalResult.Error)
        assertEquals(GoalError.NoteTooLong(101), (resLongNote as GoalResult.Error).error)

        // Bad amount
        val resZero = repository.addSaved(goal.id, 0L, "Note", now, now)
        assertTrue(resZero is GoalResult.Error)
        assertEquals(GoalError.BadAmount(0L), (resZero as GoalResult.Error).error)

        // Over limit
        val resOver = repository.addSaved(goal.id, MAX_AMOUNT_PAISE + 1L, "Note", now, now)
        assertTrue(resOver is GoalResult.Error)
        assertEquals(GoalError.OverLimit(MAX_AMOUNT_PAISE + 1L), (resOver as GoalResult.Error).error)

        // Adding to non-existent goal
        val resNotFound = repository.addSaved(9999L, 1000L, "Note", now, now)
        assertTrue(resNotFound is GoalResult.Error)
        assertEquals(GoalError.GoalNotFound(9999L), (resNotFound as GoalResult.Error).error)

        // Adding to deleted goal
        repository.deleteGoal(goal.id, now)
        val resDeleted = repository.addSaved(goal.id, 1000L, "Note", now, now)
        assertTrue(resDeleted is GoalResult.Error)
        assertEquals(GoalError.GoalDeleted(goal.id), (resDeleted as GoalResult.Error).error)
    }

    @Test
    fun editEntry_success_andValidation() = runBlocking {
        val goal = (repository.createGoal("Gadget", 100000L, null, now) as GoalResult.Success).data
        val entry = (repository.addSaved(goal.id, 25000L, "Note 1", now, now) as GoalResult.Success).data

        val editRes = repository.editEntry(entry.id, 30000L, "  Updated note  ", now + 500L, now + 1000L)
        assertTrue(editRes is GoalResult.Success)
        val updated = (editRes as GoalResult.Success).data
        assertEquals(30000L, updated.amountPaise)
        assertEquals("Updated note", updated.note)
        assertEquals(now + 500L, updated.occurredAt)
        assertEquals(now + 1000L, updated.updatedAt)

        // Blank note failure
        val failNote = repository.editEntry(entry.id, 30000L, "", now, now)
        assertTrue(failNote is GoalResult.Error)
        assertEquals(GoalError.BlankNote, (failNote as GoalResult.Error).error)

        // Entry not found
        val notFound = repository.editEntry(9999L, 30000L, "Test", now, now)
        assertTrue(notFound is GoalResult.Error)
        assertEquals(GoalError.EntryNotFound(9999L), (notFound as GoalResult.Error).error)
    }

    @Test
    fun deleteAndUndeleteEntry() = runBlocking {
        val goal = (repository.createGoal("Gadget", 100000L, null, now) as GoalResult.Success).data
        val entry = (repository.addSaved(goal.id, 25000L, "Note", now, now) as GoalResult.Success).data

        // Delete entry
        val delRes = repository.deleteEntry(entry.id, now)
        assertTrue(delRes is GoalResult.Success)
        assertTrue(repository.getEntryById(entry.id)!!.isDeleted)

        // Undelete entry
        val undelRes = repository.undeleteEntry(entry.id, now)
        assertTrue(undelRes is GoalResult.Success)
        assertFalse(repository.getEntryById(entry.id)!!.isDeleted)
    }

    // --- Observe Goals with Totals & Entries Observation ---

    @Test
    fun observeGoalsWithTotals_sumsNonDeletedEntries_andCalculatesReached() = runBlocking {
        // Goal 1: Target ₹500 (50,000 paise)
        val goal1 = (repository.createGoal("Goal 1", 50000L, null, now) as GoalResult.Success).data
        // Goal 2: Target ₹100 (10,000 paise) - will be deleted
        val goal2 = (repository.createGoal("Goal 2", 10000L, null, now) as GoalResult.Success).data

        // Add entries to Goal 1
        val e1 = (repository.addSaved(goal1.id, 20000L, "Part 1", now, now) as GoalResult.Success).data
        val e2 = (repository.addSaved(goal1.id, 30000L, "Part 2", now, now) as GoalResult.Success).data
        // Add an entry that we then delete
        val e3 = (repository.addSaved(goal1.id, 15000L, "Part 3", now, now) as GoalResult.Success).data
        repository.deleteEntry(e3.id, now)

        // Delete Goal 2
        repository.deleteGoal(goal2.id, now)

        val list = repository.observeGoalsWithTotals().first()
        // Deleted Goal 2 must NEVER appear
        assertEquals(1, list.size)

        val g1Totals = list.first()
        assertEquals(goal1.id, g1Totals.id)
        assertEquals("Goal 1", g1Totals.name)
        // Total should be e1 (20000) + e2 (30000) = 50000. e3 is deleted so ignored!
        assertEquals(50000L, g1Totals.totalPaise)
        assertTrue("Reached must be true when totalPaise >= targetPaise", g1Totals.reached)
        assertEquals(100, g1Totals.percent)

        // Now delete e2 (30000), total becomes 20000 < 50000
        repository.deleteEntry(e2.id, now)
        val updatedList = repository.observeGoalsWithTotals().first()
        val updatedG1 = updatedList.first()
        assertEquals(20000L, updatedG1.totalPaise)
        assertFalse("Reached must be false when totalPaise < targetPaise", updatedG1.reached)
        assertEquals(40, updatedG1.percent)
    }

    @Test
    fun observeEntries_returnsNonDeletedNewestFirst() = runBlocking {
        val goal = (repository.createGoal("Jar", 100000L, null, now) as GoalResult.Success).data

        val e1 = (repository.addSaved(goal.id, 10000L, "Oldest", now - 2000L, now) as GoalResult.Success).data
        val e2 = (repository.addSaved(goal.id, 20000L, "Middle", now - 1000L, now) as GoalResult.Success).data
        val e3 = (repository.addSaved(goal.id, 30000L, "Newest", now, now) as GoalResult.Success).data
        val e4 = (repository.addSaved(goal.id, 40000L, "To delete", now + 1000L, now) as GoalResult.Success).data

        repository.deleteEntry(e4.id, now)

        val entries = repository.observeEntries(goal.id).first()
        // e4 is excluded because isDeleted = 1
        assertEquals(3, entries.size)
        // Newest first: e3, e2, e1
        assertEquals(e3.id, entries[0].id)
        assertEquals(e2.id, entries[1].id)
        assertEquals(e1.id, entries[2].id)
    }

    // --- Strict Spending Isolation ---

    @Test
    fun spendingTotalsAndTransactionsAreIdenticalBeforeAndAfterGoalOperations() = runBlocking {
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                INSERT INTO transactions (id, amount_paise, record_type, date_time, merchant_name, category, categorySource, payment_mode, note, transaction_hash, source, review_status, uncertainty_bits, created_at, updated_at, is_deleted)
                VALUES 
                (1, 150000, 'EXPENSE', 1000, 'Supermarket', 'Groceries', 'AUTO', 'UPI', 'Weekly groceries', 'h1', 'SMS', 'CONFIRMED', 0, 1000, 1000, 0),
                (2, 50000, 'EXPENSE', 2000, 'Cafe', 'Food & Drinks', 'AUTO', 'UPI', NULL, 'h2', 'SMS', 'CONFIRMED', 0, 2000, 2000, 0),
                (3, 20000, 'INCOME', 3000, 'Cafe Refund', 'Food & Drinks', 'AUTO', 'UPI', NULL, 'h3', 'SMS', 'CONFIRMED', 0, 3000, 3000, 0),
                (4, 30000, 'EXPENSE', 4000, 'Unknown POS', 'Others', 'AUTO', 'CARD', NULL, 'h4', 'SMS', 'NEEDS_REVIEW', 1, 4000, 4000, 0),
                (5, 10000, 'EXPENSE', 5000, 'Wrong Entry', 'Others', 'USER', 'CASH', NULL, 'h5', 'MANUAL_CASH', 'CONFIRMED', 0, 5000, 5000, 1)
                """.trimIndent()
            )
        }

        fun queryTransactionsSnapshot(): List<String> {
            val list = mutableListOf<String>()
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM transactions ORDER BY id ASC").use { rs ->
                    while (rs.next()) {
                        list.add("${rs.getLong("id")}|${rs.getLong("amount_paise")}|${rs.getString("record_type")}|${rs.getString("merchant_name")}|${rs.getInt("is_deleted")}")
                    }
                }
            }
            return list
        }

        fun queryActiveExpenseSpending(): Long {
            var sum = 0L
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COALESCE(SUM(amount_paise), 0) FROM transactions WHERE is_deleted = 0 AND record_type = 'EXPENSE'").use { rs ->
                    if (rs.next()) sum = rs.getLong(1)
                }
            }
            return sum
        }

        val initialSnapshot = queryTransactionsSnapshot()
        val initialSpending = queryActiveExpenseSpending()
        assertEquals(5, initialSnapshot.size)
        assertEquals(230000L, initialSpending) // 150000 + 50000 + 30000 = 230000

        // Perform full suite of goal operations
        val g1 = (repository.createGoal("Trip", 1000000L, null, now) as GoalResult.Success).data
        val g2 = (repository.createGoal("Laptop", 5000000L, todayStart + 86400000L, now) as GoalResult.Success).data
        val e1 = (repository.addSaved(g1.id, 50000L, "Cooked food", now, now) as GoalResult.Success).data
        val e2 = (repository.addSaved(g2.id, 100000L, "Skipped gadget", now, now) as GoalResult.Success).data
        repository.editEntry(e1.id, 60000L, "Cooked food at home", now, now)
        repository.deleteEntry(e2.id, now)
        repository.undeleteEntry(e2.id, now)
        repository.deleteGoal(g2.id, now)
        repository.createGoal("Laptop", 6000000L, null, now)

        val postSnapshot = queryTransactionsSnapshot()
        val postSpending = queryActiveExpenseSpending()

        // Must be completely identical!
        assertEquals("Transactions count must not change", initialSnapshot.size, postSnapshot.size)
        assertEquals("Transactions rows must be completely identical", initialSnapshot, postSnapshot)
        assertEquals("Spending total must be completely unchanged by goal operations", initialSpending, postSpending)
    }

    // --- SQLite Implementation of GoalDao executing real queries against SQLite JVM ---

    class SqliteGoalDao(private val conn: Connection) : GoalDao {

        override suspend fun insertGoal(goal: GoalEntity): Long {
            val sql = "INSERT INTO goals (name, name_key, target_paise, deadline_at, is_deleted, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setString(1, goal.name)
                stmt.setString(2, goal.nameKey)
                stmt.setLong(3, goal.targetPaise)
                if (goal.deadlineAt != null) stmt.setLong(4, goal.deadlineAt) else stmt.setNull(4, java.sql.Types.INTEGER)
                stmt.setInt(5, goal.isDeleted)
                stmt.setLong(6, goal.createdAt)
                stmt.setLong(7, goal.updatedAt)
                stmt.executeUpdate()
                stmt.generatedKeys.use { rs ->
                    if (rs.next()) return rs.getLong(1)
                }
            }
            error("Failed to insert goal")
        }

        override suspend fun updateGoal(goal: GoalEntity) {
            val sql = "UPDATE goals SET name = ?, name_key = ?, target_paise = ?, deadline_at = ?, is_deleted = ?, updated_at = ? WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, goal.name)
                stmt.setString(2, goal.nameKey)
                stmt.setLong(3, goal.targetPaise)
                if (goal.deadlineAt != null) stmt.setLong(4, goal.deadlineAt) else stmt.setNull(4, java.sql.Types.INTEGER)
                stmt.setInt(5, goal.isDeleted)
                stmt.setLong(6, goal.updatedAt)
                stmt.setLong(7, goal.id)
                stmt.executeUpdate()
            }
        }

        override suspend fun getGoalById(id: Long): GoalEntity? {
            val sql = "SELECT * FROM goals WHERE id = ? LIMIT 1"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) return readGoal(rs)
                }
            }
            return null
        }

        override suspend fun getGoalByNameKey(nameKey: String): GoalEntity? {
            val sql = "SELECT * FROM goals WHERE name_key = ? LIMIT 1"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, nameKey)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) return readGoal(rs)
                }
            }
            return null
        }

        override fun observeAllGoals(): Flow<List<GoalEntity>> = flow {
            emit(getAllGoals())
        }

        override suspend fun getAllGoals(): List<GoalEntity> {
            val list = mutableListOf<GoalEntity>()
            val sql = "SELECT * FROM goals WHERE is_deleted = 0 ORDER BY created_at ASC"
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    while (rs.next()) list.add(readGoal(rs))
                }
            }
            return list
        }

        override fun observeGoalsRaw(): Flow<List<GoalAggregateRaw>> = flow {
            val list = mutableListOf<GoalAggregateRaw>()
            val sql = """
                SELECT 
                    g.id,
                    g.name,
                    g.name_key,
                    g.target_paise,
                    g.deadline_at,
                    g.is_deleted,
                    g.created_at,
                    g.updated_at,
                    COALESCE(SUM(CASE WHEN e.is_deleted = 0 THEN e.amount_paise ELSE 0 END), 0) AS total_paise
                FROM goals g
                LEFT JOIN goal_entries e ON g.id = e.goal_id
                WHERE g.is_deleted = 0
                GROUP BY g.id
                ORDER BY g.created_at ASC
            """.trimIndent()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        val deadlineObj = rs.getObject("deadline_at")
                        val deadline = if (deadlineObj != null) rs.getLong("deadline_at") else null
                        list.add(
                            GoalAggregateRaw(
                                id = rs.getLong("id"),
                                name = rs.getString("name"),
                                name_key = rs.getString("name_key"),
                                target_paise = rs.getLong("target_paise"),
                                deadline_at = deadline,
                                is_deleted = rs.getInt("is_deleted"),
                                created_at = rs.getLong("created_at"),
                                updated_at = rs.getLong("updated_at"),
                                total_paise = rs.getLong("total_paise")
                            )
                        )
                    }
                }
            }
            emit(list)
        }

        override suspend fun softDeleteGoal(id: Long, tombstoneKey: String, now: Long) {
            val sql = "UPDATE goals SET is_deleted = 1, name_key = ?, updated_at = ? WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, tombstoneKey)
                stmt.setLong(2, now)
                stmt.setLong(3, id)
                stmt.executeUpdate()
            }
        }

        override suspend fun undeleteGoal(id: Long, restoredNameKey: String, now: Long) {
            val sql = "UPDATE goals SET is_deleted = 0, name_key = ?, updated_at = ? WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, restoredNameKey)
                stmt.setLong(2, now)
                stmt.setLong(3, id)
                stmt.executeUpdate()
            }
        }

        override suspend fun insertEntry(entry: GoalEntryEntity): Long {
            val sql = "INSERT INTO goal_entries (goal_id, amount_paise, note, occurred_at, is_deleted, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setLong(1, entry.goalId)
                stmt.setLong(2, entry.amountPaise)
                stmt.setString(3, entry.note)
                stmt.setLong(4, entry.occurredAt)
                stmt.setInt(5, entry.isDeleted)
                stmt.setLong(6, entry.createdAt)
                stmt.setLong(7, entry.updatedAt)
                stmt.executeUpdate()
                stmt.generatedKeys.use { rs ->
                    if (rs.next()) return rs.getLong(1)
                }
            }
            error("Failed to insert entry")
        }

        override suspend fun updateEntry(entry: GoalEntryEntity) {
            val sql = "UPDATE goal_entries SET goal_id = ?, amount_paise = ?, note = ?, occurred_at = ?, is_deleted = ?, updated_at = ? WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, entry.goalId)
                stmt.setLong(2, entry.amountPaise)
                stmt.setString(3, entry.note)
                stmt.setLong(4, entry.occurredAt)
                stmt.setInt(5, entry.isDeleted)
                stmt.setLong(6, entry.updatedAt)
                stmt.setLong(7, entry.id)
                stmt.executeUpdate()
            }
        }

        override suspend fun getEntryById(id: Long): GoalEntryEntity? {
            val sql = "SELECT * FROM goal_entries WHERE id = ? LIMIT 1"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) return readEntry(rs)
                }
            }
            return null
        }

        override fun observeEntriesForGoal(goalId: Long): Flow<List<GoalEntryEntity>> = flow {
            emit(getActiveEntriesForGoal(goalId))
        }

        override suspend fun getAllEntriesForGoal(goalId: Long): List<GoalEntryEntity> {
            val list = mutableListOf<GoalEntryEntity>()
            val sql = "SELECT * FROM goal_entries WHERE goal_id = ? ORDER BY occurred_at DESC, id DESC"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, goalId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) list.add(readEntry(rs))
                }
            }
            return list
        }

        override suspend fun getActiveEntriesForGoal(goalId: Long): List<GoalEntryEntity> {
            val list = mutableListOf<GoalEntryEntity>()
            val sql = "SELECT * FROM goal_entries WHERE goal_id = ? AND is_deleted = 0 ORDER BY occurred_at DESC, id DESC"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, goalId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) list.add(readEntry(rs))
                }
            }
            return list
        }

        override suspend fun softDeleteEntry(id: Long, now: Long) {
            val sql = "UPDATE goal_entries SET is_deleted = 1, updated_at = ? WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, now)
                stmt.setLong(2, id)
                stmt.executeUpdate()
            }
        }

        override suspend fun undeleteEntry(id: Long, now: Long) {
            val sql = "UPDATE goal_entries SET is_deleted = 0, updated_at = ? WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, now)
                stmt.setLong(2, id)
                stmt.executeUpdate()
            }
        }

        private fun readGoal(rs: java.sql.ResultSet): GoalEntity {
            val deadlineObj = rs.getObject("deadline_at")
            val deadline = if (deadlineObj != null) rs.getLong("deadline_at") else null
            return GoalEntity(
                id = rs.getLong("id"),
                name = rs.getString("name"),
                nameKey = rs.getString("name_key"),
                targetPaise = rs.getLong("target_paise"),
                deadlineAt = deadline,
                isDeleted = rs.getInt("is_deleted"),
                createdAt = rs.getLong("created_at"),
                updatedAt = rs.getLong("updated_at")
            )
        }

        private fun readEntry(rs: java.sql.ResultSet): GoalEntryEntity {
            return GoalEntryEntity(
                id = rs.getLong("id"),
                goalId = rs.getLong("goal_id"),
                amountPaise = rs.getLong("amount_paise"),
                note = rs.getString("note"),
                occurredAt = rs.getLong("occurred_at"),
                isDeleted = rs.getInt("is_deleted"),
                createdAt = rs.getLong("created_at"),
                updatedAt = rs.getLong("updated_at")
            )
        }
    }
}
