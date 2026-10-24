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
  - *Inbound Money Safety & Guard Hardening (Prompt M4):* `"transferred to"` only counts as outbound spend when NOT followed by `"your"`, `"a/c"`, `"account"`, or `"bank account"`. In `adapt()`, non-refund `EXPENSE`, `CREDIT`, and `TRANSFER` classifications require `hasCompletedSpend` to be true in the message body, ensuring inbound transfers (`"transferred to your account/bank account/A/c"`), payouts, and credit alerts are never ingested as `EXPENSE`.
  - *UPI VPA Extraction & Merchant Sanitization (Prompt M4):* Replaces junk/placeholder merchant values (`"upi"`, `"neft"`, `"imps"`, `"rtgs"`, `"atm"`, `"pos"`, `"card"`, `"bank"`, `"unknown merchant"`, blank) by extracting valid UPI VPA handles (3–20 chars, letters only with `.` and `-`, non-phone number, uppercased e.g. `"ZEPTO"`, `"SWIGGY"`) while leaving phone number handles (`9876543210@ybl`) as `"Unknown Merchant"`. Strips trailing `"via <anything>"` (e.g. `"SWIGGY via UPI"` -> `"SWIGGY"`). Sanitizes fraud advisory clauses in-memory before upstream parser dispatch.
  - *Regression Corpus (`SmsRegressionCorpusTest.kt`):* Table-driven regression suite (`app/src/test/java/com/autoflow/app/data/adapter/SmsRegressionCorpusTest.kt`) expanded to 42 test cases covering M4 inbound transfer safety / UPI VPA extraction, M3 payee credit messages, M2 corner cases, Prompt M promise/completed pairs, Prompt 0 safety probe controls, and Prompt J narrowed-guard positive controls.
  - *Reminders & Summaries:* Blocks messages starting with `"reminder:"` or payment/mandate reminders, while allowing debits containing `"Reminder"` later in body text. Blocks monthly spend summaries, promos, card statements, and card debt repayments.

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
2. **Hardcoded Category:** Category is currently hardcoded to "Others" across all ingested and cash transactions (investigated in Prompt L).
3. **Peer-to-Peer UPI VPA Names:** Peer handles without real merchant identity require user review. Phone-number handles fall back to "Unknown Merchant".
4. **AxisBankParser spentAtPattern Boundary:** `spentAtPattern` in `AxisBankParser` triggers on messages containing `"Credit Card"` even without the word `"Spent"` (to tighten in a future update).
5. **OutOfScope Commit 6e8ee14:** An `extractReference` modification in commit `6e8ee14` was outside its originally requested scope.
6. **Play Store Policy:** Google Play Store SMS permission (`RECEIVE_SMS`) policy justification and compliance pathway is unresolved.

## 5. What Is NOT Verified

- **Room SQL Queries via Real SQLite:** `MainViewModelTest` runs against an in-memory fake DAO (`FakeTransactionDao`) rather than a Room SQLite database; actual Room SQLite queries in `TransactionDao` (`getAllActiveTransactions`, date range queries, etc.) are untested against SQLite in JVM unit tests (not verified).
- **Android 14+ Doze Mode SMS Reception:** Background SMS receiver reliability under aggressive OEM battery optimizations and deep doze without a foreground service (not verified).
- **Non-Axis/Non-HDFC Carrier SMS:** Real carrier-delivered SMS layout variations for ICICI, SBI, and Kotak banks beyond synthetic test fixtures (not verified).
- **High-Volume Database Performance:** Room query performance and Compose list scrolling under large datasets (50,000+ transactions) on low-end hardware (not verified).

