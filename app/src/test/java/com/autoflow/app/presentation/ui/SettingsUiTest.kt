package com.autoflow.app.presentation.ui

import com.autoflow.app.R
import com.autoflow.app.data.db.dao.CustomCategoryDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.CustomCategoryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.repository.CategoryRepository
import com.autoflow.app.data.repository.TransactionRepository
import com.autoflow.app.domain.category.CategoryItem
import com.autoflow.app.domain.category.CategoryOperationResult
import com.autoflow.app.presentation.theme.AppThemeMode
import com.autoflow.app.presentation.theme.DarkAutoFlowColors
import com.autoflow.app.presentation.theme.LightAutoFlowColors
import com.autoflow.app.presentation.ui.components.AFCategoryIcons
import com.autoflow.app.presentation.ui.components.CategoryThemeRegistry
import com.autoflow.app.presentation.viewmodel.InMemoryThemePreferences
import com.autoflow.app.presentation.viewmodel.MainViewModel
import com.autoflow.app.presentation.viewmodel.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsUiTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeCustomDao: FakeCustomCategoryDao
    private lateinit var fakeTxDao: FakeTransactionDao
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var txRepo: TransactionRepository
    private lateinit var themePrefs: InMemoryThemePreferences
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeCustomDao = FakeCustomCategoryDao()
        fakeTxDao = FakeTransactionDao()
        categoryRepo = CategoryRepository(
            customCategoryDao = fakeCustomDao,
            transactionDao = fakeTxDao
        )
        txRepo = TransactionRepository(
            transactionDao = fakeTxDao,
            customCategoryDao = fakeCustomDao
        )
        themePrefs = InMemoryThemePreferences(initial = AppThemeMode.SYSTEM)
        viewModel = MainViewModel(
            transactionRepository = txRepo,
            categoryRepository = categoryRepo,
            themePreferences = themePrefs,
            timeProvider = { Instant.ofEpochMilli(1700000000000L) }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testThemeModePersistenceAndMapping() {
        assertEquals(AppThemeMode.SYSTEM, themePrefs.themeMode.value)
        assertEquals(AppThemeMode.SYSTEM, viewModel.themeMode.value)

        viewModel.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, themePrefs.themeMode.value)
        assertEquals(AppThemeMode.DARK, viewModel.themeMode.value)

        viewModel.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, themePrefs.themeMode.value)
        assertEquals(AppThemeMode.LIGHT, viewModel.themeMode.value)
    }

    @Test
    fun testCategoryValidationPlainWordMessages() {
        assertEquals("Name cannot be empty", CategoryOperationResult.NameBlank.toUserMessage())
        assertEquals("Name must be 20 characters or less", CategoryOperationResult.NameTooLong.toUserMessage())
        assertEquals(
            "Name contains invalid characters. Use letters, numbers, spaces, and & - ' only",
            CategoryOperationResult.NameInvalidCharacters.toUserMessage()
        )
        assertEquals("A category with this name already exists", CategoryOperationResult.NameNotUnique.toUserMessage())
        assertEquals("Please select a valid icon", CategoryOperationResult.InvalidIcon.toUserMessage())
        assertEquals("Please select a valid color", CategoryOperationResult.InvalidColor.toUserMessage())
        assertEquals(
            "Limit reached. You can only create up to 30 custom categories",
            CategoryOperationResult.TooManyCategories.toUserMessage()
        )
        assertEquals(
            "Built-in categories cannot be edited or deleted",
            CategoryOperationResult.BuiltInCategoryReadOnly.toUserMessage()
        )
        assertEquals("Category not found", CategoryOperationResult.CategoryNotFound.toUserMessage())
        assertNull(CategoryOperationResult.Success().toUserMessage())
    }

    @Test
    fun testCustomCategoryColorAndIconResolution() {
        val customCat = CategoryItem(
            name = "Pet Care",
            isBuiltIn = false,
            iconKey = "PAW",
            colorKey = "ORANGE"
        )
        CategoryThemeRegistry.updateCategories(listOf(customCat))

        // Icon resolves to ic_cat_paw instead of ic_cat_unknown
        val iconRes = AFCategoryIcons.getDrawableRes("Pet Care")
        assertEquals(R.drawable.ic_cat_paw, iconRes)

        // Color in Light theme resolves to ORANGE (catFood token) instead of catUnknown
        val lightColor = LightAutoFlowColors.forCategory("Pet Care")
        assertEquals(LightAutoFlowColors.catFood, lightColor)

        // Color in Dark theme resolves to ORANGE (catFood token) instead of catUnknown
        val darkColor = DarkAutoFlowColors.forCategory("Pet Care")
        assertEquals(DarkAutoFlowColors.catFood, darkColor)
    }

    @Test
    fun testAddCategoryFlowThroughViewModel() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.allCategoryItems.collect()
        }
        advanceUntilIdle()

        val result = viewModel.addCategory(
            name = "Gaming",
            iconKey = "GIFT",
            colorKey = "PURPLE"
        )
        assertTrue(result is CategoryOperationResult.Success)
        advanceUntilIdle()

        val items = viewModel.allCategoryItems.value
        val gaming = items.find { it.name == "Gaming" }
        assertNotNull(gaming)
        assertEquals("GIFT", gaming?.iconKey)
        assertEquals("PURPLE", gaming?.colorKey)
    }

    @Test
    fun testUpdateCategoryFlowThroughViewModel() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.allCategoryItems.collect()
        }
        advanceUntilIdle()

        viewModel.addCategory("Books", "BOOK", "BLUE")
        advanceUntilIdle()

        val updateResult = viewModel.updateCategory(
            currentName = "Books",
            newName = "Study",
            iconKey = "BOOK",
            colorKey = "TEAL"
        )
        assertTrue(updateResult is CategoryOperationResult.Success)
        advanceUntilIdle()

        val items = viewModel.allCategoryItems.value
        assertNull(items.find { it.name == "Books" })
        val study = items.find { it.name == "Study" }
        assertNotNull(study)
        assertEquals("TEAL", study?.colorKey)
    }

    @Test
    fun testDeleteCategoryFlowThroughViewModel() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.allCategoryItems.collect()
        }
        advanceUntilIdle()

        viewModel.addCategory("Hobbies", "MUSIC", "PINK")
        advanceUntilIdle()
        assertNotNull(viewModel.allCategoryItems.value.find { it.name == "Hobbies" })

        val deleteResult = viewModel.deleteCategory("Hobbies")
        assertTrue(deleteResult is CategoryOperationResult.Success)
        advanceUntilIdle()

        assertNull(viewModel.allCategoryItems.value.find { it.name == "Hobbies" })
    }

    private class FakeCustomCategoryDao : CustomCategoryDao {
        private val items = mutableListOf<CustomCategoryEntity>()
        private val flow = MutableStateFlow<List<CustomCategoryEntity>>(emptyList())

        private fun emit() {
            flow.value = items.toList()
        }

        override fun getAll(): Flow<List<CustomCategoryEntity>> = flow

        override suspend fun getAllSync(): List<CustomCategoryEntity> = items.toList()

        override suspend fun getByNameKey(nameKey: String): CustomCategoryEntity? =
            items.find { it.nameKey == nameKey }

        override suspend fun count(): Int = items.size

        override suspend fun insert(entity: CustomCategoryEntity) {
            items.removeAll { it.nameKey == entity.nameKey }
            items.add(entity)
            emit()
        }

        override suspend fun update(entity: CustomCategoryEntity) {
            val idx = items.indexOfFirst { it.nameKey == entity.nameKey }
            if (idx != -1) {
                items[idx] = entity
                emit()
            }
        }

        override suspend fun updateCategoryValues(
            oldKey: String,
            newKey: String,
            newName: String,
            newIcon: String,
            newColor: String
        ): Int {
            val idx = items.indexOfFirst { it.nameKey == oldKey }
            if (idx != -1) {
                val existing = items[idx]
                items[idx] = existing.copy(
                    nameKey = newKey,
                    name = newName,
                    iconKey = newIcon,
                    colorKey = newColor
                )
                emit()
                return 1
            }
            return 0
        }

        override suspend fun deleteByKey(nameKey: String): Int {
            val removed = items.removeAll { it.nameKey == nameKey }
            if (removed) emit()
            return if (removed) 1 else 0
        }
    }

    private class FakeTransactionDao : TransactionDao {
        private val transactions = mutableListOf<TransactionEntity>()
        private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())

        override suspend fun insert(transaction: TransactionEntity): Long {
            transactions.add(transaction)
            flow.value = transactions.toList()
            return transaction.id
        }

        override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> =
            transactions.map { insert(it) }

        override suspend fun update(transaction: TransactionEntity) {
            val idx = transactions.indexOfFirst { it.id == transaction.id }
            if (idx != -1) {
                transactions[idx] = transaction
                flow.value = transactions.toList()
            }
        }

        override suspend fun getById(id: Long): TransactionEntity? =
            transactions.find { it.id == id }

        override suspend fun getByHash(hash: String): TransactionEntity? =
            transactions.find { it.transactionHash == hash }

        override suspend fun findDuplicateCandidate(
            reference: String,
            recordType: RecordType,
            amountPaise: Long,
            minDateTime: Long,
            maxDateTime: Long
        ): TransactionEntity? = null

        override suspend fun findExpensesByReference(reference: String): List<TransactionEntity> =
            emptyList()

        override suspend fun findExpensesByMerchant(
            merchantName: String,
            minDateTime: Long,
            maxDateTime: Long
        ): List<TransactionEntity> = emptyList()

        override fun getAllActiveTransactions(): Flow<List<TransactionEntity>> = flow

        override fun getTransactionsForPeriod(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> =
            flow.map { list -> list.filter { it.dateTime in startTime..endTime } }

        override fun getTransactionsByReviewStatus(status: ReviewStatus): Flow<List<TransactionEntity>> =
            flow.map { list -> list.filter { it.reviewStatus == status } }

        override fun getUnreviewedCount(): Flow<Int> =
            flow.map { list -> list.count { it.reviewStatus == ReviewStatus.NEEDS_REVIEW } }

        override suspend fun softDelete(id: Long, updatedAt: Long) {
            val idx = transactions.indexOfFirst { it.id == id }
            if (idx != -1) {
                transactions[idx] = transactions[idx].copy(isDeleted = 1, updatedAt = updatedAt)
                flow.value = transactions.toList()
            }
        }

        override suspend fun undelete(id: Long, updatedAt: Long) {
            val idx = transactions.indexOfFirst { it.id == id }
            if (idx != -1) {
                transactions[idx] = transactions[idx].copy(isDeleted = 0, updatedAt = updatedAt)
                flow.value = transactions.toList()
            }
        }

        override suspend fun getAllAutoCategorizedTransactions(): List<TransactionEntity> =
            transactions.filter { it.categorySource == "AUTO" }

        override fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> =
            flow.map { list -> list.filter { it.dateTime in startTime until endTime } }

        override fun searchTransactions(
            textPattern: String,
            exactPaise: Long?,
            digitPattern: String?,
            limit: Int
        ): Flow<List<TransactionEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun updateCategory(oldCategory: String, newCategory: String): Int {
            var updated = 0
            for (i in transactions.indices) {
                if (transactions[i].category == oldCategory) {
                    transactions[i] = transactions[i].copy(category = newCategory)
                    updated++
                }
            }
            if (updated > 0) flow.value = transactions.toList()
            return updated
        }

        override suspend fun reassignCategoryToOthers(category: String, now: Long): Int {
            var count = 0
            for (i in transactions.indices) {
                if (transactions[i].category == category) {
                    transactions[i] = transactions[i].copy(category = "Others", categorySource = "AUTO", updatedAt = now)
                    count++
                }
            }
            if (count > 0) flow.value = transactions.toList()
            return count
        }

        override suspend fun countActiveTransactionsInCategory(category: String): Int =
            transactions.count { it.category.equals(category, ignoreCase = true) && it.isDeleted == 0 }
    }
}
