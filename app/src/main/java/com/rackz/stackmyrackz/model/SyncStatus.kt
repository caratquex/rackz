package com.rackz.stackmyrackz.model

/** Synchronization state between local Room storage and Google Sheets. */
enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
    CONFLICT
}
