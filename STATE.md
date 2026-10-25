# AutoFlow — System State & Architecture

## 1. What Is Built (By Phase)

- **Phase 1 (Audit & Licensing):** Evaluated upstream PennyWise parser-core engine; verified AGPL-3.0 compatibility with private local on-device operation; added root `LICENSE` (AGPL-3.0) and `NOTICE` files.
- **Phase 2A (Parser Core & Domain Adapter):** Integrated decoupled `parser-core` module via Gradle; implemented `AutoFlowParserAdapter` mapping `ParsedTransaction` to Room `TransactionEntity`; enforced integer paise conversion, expense-only scoping, dropped non-expense entries (salary/income/balance), uncertainty bitmasks, and zero raw SMS retention.
- **Phase 2B (Ingestion Pipeline, Storage & Receivers):** Room SQLite storage (`TransactionEntity`, `RefundMatchEntity`, `TransactionDao`, `RefundMatchDao`); `DefaultIngestionPipeline` with 2-tier deduplication, soft-delete safety, and refund amount capping; `SmsBroadcastReceiver` registered strictly for `RECEIVE_SMS` with `SmsMessageProcessor` background dispatcher.
- **Phase 2C (Axis Bank CC Fix):** Targeted regex addition (`spentAtPattern`) in `AxisBankParser` for credit card transactions with trailing `"IST at <MERCHANT>."` layout without touching shared base parsers.
- **Phase 3 (Presentation Layer):** Jetpack Compose Material 3 UI (`HomeScreen`, `EditTransactionScreen`, `AddCashExpenseScreen`, `MainViewModel`); spend totals (Today and This Month in local timezone); single-line "Add Cash" action; prefilled empty field for "Unknown Merchant" with optional placeholder; muted "Not counted" styling for `EXCLUDED` transactions with a "Restore" button; date and time pickers for manual cash entries; centralized design tokens (`Theme.kt`).
- **Phase 4 (Ingestion Safety Guards & Hardening):** Conservative phrase-based guard (`isNonTransactionMessage`) in `AutoFlowParserAdapter` and `SmsMessageProcessor` rejecting non-transaction alerts while protecting legitimate spends:
  - *OTP Guard (Narrowed & Hardened):* Blocks only when an explicit OTP phrase is present (`"is the OTP"`, `"OTP is"`, `"OTP for ... is"`, `"verification code"`) or a 4–8 digit code appears within ~30 chars of `"OTP"` or `"One Time Password"`. Bare `"OTP"` and fraud warning footers (`"Never share OTP"`, `"Do not share your OTP"`, `"never disclose your One Time Password"`) do NOT block transactions.
  - *Credit Limit Guard (Narrowed):* Blocks only when `"credit limit"` is followed within ~35 chars by `"is now"`, `"increased to"`, `"enhanced to"`, `"revised to"`, or `"has been (increased|enhanced|revised)"`. Spends carrying `"Available credit limit"` or `"Avl limit"` are never blocked.
  - *Completed Refund Precedence & Global Promise Guard (Prompt M, M2, M3, M4):* Global phrase-based promise guard blocks messages promising a future/conditional refund or reversal (`"will (be|get) (reversed|refunded|credited|initiated|processed)"`, `"refund (has been |is )?initiated"`, `"if debited"`, `"refund, if applicable"`, `"will reflect/credit/reverse"`, or `"within N days"` without a completed refund action). Failed/declined debits are strictly ignored via raw promise check even if "debited" appears. The bare word `"credited"` is not a refund signal (prevents payee credits like `"ZOMATO credited"`, `"credited to VPA"`, or wallet cashback credits from flipping spends). True refund signals require `"refund"`, `"refunded"`, `"refund of"`, `"reversal"`, `"reversed"`, `"chargeback"`, `"returned"`, or `"credited back"`. Completed refunds override past debit mentions (`paid`, `debited`, `spent`), classifying as `REFUND`. Outbound payments misclassified as `INCOME` by upstream parsers due to payee credit notices are ingested as `EXPENSE`.
  - *Inbound Money Safety & Strong Outbound Phrase Gating (Prompt M4, M5, M6, M7):* In `adapt()`, messages already classified by parser-core as `EXPENSE` or `CREDIT` are never gated by outbound words, and inbound markers (`"paid by"`, `"sent by"`, `"salary of"`, or existing `isInboundTransfer` patterns: `"transferred to your account/bank account/A/c"`, `"credit alert"`, `"payout"`) drop a message ONLY IF it has NO strong outbound spend phrase (`"debit alert"`, `"debited"`, `"spent"`, `"withdrawn"`, `"sent to"`, `"paid to"`, `"transferred to <name>"`, `"dr"`). If a strong outbound spend phrase is present, the message stays an `EXPENSE`, ensuring outbound debits with payment instrument clauses (e.g. `"Paid by UPI"`, `"Sent by you via UPI"`, `"Paid by HDFC Bank Debit Card"`) are ingested while pure inbound deposits (M6 rows a-e) remain strictly ignored or refunded.
  - *Widened Spend Vocabulary (Prompt M5):* Expanded `completedSpendStatementRegex` to recognize `charged`, `deducted`, `used`, `debit card`, and `dr` alongside existing spend keywords (`debited`, `debit alert`, `spent`, `paid`, `sent`, `transferred to <name>`, `withdrawn`, `purchase`).
  - *Regression Corpus (`SmsRegressionCorpusTest.kt`):* Table-driven regression suite (`app/src/test/java/com/autoflow/app/data/adapter/SmsRegressionCorpusTest.kt`) expanded to 61 test cases covering Prompt M7 active rows N-1..N-5 (outbound debit messages with payment instrument clauses), M6 inbound money false-expense guards, M5 spend variations / parser-core limitation controls, M4 inbound transfer safety / UPI VPA extraction, M3 payee credit messages, M2 corner cases, Prompt M promise/completed pairs, Prompt 0 safety probe controls, and Prompt J narrowed-guard positive controls.
  - *Reminders & Summaries:* Blocks messages starting with `"reminder:"` or payment/mandate reminders, while allowing debits containing `"Reminder"` later in body text. Blocks monthly spend summaries, promos, card statements, and card debt repayments.

- **Phase 5 (Category Engine v1, User Rules & Room v2 Migration Proof):** CategoryEngine with 4-tier resolution hierarchy: (1) user's saved merchant rule on exact normalized key, (2) built-in aliases (`"swiggy inst"`, `"instamart"` -> `Groceries`; `"makemytrip"`, `"make my trip"`, `"irctc"` -> `Travel`), (3) keyword mapper (`KeywordCategoryMapper.kt` derived from PennyWise's `SharedCategoryMapping.kt` with pure Kotlin, whole-word/phrase regex matching on normalized merchant text, mapping upstream categories to AutoFlow's 9 fixed categories), (4) fallback to `"Others"`. Room database bumped to version 2 via explicit `MIGRATION_1_2` creating `merchant_categories` table and adding `categorySource` column (`'AUTO'` vs `'USER'`). Atomic retro-updates when user corrects a category: updates target row to `USER`, upserts rule, updates other active `AUTO` rows with same normalized key, and skips `USER` or soft-deleted rows. Junk merchants never store rules. Credit card payment messages classified as `"Others"` with reviewStatus `EXCLUDED`. Room schemas v1 and v2 exported to `app/schemas/`. Pure JVM SQLite migration test (`RoomMigration1To2Test.kt`) using `sqlite-jdbc` proves data preservation across sample rows (`categorySource` defaulted to `'AUTO'`), verifies `PRAGMA table_info` for `transactions` and `merchant_categories` matches 2.json, and proves real production DAO SQL (`TransactionDao.QUERY_GET_ALL_AUTO`) and retro-update logic never return or modify `USER` or soft-deleted rows. Verified on connected Android device after database backup.
- **Phase 6 (Payment Mode, Optional Note, Editable Date, Range Query & Room v3 Migration — Prompt P):** Room database bumped to version 3 via explicit `MIGRATION_2_3` (`ALTER TABLE transactions ADD COLUMN payment_mode TEXT NOT NULL DEFAULT 'UNKNOWN'`, `ALTER TABLE transactions ADD COLUMN note TEXT`, and backfill `UPDATE transactions SET payment_mode = 'CASH' WHERE source = 'MANUAL_CASH'`). Room schema `3.json` exported. Payment mode detection at SMS ingestion in `AutoFlowParserAdapter` (`CREDIT`, `UPI`, `BANK`, fallback `UNKNOWN`; manual cash entries default to `CASH`). `FinancialTransaction` domain model updated with `paymentMode: String` and `note: String?`. Typed repository update result (`UpdateTransactionResult` with `Success`, `NotFound`, `FutureDateNotAllowed`, `NoteTooLong`, `InvalidAmount`, `CannotEditSmsAmount`, `InvalidPaymentMode`); amount editable only for `MANUAL_CASH` rows (positive and `<= 10_000_000_000L` paise); transaction hash remains immutable on edit. Half-open range query `[start, end)` (`date_time >= :startTime AND date_time < :endTime`) in DAO and repository (`getTransactionsInRange`), with pure helper delegates to `SpendingStats` for 5-month spending series (net spend floored at 0) and range category breakdown. Pure JVM SQLite migration test (`RoomMigration2To3Test.kt`) proves schema preservation, defaults, manual backfill, `PRAGMA table_info` parity with `3.json`, and range query bounds on real SQLite. Tested on connected physical Android device after backup.

## 2. Money & Privacy Rules

- **Paise Representation:** All monetary values stored and calculated as positive `Long` integers in paise (`round(amount * 100)`). No floating-point types in Room entities or math.
- **Expense-Only Scope:** Outbound payments are `EXPENSE`. Reversals, cashbacks, and refunds are `REFUND`. Ordinary salary credits and peer deposits without refund signals are ignored.
- **Spending Totals:** Only active (`is_deleted = 0`), unexcluded (`reviewStatus != EXCLUDED`), `EXPENSE` records count toward Today and This Month totals. `NEEDS_REVIEW` expenses are counted. `REFUND` records do not deduct from totals in MVP.
- **Zero Raw SMS Retention:** Message text exists transiently in memory during parsing, dereferenced immediately, never persisted to disk or SQLite.
- **Zero Network/Cloud Access:** No `INTERNET` permission in `AndroidManifest.xml`; no telemetry, analytics, or cloud sync.
- **Restricted Permissions:** Declares `android.permission.RECEIVE_SMS` only. `READ_SMS` is prohibited and absent.
- **Privacy-First Logging:** Android `Log.*` calls never output raw SMS bodies, OTPs, or full account numbers.

## 3. Deduplication Design (As Implemented in Code)

`DefaultIngestionPipeline.kt` runs a sequential two-tier check within a database transaction:
1. **Tier 1 (Exact Hash Match):** Queries `transactionDao.getByHash(transactionHash)`. If an active record matches, drops as `DuplicateIgnored`. If a soft-deleted record matches (`is_deleted = 1`), drops as `SoftDeletedIgnored` to prevent reviving deleted items.
2. **Tier 2 (Reference & Time Window Match):** For transactions with non-blank `reference`, queries records with identical `reference`, `recordType`, and `amountPaise` within `[dateTime - 48 hours, dateTime + 48 hours]` (`val windowMs = 48 * 3600 * 1000L`). If soft-deleted, drops as `SoftDeletedIgnored`; if active, drops as `DuplicateIgnored`.
3. **Refund Capping:** Ingested refunds search for matching prior expenses by reference or merchant. Total refunded paise is capped at original expense amount. Unmatched refunds are tagged `NEEDS_REVIEW` with `UncertaintyReason.UNMATCHED_REFUND`.

## 4. Known Open Issues

1. **Refund UI Reconciliation:** Refunds appear in the transaction feed with a badge, but do not yet automatically link to or offset specific expense rows in the totals UI.
2. **Peer-to-Peer UPI VPA Names:** Peer handles without real merchant identity require user review. Phone-number handles fall back to "Unknown Merchant".
4. **AxisBankParser spentAtPattern Boundary:** `spentAtPattern` in `AxisBankParser` triggers on messages containing `"Credit Card"` even without the word `"Spent"` (to tighten in a future update).
5. **OutOfScope Commit 6e8ee14:** An `extractReference` modification in commit `6e8ee14` was outside its originally requested scope.
6. **Play Store Policy:** Google Play Store SMS permission (`RECEIVE_SMS`) policy justification and compliance pathway is unresolved.

## 5. What Is NOT Verified

- **Historical Room SQL Queries via Real SQLite:** `MIGRATION_1_2` and new DAO queries (`getAllAutoCategorizedTransactions`, `MerchantCategoryDao` upsert and select) are verified on real SQLite via `sqlite-jdbc` in `RoomMigration1To2Test.kt`. However, historical Room queries in `TransactionDao` (`getTransactionsForPeriod`, `getUnreviewedCount`, etc.) still rely on `FakeTransactionDao` in JVM unit tests.
- **Android 14+ Doze Mode SMS Reception:** Background SMS receiver reliability under aggressive OEM battery optimizations and deep doze without a foreground service (not verified).
- **Non-Axis/Non-HDFC Carrier SMS:** Real carrier-delivered SMS layout variations for ICICI, SBI, and Kotak banks beyond synthetic test fixtures (not verified).
- **High-Volume Database Performance:** Room query performance and Compose list scrolling under large datasets (50,000+ transactions) on low-end hardware (not verified).

## 6. Goals Domain

- **Pure Kotlin Architecture:** Fully decoupled domain layer (`domain/goals/`) with zero Android, UI, or database dependencies. All monetary values use integer paise with a hard limit of 10 crore rupees (`MAX_AMOUNT_PAISE = 10_000_000_000L`).
- **Goal Kinds & Entry Semantics:**
  - `SAVINGS`: Tracks funds set aside toward a target; accepts `SET_ASIDE`, `AVOIDED`, and `USED` entries.
  - `AVOIDANCE`: Tracks money saved by avoiding expenses (e.g. quitting habits); accepts only `AVOIDED` entries (`SET_ASIDE` and `USED` rejected with `GoalError.WrongEntryType`).
- **Entry Validations & Notes:**
  - `AVOIDED` entries require a non-blank description between 1 and 80 characters (`DescriptionRequired` / `DescriptionTooLong`).
  - `SET_ASIDE` and `USED` entries support optional notes up to 120 characters (`NoteTooLong`). Validated on both creation and edit.
- **Saved Balance Invariant & InsufficientBalance:**
  - Saved balance is strictly calculated as `sum(SET_ASIDE) - sum(USED)` over non-deleted entries.
  - Balance cannot drop below zero: any operation that would make balance negative (oversized `USED`, editing `USED` up past the balance, deleting or reducing `SET_ASIDE` below the `USED` total) is rejected with `GoalError.InsufficientBalance(availablePaise, requestedPaise)`.
- **Expense Linking & Spending from Goals:**
  - `linkExpense(goalId, transactionId, expenseAmountPaise, amountPaise)`: Links an existing expense transaction to a goal. Rejects `amountPaise > expenseAmountPaise` (`AmountExceedsExpense`) and `amountPaise <= 0`. Replaces earlier links for the same transaction (including across goals).
  - `unlinkExpense(transactionId)`: Soft-deletes the linked `USED` entry and restores the goal balance.
  - `takeOut(goalId, amountPaise, note)`: Records non-expense withdrawals from savings (`linkedTransactionId = null`).
  - `cancelUsageForTransaction(transactionId)` & `restoreUsageForTransaction(transactionId)`: Idempotently soft-deletes and restores linked `USED` entries for expense deletion and Undo workflows.
- **Progress Calculations:** Savings goals compute progress percent and `isReached` against net saved amount (`setAsidePaise - usedPaise`). Avoidance goals compute progress against `avoidedPaise`.

## 7. Spending Statistics Domain

- **Pure Kotlin Architecture:** Decoupled analytics and statistics calculation engine (`domain/stats/SpendingStats.kt`) with zero Android, UI, or database dependencies. All amounts calculated as integer paise (`Long`) with overflow-safe arithmetic (`Math.addExact`, `BigInteger`).
- **Spending Eligibility Rules:** Reuses core criteria from `domain/totals/SpendingTotals.kt`:
  - Active (`is_deleted = 0`), outbound (`recordType == EXPENSE`), non-excluded (`reviewStatus != EXCLUDED`).
  - Pending review (`NEEDS_REVIEW`) items are included.
  - `REFUND` records are excluded (not subtracted in MVP).
- **Period Ranges (`periodRange`):** Calculates half-open intervals `[start, end)` in UTC instants based on caller-supplied `ZoneId` and `Instant` anchor:
  - `TODAY`: Local midnight to next midnight.
  - `THIS_WEEK`: ISO-8601 Monday midnight to next Monday midnight.
  - `THIS_MONTH`: First day of month at midnight to first day of next month at midnight.
- **5-Month Trend Series (`monthlySeries`):**
  - Generates exactly 5 `MonthPoint(month, totalPaise, isFuture)` points: two preceding months, the selected month, and two following months.
  - Months strictly after `currentMonth` are flagged `isFuture = true` with `totalPaise = 0L`.
- **Category Breakdown (`categoryBreakdown`):**
  - Aggregates spending per category over a given `PeriodRange`. Omits categories with zero spend.
  - Whole-number percentage calculation using the **Largest Remainder Method** (Hamilton/Hare–Niemeyer), guaranteeing percentages sum to exactly 100% when total spend > 0.
  - Deterministic tie-breaking: sorts by largest remainder descending, then largest spend descending, then alphabetical category name ascending.
- **Daily Totals (`dayTotal`, `dailyTotals`):** Computes spend totals per local calendar day or across ranges, clamped at a minimum of `0L`.

## 8. Mode, Note, Editable Date, Range Query & Room v3 (Prompt P)

- **Room Version 3 Schema & Explicit Migration:**
  - Added columns `payment_mode TEXT NOT NULL DEFAULT 'UNKNOWN'` and `note TEXT` (nullable) to `transactions`.
  - Migration `MIGRATION_2_3`:
    1. `ALTER TABLE transactions ADD COLUMN payment_mode TEXT NOT NULL DEFAULT 'UNKNOWN'`
    2. `ALTER TABLE transactions ADD COLUMN note TEXT`
    3. `UPDATE transactions SET payment_mode = 'CASH' WHERE source = 'MANUAL_CASH'`
  - Verified on real SQLite via `RoomMigration2To3Test.kt`: old rows preserved intact, SMS rows default to `UNKNOWN`, manual rows backfilled to `CASH`, PRAGMA table_info matches schema `3.json`.
  - Verified on physical test device `dfa4dd90` after database backup (`before_v3.db`, `-wal`, `-shm`). Zero runtime or Room errors.
- **Payment Mode Detection & Support Email Disambiguation (Prompt P2):**
  - Detected at transient SMS ingestion time in `AutoFlowParserAdapter.detectPaymentMode(smsBody)`:
    1. `CREDIT`: credit card, cc, card ending/no followed by credit
    2. `UPI`: upi, vpa, @ handle (`@[a-zA-Z0-9_-]+(?![a-zA-Z0-9_.-]*\.[a-zA-Z0-9])`). Gated so that tokens containing dots after the `@` (such as bank customer support email addresses `cardservices@hdfcbank.net`) are NEVER misidentified as UPI handles.
    3. `BANK`: debit card, neft, imps, rtgs, atm, a/c debited, debited from a/c
    4. `UNKNOWN`: any unrecognized payment method
  - Manual cash entries set `payment_mode = CASH`.
  - Validated by table-driven test suite `PaymentModeDetectionTest.kt` (20 realistic test cases covering all 4 modes, support email endings, and handle variations).
- **Domain Model Updates:**
  - `FinancialTransaction` includes `paymentMode: String` (defaults to `"UNKNOWN"`) and `note: String?` (defaults to `null`).
- **Repository `updateTransaction` & Atomicity:**
  - Atomic single-transaction update with typed validation results (`UpdateTransactionResult`):
    - Rejects future dates (`dateTime > now` -> `FutureDateNotAllowed`).
    - Note length boundary: trimmed, max 200 chars (`NoteTooLong`), whitespace-only becomes `null`.
    - Amount edit: allowed only for manual rows (`source == MANUAL_CASH`) within `1..10_000_000_000L` paise (`InvalidAmount`). Rejected for SMS rows (`CannotEditSmsAmount`).
    - Validates payment mode against `PaymentMode.ALL` (`InvalidPaymentMode`).
    - Transaction hash is immutable and never changed on edit.
    - Category rule behavior preserved: real merchant category changes upsert user rules and retro-update active `AUTO` rows, while leaving `USER` and soft-deleted rows untouched.
  - Atomicity verified both on real SQLite (`RoomMigration2To3Test.kt`) and repository level (`TransactionRepositoryUpdateTest.kt`): failure in Step 2 completely rolls back Step 1.
- **Half-Open Range Query `[start, end)`:**
  - `TransactionDao.QUERY_GET_IN_RANGE`: `SELECT * FROM transactions WHERE is_deleted = 0 AND date_time >= :startTime AND date_time < :endTime ORDER BY date_time DESC`.
  - Verified on real SQLite in `RoomMigration2To3Test`: inclusive of start bound, exclusive of end bound, excludes soft-deleted rows. Mutation of production SQL from `<` to `<=` proved red on real SQLite.
  - Exposes `fiveMonthSpendingSeries` (selected month + 4 prior, net spend floored at 0) and `categoryBreakdown` delegates via `SpendingStats`.
- **Live Device Database Verification (Prompt P2):**
  - Inspected live physical device database (`live_after_v3.db`) with `sqlite-jdbc`:
    - `PRAGMA user_version = 3`.
    - `payment_mode`: 3 `CASH`, 2 `UNKNOWN`.
    - `source` x `payment_mode`: 3 `MANUAL_CASH` -> `CASH`, 2 `SMS_AUTO` -> `UNKNOWN`. Total 5 rows intact.

## 9. updateTransaction Atomicity Proof & Production SQL Auditing (Prompt P3)

- **Production Atomicity Proof on Real SQLite (`TransactionRepositoryRealSqliteAtomicityTest.kt`):**
  - Host-JVM unit tests cannot run Android Room's SQLite framework driver without Android OS.
  - `TransactionRepository` accepts an injectable transaction runner: in production on Android, `AutoFlowDatabase.runInTransaction` is supplied.
  - In `TransactionRepositoryRealSqliteAtomicityTest.kt`, the repository executes against real SQLite (`sqlite-jdbc`, schema migrated from v2 to v3) using a real JDBC transaction runner:
    `conn.autoCommit = false; try { block(); conn.commit() } catch (e) { conn.rollback(); throw e } finally { conn.autoCommit = true }`.
  - When write 2 (`merchantCategoryDao.upsert`) fails with a SQLite constraint/disk exception, real SQLite `conn.rollback()` executes.
  - Assertions on raw SQLite state prove write 1 is completely rolled back: `category` ("Food"), `categorySource` ("AUTO"), `payment_mode` ("UPI"), `note` (`null`), and `date_time` (original timestamp) remain 100% unchanged.
  - **Production Mutation Proof:** Moving `transactionDao.update(updated)` outside `runInTransaction` in `TransactionRepository.kt` caused `updateTransaction_whenSecondWriteFails_firstWriteIsRolledBackInRealSqlite` to fail with `org.junit.ComparisonFailure: expected:<[Food]> but was:<[Coffee]>`. Reverted cleanly with `git diff` showing zero changes.
- **DAO UPDATE Statement Auditing:**
  - `updateTransaction` in production does not execute hand-written partial SQL; it calls `@Update suspend fun update(transaction: TransactionEntity)`.
  - Room compiles this to: `UPDATE OR ABORT transactions SET id = ?, amount_paise = ?, record_type = ?, date_time = ?, merchant_name = ?, category = ?, categorySource = ?, payment_mode = ?, note = ?, account_number = ?, bank_name = ?, reference = ?, transaction_hash = ?, source = ?, review_status = ?, uncertainty_bits = ?, created_at = ?, updated_at = ?, is_deleted = ? WHERE id = ?`.
  - Exposed as production constant `TransactionDao.SQL_UPDATE`.
  - `RoomMigration2To3Test.kt` updated to reference `TransactionDao.SQL_UPDATE` directly rather than handwritten partial SQL strings.
- **SpendingStats.kt Reconciliation:**
  - Audited git diff hunk in commit `050a25d`: Myers diff relocated `dayTotal` and `dailyTotals` from bottom of file when `fiveMonthSpendingSeries` was inserted. All S1 tests pass untouched.
  - Audited `monthlySeries` (S1 centered window `[-2..+2]` without refund deduction) vs `fiveMonthSpendingSeries` (P trailing 5-month window `[-4..0]` with net spend floored at 0). S1 tests and callers preserved without deletion pending UI integration.

## 10. UI Design Foundation, Typography & WCAG Contrast (Prompts D1, D1b)

- **Design Tokens & Theme Centralization (`Theme.kt`):**
  - Semantic color system defined in `Theme.kt` with Light and Dark palettes (`bg`, `sheet`, `ink`, `muted`, `line`, `brand`, `tint`, `onTint`, `button`, `buttonText`, `amberBg`, `amberFg`, `chip`, `hero`, `field`, `red`, `bar`, `track`).
  - Category color palettes for all 9 AutoFlow categories plus `Unknown`.
  - All UI colors strictly resolved through `AutoFlowTheme.colors` or `LocalAutoFlowColors`; verified by `verify.ps1` regex checks forbidding hardcoded hex colors outside `Theme.kt`.
- **WCAG 2.1 AA Contrast Ratios (Prompt D1b):**
  - Comprehensive contrast test suite in `ThemeContrastTest.kt` verifies 36 foreground/background color combinations across Light and Dark themes.
  - Adjusted Light category colors to satisfy WCAG 2.1 AA normal text contrast (>= 4.5:1) against 11% category tint tiles:
    - Food: `#C2410C` -> `#B83A0B` (contrast improves from 4.41:1 to 4.86:1).
    - Bills: `#A16207` -> `#8F5B05` (contrast improves from 4.24:1 to 4.88:1).
  - Zero exceptions permitted: all 36 pairs strictly pass >= 4.5:1.
  - Mutation proof verified: temporarily reverting Bills to `#A16207` causes `ThemeContrastTest` to fail with `Expected Bills on 11% tile in Light to pass >= 4.5, was 4.24`.
- **Typography Scale & Variable Font:**
  - Bundled single variable font `PlusJakartaSans-VariableFont_wght.ttf` as `res/font/plus_jakarta_sans.ttf` with accompanying `OFL.txt` license in root `NOTICE`.
  - Font family declared with `@OptIn(ExperimentalTextApi::class)` and explicit `FontVariation.Settings(FontVariation.weight(wght))` for weights 400, 500, 600, 700, and 800.
  - Material 3 `Typography` scale configured: `displayLarge` (36sp/700), `headlineLarge` (28sp/700), `titleLarge` (20sp/600), `titleMedium` (16sp/600), `bodyLarge` (15sp/400), `bodyMedium` (14sp/400), `labelLarge` (13sp/600), `labelMedium` (12sp/500), `labelSmall` (11sp/500).
  - Tabular figures (`FontFeatureSettings("tnum")`) applied across numeric display styles for aligned monetary values.
- **Reusable UI Building Blocks (`presentation/ui/components/`):**
  - 10 standalone Compose components with comprehensive `@Preview` definitions for light and dark themes:
    `AFTopBar`, `AFBottomNav`, `AFTransactionRow`, `AFCategoryTile`, `AFCategoryChip`, `AFBadge`, `AFButton`, `AFSegmented`, `AFSheet`, `AFField`.
    Components do not yet replace production screen layouts to ensure zero behavioral disruption.
  - 10 category vector drawables added to `res/drawable/` (`ic_cat_food`, `ic_cat_groceries`, `ic_cat_transport`, `ic_cat_shopping`, `ic_cat_bills`, `ic_cat_entertainment`, `ic_cat_health`, `ic_cat_travel`, `ic_cat_others`, `ic_cat_unknown`) with unified icon resolver `CategoryIcons.iconResFor(category)`.
- **Physical Device & Logcat Verification:**
  - Debug APK compiled (`assembleDebug`) and installed on test device `dfa4dd90` (`adb install -r`).
  - App launched into foreground (`am start -n com.autoflow.app/.MainActivity`); 30-second logcat monitored with zero application errors, zero font crashes, zero Room crashes, and zero SMS leakage.

## 11. Custom Categories: Add, Rename, Delete & Room v4 (Prompt C1)

- **Room Version 4 Migration & Schema Parity (`RoomMigration3To4Test.kt`):**
  - Added table `custom_categories` (`name_key TEXT PRIMARY KEY NOT NULL`, `name TEXT NOT NULL`, `icon_key TEXT NOT NULL`, `color_key TEXT NOT NULL`, `created_at INTEGER NOT NULL`).
  - Room migration `MIGRATION_3_4` registered in `AutoFlowDatabase` with SQL constant `MIGRATION_3_4_SQL`.
  - Schema `4.json` generated and committed. Verified on real SQLite via `RoomMigration3To4Test`: column types, nullability, and primary key parity against reference schema; existing transactions and merchant rules preserved intact; new table initialized empty.
- **Normalization & Validation Rules (`CategoryModels.kt`):**
  - Name normalization: trim, collapse repeated spaces, lowercase with `Locale.ROOT`.
  - Stored name: as typed with leading/trailing whitespace trimmed and inner spaces collapsed.
  - Length constraint: 1 to 20 characters after trimming (`NameBlank`, `NameTooLong`).
  - Character set constraint: letters, digits, spaces, `&`, `-`, `'` only (`NameInvalidCharacters`).
  - Built-in / Reserved collisions: rejected with `NameNotUnique` if normalized key matches any of `Food`, `Groceries`, `Transport`, `Shopping`, `Bills`, `Entertainment`, `Health`, `Travel`, `Others`, `Unknown`, `Refund`, `Cash`, `Not counted`.
  - Custom category collision: rejected with `NameNotUnique` if another custom category shares the same normalized key. Case/spacing-only rename of the same category is permitted.
  - Icons & Colors: icon must be one of `PAW`, `HOME`, `GIFT`, `BOOK`, `COFFEE`, `TOOL`, `LEAF`, `MUSIC`, `PHONE`, `WALLET`, `BAG`, `CALENDAR` (`InvalidIcon`); color must be one of `ORANGE`, `GREEN`, `BLUE`, `PINK`, `GOLD`, `PURPLE`, `TEAL`, `RED` (`InvalidColor`).
  - Capacity: hard limit of 30 custom categories (`TooManyCategories`).
  - Read-only built-ins: attempting to rename or delete a built-in category fails with `BuiltInCategoryReadOnly`.
  - Non-existent category operations return `CategoryNotFound`.
- **Registry & Atomic Operations (`CategoryRepository.kt`):**
  - `getAllCategories()`: Flow emitting all 9 built-ins in fixed order followed by custom categories ordered by `created_at` ASC.
  - `addCategory(name, iconKey, colorKey)`: Validates rules, performs atomic insert, maps primary-key race conditions to `NameNotUnique`.
  - `updateCategory(currentName, newName, iconKey, colorKey)`: Atomic single transaction updates `custom_categories`, all `transactions` (including soft-deleted), and all `merchant_categories`.
  - `countTransactionsInCategory(name)`: Counts non-deleted active transactions in the category.
  - `deleteCategory(name)`: Atomic single transaction reassigns all `transactions` (including soft-deleted) to `"Others"` with `categorySource = 'AUTO'`, deletes associated `merchant_categories`, and removes the `custom_categories` record.
  - Tested on real SQLite with real JDBC transaction rollback proofs and production mutation proofs.
- **Integration with Existing Logic:**
  - `TransactionRepository.updateTransaction` accepts custom categories and rejects non-existent categories with `UpdateTransactionResult.CategoryNotFound`.
  - `categoryBreakdown` and `fiveMonthSpendingSeries` dynamically group and aggregate any category name, proven by unit tests.
- **Live Device Migration & Database Verification:**
  - Debug APK installed on physical device `dfa4dd90` via `adb install -r`.
  - Pre-install (`..\backups\v3_pre_c1.db`) vs post-install (`..\backups\v4_after.db`) inspected via sqlite-jdbc:
    - `PRAGMA user_version`: 3 -> 4.
    - Transaction count before install: 5; after install: 5 (matched 100%).
    - Payment mode breakdown: 3 `CASH`, 2 `UNKNOWN` preserved unchanged.
    - `custom_categories` count: 0.
  - Clean 30-second error logcat with zero Room, SQLite, or application errors.

## 11. App Frame & Home Screen (Prompt UI-1)

- **Isolation & Setup:**
  - Executed strictly in worktree `..\autoflow-ui2` on branch `ui-home`.
  - Data, domain, schema, and `parser-core` layers kept 100% isolated (`git diff master --stat -- app/src/main/java/com/autoflow/app/data app/src/main/java/com/autoflow/app/domain app/schemas parser-core` is empty).
- **App Frame & Bottom Navigation:**
  - Replaced legacy UI with approved wireframe components.
  - Bottom bar contains Home tab on the left and a circular round `+` button in the center (opens existing `AddCashExpenseScreen`).
  - Omitted Review and Categories tabs from the bottom bar because those screens do not exist yet in the codebase.
  - Floating action button ("Add Cash") removed in favor of the center `+` button.
  - Top bar displays "AutoFlow"; Settings icon omitted because Settings screen is not built yet.
- **Home Content & Navigation:**
  - **Period Pill:** Dropdown selector for `Today`, `This week`, `This month` (default), or historical month (e.g. `Sep 2026`). Uses phone's timezone and reuses `periodRange(StatsPeriod, Instant, ZoneId)`. Picking a predefined period returns from a past month back to the current period.
  - **One Big Number:** Displays total counted period spend formatted via `CurrencyFormatter.formatPaiseToInr` (floored at 0L, never negative), count of counted transactions ("Across N transactions"), and `(i)` button opening an explanatory dialog stating "Refunds are not included yet."
  - **5-Month Spending Chart:** Centered on viewed month via `monthlySeries`. Renders proportional bars with accessibility content description (e.g. "Spending by month, August to December, October selected"). Future months are rendered in muted colors and are non-selectable.
  - **Grouped Transaction List:** Grouped by calendar day with day header and `dayTotal` (never negative). Uses `AFTransactionRow` with category icon tiles, merchant name, `Category · time` subtitle, amount (green `+` for refunds), and context-aware badges: `Check` (amber, for `NEEDS_REVIEW`), `Not counted` (muted, for `EXCLUDED`), `Refund` (tint, for `REFUND`), `Cash` (outline, for manual cash). Tapping opens `EditTransactionScreen`.
  - **Empty State:** Friendly empty state card with an "Add expense" button when no transactions exist for the selected period.
- **Indian Currency Formatter:**
  - Formats paise values with Indian digit grouping (`₹1,18,450.00`, `₹10,00,000.00`).
  - Unit tests verified for exact required test points: 0, 5, 130, 99999, 100000, 1000000 paise.
- **Pure JVM Unit Tests & Production Mutation Proof:**
  - `HomeScreenTest.kt` contains 8 pure JVM tests verifying period switching, past month selection, future month unselectable, return to current period, empty state, badge rules, day grouping, and day totals.
  - Production mutation proof: disabling future month rejection in `MainViewModel.selectMonth` caused `HomeScreenTest.testFutureMonthNonSelectable` to fail with `AssertionError`. Reverted cleanly to green.
- **Live Device Verification:**
  - Debug APK built (`assembleDebug`) and installed on connected device `dfa4dd90` via `adb install -r`.
  - 30-second error-filtered logcat (`*:E`) confirmed 0 app crashes, 0 Room crashes, 0 font crashes, and 0 SMS leakage.

## 12. System Bar Insets & Smooth Catmull-Rom Line Chart (Prompt UI-1b)

- **System Bar Edge-to-Edge Safe Insets (`AFTopBar.kt`, `AFBottomNav.kt`):**
  - Resolved status bar and gesture navigation bar overlaps in the app frame:
    - `AFTopBar`: Added default `windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)`. The `Surface` extends behind the status bar while inner content begins safely below clock and status icons.
    - `AFBottomNav`: Added default `windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)`. The `Surface` sheet background extends behind the phone's gesture line or 3-button navigation bar, while the 64dp navigation row and round center `+` button sit completely above the gesture bar.
    - Verified against existing screens (`AddCashExpenseScreen`, `EditTransactionScreen`) where `Scaffold` and `TopAppBar` safely manage top and bottom insets.
    - Bottom content padding on `LazyColumn` (`bottom = AppSpacing.xl`) guarantees that the last transaction row is fully visible and not concealed behind the bottom navigation bar.
- **Smooth Line Chart Engine (`SmoothChartGeometry.kt`, `AFSmoothLineChart.kt`):**
  - **Catmull-Rom to Cubic Bézier Spline:** Pure geometry calculation transforms 5 monthly points into smooth cubic Bézier segments with tension 0.5. If all points are 0 or equal, renders a flat horizontal line across the vertical midpoint.
  - **Visual Styling:** 2.75dp stroke with `StrokeCap.Round` in `colors.brand`. Soft vertical gradient under the line using `colors.bar.copy(alpha = 0.28f)` fading to 0.0f at canvas bottom.
  - **Selected Month Indicator:** Rendered as a prominent dot centered on the curve at the selected month (sheet-filled circle of radius 7dp surrounded by a 3dp `colors.brand` ring). Dot padding ensures high points are never clipped at canvas borders.
  - **Equal Month Columns & Touch Targets:** 5 columns of equal width spanning edge-to-edge. Each label column has a touch target >= 48dp. Tapping selects the month. Selected month displays in bold `colors.ink`; non-selected in `colors.muted`.
  - **Future Month Handling:** Future months (e.g. Nov and Dec when current month is Oct) are muted, non-tappable, and the line cleanly terminates at the current month rather than plunging to zero.
  - **Edge-to-Edge Placement:** Removed the enclosing Card/border around the chart; `AFSmoothLineChart` sits edge-to-edge directly on `colors.bg`.
  - **Accessibility:** Preserves semantic accessibility content descriptions.
- **Pure JVM Unit Tests & Production Mutation Proof (`SmoothChartGeometryTest.kt`):**
  - 5 pure JVM tests verify:
    1. Points land in order and within canvas bounds.
    2. Flat data yields a centered horizontal flat line.
    3. Spline line stops cleanly before future months.
    4. Highest month does not clip (respects dot and stroke padding).
    5. Selected month dot coordinates match the data point.
  - Production mutation proof: mutator removing future point filtering in segment generation caused `SmoothChartGeometryTest.testLineStopsBeforeFutureMonths` to fail with `AssertionError: Line should stop before future months`. Reverted cleanly to green.
- **Data Isolation Proof:**
  - `git diff master --stat -- app/src/main/java/com/autoflow/app/data app/src/main/java/com/autoflow/app/domain app/schemas parser-core` verified completely empty.
- **Physical Device & Visual Screenshot Verification:**
  - Installed debug APK (`app-debug.apk`) on connected physical device `dfa4dd90` (`adb install -r`).
  - Switched UI modes and captured screenshots via `cmd /c` to prevent PowerShell redirection corruption: `home_dark.png` and `home_light.png`.
  - Visual inspection via `view_file` confirmed clean separation from the status bar, bottom bar and round `+` button elevated cleanly above the gesture navigation bar, and high-fidelity smooth Catmull-Rom spline curves.
  - 30-second error logcat confirmed zero application crashes, zero Room crashes, and zero SMS leaks.

## 13. Add Expense and Edit Transaction Screens Rebuild (Prompt UI-2)

- **Screen Architecture & AF Component Migration (`AddCashExpenseScreen.kt`, `EditTransactionScreen.kt`):**
  - Rebuilt both screens using Theme.kt tokens and reusable AF building blocks: `AFTopBar`, `AFField`, `AFCategoryChip`, `AFSegmented`, and `AFButton`.
  - Configured as pushed sub-screens: dedicated back navigation icon, bottom navigation bar omitted, scrollable content (`rememberScrollState`), and `Modifier.imePadding()` ensuring soft keyboards never conceal action buttons.
  - Adheres to edge-to-edge system bar safe insets, guaranteeing seamless rendering across both gesture navigation and 3-button navigation layouts.
- **Add Expense Screen Specification:**
  - Amount input: numeric keypad with ₹ prefix, supporting whole rupees or up to 2 decimal places (values up to ₹10,00,00,000 / 10 crore).
  - Validation: Save button disabled with helper text `"Save turns on once the amount is valid"` whenever amount is empty, zero, or malformed.
  - Merchant / note field: optional entry, defaulting to `"Cash Expense"` if left blank.
  - Category selector: horizontal wrapping chip flow dynamically loaded from `CategoryRepository` registry, defaulting to `"Food"`.
  - Payment mode selector: `AFSegmented` control offering `Cash` (default), `Credit`, `Bank`, and `UPI`.
  - Date & time picker: displays `"Now · <Day, d Mmm>"` by default. Tapping launches sequential Android `DatePickerDialog` (with maxDate set to current timestamp) and `TimePickerDialog`. Future timestamps strictly rejected with error `"Future date and time not allowed"`.
  - Successfully creates manual transactions and routes back to Home.
- **Edit Transaction Screen Specification:**
  - Top bar: back navigation arrow and trash can icon launching a delete confirmation dialog (`"Delete this transaction?"`, `Cancel`/`Delete`).
  - Origin header card:
    - **SMS transaction:** Large fixed amount display with `"From bank SMS"` badge, lock icon, and note `"Amount is fixed"`.
    - **Cash entry:** Large amount display with `"Cash entry"` badge and an editable amount input field.
  - Merchant field: editable text field. Editing category updates merchant category association for future SMS transactions.
  - Date & time row: editable via sequential date and time pickers; future timestamps rejected with error.
  - Category chips: dynamic list populated from category registry with current transaction category pre-selected.
  - Payment mode: `AFSegmented` with `Cash`, `Credit`, `Bank`, and `UPI`. Historical transactions with `UNKNOWN` mode display with no option selected and remain `UNKNOWN` unless explicitly tapped.
  - Note field: optional user note field capped at 200 characters with live counter (`0/200`); raw bank SMS message text is never exposed.
  - Action buttons:
    - `"Save changes"`: primary button, enabled only when the form has valid edits and is dirty.
    - `"Not an expense"` / `"Restore"`: secondary outlined button toggling transaction exclusion (`isExcludedFromBudget`) without deleting the record.
  - Unsaved changes safeguard: pressing back when the form has uncommitted modifications opens a discard confirmation dialog (`"Discard changes?"`, `Keep editing`/`Discard`).
- **Form Validation & Pure JVM Domain Logic (`FormLogic.kt`, `FormLogicTest.kt`):**
  - Encapsulated presentation state and validation inside `FormLogic.kt`:
    - `ExpenseFormLogic`: amount parsing, decimal validation, scale validation, and future timestamp checks.
    - `AddExpenseFormState`: handles add form state, default merchant fallback, and Save button enablement.
    - `EditTransactionFormState`: tracks dirty states across all 6 fields, handles SMS lock vs manual editable amount, handles restore vs exclude labeling, and manages discard dialog conditions.
  - 10 pure JVM unit tests in `FormLogicTest.kt` covering all validation branches, invalid decimal strings, future dates, dirty state tracking, and UNKNOWN mode handling. Red test phase demonstrated prior to implementation.
  - Production mutation proof: removing the future timestamp check caused `FormLogicTest.testAddExpenseFutureDateRejected` to fail with `AssertionError`. Reverted cleanly.
- **Real SQLite Payment Mode Persistence Test (`ManualTransactionPaymentModeRealSqliteTest.kt`):**
  - Verified manual transaction insertion with all payment modes (`CASH`, `CREDIT`, `BANK`, `UPI`) and round-trip retrieval against real SQLite upgraded to Room v4 schema.
- **Data & Schema Isolation Proof:**
  - `git diff master --stat -- app/src/main/java/com/autoflow/app/data app/src/main/java/com/autoflow/app/domain app/schemas parser-core` verified completely empty.
- **Physical Device & Visual Verification (Device `dfa4dd90`):**
  - Assembled debug APK and installed on physical device (`adb install -r`).
  - Monitored 30-second error logcat (`*:E`) showing zero application errors or Room crashes.
  - Captured 8 screen images into `screens/` via `cmd /c` (dark and light modes):
    - `add_empty_dark.png`, `add_empty_light.png`
    - `add_datepicker_dark.png`, `add_datepicker_light.png`
    - `edit_sms_dark.png`, `edit_sms_light.png`
    - `edit_manual_dark.png`, `edit_manual_light.png`
  - Verified visually via `view_file` for accurate layout, colors, typography, touch padding, and contrast.

## 14. Review Tab ("Needs a check" Screen) Implementation (Prompt UI-3)

- **Review Screen Architecture (`ReviewScreen.kt`):**
  - Built using Theme.kt tokens and AF components (`AFTopBar`, `AFCategoryChip`, `AFButton`).
  - Top bar displaying `Needs a check (N)`.
  - Grouped chronologically by day (`Today`, `Yesterday`, and formatted dates), exactly matching Home screen day header styling.
  - Each pending item card displays:
    - Merchant name, formatted time, and formatted amount in ₹ (integer paise).
    - Top 3 likely category suggestions derived from `CategoryEngine.resolveCategory(merchant)` and built-in frequency fallbacks.
    - Quick confirmation: tapping a category chip calls `TransactionRepository.updateTransaction`, updates the category, sets `reviewStatus = CONFIRMED`, and immediately removes the item from the pending list.
    - `"Later"` button: defers review action and keeps the item pending.
    - Card body tap navigation: tapping anywhere on the card header/body navigates directly into the existing `EditTransactionScreen` with full edit capabilities; saving or returning routes back cleanly to Review.
  - Empty state (`ReviewEmptyState`):
    - Displayed when all pending items are resolved: green checkmark icon in circular tint container, bold heading `"All caught up"`, and subtitle `"Nothing needs your attention"`.
- **Live Synchronized Review Count & Home Banner:**
  - `AFBottomNav.kt`: added amber count badge support to navigation items; displays active unreviewed count on the Review tab icon.
  - `HomeScreen.kt`: amber review banner (`ReviewCheckBanner`) shown below the period selector when `unreviewedCount > 0`, displaying `"N need a check"` with an arrow. Tapping navigates directly to the Review tab. Banner cleanly hides when count is 0.
  - Pending-review expenses remain strictly counted in Home totals and charts (integer paise arithmetic intact).
- **Comprehensive Unit Testing (`ReviewScreenTest.kt`):**
  - 4 test cases verifying all business rules:
    - `confirmCategoryClearsReviewStatusAndDecrementsCount`: confirms category selection marks status as CONFIRMED and decrements count.
    - `onLaterPreservesReviewStatusAndCount`: confirms tapping Later preserves pending state and count.
    - `homeBannerVisibilityTogglesWithUnreviewedCount`: confirms banner is visible when count > 0 and hidden when count == 0.
    - `unreviewedExpensesRemainCountedInTotals`: confirms unreviewed items are counted in total spend alongside confirmed items.
  - Demonstrated RED test phase initially against unimplemented stubs, followed by GREEN test phase after wiring.
- **Isolation & Integrity:**
  - `git diff master --stat -- app/src/main/java/com/autoflow/app/data app/src/main/java/com/autoflow/app/domain app/schemas parser-core` verified 100% empty.
  - Production database backed up to `backups/v4_pre_ui3.db` (94,208 bytes, % 4096 == 0) and restored to original state post-verification.
  - Zero hardcoded colors outside `Theme.kt`.
- **Physical Device & Visual Verification (Device `dfa4dd90`):**
  - Installed debug APK and verified live behavior on physical device in both Dark and Light modes.
  - Captured artifacts:
    - `home_review_banner.png`: Home screen with amber "2 need a check" banner and Review tab badge count 2.
    - `review_items_dark.png`: Review screen displaying pending items in Dark theme.
## 15. Categories Tab & Spend Ring Chart (Prompt UI-4)

- **Screen Architecture & AF Component Migration (`CategoriesScreen.kt`, `AFRingChart.kt`, `AFPeriodPill.kt`, `CategoriesUiState.kt`):**
  - Added "Categories" tab to `AFBottomNav` across Home, Review, and Categories screens.
  - Built using Theme.kt tokens and AF components (`AFTopBar`, `AFBottomNav`, `AFTransactionRow`).
  - **Shared Period Pill (`AFPeriodPill.kt`):**
    - Extracted period selector from `HomeScreen.kt` into reusable component shared between Home and Categories tabs.
    - Supports `This month` (default), `Today`, `This week`, and past 5 calendar months in dropdown menu.
    - Future months strictly excluded/non-selectable.
  - **Ring Chart Hero (`AFRingChart.kt`):**
    - Dynamic donut/ring chart with arc slices mapped proportionally to category spend.
    - Category colors mapped via `AutoFlowTheme.colors.forCategory(category)`.
    - Inner center summary displays top category name, formatted spend (integer paise in INR), and percentage of total spend.
  - **Category Breakdown List:**
    - Sorted descending by net spend.
    - Each row displays category icon tile, category name, formatted net spend in ₹, and percentage of total spend.
    - Tapping a row opens `CategoryDetailScreen` listing that category's transactions for the selected period.
  - **Category Detail Drilldown (`CategoryDetailScreen`):**
    - Top bar: back arrow, category icon, and category name title.
    - Transaction rows rendered via `AFTransactionRow` (title, subtitle, amount, category icon tile; NO payment-mode badge).
    - Tapping a transaction opens `EditTransactionScreen`; saving or back returns cleanly to the category detail list, and top bar back returns to the Categories tab.
  - **Empty State:**
    - When selected period has zero spend, ring chart is hidden and friendly empty state card is displayed ("No spending in this period").
  - **Custom Categories Fallback:**
    - Custom categories appear with their spend; neutral fallback used for icon (`R.drawable.ic_cat_unknown`) and color (`AutoFlowTheme.colors.catUnknown`).
- **Refunds as Adjustments Rule:**
  - Refunds are adjustments that reduce the specific category's spend:
    `netPaise = (expensePaise - refundPaise).coerceAtLeast(0L)`.
  - Categories with zero or net negative spend are excluded from the ring chart and list.
- **Pure JVM Unit Testing (`CategoriesUiTest.kt`):**
  - 5 comprehensive tests:
    1. `testCategoryTotalsAndPercentageRounding`: verifies exact paise arithmetic, descending sort, and percentage calculations.
    2. `testRefundsReduceCategorySpend`: verifies refunds adjust and reduce the specific category's spend.
    3. `testPeriodTransitionUpdatesState`: verifies changing period updates category breakdown and totals.
    4. `testEmptyPeriodYieldsEmptyCategories`: verifies empty state when no expenses exist in the period.
    5. `testCustomCategoryFallbackHandling`: verifies custom categories with non-standard names map to fallback tokens and aggregate correctly.
  - RED test phase demonstrated prior to implementation (`Unresolved reference 'CategoriesUiState'`), followed by GREEN test phase after implementing `CategoriesUiState.kt`.
- **Data & Schema Isolation Proof:**
  - `git diff master --stat -- app/src/main/java/com/autoflow/app/data app/src/main/java/com/autoflow/app/domain app/schemas parser-core` verified 100% empty.
  - Data queried via existing `TransactionRepository.getAllActiveTransactions()` Flow; zero DB schema or query changes.
  - Production database backed up to `..\backups\v4_pre_ui4.db` (94,208 bytes, % 4096 == 0).
  - Zero hardcoded colors outside `Theme.kt`.
- **Physical Device & Visual Verification (Device `dfa4dd90`):**
  - Installed debug APK and verified live behavior on physical device in both Dark and Light modes.
  - Verified navigation from Categories tab -> Category Detail list -> Edit transaction -> back to Detail -> back to Categories.
  - Captured artifacts:
    - `categories_dark.png`: Categories tab with ring chart and breakdown in Dark mode.
    - `categories_light.png`: Categories tab with ring chart and breakdown in Light mode.
    - `category_detail_list.png`: Groceries detail transaction list without payment-mode badges.
    - `categories_empty.png`: Friendly empty state when selecting "Sep 2026" period.

## 16. Settings & Custom Categories UI (Prompt UI-5)

- **Architecture & Screen Composition (`SettingsScreen.kt`, `MyCategoriesScreen.kt`, `AddEditCategoryScreen.kt`):**
  - **Settings Entrypoint:** Added settings gear icon on the top-left of Home screen's `AFTopBar`, routing to `SettingsScreen`.
  - **Appearance Section:** Three-way segmented switch (System, Light, Dark) implemented with `AFSegmented`. Persisted synchronously across app launches via `SharedPreferencesThemePreferences` (stored in `"autoflow_settings"`, key `"app_theme_mode"`). Observed reactively by `MainViewModel.themeMode` and applied at `AutoFlowTheme` in `MainActivity`.
  - **Privacy Section:** SMS access On/Off state reflecting `RECEIVE_SMS` permission. Off state displays friendly explanation and button opening application details in system settings.
  - **Categories Section:** Row navigation to "My categories" screen.
  - **My Categories Screen (`MyCategoriesScreen.kt`):**
    - Usage banner: "N of 30 used".
    - Custom category rows: Displays custom icon tile, category name, transaction count ("N transactions"), and tap chevron.
    - Empty state: Friendly card shown when 0 custom categories exist ("No custom categories yet").
    - Action: "Add category" button at bottom.
  - **Add / Edit Category Screen (`AddEditCategoryScreen.kt`):**
    - Live Preview Card: Displays live tile with chosen icon and color, category name, and key labels.
    - Category Name Field: Max 20 characters, validated against allowed character set (`[a-zA-Z0-9 &'-]`), reserved names, and duplicates. Repository validation errors mapped to plain words ("A category with this name already exists", "Category name is required", etc.).
    - 12 Icon Grid: Vector drawables for `PAW`, `HOME`, `GIFT`, `BOOK`, `COFFEE`, `TOOL`, `LEAF`, `MUSIC`, `PHONE`, `WALLET`, `BAG`, `CALENDAR`.
    - 8 Color Grid: Palette chips for `ORANGE`, `GREEN`, `BLUE`, `PINK`, `GOLD`, `PURPLE`, `TEAL`, `RED`.
    - Save Button: Validates and calls `MainViewModel.addCategory` or `updateCategory`.
    - Delete Action: In edit mode, top bar displays trash icon opening confirmation dialog ("Its transactions will move to Others and its merchant rules will be removed"). Deletion calls `CategoryRepository.deleteCategory`, atomically reassigning transactions and clearing rules.
- **Dynamic Category Theme Mapping (`CategoryThemeRegistry.kt`):**
  - Reactive registry updated automatically whenever `MainViewModel.customCategories` updates.
  - In `AFCategoryIcons.kt`, `forCategory(name)` checks `CategoryThemeRegistry` for custom icon before falling back to `R.drawable.ic_cat_unknown`.
  - In `Theme.kt`, `forCategory(name)` checks `CategoryThemeRegistry` for custom color palette before falling back to `catUnknown`.
  - Replaces neutral fallback across Categories tab, Home transaction rows, Review chips, and Edit screen with the custom category's chosen icon and color.
- **Color Palette & Accessibility Proof (`ThemeContrastTest.kt`):**
  - Added all 8 custom category color keys to `Theme.kt` with Light and Dark variants.
  - All 8 colors verified in `ThemeContrastTest` against 11% tile background on sheet background.
  - Test expanded from 36 pairs to 52 pairs; 100% pass WCAG AA >= 4.5:1 (ratios between 4.88:1 and 12.87:1).
- **Unit Testing (`SettingsUiTest.kt`):**
  - Verified appearance persistence via `InMemoryThemePreferences`.
  - Verified plain words validation messages for all `CategoryOperationResult` error cases.
  - Verified dynamic color and icon resolution via `CategoryThemeRegistry`.
  - Verified Add, Update, and atomic Delete flows using `FakeCustomCategoryDao` and `FakeTransactionDao`.
- **Data & Schema Isolation Proof:**
  - `git diff master --stat -- app/src/main/java/com/autoflow/app/data app/src/main/java/com/autoflow/app/domain app/schemas parser-core` verified 100% empty.
  - Zero hardcoded colors outside `Theme.kt`.
- **Physical Device & Visual Verification (Device `dfa4dd90`):**
  - Phone database backed up to `..\backups\v4_pre_ui5.db` (94,208 bytes, % 4096 == 0) prior to test data creation.
  - Captured artifacts:
    - `home_settings_icon.png`: Settings gear icon on top-left of Home screen.
    - `settings_screen.png`: Settings screen in Light theme.
    - `settings_dark_theme.png`: Settings screen in Dark theme switched via segmented control.
    - `my_categories_empty.png`: My Categories empty state ("0 of 30 used").
    - `add_category_screen.png`: Add category screen with live preview and pickers.
    - `add_category_filled.png`: Add category screen with "Gaming", GIFT icon, and PURPLE color selected.
    - `my_categories_list.png`: My Categories list with "Gaming" and "Pet Care" ("2 of 30 used").
    - `edit_category_screen.png`: Edit category screen for "Gaming".
    - `delete_category_dialog.png`: Confirmation dialog for deleting "Gaming".
    - `my_categories_after_delete.png`: My Categories list after deleting "Gaming", showing "Pet Care" ("1 of 30 used").
    - `categories_custom_category.png`: Categories tab ring chart and breakdown list displaying "Pet Care" with ORANGE ring slice/tile and PAW icon.
  - Phone database restored from `v4_pre_ui5.db` and verified (94,208 bytes, % 4096 == 0).

## 17. First Run & Permission Help UI (Prompt UI-6)

- **Architecture & Screen Composition (`FirstRunScreen.kt`, `PermissionHelpScreen.kt`):**
  - **First Run Screen (`FirstRunScreen.kt`):**
    - Shown once on first launch only; checks `isFirstRunCompleted` from `FirstRunPreferences`.
    - Hero: Tinted rounded square with Shield vector icon (`ic_shield_private.xml`).
    - Title: "Your spending, tracked privately".
    - Subtitle: "AutoFlow reads bank SMS on your phone only. Nothing leaves your device. Messages are not saved."
    - 3 Key Value Props:
      - "Stays on your phone — No account, no upload" (`ic_point_device.xml`).
      - "Messages are not saved — Only the amount, merchant and time are kept" (`ic_point_no_save.xml`).
      - "Cash works without SMS — Add cash spending any time" (`ic_point_cash.xml`).
    - Primary Button: "Allow SMS access" launches real `RECEIVE_SMS` runtime permission prompt (`ActivityResultContracts.RequestPermission`).
      - Granted -> sets first run completed, navigates to Home.
      - Denied -> sets first run completed, navigates to Home without nagging.
      - Blocked/Restricted (sideloaded app restrictions via `AppOpsManager.unsafeCheckOpNoThrow("android:access_restricted_settings", ...)`) -> routes to Permission Help.
    - Secondary Action: "Not now, I will add cash only" text button immediately marks first run completed and routes to Home without prompting.
  - **Permission Help Screen (`PermissionHelpScreen.kt`):**
    - Header: `AFTopBar` with title "Permission help" and back navigation arrow.
    - Intro: "Android blocks SMS access for apps installed outside the Play Store. It takes three taps to allow it."
    - 3 Numbered Step Cards:
      1. "Open AutoFlow's app settings with the button below"
      2. "Tap the menu at the top right, then choose Allow restricted settings"
      3. "Come back and tap Allow SMS access"
    - Primary Button: "Open app settings" launches `ACTION_APPLICATION_DETAILS_SETTINGS` targeting AutoFlow's package.
    - Secondary Button: "Allow SMS access" launches runtime permission request.
    - Auto-Detection on Resume: Observes lifecycle `ON_RESUME` after `ON_PAUSE` (when user comes back from App Settings or system prompt) to automatically detect permission grant and proceed to Home (or back to Settings if opened from Settings > Privacy).
- **First-Run State & Preferences (`FirstRunPreferences.kt`):**
  - Stored in SharedPreferences (`"autoflow_settings"`, key `"has_completed_first_run"`).
  - Observed reactively in `MainViewModel` via `isFirstRunCompleted: StateFlow<Boolean>`.
  - Never shown again on subsequent launches once completed.
  - Provided clean debug-only reset mechanism in `SettingsScreen` and debug intent (`--ez reset_first_run true`), verified to be gated by `ApplicationInfo.FLAG_DEBUGGABLE` so it cannot trigger in release builds.
- **Unit Testing (`FirstRunTest.kt`):**
  - Verified initial flag state, persistence after "Not now", persistence after permission grant/denial.
  - Verified routing for `PermissionPromptResult.Granted`, `Denied`, and `BlockedOrRestricted`.
- **Data & Schema Isolation Proof:**
  - `git diff master --stat -- app/src/main/java/com/autoflow/app/data app/src/main/java/com/autoflow/app/domain app/schemas parser-core` verified 100% empty.
  - `AndroidManifest.xml` verified: `RECEIVE_SMS` only, zero `READ_SMS`.
- **Physical Device & Visual Verification (Device `dfa4dd90`):**
  - Phone database backed up to `..\backups\v4_pre_ui6.db` (94,208 bytes, % 4096 == 0) prior to test execution.
  - Captured artifacts:
    - `first_run_screen.png`: First Run screen with shield hero, title, 3 value prop points, and action buttons.
    - `screen_after_allow.png` / `screen_relaunch_home.png`: Seamless transition to Home screen and persistence on subsequent launches.
    - `settings_privacy_off.png`: Settings screen Privacy section showing SMS access "Off" badge in amber with right chevron.
    - `permission_help_screen.png`: Permission Help screen with 3 numbered cards, "Open app settings" and "Allow SMS access" buttons.
    - Database integrity verified after test runs (94,208 bytes, % 4096 == 0).

## 18. Goals Data Layer (Room Version 5) — Prompt G1 (October 25, 2026)
- **Worktree & Scope:**
  - Work executed strictly within worktree `..\autoflow-g1` on branch `goals-data` branched from `ui-firstrun`.
  - Database + repository + tests only. Zero UI screens, zero navigation modifications, zero phone/adb commands (JVM-only).
  - Existing tables (`transactions`, `refund_matches`, `merchant_categories`, `custom_categories`) completely untouched.
  - `parser-core` completely untouched.
- **Room Version 5 Schema (`schemas/5.json`):**
  - Room version bumped from 4 to 5 (`version = 5`, `exportSchema = true`).
  - `goals` table:
    - `id` (INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)
    - `name` (TEXT NOT NULL, 1-30 chars trimmed)
    - `name_key` (TEXT NOT NULL, normalized lowercase)
    - `kind` (TEXT NOT NULL, 'SAVE' or 'AVOID')
    - `target_paise` (INTEGER NOT NULL, > 0)
    - `created_at` (INTEGER NOT NULL)
    - `updated_at` (INTEGER NOT NULL)
    - Unique index `index_goals_name_key` on (`name_key`).
  - `goal_entries` table:
    - `id` (INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)
    - `goal_id` (INTEGER NOT NULL, FK -> `goals(id)` ON DELETE CASCADE)
    - `type` (TEXT NOT NULL: 'SET_ASIDE', 'AVOIDED', 'USED')
    - `amount_paise` (INTEGER NOT NULL, > 0)
    - `note` (TEXT NULLABLE)
    - `occurred_at` (INTEGER NOT NULL)
    - `linked_transaction_id` (INTEGER NULLABLE, plain column)
    - `is_deleted` (INTEGER NOT NULL DEFAULT 0)
    - `created_at` (INTEGER NOT NULL)
    - `updated_at` (INTEGER NOT NULL)
    - Indices: `index_goal_entries_goal_id` on (`goal_id`), `index_goal_entries_linked_transaction_id` on (`linked_transaction_id`).
- **Database Migration (`MIGRATION_4_5`):**
  - Explicit `Migration(4, 5)` executing `MIGRATION_4_5_SQL`: drops draft legacy `goal_contributions` and `goals` tables (verified to contain 0 rows in production backup `v4_pre_ui6.db`), and creates v5 `goals` and `goal_entries` tables and indices.
  - No fallback to destructive migration.
  - Verified on real SQLite with real backup copy (`RoomMigration4To5Test.kt`): all 5 existing transactions remain 100% byte-for-byte identical; table PRAGMA columns match `5.json`.
- **Goals Repository (`GoalRepository` / `DefaultGoalRepository`):**
  - Fully implements domain rules and validation:
    - Available balance on SAVE goal = `sum(SET_ASIDE) - sum(USED)`. Balance never drops below zero. Every write that would cause negative balance is rejected with `GoalError.InsufficientBalance`.
    - Three ways to use money: `useLinkExpense` (amount capped by available and remaining linkable expense amount; multiple links supported), `useNewExpense` (atomic all-or-nothing execution via `runInTransaction`), `useTakeOut` (no linked expense).
    - Entry editing: linked expense is locked; amount bounded by expense limit and available balance; moving across goals validates both source and destination goals.
    - Soft delete (`is_deleted = 1`) and undelete (`is_deleted = 0`): deleting SET_ASIDE or restoring USED validated against negative balance.
    - Remove used ("Give money back"): soft-deletes USED entry and leaves linked expense untouched.
    - Exposes `isLinkedExpenseRemoved = true` when linked transaction is soft-deleted.
    - Integer percentage calculation (0-100%).
    - Complete separation: Goal operations never alter `transactions` spending totals or category series.
- **Verification & Mutation Proofs:**
  - 292 unit tests passing (up from 264 baseline, +28 tests).
  - 3 mutation proofs executed against production code (`DefaultGoalRepository.kt`), caught with RED, reverted to empty git diff:
    1. Bypassed available balance check in `useTakeOut` -> caught by `availableBalance_cannotDropBelowZero`.
    2. Bypassed remaining linkable expense cap in `useLinkExpense` -> caught by `useLinkExpense_multipleLinksOnSameExpense_upToExpenseAmount`.
    3. Leaked avoided spending into `savedPaise` in `observeGoals` -> caught by `addAvoided_onSaveGoal_doesNotAffectSavedOrAvailable`.

## 19. Goals Data Layer, "Saved" Motivation Jar Version (Room Version 6) — Task G1c (October 25, 2026)

- **Worktree & Scope:**
  - Work executed strictly within worktree `..\autoflow-g1c` on branch `goals-simple` branched from `master` (`54c7c0d`).
  - Data layer, Room database, DAO, repository, and JVM SQLite tests only. No UI, no phone, zero ADB commands.
  - Spending queries, transaction records, and `parser-core` completely untouched.
  - Zero raw SMS stored/logged. No AI/ML. No income, budget, balance or email.

- **Motivation Jar Conceptual Model:**
  - Goals represent a motivation jar rather than real money.
  - A goal has a name, target, and optional deadline.
  - Users record entries ("how they saved" + amount). The jar total is strictly `sum(non-deleted entries)`.
  - Replaces earlier set-aside/avoided/used design; all old concepts, linking to expenses, `kind`, and `type` are completely removed.

- **Room Version 6 Schema (`schemas/6.json`):**
  - Room version bumped from 5 to 6 (`version = 6`, `exportSchema = true`).
  - `goals` table:
    - `id` (INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)
    - `name` (TEXT NOT NULL, 1-30 chars trimmed)
    - `name_key` (TEXT NOT NULL, UNIQUE index `index_goals_name_key`)
    - `target_paise` (INTEGER NOT NULL, > 0)
    - `deadline_at` (INTEGER, nullable)
    - `is_deleted` (INTEGER NOT NULL DEFAULT 0)
    - `created_at` (INTEGER NOT NULL)
    - `updated_at` (INTEGER NOT NULL)
    - (`kind` column removed)
  - `goal_entries` table:
    - `id` (INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)
    - `goal_id` (INTEGER NOT NULL, FK -> `goals(id)` ON DELETE CASCADE)
    - `amount_paise` (INTEGER NOT NULL, > 0)
    - `note` (TEXT NOT NULL, required, never blank)
    - `occurred_at` (INTEGER NOT NULL)
    - `is_deleted` (INTEGER NOT NULL DEFAULT 0)
    - `created_at` (INTEGER NOT NULL)
    - `updated_at` (INTEGER NOT NULL)
    - Index: `index_goal_entries_goal_id` on (`goal_id`)
    - (`type` and `linked_transaction_id` columns and indexes removed)

- **Explicit Database Migration (`MIGRATION_5_6`):**
  - Rebuilds both tables non-destructively:
    1. Creates `goals_new`
    2. Copies goals (`id, name, name_key, target_paise, NULL, 0, created_at, updated_at`)
    3. Creates `goal_entries_new` referencing `goals_new(id)` with `ON DELETE CASCADE`
    4. Copies entries where `type IN ('SET_ASIDE', 'AVOIDED')`, converting blank/null notes to `'Saved'`. Drops old `USED` entries.
    5. Drops old `goal_entries` and `goals`.
    6. Renames `goals_new` -> `goals` and `goal_entries_new` -> `goal_entries`.
    7. Recreates indices `index_goals_name_key` and `index_goal_entries_goal_id`.
  - Foreign key and index definitions match `6.json` schema exactly.
  - Verified on real SQLite with `RoomMigration5To6Test.kt`.

- **Repository (`GoalRepository` / `DefaultGoalRepository`):**
  - All operations return typed `GoalResult` (no exceptions for expected failures):
    - `createGoal(name, targetPaise, deadlineAt?, now)`: validates name 1..30 chars, case/space-collapsed uniqueness (`name_key`), target > 0, deadline not before today.
    - `updateGoal(id, name, targetPaise, deadlineAt?, now)`: validates name, uniqueness among other goals, target > 0, unchanged old deadline allowed, new/changed past deadline rejected, rejected on deleted goals.
    - `deleteGoal(id, now)`: soft delete (`is_deleted = 1`), updates `name_key` to unique tombstone (`deleted_${id}_${UUID.randomUUID()}`), name immediately reusable, entries kept.
    - `undeleteGoal(id, now)`: restores original `name_key`. If taken meanwhile by another active goal, returns typed `GoalError.NameTaken(name)` and changes nothing.
    - `addSaved(goalId, amountPaise, note, occurredAt, now)`: validates positive amount, non-blank note (<= 100 chars), rejected on deleted goal (`GoalDeleted`).
    - `editEntry(id, amountPaise, note, occurredAt, now)`: validates amount, non-blank note, returns `EntryNotFound` if missing.
    - `deleteEntry(id, now)` / `undeleteEntry(id, now)`: soft delete/restore (`is_deleted` 1 / 0).
    - `observeGoalsWithTotals()`: emits Flow of `GoalWithTotals` where `totalPaise = sum(non-deleted entries)` and `reached = totalPaise >= targetPaise`. Deleted goals never appear.
    - `observeEntries(goalId)`: emits non-deleted entries, newest first (`occurred_at DESC, id DESC`).
  - Separation: Goal operations never alter `transactions` spending totals or records.

- **Verification & Mutation Proofs:**
  - 3 mutation proofs executed against production code:
    1. Break total calculation in `observeGoalsWithTotals` -> caught RED by `observeGoalsWithTotals_sumsNonDeletedEntries_andCalculatesReached`, restored GREEN.
    2. Break immediate name reuse in `deleteGoal` -> caught RED by `deleteGoal_softDeletes_andAllowsImmediateNameReuse`, restored GREEN.
    3. Break migration copy in `MIGRATION_5_6_SQL` (dropping SET_ASIDE) -> caught RED by `testMigration5To6_dataPreservationAndRebuild`, restored GREEN.

## 20. Goals Screens, "Saved" Motivation Jar UI — Task G2b (October 25, 2026)

- **Worktree & Scope:**
  - Branch `goals-ui2` in worktree `..\autoflow-g2b` based on `goals-simple` commit `5a561be`.
  - UI, ViewModel, navigation, state formatting, and unit tests only.
  - Zero changes to data, domain, Room database schema (v6 intact), or `parser-core`.
  - Strict isolation: `AddCashExpenseScreen.kt` and `EditTransactionScreen.kt` do not touch or mention goals (proven via `git grep -i "goal"` with 0 matches).
  - Integer paise everywhere, Indian number formatting via `CurrencyFormatter`, centralized design tokens (`Theme.kt`), zero hardcoded colors.

- **Screens Implemented & Verified:**
  1. **Goals Tab (`GoalsTabScreen.kt`):**
     - Bottom navigation updated to 5 slots: Home, Goals, [+], Review, Categories (+ in center). Safe insets honored.
     - Top bar "Goals" with tinted pill button "Add goal".
     - Empty state when no active goals: tinted jar/wallet icon in rounded square, friendly headline, explanation, and "Add a goal" button.
     - Goals list: sorted by creation order, displaying goal name, deadline indicator (No deadline, D days left, Deadline passed in red, or Reached in green), target progress bar, and amounts (`₹X.XX of ₹Y,YYY · Z%`).
     - Reached badge and full progress bar when total >= target.
     - Tap card navigates to Goal Detail screen.
     - Snackbar with "Undo" action on goal deletion.
  2. **New Goal Screen (`NewGoalScreen.kt`):**
     - Top bar with cancel (X) and screen title "New goal".
     - Name field (1..30 chars, required) with inline validation for uniqueness.
     - Target amount display (`₹0` initial) with custom 48dp on-screen number pad (`AFNumberPad.kt`).
     - Optional deadline selector opening Android date picker. Same day or past date selection shows error.
     - Explanatory copy: "A goal is a jar for motivation. It does not move real money."
     - Save button disabled until name and target > 0 are entered.
  3. **Goal Detail Screen (`GoalDetailScreen.kt`):**
     - Top bar with back arrow, goal name, and 3-dot overflow menu (Edit goal, Delete goal).
     - Hero card: "Saved so far", formatted amount, target, percent, and deadline/reached status.
     - Progress bar (green fill; when reached, shows green banner "✓ You reached your target").
     - Prominent green primary button "Saved" to log a savings entry.
     - History section: list of savings entries with green "Saved" badge, note, formatted date/time, and `+₹X.XX` amount in green.
     - Tapping any history entry navigates to Edit Entry screen.
  4. **Saved Entry Screen (`AddSavedScreen.kt`):**
     - Top bar with cancel (X) and screen title "Saved".
     - Large amount display with custom number pad.
     - Required note field ("How did you save? (required)", e.g. "Avoided ordering food").
     - Disabled Save button until positive amount and non-blank note are provided.
     - Helper text: "Counts towards the goal. It is not real money moved."
  5. **Edit Goal Screen (`EditGoalScreen.kt`):**
     - Pre-populated goal name, target amount with number pad, and deadline selector.
     - Explanatory note: "Changing the target never changes what you have saved so far."
     - Destructive "Delete goal" button in red.
     - Save changes updates goal in repository.
  6. **Edit Entry Screen (`EditEntryScreen.kt`):**
     - Pre-populated amount, number pad, note text field, and non-editable date/time.
     - "Save changes" updates entry.
     - Destructive "Delete entry" button with immediate navigation back and undo snackbar.

- **Theme & Architecture:**
  - Both Light and Dark modes fully supported and verified via phone screenshots.
  - Zero hardcoded colors outside `Theme.kt`.
  - Reusable components: `AFNumberPad` (accessible 48dp touch targets), `AFProgressBar` (custom canvas-based progress indicator), `AFButton` (added `TINTED` and `OUTLINE` variants).
  - QA configuration: Installed strictly as `com.autoflow.app.qa` (applicationIdSuffix `.qa`, "AutoFlow QA"). Real production package `com.autoflow.app` remained completely untouched. No phone permissions changed.

- **Testing & Verification:**
  - Full test suite: 2,458 tests passing (1,962 parser-core, 248 app debug, 248 app release).
  - 3 mutation proofs executed against UI state decisions and caught by automated unit tests:
    1. Broken deadline calculation in `GoalDeadlineFormatter`: caught by `GoalDeadlineFormatterTest.testPastDeadlineShowsPassed`.
    2. Broken reached status calculation in `GoalsUiState`: caught by `GoalsUiStateTest.testGoalCardCalculatesProgressAndReached`.
    3. Premature Save button enablement in `AddSavedUiState`: caught by `GoalsUiStateTest.testAddSavedUiStateValidation`.

## 21. Goals (Saved Version) Merged to Master — Task M1 (October 25, 2026)

- Fast-forward merged branch `goals-ui2` into `master` at commit `3bd6312a7b753e89c6e9b037d5f25a59e84b28e1`.
- Incorporates the complete motivation jar data layer (Room v6 migration, `GoalRepository`), UI screens (`GoalsTabScreen`, `NewGoalScreen`, `GoalDetailScreen`, `AddSavedScreen`, `EditGoalScreen`, `EditEntryScreen`), and unit tests.
- Worktrees cleaned up and merged feature branches (`ui-settings`, `ui-firstrun`, `goals-data`, `goals-simple`, `goals-ui2`) removed.
- Full verification suite passing (2,458 tests, zero hardcoded colors, permissions and privacy checks clean).

## 22. Fix v5->v6 Migration & Live Phone Deployment — Task F1 (October 25, 2026)

- **Root Cause & Fix (MIGRATION_5_6_SQL):**
  - In Task I1, Room database migration from v5 to v6 failed on Android SQLite because `ALTER TABLE ... RENAME TO` on Android does not rewrite foreign key references from child tables (referencing `goals_new` instead of `goals`).
  - Rewrote `MIGRATION_5_6_SQL` in `AutoFlowDatabase.kt` to completely eliminate `RENAME TO`:
    1. Creates `_goals_stage` and `_entries_stage` staging tables via `CREATE TABLE ... AS SELECT`.
    2. Drops legacy `goal_entries` and `goals` tables.
    3. Creates target `goals` and `goal_entries` tables directly matching Room schema `6.json` (foreign key directly references `goals`).
    4. Populates target tables from staging tables (`SET_ASIDE` and `AVOIDED` preserved; `USED` dropped).
    5. Drops staging tables.
  - Added test `testMigration5To6_foreignKeyList_referencesGoals` in `RoomMigration5To6Test.kt` verifying `PRAGMA foreign_key_list(goal_entries)` references `"goals"`.
  - Added guard test `testMigration5To6_containsNoRenameTo_guardTest` forbidding `RENAME TO` in migration SQL.
  - Executed mutation proof catching `RENAME TO` and verified GREEN.

- **Android Device Verification (QA App):**
  - Built QA build (`assembleQa`) and installed `com.autoflow.app.qa` on device `dfa4dd90`.
  - Staged pre-install v5 database copy (`pre_goals_install_20261025_0848.db`).
  - Launched QA app cleanly without crash; verified Home (₹2,579.00 across 3 transactions this month, 5 total), Goals tab empty state.
  - SQLite verified: `user_version = 6`, `foreign_key_list` references `goals`, `integrity_check` ok, transactions count 5, sum 267900 paise.

- **Real Phone Deployment (com.autoflow.app):**
  - Took fresh read-only backup: `pre_fix_install_20261025_0905.db` (102400 bytes, user_version 5, 5 transactions, sum 267900 paise).
  - Built debug APK (`assembleDebug`) and installed on `dfa4dd90` via `adb install -r`.
  - Launched real app cleanly:
    - Home screen shows ₹2,579.00 across 3 transactions this month (5 transactions total preserved).
    - Goals tab opens in motivation jar empty state ("Add goal").
    - Settings screen shows SMS access is On.
  - Post-install backup `post_fix_install_20261025_0907.db` (98304 bytes, Modulo 4096 = 0):
    - `user_version`: 6.
    - `PRAGMA foreign_key_list(goal_entries)`: references `goals`.
    - `PRAGMA integrity_check`: ok.
    - `transactions`: 5 rows, sum 267900 paise.
  - Uninstalled QA copy `com.autoflow.app.qa`; confirmed `com.autoflow.app` is the sole AutoFlow app on device.

## 23. Home-Screen Widget (+ Expense, + Saved) & Quick Expense — Task W1 (October 25, 2026)

- **Home-Screen Widget (`AutoFlowWidgetProvider`):**
  - Built 4x1 AppWidgetProvider home-screen widget "AutoFlow" with two pill buttons side by side: `+ Expense` (strong green, left) and `+ Saved` (light green, right).
  - Resizable horizontally and vertically (`resizeMode="horizontal|vertical"`).
  - Full privacy compliance: displays zero transaction data, zero amounts, and zero goal names on the home screen.
  - Automatic system light and dark mode adaptation via native resource qualifiers (`res/values/colors.xml` and `res/values-night/colors.xml`) with rounded shape drawables (`widget_button_expense_bg.xml`, `widget_button_saved_bg.xml`).
  - Widget picker metadata (`res/xml/autoflow_widget_info.xml`): short title "AutoFlow", description "Quickly log an expense or saved money", preview drawable (`widget_preview.xml`).
- **Quick Expense Screen (`QuickExpenseScreen`):**
  - Dedicated fast-logging screen launched only from widget `+ Expense`.
  - Header: Close 'X' button at top left, title "Quick expense".
  - Large amount display with `AFNumberPad` (Indian rupee format).
  - Optional note field: "Merchant or note (optional)" with placeholder "e.g. Tea, auto fare".
  - Full category selection chips (all standard categories plus user-created custom categories).
  - "Save expense" primary button (disabled until valid amount is entered).
  - Footnote: "Saved as Cash, now. Use + in the app for date, time and mode."
  - Direct database row equivalence: inserts manual cash transaction using identical validation rules as standard Add expense.
  - Returns to launcher/caller immediately upon save or close.
- **Saved Action Routing (`WidgetActionResolver`, `GoalPreferences`):**
  - Pure Kotlin resolver handles destination routing for widget intents.
  - Tracks `lastUsedGoalId` in `GoalPreferences` (SharedPreferences).
  - When 0 active goals exist: routes to `NewGoalScreen` with hint banner "Create a goal first, then tap Saved". Creating a goal navigates directly to `AddSavedScreen` for that goal.
  - When 1 active goal exists: routes directly to `AddSavedScreen` with that goal preselected.
  - When >1 active goals exist: routes to `AddSavedScreen` displaying "Which goal?" selection chips, preselecting `lastUsedGoalId` (or falling back to the first goal).
  - Tapping Saved from inside a goal card in the app remains locked to that goal (chips hidden).
  - Returns cleanly to phone launcher upon cancel or completion when launched from widget.
- **Wording Cleanup:**
  - Removed the word "Avoided" from all widget/related UI and placeholder text across the codebase (e.g. updated placeholder to "e.g. Skipped ordering food").
- **Security & Lifecycle Safety:**
  - Registered `AutoFlowWidgetProvider` as the only exported component.
  - PendingIntents configured with `FLAG_IMMUTABLE` and `Intent.FLAG_ACTIVITY_SINGLE_TOP`.
  - Safe intent routing in `MainActivity.kt` handles cold start (`onCreate`) and background resume (`onNewIntent`).
  - Unknown, malformed, or null intent actions are safely ignored without navigation.
- **Unit Testing & Mutation Proofs:**
  - Added `WidgetIntentHandlingTest.kt` (12 tests covering all intent routing, cold start, single top, goal resolution, and fallback branches).
  - Added `QuickExpenseEquivalenceTest.kt` (verifies amount parsing, boundary limits, note length limits, custom categories, and row equivalence with manual cash).
  - Two automated mutation proofs executed:
    1. Replaced fallback `else -> null` in `WidgetActionResolver.resolveDestination` with `QuickExpense`: caught by 3 tests (`testNullAction_isIgnored`, `testMalformedAction_isIgnored`, `testUnknownAction_isIgnored`).
    2. Disabled `lastUsedGoalId` lookup in `WidgetActionResolver.resolveDestination`: caught by `testActionSaved_withManyGoals_andLastUsedSet_preselectsLastUsedGoal`.
- **Physical Phone Verification (`dfa4dd90`):**
  - Verified on real hardware using QA package `com.autoflow.app.qa` (production app `com.autoflow.app` left untouched).
  - Widget successfully placed and tested in light and dark mode.
  - Quick expense logged ₹120.00 cash expense ("Chai", Food category); verified reflected on Home screen.
  - Saved flow verified with 0 goals (hint banner), 1 goal (direct), and 2 goals (chips pre-selection).

## 24. Widget Merged to Master — Task M2 (October 25, 2026)

- Fast-forward merged branch `widget1` into `master` at commit `7ad9ddf1805e6d83c8554b9934734cceb16e0ad3`.
- Worktree `..\autoflow-w1` cleanly removed and feature branch `widget1` deleted.
- All 7 verification checks passed on `master` (2,496 tests passing: 1,962 parser-core, 267 app debug, 267 app release; 0 hardcoded colors, RECEIVE_SMS only, 0 message body leaks).

## 25. Captured-Expense Notification with Category Buttons — Task N1 (October 25, 2026)

- **Initial Audit:**
  - Audited existing codebase for captured-SMS notification channels, builders, broadcast receivers, and settings switches.
  - Confirmed none existed; built clean, decoupled notification architecture without duplication.
- **Notification Channel & Core States (`CapturedExpenseNotificationBuilder`, `CapturedExpenseNotificationHelper`):**
  - Notification channel `"Captured expenses"` (`channel_captured_expenses`) with importance `LOW` (no sound, zero vibration).
  - Four distinct notification states:
    1. **State 1 (Needs a category):** For unknown merchants or transactions requiring review.
       - Title: `"₹320.00 spent at <merchant>"` (fallback `"Unknown Merchant"`).
       - Text: `"Pick a category so it is counted correctly"`.
       - Actions: Top 2 predicted category chips using existing review suggestion logic (`getTopLikelyCategories`) + `"More…"`.
    2. **State 2 (Merchant already known):** For merchants with an existing rule/category.
       - Title: `"₹130.00 spent at <merchant>"`.
       - Text: `"<Category> · counted in today's total"`.
       - Actions: `"Change"` and `"Not an expense"`.
    3. **State 3 (Category chosen):** In-place notification update following category selection.
       - Title: `"Saved: ₹320.00 · <Category>"`.
       - Text: `"Future spends at this merchant will use <Category>"`.
       - Actions: `"Undo"`. Auto-dismisses after 15 seconds (`setTimeoutAfter(15000)` and background handler).
    4. **State 4 (Not an expense):** In-place notification update when excluded.
       - Title: `"Marked as not an expense"`.
       - Text: `"<Amount> at <Merchant> not counted"`.
       - Actions: `"Undo"`. Auto-dismisses after 15 seconds.
- **Deep Linking & Interaction:**
  - Tapping notification body, `"Change"`, or `"More…"` triggers `ACTION_EDIT_TRANSACTION` (`com.autoflow.app.ACTION_EDIT_TRANSACTION`) with `EXTRA_TRANSACTION_ID`.
  - Opens `EditTransactionScreen` directly with fixed, read-only amount for SMS expenses, enabling full category picker and note editing. Auto-cancels notification on tap.
- **Background Action Handling (`NotificationActionReceiver`, `TransactionRepository`):**
  - Non-exported BroadcastReceiver executes when the app UI is closed or backgrounded.
  - Validates transaction ID against SQLite database before performing operations.
  - Category assignment updates transaction, saves merchant category rule, and retroactively updates matching active AUTO rows. Undo restores previous category and deletes saved rule.
  - Mark not an expense excludes transaction (`reviewStatus = EXCLUDED`), dropping it from spend totals. Undo restores previous review status.
- **Privacy & Security Guarantees:**
  - Zero raw SMS body retention: notifications, PendingIntent extras, and log outputs never contain SMS body text.
  - Lock screen privacy: Set to `VISIBILITY_PRIVATE` with a sanitized public notification (`"AutoFlow"`, `"New expense captured"`).
  - No `READ_SMS` permission declared.
- **Settings Toggle (`SettingsScreen`, `NotificationPreferences`):**
  - Added "Notifications" section with switch `"Notify me when an expense is captured"` and subtitle `"Shows amount and merchant"`.
  - Defaults to `ON`. When toggled `OFF`, expenses continue to be ingested silently into SQLite with zero notifications posted.
  - Dynamically displays `"Notifications are disabled in system settings"` if system-level app notification permissions are revoked.
- **Safe QA Testing Hook (Isolated in `src/qa`):**
  - Implemented `QaFakeSmsReceiver` (`com.autoflow.app.qa.ACTION_INJECT_FAKE_SMS`) strictly within `src/qa` for SIM-free device verification.
  - Verified completely absent from `app-debug.apk` and `app-release-unsigned.apk` manifests and dex files.
- **Unit Testing & Mutation Proofs:**
  - Unit tests: `NotificationPreferencesTest` (3 tests), `CapturedExpenseNotificationContentTest` (6 tests), `NotificationActionHandlerTest` (6 tests).
  - Executed 3 mutation proofs:
    1. Mutated State 1 text to `"Please pick a category"`: caught by `CapturedExpenseNotificationContentTest.testBuildNeedsCategory_contentAndActions` and `NotificationActionHandlerTest.testUndoAssignCategory_revertsCategoryAndDeletesSavedRule`.
    2. Deleted `merchantCategoryDao.deleteByKey` in `undoAssignCategory`: caught by `NotificationActionHandlerTest.testUndoAssignCategory_revertsCategoryAndDeletesSavedRule`.
    3. Nullified `timeoutMs` in `buildCategorySaved`: caught by `CapturedExpenseNotificationContentTest.testBuildCategorySaved_contentAndActions`.
- **Physical Device Verification (`dfa4dd90`):**
  - Verified on physical phone using isolated QA package `com.autoflow.app.qa` (production app `com.autoflow.app` left untouched).
  - Tested all notification states, undo flows, background execution with force-stopped app, lock screen rendering, and Home screen balance updates.

## 26. Category Popup Fix, New-Shop Rule & Compact Forms — Task N3 (October 25, 2026)

- **Part 1: Category Popup Activity Fix (`CategoryPopupActivity`):**
  - **Phone Diagnosis & Root Cause:** Diagnosis via 8 adb commands revealed `CategoryPopupActivity` was configured with `android:noHistory="true"` and `android:launchMode="singleTask"` in `AndroidManifest.xml`. When launched from the notification shade, Android's window manager collapsed the shade and immediately destroyed the translucent activity within 19ms (`OplusScrollToTopManager: window dying`), falling back to `MainActivity`.
  - **Fix:** Removed `android:noHistory="true"` and `android:launchMode="singleTask"`. Updated `CapturedExpenseNotificationHelper` to use deterministic unique `requestCode`s derived from `(transactionId, actionKey)` for all notification PendingIntents, preventing intent collision.
  - **Unit Test & Mutation Proof:** Tested in `CapturedExpenseNotificationContentTest.testActionRequestCode_uniqueAndDeterministic`.
- **Part 2: New-Shop Rule (First Spend Needs Category):**
  - Added `TransactionRepository.hasMerchantRule(merchantName)`.
  - In `CapturedExpenseNotificationBuilder.buildInitialContent`, any first-time merchant (`hasSavedRule == false`) or row in `NEEDS_REVIEW` or categorized as "Others"/"Unknown" triggers `needsCategory = true`, presenting the top 2 likeliest categories + "More…".
  - Known-merchant state ("Change" + "Not an expense") is shown only when a saved rule exists.
  - Tapping Undo removes the merchant rule; subsequent spends at that merchant fall back to "needs category".
  - Tested in `CapturedExpenseNotificationContentTest.testNewShopRule_firstTimeMerchant_needsCategory`, `testNewShopRule_merchantWithSavedRule_knownMerchantState`, and `NotificationActionHandlerTest.testUndoMerchantRule_subsequentSpendNeedsCategory`.
- **Part 3: Compact Forms & Centralized Chip Tokens:**
  - Defined `AFChipTokens` (`visualHeight = 36.dp`, `minTouchTarget = 40.dp`, `horizontalPadding = 12.dp`, `gap = 6.dp`, `fontSize = 13.sp`, `lineHeight = 18.sp`) in `AFCategoryChip.kt` and `Theme.kt`.
  - Reused across `AFCategoryChip` and `AFSegmented`, removing oversized 48dp/44dp min heights.
  - Category lists wrap naturally using `FlowRow` with `chipGap` (6dp), eliminating horizontal category scrolling in `ReviewScreen`.
  - Pinned primary action buttons in `Scaffold`'s `bottomBar` with `navigationBarsPadding()` and `imePadding()` across `AddCashExpenseScreen`, `QuickExpenseScreen`, `EditTransactionScreen`, and `AddSavedScreen`.
  - In `EditTransactionScreen`, secondary button ("Not an expense" / "Count this expense") stays inside the scrollable content area.
  - Fluid sizing accommodates large font scale and display settings without clipping.

## 27. Notification Rule Refinement, GitHub Sync & Main Merge — Task M3/M3b (October 25, 2026)

- **Part 0: Rule Refinement (`CapturedExpenseNotificationBuilder`):**
  - Updated `needsCategory()` logic:
    - If `merchantName` is blank or `"Unknown Merchant"` -> `true`.
    - If `reviewStatus == NEEDS_REVIEW` -> `true` (always needs category, rule or no rule).
    - If `hasSavedRule == true` -> `false` (known-merchant state).
    - Fallback: returns `true` only if category is `"Others"`, `"Unknown"`, or blank.
    - Result: Parser-categorized rows (e.g. Swiggy -> Food) with no user rule show the known-merchant state (`"Food · counted in today's total"` with "Change" and "Not an expense"), while unmapped merchants or review items present category suggestions.
  - Added comprehensive test matrix in `CapturedExpenseNotificationContentTest.kt`:
    - `testRuleA_parserCategorisedFood_noRule_showsKnownState`
    - `testRuleB_othersCategory_noRule_showsNeedsCategory`
    - `testRuleC_reviewStatusNeedsReview_noRule_showsNeedsCategory`
    - `testRuleD_savedRuleWithOthersCategory_showsKnownState`
    - `testRuleE_savedRuleInReview_showsNeedsCategory`
  - Mutation proof verified: mutating line 68 to `return !hasSavedRule` broke `testRuleA` with an `AssertionError`.
- **Part 1: Branch Sync & Merge into Main:**
  - Integrated `main` (`85f4caa`, `.gitignore` commit from GitHub setup) into `notif3` via standard merge commit (`d8ca415`), preserving full non-linear history without rebasing or force flags.
  - Confirmed `git ls-files` tracks zero forbidden files (`.db`, `.keystore`, `.jks`, `local.properties`).
  - Full suite verified via `scripts/verify.ps1`: all 7 checks passed, 2556 unit tests passing.

## 28. Widget Polish, Square Widget, Search, Daily Recap & Spend Widget — Task R1 (October 25, 2026)

- **Part A: Home-Screen Widget Polish & Square 2x2 Widget (Commit `bbaf1ab`):**
  - Polished original 4x1 AutoFlow widget container with 26dp rounded corner background (`widget_container_bg.xml`), 1dp subtle outline (`widget_container_stroke`), 10dp gap, and 48dp pill heights with 24dp rounded button corners.
  - Added new 2x2 resizable "AutoFlow square" widget (`AutoFlowSquareWidgetProvider`, `widget_autoflow_square.xml`, `autoflow_square_widget_info.xml`) with stacked pill buttons (`+ Expense` over `+ Saved`).
  - Added theme colors `widget_container_bg` and `widget_container_stroke` for light (`#F0FFFFFF`, `#E5E7EB`) and dark mode (`#FF12201C`, `#22332F`).
  - Unit tests in `WidgetIntentHandlingTest.kt` passing 100%.

- **Part B: Fast Transaction Search (Commit `05d81a5` & `3f29995`):**
  - Added search icon in `HomeScreen` top bar routing to `SearchScreen` (`Screen.Search`).
  - Auto-focused search input with 250ms debounce in `MainViewModel`.
  - Searches across `merchant_name`, `note`, `category`, and amounts:
    - Rupee amount parsing supports exact rupee/paise matching (e.g. "130", "130.5", "130.50", "₹130").
    - Digits-only fallback matching (e.g. "50" matches ₹50, ₹50.00, ₹150, ₹500).
    - SQL wildcard escaping (`%` and `_`) prevents unintended LIKE pattern injection.
  - Real SQLite test suite `TransactionSearchRealSqliteTest.kt` and `TransactionSearchHelperTest.kt`.
  - Mutation Proof 1: Mutating `TransactionSearchHelper.escapeLike` to omit `%` escaping failed `TransactionSearchRealSqliteTest.testSearch_escapePercentAndUnderscore` (`AssertionError at TransactionSearchRealSqliteTest.kt:172`).
  - Color audit compliance: `SearchScreen` uses `Brush.verticalGradient(listOf(colors.brand, colors.brand))` for `cursorBrush`.

- **Part C: Daily Spending Recap Notification & Settings (Commit `b21b4f9`):**
  - WorkManager one-time daily worker (`DailyRecapWorker`) scheduled without `RECEIVE_BOOT_COMPLETED` or exact alarms, rescheduling self each execution and on app startup.
  - Channel "Daily recap" with low importance (`IMPORTANCE_LOW`, silent) and lock-screen sanitization (`VISIBILITY_PRIVATE` with generic public version).
  - Pure builder `DailyRecapContentBuilder`: summarizes today's total spend, expense count, top category breakdown, and unreviewed count; returns `null` when no spend and no review items exist.
  - Settings UI rows for "Daily recap" toggle and "Recap time" time picker dialog (default 21:00 / 9:00 PM).
  - Unit test suite `DailyRecapTest.kt` (10 tests).
  - Mutation Proof 2: Mutating line 34 in `DailyRecapContentBuilder.kt` to return early if `countedExpenses.isEmpty()` (omitting `reviewCount == 0`) failed `DailyRecapTest.testRecapContent_zeroCountedExpensesWithReviewItems` (`AssertionError at DailyRecapTest.kt:124`).

- **Part D: "AutoFlow this month" Spend Widget (Commit `7a6afc2`):**
  - Built 2x1 non-resizable spend widget (`AutoFlowSpendWidgetProvider`, `widget_autoflow_spend.xml`, `autoflow_spend_widget_info.xml`) showing "This month" and formatted total (e.g. "₹8,420") using `CurrencyFormatter`.
  - 30-minute periodic update (`updatePeriodMillis = 1800000`).
  - Pure builder `SpendWidgetContentBuilder` filtering out soft-deleted, excluded, refund, and out-of-month rows.
  - Live write hook (`TransactionRepository.onTransactionWritten` / `SpendWidgetUpdater.notifyDataChanged`) triggered on all write paths: insert, update, soft-delete, restore, and markNotAnExpense.
  - Tapping the spend widget opens MainActivity (Home).
  - Unit test suite `SpendWidgetTest.kt` passing 100%.

## 29. Fix Search Showing "No Matches" & Open Home Launch Routing — Task R2 (October 25, 2026)

- **Bug 1: Search Showing "No Matches" on Device:**
  - *Root Cause:* `TransactionDao.searchTransactions` declared a default method body `= kotlinx.coroutines.flow.emptyFlow()`. In Room with KSP, interface methods with default bodies are treated as already implemented, causing Room's symbol processor to bypass generating the `@Query` method in `TransactionDao_Impl.java`. At runtime on the phone, calling `searchTransactions` directly executed the default empty flow, returning 0 transactions and displaying "No matches" for any query.
  - *Fix:* Removed `= kotlinx.coroutines.flow.emptyFlow()` from `TransactionDao.kt` line 159 so Room generates the full query implementation. Added explicit overrides to all unit test fake DAOs.
  - *Unit Test:* Added `testSearchFlow_drivesSearchScreenState` in `MainViewModelTest.kt` covering the full query sequence (empty -> "hint", "Chai" -> "results", "410" -> "results", "Food" -> "results", "nonsensexyz" -> "No matches", empty -> "hint").
  - *Mutation Proof:* Mutating `InMemoryTransactionDao.searchTransactions` to return `flowOf(emptyList())` failed at `MainViewModelTest.kt:332` (`org.junit.ComparisonFailure: expected:<[result]s> but was:<[No matche]s>`).

- **Bug 2: Notification & Widget "Open Home" Resuming Old Screen:**
  - *Root Cause:* Tapping the daily recap notification or the spend widget reopened `MainActivity` (via `onNewIntent` when already in memory), but without an intent extra or navigation event instructing the UI to return to the Home tab, Compose preserved the active `currentScreen` state (e.g. Settings or Review).
  - *Fix:*
    - Created pure `AppLaunchResolver` (`AppLaunchResolver.kt`) mapping incoming intents/actions to `AppLaunchTarget` (`Home`, `EditTransaction(id)`, `Saved(selectedGoalId, goals)`, `QuickExpense`, `NewGoalWithHint`, `None`).
    - Standardized `buildHomeIntent` attaching `EXTRA_OPEN_HOME = true` and flags `Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP`.
    - Wired `DailyRecapNotificationHelper.createOpenIntent(context)` and `AutoFlowSpendWidgetProvider.createHomeIntent(context)` through `buildHomeIntent`.
    - Wired `MainActivity.onCreate` and `MainActivity.onNewIntent` to call `viewModel.handleAppLaunchIntent(intent)`.
    - `MainViewModel` exposes `resetToHomeEvent: StateFlow<Int>` incremented on `resetToHome()`.
    - `AutoFlowApp` observes `resetToHomeEvent` and transitions `currentScreen = Screen.Home` when first run is completed.
  - *Unit Tests:* `AppLaunchResolverTest.kt` verifying all resolver targets and intent extra/flag assertions.
  - *Mutation Proof:* Mutating `isOpenHome || action == ACTION_OPEN_HOME` to `action == ACTION_OPEN_HOME` in `AppLaunchResolver.kt` failed at `AppLaunchResolverTest.kt:63` (`java.lang.AssertionError: expected:<Home> but was:<None>`).

## 30. Merge Tasks R1 & R2 into Main & Update Real App — Task M4 (October 25, 2026)

- **Merge:** Fast-forward merged `feat1` into `main` (commit `489651e`), bringing in:
  - Part A: Home-screen widget polish & square 2x2 widget.
  - Part B: Fast transaction search screen, DAO query, amount matching and real SQLite tests.
  - Part C: Daily spending recap notification, scheduler, and settings rows.
  - Part D: "AutoFlow this month" 2x1 spend widget with live repository write hooks.
  - Task R2: Room DAO query generation fix (Bug 1) and intent open-home launch resolver (Bug 2).
- **Verification:** Ran `scripts/verify.ps1` on `main`: all 7 checks passed, 2642 unit tests passing (1962 parser-core, 340 app debug, 340 app release).
- **Cleanup:** Worktree `..\autoflow-r1` removed, branch `feat1` deleted via `git branch -d`.
- **Real App Update:** Pre-install database backup verified, debug APK installed cleanly over `com.autoflow.app` via `adb install -r`, post-install backup verified preserving database integrity and schema version 6.

## 31. App Launcher Icons, Bottom-Bar Tab Icons & Home Screen Polish — Task P1 (October 25, 2026)

- **Worktree & Scope:**
  - Work executed in worktree `..\autoflow-p1` on branch `polish1` branched from `main` (`f1ee699`).
  - Styling and icon polish only. Zero logic or state-machine changes.
  - Zero permissions added; `parser-core` untouched; zero raw SMS logged; zero hardcoded colors in Kotlin outside `Theme.kt`.

- **Part A: App Launcher Icons (Commit `b514f43`):**
  - Adaptive launcher icon with full backward compatibility:
    - `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` referencing background, foreground, and monochrome.
    - Main variant background: `res/drawable/ic_launcher_background.xml` (108x108 viewport, linear gradient `#0D9E71` to `#047857`).
    - Foreground: `res/drawable/ic_launcher_foreground.xml` (crisp rupee symbol strokeWidth 6.5 within inner 66dp safe zone, white `#FFFFFF`).
    - Monochrome: `res/drawable/ic_launcher_monochrome.xml` (black stroke for Android 13+ themed icons).
    - QA variant: `app/src/qa/res/drawable/ic_launcher_background.xml` (amber gradient `#F59E0B` to `#B45309`) enabling instant visual distinction between QA and production builds.
    - `AndroidManifest.xml` updated to point to `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
    - Unit tests in `LauncherIconResourceTest.kt` verifying all XML drawables, vector paths, and safe-zone containment.

- **Part B: Bottom-Bar Tab Icons (Commit `94d54b0`):**
  - Custom vector drawables matching wireframe stroke silhouettes (strokeWidth 1.75 unselected, 2.0 selected):
    - Home: `ic_nav_home.xml` / `ic_nav_home_selected.xml` (house outline with door cutout).
    - Goals: `ic_nav_goals.xml` / `ic_nav_goals_selected.xml` (three concentric target rings).
    - Review: `ic_nav_review.xml` / `ic_nav_review_selected.xml` (clipboard with top clip and checkmark).
    - Categories: `ic_nav_categories.xml` / `ic_nav_categories_selected.xml` (pie chart with pulled-out slice).
  - Helper `createStandardNavItems(unreviewedCount)` in `AFBottomNav.kt` unified across all 4 tab destinations (`HomeScreen`, `GoalsTabScreen`, `ReviewScreen`, `CategoriesScreen`).
  - Unit tests in `NavIconResourceTest.kt` verifying all 8 vector files and resource bindings.

- **Part C: Home Screen Wireframe 1/2 Layout Polish (Commit `9aca117`):**
  - Two-section layout with top block (<40% screen height) and bottom list sheet (`weight(1f)`):
    - Top block has subtle vertical gradient background from `Theme.colors.hero` (`#ECFDF5` light, `#0F2A22` dark) to `Theme.colors.bg`, ending at 240dp.
    - Top row: Settings button (44dp touch area) on left, centered `AFPeriodPill` (40dp high, 16dp pad, 14.5sp bold, surface fill, 1dp line outline), Search button (44dp touch area) on right. Old "AutoFlow" title row removed.
    - Big number section: 46sp extra-bold, tabular numbers (`fontFeatureSettings = "tnum"`), letter spacing -1sp, centered; centered count subtitle "Across N transactions" 14sp semibold muted with 44x28dp touch area refund info button.
    - Chart: `AFSmoothLineChart` (88dp canvas + 36dp month label row), dotted muted line for future months, past/current clickable, future months unclickable.
    - Review check pill (wireframe 2): 34dp high, amber background, 13sp bold with info icon and chevron; visible only when `unreviewedCount > 0`.
    - Bottom list sheet: `Surface` with `Theme.colors.sheet`, 28dp rounded top corners, 6dp top shadow elevation, 36x4dp grab handle at top center (10dp from top).
    - List rows: flat style (`isFlat = true`) with 20dp horizontal padding, min 64dp height, no card background/border, spaced items, 15sp extra-bold group header with total on right.
    - Bottom fade: 56dp gradient overlay to `Theme.colors.sheet` at the bottom of the sheet.
    - Empty state and SMS permission banner sit cleanly inside the bottom sheet.
  - Unit tests in `HomeScreenTest.kt` verifying `formatReviewCheckLabel`, `formatDayHeader`, and future-month detection in `monthlySeries`.

- **Verification & QA Installation:**
  - `scripts/verify.ps1`: All 7 checks passed (2656 unit tests: 1962 parser-core, 347 app debug, 347 app release).
  - Zero hardcoded colors outside `Theme.kt`. Zero prohibited permissions (`READ_SMS` absent).
  - Built `assembleQa` and installed over `com.autoflow.app.qa` on device `dfa4dd90`.






