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
