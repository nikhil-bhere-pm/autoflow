# AutoFlow Tester Guide

Welcome to the AutoFlow test build! Thank you for helping us test AutoFlow.

AutoFlow is a fast, 100% private, on-device expense tracker for Android that automatically tracks your expenses when your bank sends you a transaction SMS—without ever sending your data to the internet.

---

## 1. How to Install the App

1. Download the file **`AutoFlow-0.1.0-test1.apk`** to your Android phone.
2. Open your phone's **Files** app or tap the downloaded file in your browser (e.g., Chrome).
3. If prompted: **"For your security, your phone is not allowed to install unknown apps from this source"**:
   - Tap **Settings**.
   - Turn on **"Allow from this source"**.
   - Tap the Back arrow, then tap **Install**.
4. Once installation is complete, tap **Open**.

---

## 2. Permissions & Why We Need Them

When you launch AutoFlow, Android will ask you for two permissions:

1. **SMS Permission (`RECEIVE_SMS`)**:
   - **Why**: Allows AutoFlow to detect incoming transaction alerts from your bank in real time and automatically record the amount and merchant.
   - **What we NEVER do**: We **never** read your personal text messages, and we **never** store or read one-time passwords (OTPs). AutoFlow only parses automated bank debit/credit alerts.
2. **Notification Permission (`POST_NOTIFICATIONS`)**:
   - **Why**: When a bank SMS arrives, AutoFlow immediately shows you a notification displaying what was captured (amount, merchant, category). You can tap category buttons directly on the notification to categorize or discard the expense.

### Privacy Guarantee
- **100% On-Device**: AutoFlow does not even have internet permission. Your transactions, accounts, and categories never leave your phone.
- **Zero Cloud Tracking**: There are no tracking scripts, analytics trackers, or external servers.

---

## 3. 8 Sample Tasks to Try

Here are eight key features to test:

1. **Add a Cash Expense**:
   - Tap the **`+`** (or Add Expense) button.
   - Enter an amount (e.g., \$15.00 or ₹150), merchant/note, pick a category, and save.
2. **Receive an Automated Bank Transaction**:
   - Make a small purchase or transfer with your bank.
   - Notice that AutoFlow captures the transaction instantly as the SMS arrives.
3. **Interact with the Notification**:
   - Look at the captured expense notification in your notification tray.
   - Tap one of the quick category pill actions to change its category, or tap the notification to open the app.
4. **Change a Category**:
   - Tap any transaction in the list to open its details.
   - Select a different category and verify the balance and category breakdown update.
5. **Search Transactions**:
   - Tap the **Search** icon at the top of the Home screen.
   - Type a merchant name (e.g., "Starbucks" or "Amazon"), an amount (e.g., "410"), or a category name.
6. **Add a Home Screen Widget**:
   - Long-press an empty space on your phone's home screen and tap **Widgets**.
   - Find **AutoFlow** and try placing a widget (Square Widget, Quick Action Widget, or Spend Widget).
7. **Explore Categories & Autopay**:
   - Go to the **Categories** tab.
   - Tap the **Autopay** button at the top to view upcoming autopayments and toggle reminders.
8. **Export a Backup**:
   - Open **Settings** (gear icon) and tap **Export Backup** to save a local backup copy of your database.

---

## 4. How to Give Feedback & Report Bugs

If something looks wrong, crashes, or doesn't parse correctly, we want to know!

### What to Share:
- What you did right before the issue occurred.
- What phone model and Android version you are using (e.g., Pixel 7, Android 14).
- A screenshot of the app screen where the problem happened.

### What NOT to Share (Important Security Rule):
- **NEVER** screenshot or share bank account numbers, OTPs, PINs, or sensitive financial information.
- If an SMS fails to parse, describe the problem without sharing confidential personal identifiers or security codes.
