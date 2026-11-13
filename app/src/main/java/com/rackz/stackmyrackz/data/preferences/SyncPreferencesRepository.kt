package com.rackz.stackmyrackz.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rackz.stackmyrackz.model.DefaultCategories
import com.rackz.stackmyrackz.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

const val DEFAULT_SPREADSHEET_ID = "18EqxFXErZ6c4HVcvV5UZ4W8b3quUcdBaBXPcHE2bP5A"

private val Context.syncPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "racks_sync_prefs"
)

/** Persistent synchronization settings, account state, and discovered sheet categories. */
data class SyncPreferencesState(
    val spreadsheetId: String = DEFAULT_SPREADSHEET_ID,
    val accountEmail: String? = null,
    val manualAccessToken: String? = null,
    val lastSyncedAtEpochMillis: Long? = null,
    val lastSyncError: String? = null,
    val isSyncing: Boolean = false,
    val expenseCategories: List<String> = DefaultCategories.expenseCategories,
    val incomeCategories: List<String> = DefaultCategories.incomeCategories
) {
    fun categoriesFor(type: TransactionType): List<String> = when (type) {
        TransactionType.EXPENSE -> expenseCategories.ifEmpty { DefaultCategories.expenseCategories }
        TransactionType.INCOME -> incomeCategories.ifEmpty { DefaultCategories.incomeCategories }
    }
}

/** Extracts a clean Google Spreadsheet ID from either a bare ID or a full Google Sheets URL. */
fun extractSpreadsheetId(rawInput: String): String {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return DEFAULT_SPREADSHEET_ID
    val urlMatch = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)").find(trimmed)
    return urlMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: trimmed
}

/** Contract for storing and observing Google Sheets synchronization state and categories. */
interface SyncPreferencesRepository {
    val state: Flow<SyncPreferencesState>

    suspend fun getSnapshot(): SyncPreferencesState

    suspend fun setSpreadsheetId(rawInput: String)

    suspend fun setAccountEmail(email: String?)

    suspend fun setManualAccessToken(token: String?)

    suspend fun setSyncInProgress(inProgress: Boolean)

    suspend fun recordSyncSuccess(timestampMillis: Long)

    suspend fun recordSyncError(message: String?)

    suspend fun updateCategories(
        expenseCategories: List<String>,
        incomeCategories: List<String>
    )
}

/** DataStore-backed implementation of [SyncPreferencesRepository]. */
@Singleton
class DataStoreSyncPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncPreferencesRepository {

    private object Keys {
        val SPREADSHEET_ID = stringPreferencesKey("spreadsheet_id")
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val MANUAL_ACCESS_TOKEN = stringPreferencesKey("manual_access_token")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
        val IS_SYNCING = booleanPreferencesKey("is_syncing")
        val EXPENSE_CATEGORIES = stringPreferencesKey("expense_categories_json")
        val INCOME_CATEGORIES = stringPreferencesKey("income_categories_json")
    }

    override val state: Flow<SyncPreferencesState> = context.syncPrefsDataStore.data.map { prefs ->
        SyncPreferencesState(
            spreadsheetId = prefs[Keys.SPREADSHEET_ID]?.takeIf { it.isNotBlank() }
                ?: DEFAULT_SPREADSHEET_ID,
            accountEmail = prefs[Keys.ACCOUNT_EMAIL]?.takeIf { it.isNotBlank() },
            manualAccessToken = prefs[Keys.MANUAL_ACCESS_TOKEN]?.takeIf { it.isNotBlank() },
            lastSyncedAtEpochMillis = prefs[Keys.LAST_SYNCED_AT],
            lastSyncError = prefs[Keys.LAST_SYNC_ERROR]?.takeIf { it.isNotBlank() },
            isSyncing = prefs[Keys.IS_SYNCING] ?: false,
            expenseCategories = decodeStringList(
                prefs[Keys.EXPENSE_CATEGORIES],
                DefaultCategories.expenseCategories
            ),
            incomeCategories = decodeStringList(
                prefs[Keys.INCOME_CATEGORIES],
                DefaultCategories.incomeCategories
            )
        )
    }

    override suspend fun getSnapshot(): SyncPreferencesState = state.first()

    override suspend fun setSpreadsheetId(rawInput: String) {
        val cleanId = extractSpreadsheetId(rawInput)
        context.syncPrefsDataStore.edit { prefs ->
            prefs[Keys.SPREADSHEET_ID] = cleanId
        }
    }

    override suspend fun setAccountEmail(email: String?) {
        val clean = email?.trim()?.takeIf { it.isNotEmpty() }
        context.syncPrefsDataStore.edit { prefs ->
            if (clean == null) {
                prefs.remove(Keys.ACCOUNT_EMAIL)
            } else {
                prefs[Keys.ACCOUNT_EMAIL] = clean
            }
            prefs.remove(Keys.LAST_SYNC_ERROR)
        }
    }

    override suspend fun setManualAccessToken(token: String?) {
        val clean = token?.trim()?.takeIf { it.isNotEmpty() }
        context.syncPrefsDataStore.edit { prefs ->
            if (clean == null) {
                prefs.remove(Keys.MANUAL_ACCESS_TOKEN)
            } else {
                prefs[Keys.MANUAL_ACCESS_TOKEN] = clean
            }
            prefs.remove(Keys.LAST_SYNC_ERROR)
        }
    }

    override suspend fun setSyncInProgress(inProgress: Boolean) {
        context.syncPrefsDataStore.edit { prefs ->
            prefs[Keys.IS_SYNCING] = inProgress
        }
    }

    override suspend fun recordSyncSuccess(timestampMillis: Long) {
        context.syncPrefsDataStore.edit { prefs ->
            prefs[Keys.IS_SYNCING] = false
            prefs[Keys.LAST_SYNCED_AT] = timestampMillis
            prefs.remove(Keys.LAST_SYNC_ERROR)
        }
    }

    override suspend fun recordSyncError(message: String?) {
        val clean = message?.trim()?.takeIf { it.isNotEmpty() }
        context.syncPrefsDataStore.edit { prefs ->
            prefs[Keys.IS_SYNCING] = false
            if (clean == null) {
                prefs.remove(Keys.LAST_SYNC_ERROR)
            } else {
                prefs[Keys.LAST_SYNC_ERROR] = clean
            }
        }
    }

    override suspend fun updateCategories(
        expenseCategories: List<String>,
        incomeCategories: List<String>
    ) {
        val mergedExpense = mergeOrdered(expenseCategories, DefaultCategories.expenseCategories)
        val mergedIncome = mergeOrdered(incomeCategories, DefaultCategories.incomeCategories)
        context.syncPrefsDataStore.edit { prefs ->
            prefs[Keys.EXPENSE_CATEGORIES] = encodeStringList(mergedExpense)
            prefs[Keys.INCOME_CATEGORIES] = encodeStringList(mergedIncome)
        }
    }

    private fun encodeStringList(items: List<String>): String {
        val array = JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    private fun decodeStringList(raw: String?, fallback: List<String>): List<String> {
        if (raw.isNullOrBlank()) return fallback
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optString(i).trim()
                if (item.isNotEmpty()) result.add(item)
            }
            result.ifEmpty { fallback }
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun mergeOrdered(discovered: List<String>, defaults: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        discovered.map { it.trim() }.filter { it.isNotEmpty() }.forEach { seen.add(it) }
        defaults.forEach { seen.add(it) }
        return seen.toList()
    }
}

/** Lightweight in-memory implementation of [SyncPreferencesRepository] for unit tests and previews. */
class InMemorySyncPreferencesRepository(
    initialState: SyncPreferencesState = SyncPreferencesState()
) : SyncPreferencesRepository {

    private val _state = MutableStateFlow(initialState)
    override val state: Flow<SyncPreferencesState> = _state.asStateFlow()

    override suspend fun getSnapshot(): SyncPreferencesState = _state.value

    override suspend fun setSpreadsheetId(rawInput: String) {
        _state.update { it.copy(spreadsheetId = extractSpreadsheetId(rawInput)) }
    }

    override suspend fun setAccountEmail(email: String?) {
        _state.update {
            it.copy(
                accountEmail = email?.trim()?.takeIf { v -> v.isNotEmpty() },
                lastSyncError = null
            )
        }
    }

    override suspend fun setManualAccessToken(token: String?) {
        _state.update {
            it.copy(
                manualAccessToken = token?.trim()?.takeIf { v -> v.isNotEmpty() },
                lastSyncError = null
            )
        }
    }

    override suspend fun setSyncInProgress(inProgress: Boolean) {
        _state.update { it.copy(isSyncing = inProgress) }
    }

    override suspend fun recordSyncSuccess(timestampMillis: Long) {
        _state.update {
            it.copy(
                isSyncing = false,
                lastSyncedAtEpochMillis = timestampMillis,
                lastSyncError = null
            )
        }
    }

    override suspend fun recordSyncError(message: String?) {
        _state.update {
            it.copy(
                isSyncing = false,
                lastSyncError = message?.trim()?.takeIf { v -> v.isNotEmpty() }
            )
        }
    }

    override suspend fun updateCategories(
        expenseCategories: List<String>,
        incomeCategories: List<String>
    ) {
        _state.update { current ->
            val exp = (expenseCategories + DefaultCategories.expenseCategories)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val inc = (incomeCategories + DefaultCategories.incomeCategories)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            current.copy(expenseCategories = exp, incomeCategories = inc)
        }
    }
}
