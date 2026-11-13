# Product Requirements Document

# Personal Money Tracker — Native Android

**Version:** 1.0 — MVP  
**Platform:** Android (APK)  
**Design System:** Material 3 Expressive / Material You  
**Primary Reference:** [Minus — Open-source Android app](<https://github.com/isaacsa51/Minus>)  
**Data Source:** [Money Tracker 2026 — Google Sheets](<https://docs.google.com/spreadsheets/d/18EqxFXErZ6c4HVcvV5UZ4W8b3quUcdBaBXPcHE2bP5A/edit>)

---

## 1\. Product Overview

### 1.1 Summary

Build a lightweight, offline-first personal money management application for Android.

The application replaces the daily data-entry experience of Google Sheets with a fast, intuitive, native Android interface.

Google Sheets remains the existing cloud financial record and reporting system. The Android application provides convenient transaction entry, financial visualization, and synchronization.

The app must be installable as a standalone APK, without requiring Google Play Store distribution.

### 1.2 Problem Statement

The current Google Sheets money tracker is functional but difficult to navigate on a mobile device.

Primary problems:

- Recording transactions requires opening a spreadsheet, locating the correct month, and editing cells.
- Spreadsheet navigation is not optimized for one-handed mobile use.
- Financial summaries are spread across different tabs.
- Internet connectivity is required for reliable direct spreadsheet editing.
- The existing interface is functional but lacks a streamlined transaction-entry experience.

### 1.3 Product Goals

1. Allow users to record expenses or income in approximately 5–10 seconds.
2. Provide a native Pixel-style Android experience.
3. Support full offline transaction entry.
4. Automatically synchronize transactions to Google Sheets when connectivity returns.
5. Display financial information through a clean, readable dashboard.
6. Preserve existing spreadsheet formulas and financial records.
7. Operate without paid backend infrastructure.

### 1.4 Non-Goals — MVP

The following features are excluded from the first release:

- Bank account integration.
- Automatic bank transaction imports.
- AI expense categorization.
- Multi-user support.
- Budget recommendations.
- Investment trading.
- Receipt scanning.
- Google Play Store publishing.
- Cross-platform iOS support.

---

## 2\. Technical Stack

| Layer | Technology |
| --- | --- |
| Programming language | Kotlin |
| UI framework | Jetpack Compose |
| Design system | Material 3 Expressive |
| Architecture | MVVM + Repository |
| Dependency injection | Hilt |
| Local database | Room (SQLite) |
| Background synchronization | WorkManager |
| Preferences | DataStore |
| Networking | Retrofit / OkHttp or Google API client |
| Authentication | Google OAuth 2.0 |
| Cloud storage | Existing Google Sheets |
| IDE | Android Studio |
| Development assistance | Codex |
| Build format | Android APK |

### 2.1 Architecture

``` text
Android Application
│
├── UI Layer
│   ├── Home Screen
│   ├── Dashboard Screen
│   └── Settings / Sync Controls
│
├── ViewModel Layer
│   ├── TransactionViewModel
│   └── DashboardViewModel
│
├── Repository Layer
│   ├── TransactionRepository
│   ├── DashboardRepository
│   └── SyncRepository
│
├── Local Storage
│   ├── Room Database
│   └── DataStore
│
└── Synchronization Layer
    ├── WorkManager
    ├── Google OAuth
    └── Google Sheets API
            │
            ▼
     Money Tracker 2026
```

### 2.2 Backend Decision

For the MVP, prefer direct Google Sheets API integration using OAuth authorization, provided it can be implemented securely and reliably.

Google Apps Script may be introduced as a server-side synchronization adapter if existing spreadsheet formulas, row insertion, or idempotent writes require more controlled operations.

Do not deploy an anonymously writable Apps Script endpoint.

No Firebase, Supabase, or external database server is required.

---

## 3\. Design Direction

### 3.1 Primary Reference: Minus

Use the open-source Minus Android application as the main reference for visual language and interaction patterns.

Reference repository:

https://github.com/isaacsa51/Minus

Study its:

- Material 3 Expressive implementation.
- Calculator-style transaction entry.
- Dynamic Color support.
- Shape language.
- Navigation patterns.
- Motion and transitions.
- Typography hierarchy.
- Component spacing.

Do not blindly copy the entire application or its branding. Build a distinct product using appropriate reusable patterns and respect source-code license obligations.

### 3.2 Design Principles

**Native First**

Use actual Jetpack Compose Material 3 components. Do not use WebView, HTML, or CSS for the interface.

**Expressive but Functional**

Use rounded shapes, tonal surfaces, and expressive animations while prioritizing clarity and efficient data entry.

**Minimal Interaction**

The Home screen should immediately present the number input. Users should not need to navigate to a separate form before entering an amount.

**Adaptive Appearance**

Support Android Dynamic Color, dark mode, and light mode.

**Responsive Feedback**

Provide visual and haptic feedback for input, saving, synchronization, and errors.

### 3.3 UI Components

Use Material 3 Expressive components where supported by the project's Compose Material 3 version.

Recommended components:

- Large top app bar.
- Single-choice segmented button.
- Filled and tonal icon buttons.
- Floating action button where appropriate.
- Modal bottom sheet.
- Filter chips.
- Navigation bar.
- Cards.
- Snackbars.
- Linear and circular progress indicators.

Use Android edge-to-edge layout with correct system bar insets.

Support predictive back navigation where available.

### 3.4 Typography

Use the native Android Material 3 typography scale.

Amounts should use large, legible typography with tabular numerals.

Examples:

- Primary amount: Display Large.
- Dashboard totals: Headline Medium.
- Section titles: Title Medium.
- Transaction details: Body Medium.
- Supporting labels: Label Medium.

### 3.5 Color

Default: Material You Dynamic Color.

Requirements:

- Derive colors from the Android system where supported.
- Provide fallback Material 3 color schemes.
- Support system light/dark appearance.
- Use semantic indicators for income, expenses, and synchronization states.
- Do not rely on color alone to communicate status.

---

## 4\. Information Architecture

The MVP contains two primary screens.

``` text
Application
│
├── Home
│   ├── Expense / Income
│   ├── Amount Input
│   ├── Category Selection
│   ├── Remark Input
│   └── Save Transaction
│
└── Dashboard
    ├── Monthly Overview
    ├── Income / Expenses
    ├── Net Savings
    ├── Spending Breakdown
    ├── Transaction History
    └── Financial Summary
```

Use a Material 3 Navigation Bar with two destinations:

1. Home
2. Dashboard

A small settings or sync control can be accessed through the top app bar. A third primary navigation destination is unnecessary for the MVP.

---

## 5\. Screen 1 — Home

### 5.1 Purpose

The Home screen is the primary transaction-entry interface.

It should allow users to record income or expenses with minimal interaction.

### 5.2 Layout

Top to bottom:

**Header**

- App title.
- Current date.
- Synchronization status indicator.
- Optional settings icon.

**Transaction Type**

- Segmented button with Expense and Income.
- Expense selected by default.
- Selection changes the available categories.

**Amount Display**

- Large currency amount.
- Default value: RM 0.00.
- Clearly visible decimal places.

**Calculator Keypad**

``` text
        RM 0.00

    [1]   [2]   [3]
    [4]   [5]   [6]
    [7]   [8]   [9]
    [.]   [0]   [⌫]
```

**Primary Action**

- Continue / Save.
- Disabled when the amount is zero or invalid.

### 5.3 Transaction Entry Flow

``` text
Open Application
       ↓
Choose Expense / Income
       ↓
Enter Amount
       ↓
Tap Continue
       ↓
Category + Remark Bottom Sheet
       ↓
Tap Save
       ↓
Save to Room Database
       ↓
Show Success Feedback
       ↓
Queue Google Sheets Sync
```

### 5.4 Category and Remark Bottom Sheet

The bottom sheet contains:

- Entered amount summary.
- Expense or Income indicator.
- Category selection.
- Optional remark.
- Transaction date (defaults to today).
- Save button.

Categories should initially be loaded from the existing Google Sheet.

Examples from the current data include:

Expense:

- Groceries \& Food
- Transportation
- Utilities \& Bills
- For Weiwei

Income:

- Salary
- Family Allowance

The full category list must be extracted from the spreadsheet during implementation. Do not treat these examples as exhaustive.

### 5.5 Amount Input Rules

- Currency: MYR.
- Maximum two decimal places.
- No negative amounts.
- Amount must be greater than zero.
- Display thousands separators.
- Prevent malformed decimal input.
- Use integer sen for monetary calculations.
- Preserve the entered amount when opening or dismissing the bottom sheet.

### 5.6 Save Behavior

When the user taps Save:

1. Validate the transaction.
2. Generate a unique transaction ID.
3. Save the transaction to Room.
4. Mark its sync status as PENDING.
5. Show immediate success feedback.
6. Reset the keypad.
7. Schedule background synchronization.

Saving locally must not depend on internet connectivity.

### 5.7 Success Feedback

Use a short animated confirmation and subtle haptic feedback.

Display:

"Expense saved"

If offline:

"Saved offline · Waiting to sync"

The user must never be told that a transaction was synchronized until the remote write has been confirmed.

---

## 6\. Screen 2 — Dashboard

### 6.1 Purpose

Provide a clear overview of financial activity and the data currently maintained in Google Sheets.

The dashboard should use cached local data and remain usable offline.

### 6.2 Dashboard Sections

**A. Monthly Overview**

Display:

- Total income.
- Total expenses.
- Net savings.
- Savings rate.

Default to the current month.

Allow navigation between available months.

**B. Spending Breakdown**

Display expense distribution by category.

Recommended visualization:

- Donut chart or horizontal bars.
- Category labels.
- Total amounts.
- Percentage of monthly expenses.

**C. Income vs Expenses**

Provide a monthly comparison chart.

Show historical trends using existing spreadsheet records.

**D. Recent Transactions**

Display a list of recent transactions.

Each row contains:

- Category.
- Remark or description.
- Date.
- Amount.
- Income/expense indicator.
- Synchronization status when relevant.

Allow tapping a transaction to view its details.

**E. Financial Overview**

Read-only summary of:

- Net worth.
- Emergency fund balance.
- Emergency fund runway.
- Savings progress.

These figures should initially be sourced from the existing spreadsheet.

### 6.3 Dashboard Behavior

- Default period: Current month.
- Display the latest locally available values immediately.
- Refresh from Google Sheets when connected.
- Show when data was last synchronized.
- Use skeleton loading only when no cached data exists.
- Handle empty months gracefully.

### 6.4 MVP Dashboard Scope

Prioritize the following sections for the first usable build:

1. Monthly income, expenses, and savings.
2. Category breakdown.
3. Recent transaction history.
4. Month selection.

Net worth and emergency fund cards can be implemented after the primary transaction flow and synchronization are stable.

---

## 7\. Existing Google Sheets Integration

### 7.1 Spreadsheet

**Name:** Money Tracker 2026

**Spreadsheet ID:**

`18EqxFXErZ6c4HVcvV5UZ4W8b3quUcdBaBXPcHE2bP5A`

Existing tabs:

- Notes
- Dashboard
- Net Worth
- Emergency Fund
- Sep 2026(Partial)
- Oct 2026
- Nov 2026
- Dec 2026

The spreadsheet uses the Asia/Kuala\_Lumpur timezone.

### 7.2 Monthly Ledger Structure

The October and November monthly sheets contain a transaction ledger beginning at row 9.

| Column | Field |
| --- | --- |
| B | Date |
| C | Type |
| D | Category |
| E | Context / Description |
| F | Amount |
| G | Notes |

Row 9 contains the ledger headers; transaction data starts at row 10.

The app must preserve this column mapping unless the schema is deliberately migrated.

### 7.3 Data Mapping

| Android field | Google Sheets field |
| --- | --- |
| Date | Date |
| Expense / Income | Type |
| Category | Category |
| Remark | Context / Description |
| Amount | Amount |
| Optional notes | Notes |

### 7.4 Existing Spreadsheet Issues

The current workbook requires a compatibility audit before enabling writes.

**Date consistency**

Existing entries include short dates such as `1/11`, full date-like strings, and blank dates on subsequent rows.

The app must not assume that every legacy date is already normalized.

Use ISO 8601 dates internally and write dates as actual Google Sheets date values using a consistent format.

Do not reinterpret ambiguous legacy dates without a documented migration rule.

**Currency formatting**

The current dashboard and ledgers display dollar symbols, while the Notes tab contains RM allocations.

Confirm that the workbook represents MYR before standardizing the display and formatting.

Do not perform currency conversion based only on the displayed symbol.

**Formula preservation**

Monthly sheets contain summary formulas.

New transaction writes must not overwrite:

- Existing transactions.
- Summary cells.
- Charts.
- Formula ranges.
- Formatting.
- Validation rules.

Audit whether summary formulas automatically include appended rows. Extend ranges or use dynamic formulas when necessary.

**Future months**

The application must support new months beyond December 2026.

Define a safe monthly-sheet creation process using a template that preserves formulas and formatting.

Do not silently write January transactions into December's ledger.

### 7.5 Transaction IDs

The existing ledger does not expose a dedicated transaction ID field.

Introduce a unique transaction ID column in the monthly ledger or a separate synchronization metadata sheet.

Recommended fields:

- Transaction ID.
- Created timestamp.
- Updated timestamp.
- Revision or version.

Transaction IDs must remain stable across synchronization retries.

Before writing, verify whether the ID already exists remotely.

### 7.6 Read and Write Operations

The synchronization integration must support:

- Read spreadsheet metadata.
- Read monthly transactions.
- Read dashboard summaries.
- Read category values.
- Insert new transactions.
- Update app-created transactions.
- Detect duplicate IDs.
- Retrieve remote changes.

All writes must use the authenticated user's authorized Google account.

---

## 8\. Offline-First Database

### 8.1 Local Storage

Use Room as the application's primary operational database.

The UI should read from Room rather than waiting for network responses.

### 8.2 Transaction Entity

``` kotlin
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String,

    val type: TransactionType,
    val amountMinor: Long,
    val currency: String = "MYR",

    val category: String,
    val remark: String?,

    val transactionDate: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,

    val syncStatus: SyncStatus,
    val remoteRevision: String?
)
```

Use appropriate Room type converters for enums, dates, and timestamps.

### 8.3 Sync Status

Supported states:

- PENDING — Awaiting upload.
- SYNCING — Upload in progress.
- SYNCED — Confirmed in Google Sheets.
- FAILED — Retryable synchronization error.
- CONFLICT — Local and remote versions differ.

### 8.4 Offline Behavior

The application must support:

- Creating transactions offline.
- Browsing cached transaction history.
- Viewing cached dashboard data.
- Restarting the application without losing unsynced data.
- Retrying failed uploads.
- Displaying pending synchronization status.

Never delete unsynced local records automatically.

---

## 9\. Synchronization System

### 9.1 Synchronization Strategy

Use WorkManager for reliable background synchronization.

Recommended triggers:

- Internet connectivity becomes available.
- Application starts.
- New transaction is saved.
- User requests manual synchronization.
- Periodic background refresh.

Use network constraints and exponential backoff for retryable failures.

Android may delay background jobs because of system power-management rules. Synchronization should therefore be eventual, not guaranteed to happen immediately.

### 9.2 Synchronization Flow

``` text
Local Transaction Created
          ↓
Save to Room
          ↓
Mark PENDING
          ↓
WorkManager Scheduled
          ↓
Internet Available?
     ├── No → Keep Pending
     └── Yes
          ↓
Authenticate
          ↓
Check Remote Transaction ID
          ↓
Write or Reconcile
          ↓
Verify Remote Result
          ↓
Mark SYNCED
          ↓
Refresh Local Dashboard
```

### 9.3 Duplicate Prevention

Every transaction must have a stable UUID.

Retries must reuse the same UUID.

A network timeout after a successful remote write must not create a second transaction.

For robust behavior, use an idempotent server-side operation or equivalent reconciliation mechanism. A simple client-side read-then-append operation is not sufficient to guarantee atomic duplicate prevention.

### 9.4 Conflict Handling

The MVP must define behavior for edits made directly in Google Sheets.

Rules:

- New local transactions are uploaded.
- Existing remote transactions are imported.
- App-created transactions are matched using UUID.
- Conflicting edits must not silently overwrite one another.
- Conflicts should be flagged for resolution.

For legacy spreadsheet records without IDs, import them using stable identifiers and avoid duplicate creation during repeated imports.

### 9.5 Manual Synchronization

Provide a Sync Now action accessible from the top app bar or settings.

Display:

- Last successful sync time.
- Pending transaction count.
- Current sync state.
- Error information when synchronization fails.

---

## 10\. Authentication and Security

### 10.1 Google Authentication

Use Google OAuth 2.0 with the minimum required Sheets permissions.

Requirements:

- User signs in with the Google account that owns or can edit the spreadsheet.
- Access tokens are handled using supported Android authentication mechanisms.
- Never embed service account private keys in the APK.
- Never store OAuth client secrets in source code.
- Do not expose financial data through public endpoints.

### 10.2 Local Security

- Store sensitive tokens using supported secure storage.
- Keep financial data in application-private storage.
- Exclude sensitive data from debug logs.
- Use HTTPS for all remote requests.
- Prevent accidental disclosure through error messages.
- Consider encrypted local database storage as a future enhancement.

Optional future feature: biometric application lock.

---

## 11\. Transaction History

The Dashboard includes a recent transaction list.

For MVP, provide a detailed history view accessible from the dashboard.

Requirements:

- Sort by transaction date, newest first.
- Filter by month.
- Filter by expense/income.
- Filter by category.
- Display remarks.
- Show pending synchronization indicators.
- Support editing and deleting app-created transactions.

Deletion should use a soft-delete or tombstone strategy until the remote deletion has synchronized.

Do not allow deletion of unsynced records to cause them to reappear after a future refresh.

---

## 12\. Error Handling

| Scenario | Expected behavior |
| --- | --- |
| No internet | Save locally |
| Google Sheets unavailable | Keep transaction pending |
| Authentication expired | Request reauthentication |
| Invalid amount | Disable Save |
| Duplicate transaction | Reconcile using transaction ID |
| Remote write failure | Retry safely |
| Spreadsheet structure changed | Stop affected writes and show error |
| Missing month sheet | Create from verified template or request setup |
| App terminated during sync | Resume safely |
| Conflicting remote edit | Preserve data and flag conflict |

The application must never silently discard financial records.

---

## 13\. Performance Requirements

- Home screen should open quickly using local data.
- Keypad interactions should respond immediately.
- Local transaction saves should normally complete in under 300 ms on the target device.
- Dashboard scrolling should remain smooth.
- Avoid unnecessary Google Sheets API requests.
- Batch synchronization where appropriate.
- Cache categories and historical transactions.
- Minimize background battery consumption.

Use release builds for meaningful performance testing.

---

## 14\. Accessibility

Requirements:

- Respect Android font scaling.
- Maintain sufficient text contrast.
- Provide accessible labels for icon-only controls.
- Support TalkBack.
- Use appropriately sized touch targets.
- Avoid color-only status indicators.
- Respect reduced-motion preferences where supported.

---

## 15\. Development Milestones

### Phase 1 — Project Foundation

- Create native Android project.
- Configure Kotlin and Jetpack Compose.
- Install Material 3 dependencies.
- Configure Dynamic Color.
- Set up navigation.
- Create Room database.
- Establish MVVM architecture.

**Deliverable:** Running Android APK with basic navigation.

### Phase 2 — Home Screen

- Implement expense/income toggle.
- Implement calculator keypad.
- Implement amount validation.
- Build category selection bottom sheet.
- Add remark and date inputs.
- Save transactions to Room.
- Add success feedback and haptics.

**Deliverable:** Fully functional offline transaction entry.

### Phase 3 — Dashboard

- Implement monthly summary cards.
- Implement category breakdown.
- Implement recent transaction list.
- Add month selection.
- Implement transaction history.

**Deliverable:** Offline financial dashboard based on local data.

### Phase 4 — Google Sheets Integration

- Audit spreadsheet formulas and formatting.
- Configure OAuth.
- Implement remote data retrieval.
- Implement transaction mapping.
- Implement safe remote insertion.
- Implement transaction ID handling.
- Implement WorkManager synchronization.
- Implement duplicate prevention and retries.

**Deliverable:** Reliable offline-to-Google-Sheets synchronization.

### Phase 5 — Refinement

- Refine Material 3 Expressive animations.
- Add haptic feedback.
- Test dark and light themes.
- Test Dynamic Color.
- Improve loading and error states.
- Test offline and interrupted synchronization.
- Optimize performance.
- Generate release APK.

**Deliverable:** Installable MVP release.

---

## 16\. Acceptance Criteria

The MVP is considered complete when:

- [ ] The app installs and launches as a native Android APK.
- [ ] The interface uses Jetpack Compose and Material 3.
- [ ] Dynamic Color works on supported Pixel devices.
- [ ] Users can enter income and expenses.
- [ ] Users can select a category and enter a remark.
- [ ] Transactions save immediately without internet.
- [ ] Transactions survive app restarts.
- [ ] Pending transactions synchronize when internet returns.
- [ ] Synchronization retries do not create duplicate transactions.
- [ ] Transactions appear in the correct monthly Google Sheet.
- [ ] Existing spreadsheet formulas remain functional.
- [ ] The dashboard displays accurate monthly totals.
- [ ] Transaction history is accessible.
- [ ] Authentication is secure.
- [ ] The app handles sync failures without data loss.
- [ ] A release APK can be generated and installed.

---

## 17\. Instructions for Codex

Use this PRD as the primary product specification.

### Development Rules

1. Build a real native Android application using Kotlin and Jetpack Compose.
2. Do not implement the UI using WebView, React, HTML, or CSS.
3. Use Material 3 Expressive as the primary design system.
4. Study Minus as an implementation and aesthetic reference.
5. Keep the code modular and maintainable.
6. Implement offline-first behavior from the beginning.
7. Keep Google Sheets integration separate from UI logic.
8. Do not overwrite or restructure existing spreadsheet data without approval.
9. Do not invent spreadsheet categories, formulas, or column positions when they can be inspected.
10. Use the latest stable compatible Android dependencies.
11. Provide unit tests for monetary calculations and synchronization logic.
12. Build and verify the app after each major phase.
13. Document setup steps and required Google Cloud configuration.
14. Do not introduce paid infrastructure without approval.

### First Implementation Task

Begin with Phase 1 and Phase 2.

Build the project foundation and a functional Home screen using local Room storage.

Do not begin writing to the live Google Sheet until the spreadsheet compatibility audit and synchronization strategy have been reviewed.

The first deliverable should be a working APK with a polished Material 3 Expressive transaction-entry experience.

---

## 18\. Future Enhancements

Potential post-MVP features:

- Account-based balance tracking.
- Transfers between accounts.
- Recurring transactions.
- Monthly spending limits.
- Savings goals.
- Emergency fund contributions.
- Net worth visualizations.
- Home screen widgets.
- Quick Settings tile for adding expenses.
- Biometric app lock.
- CSV export.
- Transaction search.
- Custom categories.
- Custom Material You color themes.
- Notifications for unsynchronized transactions.

These features must not delay the MVP.

---

**End of PRD — Version 1.0**