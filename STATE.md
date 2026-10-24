# AutoFlow — System State & Architecture

## 1. What Is Built (By Phase)

- **Phase 1 (Audit & Licensing):** Evaluated upstream PennyWise parser-core engine; verified AGPL-3.0 compatibility with private local on-device operation; added root `LICENSE` (AGPL-3.0) and `NOTICE` files.
- **Phase 2A (Parser Core & Domain Adapter):** Integrated decoupled `parser-core` module via Gradle; implemented `AutoFlowParserAdapter` mapping `ParsedTransaction` to Room `TransactionEntity`; enforced integer paise conversion, expense-only scoping, dropped non-expense entries (salary/income/balance), uncertainty bitmasks, and zero raw SMS retention.
- **Phase 2B (Ingestion Pipeline, Storage & Receivers):** Room SQLite storage (`TransactionEntity`, `RefundMatchEntity`, `TransactionDao`, `RefundMatchDao`); `DefaultIngestionPipeline` with 2-tier deduplication, soft-delete safety, and refund amount capping; `SmsBroadcastReceiver` registered strictly for `RECEIVE_SMS` with `SmsMessageProcessor` background dispatcher.
- **Phase 2C (Axis Bank CC Fix):** Targeted regex addition (`spentAtPattern`) in `AxisBankParser` for credit card transactions with trailing `"IST at <MERCHANT>."` layout without touching shared base parsers.
- **Phase 3 (Presentation Layer):** Jetpack Compose Material 3 UI (`HomeScreen`, `EditTransactionScreen`, `AddCashExpenseScreen`, `MainViewModel`); spend totals (Today and This Month in local timezone); single-line "Add Cash" action; prefilled empty field for "Unknown Merchant" with optional placeholder; muted "Not counted" styling for `EXCLUDED` transactions with a "Restore" button; date and time pickers for manual cash entries; centralized design tokens (`Theme.kt`).
- **Phase 4 (Ingestion Safety Guards):** Conservative phrase-based guard (`isNonTransactionMessage`) in `AutoFlowParserAdapter` and `SmsMessageProcessor` rejecting OTPs, failed/declined debits, credit limit updates, monthly spend summaries, upcoming mandates/reminders, promos, card statements, and card repayment receipts.

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
2. **Tier 2 (Reference & Time Window Match):** For transactions with non-blank `reference`, queries records with identical `reference`, `recordType`, and `amountPaise` within `[dateTime - 1 hour, dateTime + 1 hour]`. If soft-deleted, drops as `SoftDeletedIgnored`; if active, drops as `DuplicateIgnored`.
3. **Refund Capping:** Ingested refunds search for matching prior expenses by reference or merchant. Total refunded paise is capped at original expense amount. Unmatched refunds are tagged `NEEDS_REVIEW` with `UncertaintyReason.UNMATCHED_REFUND`.

## 4. Known Open Issues

1. **Refund UI Reconciliation:** Refunds appear in the transaction feed with a badge, but do not yet automatically link to or offset specific expense rows in the totals UI.
2. **Real-time SMS Receiver Log Task:** Daemon `adb logcat` monitoring task is active in background from prior sessions.

## 5. What Is NOT Verified

- **Android 14+ Doze Mode SMS Reception:** Background SMS receiver reliability under aggressive OEM battery optimizations and deep doze without a foreground service (unverified).
- **Non-Axis/Non-HDFC Carrier SMS:** Real carrier-delivered SMS layout variations for ICICI, SBI, and Kotak banks beyond synthetic test fixtures (unverified).
- **High-Volume Database Performance:** Room query performance and Compose list scrolling under large datasets (50,000+ transactions) on low-end hardware (unverified).
