# Racks — Personal Money Tracker (Android)

Native offline-first personal money management Android app built with **Kotlin**, **Jetpack Compose**, **Material 3 Expressive**, **Hilt**, **Room**, **DataStore**, **WorkManager**, and **Google Sheets API v4**.

## Features

- **Fast Transaction Entry (Home):** Immediate calculator-style MYR (`RM`) keypad with morphing pill buttons, rounded `Google Sans Flex` typography, swipe/toggle basic & calculator modes (`+`, `-`, `×`, `÷`, `=`), category chips, remarks, and date picker.
- **Offline-First Storage:** All transactions save immediately in Room SQLite (`amountMinor: Long` in sen, MYR 1:1) with `PENDING`, `SYNCING`, `SYNCED`, `FAILED`, and `CONFLICT` tracking.
- **Financial Dashboard:** Monthly overview (Net, Savings Rate %, Income, Expense), multi-segment category spending breakdown, type & category filters, full transaction history modal, and tap-to-edit/soft-delete with Undo.
- **Google Sheets Sync:** Direct bidirectional sync against the **Money Tracker 2026** workbook (`B:G` ledger rows starting at row 10 + hidden sync metadata in `H:J`), preserving summary formulas (`A1:J9`), auto-duplicating future monthly tabs, preventing duplicates via stable UUIDs, and syncing automatically via WorkManager on startup, edits, connectivity recovery, and periodic background intervals.
- **Adaptive Appearance:** System, Light, Dark, and Material You Dynamic Color themes.

## Google Cloud OAuth Setup

1. Create a project in [Google Cloud Console](https://console.cloud.google.com/) and enable **Google Sheets API**.
2. Configure **OAuth consent screen** (`External`), add your Gmail account under **Test users**, and add scope `https://www.googleapis.com/auth/spreadsheets`.
3. Create an **Android** OAuth Client ID:
   - Package name: `com.rackz.stackmyrackz`
   - SHA-1 fingerprint of your signing keystore (for Android debug keystore on Windows: `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android`)
4. Open **Racks**, tap the **Cloud** icon, connect your Google account, verify the Spreadsheet ID, and tap **Sync Now**.

## Build & Verify

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --no-daemon
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
