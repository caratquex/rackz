package com.rackz.stackmyrackz.data.remote

import com.rackz.stackmyrackz.data.sync.SheetLedgerMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Metadata for a single worksheet tab inside a Google Spreadsheet. */
data class SheetTabInfo(
    val sheetId: Int,
    val title: String,
    val index: Int
)

/** Top-level metadata for a Google Spreadsheet returned by the Sheets v4 API. */
data class SpreadsheetMetadata(
    val spreadsheetId: String,
    val title: String,
    val sheets: List<SheetTabInfo>
)

/** Exception representing an HTTP or API failure from Google Sheets API v4. */
class SheetsApiException(
    val statusCode: Int,
    message: String,
    val retryable: Boolean
) : Exception(message)

/** Abstraction for remote Google Sheets v4 ledger operations. */
interface SheetsRemoteDataSource {
    suspend fun getSpreadsheetMetadata(
        spreadsheetId: String,
        accessToken: String
    ): SpreadsheetMetadata

    suspend fun getLedgerRows(
        spreadsheetId: String,
        tabTitle: String,
        accessToken: String
    ): List<List<String>>

    suspend fun updateLedgerRow(
        spreadsheetId: String,
        tabTitle: String,
        rowNumber: Int,
        rowValues: List<String>,
        accessToken: String
    )

    suspend fun clearLedgerRow(
        spreadsheetId: String,
        tabTitle: String,
        rowNumber: Int,
        accessToken: String
    )

    suspend fun duplicateTemplateTabForMonth(
        spreadsheetId: String,
        sourceSheetId: Int,
        newTabTitle: String,
        accessToken: String
    )

    suspend fun ensureWealthTabExists(
        spreadsheetId: String,
        accessToken: String
    ) {}

    suspend fun getWealthRows(
        spreadsheetId: String,
        accessToken: String
    ): List<List<String>> = emptyList()

    suspend fun updateWealthRows(
        spreadsheetId: String,
        rows: List<List<String>>,
        accessToken: String
    ) {}
}

/** OkHttp-backed implementation of [SheetsRemoteDataSource] using Google Sheets REST API v4. */
@Singleton
class OkHttpSheetsApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) : SheetsRemoteDataSource {

    override suspend fun getSpreadsheetMetadata(
        spreadsheetId: String,
        accessToken: String
    ): SpreadsheetMetadata {
        val url =
            "$BASE_URL/${encodePathSegment(spreadsheetId)}?fields=spreadsheetId,properties.title,sheets.properties(sheetId,title,index)"
        val json = executeJsonRequest(
            Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $accessToken")
                .build()
        )
        val properties = json.optJSONObject("properties")
        val title = properties?.optString("title").orEmpty()
        val sheetsArray = json.optJSONArray("sheets") ?: JSONArray()
        val tabs = buildList {
            for (i in 0 until sheetsArray.length()) {
                val props = sheetsArray.optJSONObject(i)?.optJSONObject("properties") ?: continue
                add(
                    SheetTabInfo(
                        sheetId = props.optInt("sheetId"),
                        title = props.optString("title"),
                        index = props.optInt("index")
                    )
                )
            }
        }
        return SpreadsheetMetadata(
            spreadsheetId = json.optString("spreadsheetId", spreadsheetId),
            title = title,
            sheets = tabs
        )
    }

    override suspend fun getLedgerRows(
        spreadsheetId: String,
        tabTitle: String,
        accessToken: String
    ): List<List<String>> {
        val range = formatLedgerRange(
            tabTitle = tabTitle,
            startRow = SheetLedgerMapper.FIRST_LEDGER_ROW,
            endRow = SheetLedgerMapper.MAX_LEDGER_ROW
        )
        val url =
            "$BASE_URL/${encodePathSegment(spreadsheetId)}/values/${encodePathSegment(range)}?valueRenderOption=UNFORMATTED_VALUE"
        val json = executeJsonRequest(
            Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $accessToken")
                .build()
        )
        val values = json.optJSONArray("values") ?: return emptyList()
        return buildList(values.length()) {
            for (r in 0 until values.length()) {
                val rowArray = values.optJSONArray(r) ?: JSONArray()
                val cells = buildList(rowArray.length()) {
                    for (c in 0 until rowArray.length()) {
                        val raw = rowArray.opt(c)
                        add(
                            if (raw == null || raw == JSONObject.NULL) {
                                ""
                            } else {
                                raw.toString()
                            }
                        )
                    }
                }
                add(cells)
            }
        }
    }

    override suspend fun updateLedgerRow(
        spreadsheetId: String,
        tabTitle: String,
        rowNumber: Int,
        rowValues: List<String>,
        accessToken: String
    ) {
        val range = formatLedgerRange(tabTitle = tabTitle, startRow = rowNumber, endRow = rowNumber)
        val url =
            "$BASE_URL/${encodePathSegment(spreadsheetId)}/values/${encodePathSegment(range)}?valueInputOption=USER_ENTERED"

        val rowArray = JSONArray()
        rowValues.forEach { rowArray.put(it) }
        val payload = JSONObject().apply {
            put("range", range)
            put("majorDimension", "ROWS")
            put("values", JSONArray().put(rowArray))
        }

        executeJsonRequest(
            Request.Builder()
                .url(url)
                .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $accessToken")
                .build()
        )
    }

    override suspend fun clearLedgerRow(
        spreadsheetId: String,
        tabTitle: String,
        rowNumber: Int,
        accessToken: String
    ) {
        val range = formatLedgerRange(tabTitle = tabTitle, startRow = rowNumber, endRow = rowNumber)
        clearRange(spreadsheetId, range, accessToken)
    }

    override suspend fun duplicateTemplateTabForMonth(
        spreadsheetId: String,
        sourceSheetId: Int,
        newTabTitle: String,
        accessToken: String
    ) {
        val url = "$BASE_URL/${encodePathSegment(spreadsheetId)}:batchUpdate"
        val duplicateRequest = JSONObject().apply {
            put(
                "duplicateSheet",
                JSONObject().apply {
                    put("sourceSheetId", sourceSheetId)
                    put("newSheetName", newTabTitle)
                }
            )
        }
        val payload = JSONObject().apply {
            put("requests", JSONArray().put(duplicateRequest))
        }

        executeJsonRequest(
            Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $accessToken")
                .build()
        )

        val clearTargetRange = formatLedgerRange(
            tabTitle = newTabTitle,
            startRow = SheetLedgerMapper.FIRST_LEDGER_ROW,
            endRow = SheetLedgerMapper.MAX_LEDGER_ROW
        )
        clearRange(spreadsheetId, clearTargetRange, accessToken)
    }

    override suspend fun ensureWealthTabExists(
        spreadsheetId: String,
        accessToken: String
    ) {
        val metadata = getSpreadsheetMetadata(spreadsheetId, accessToken)
        val hasWealthTab = metadata.sheets.any { it.title.equals(WEALTH_TAB_TITLE, ignoreCase = true) }
        if (!hasWealthTab) {
            val url = "$BASE_URL/${encodePathSegment(spreadsheetId)}:batchUpdate"
            val addSheetRequest = JSONObject().apply {
                put(
                    "addSheet",
                    JSONObject().apply {
                        put(
                            "properties",
                            JSONObject().apply {
                                put("title", WEALTH_TAB_TITLE)
                            }
                        )
                    }
                )
            }
            val payload = JSONObject().apply {
                put("requests", JSONArray().put(addSheetRequest))
            }
            executeJsonRequest(
                Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("Authorization", "Bearer $accessToken")
                    .build()
            )
            writeRangeValues(
                spreadsheetId = spreadsheetId,
                range = "'$WEALTH_TAB_TITLE'!A1:G1",
                rows = listOf(WEALTH_HEADERS),
                accessToken = accessToken
            )
        }
    }

    override suspend fun getWealthRows(
        spreadsheetId: String,
        accessToken: String
    ): List<List<String>> {
        val range = "'$WEALTH_TAB_TITLE'!A2:G200"
        val url =
            "$BASE_URL/${encodePathSegment(spreadsheetId)}/values/${encodePathSegment(range)}?valueRenderOption=UNFORMATTED_VALUE"
        val json = executeJsonRequest(
            Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $accessToken")
                .build()
        )
        val values = json.optJSONArray("values") ?: return emptyList()
        return buildList(values.length()) {
            for (r in 0 until values.length()) {
                val rowArray = values.optJSONArray(r) ?: JSONArray()
                val cells = buildList(rowArray.length()) {
                    for (c in 0 until rowArray.length()) {
                        val raw = rowArray.opt(c)
                        add(
                            if (raw == null || raw == JSONObject.NULL) {
                                ""
                            } else {
                                raw.toString()
                            }
                        )
                    }
                }
                add(cells)
            }
        }
    }

    override suspend fun updateWealthRows(
        spreadsheetId: String,
        rows: List<List<String>>,
        accessToken: String
    ) {
        clearRange(spreadsheetId, "'$WEALTH_TAB_TITLE'!A2:G200", accessToken)
        val allRows = buildList(rows.size + 1) {
            add(WEALTH_HEADERS)
            addAll(rows)
        }
        val endRow = allRows.size.coerceAtLeast(1)
        writeRangeValues(
            spreadsheetId = spreadsheetId,
            range = "'$WEALTH_TAB_TITLE'!A1:G$endRow",
            rows = allRows,
            accessToken = accessToken
        )
    }

    private suspend fun writeRangeValues(
        spreadsheetId: String,
        range: String,
        rows: List<List<String>>,
        accessToken: String
    ) {
        val url =
            "$BASE_URL/${encodePathSegment(spreadsheetId)}/values/${encodePathSegment(range)}?valueInputOption=USER_ENTERED"
        val outerArray = JSONArray()
        rows.forEach { row ->
            val innerArray = JSONArray()
            row.forEach { innerArray.put(it) }
            outerArray.put(innerArray)
        }
        val payload = JSONObject().apply {
            put("range", range)
            put("majorDimension", "ROWS")
            put("values", outerArray)
        }
        executeJsonRequest(
            Request.Builder()
                .url(url)
                .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $accessToken")
                .build()
        )
    }

    private suspend fun clearRange(
        spreadsheetId: String,
        range: String,
        accessToken: String
    ) {
        val url =
            "$BASE_URL/${encodePathSegment(spreadsheetId)}/values/${encodePathSegment(range)}:clear"
        executeJsonRequest(
            Request.Builder()
                .url(url)
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $accessToken")
                .build()
        )
    }

    private suspend fun executeJsonRequest(request: Request): JSONObject =
        withContext(Dispatchers.IO) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val apiMessage = extractApiErrorMessage(bodyText)
                            ?: "HTTP ${response.code}"
                        val retryable = response.code == 429 || response.code >= 500
                        throw SheetsApiException(
                            statusCode = response.code,
                            message = apiMessage,
                            retryable = retryable
                        )
                    }
                    if (bodyText.isBlank()) JSONObject() else JSONObject(bodyText)
                }
            } catch (apiError: SheetsApiException) {
                throw apiError
            } catch (ioError: IOException) {
                throw SheetsApiException(
                    statusCode = -1,
                    message = ioError.message ?: "Network error",
                    retryable = true
                )
            }
        }

    private fun extractApiErrorMessage(bodyText: String): String? {
        if (bodyText.isBlank()) return null
        return runCatching {
            JSONObject(bodyText)
                .optJSONObject("error")
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun formatLedgerRange(tabTitle: String, startRow: Int, endRow: Int): String {
        val escapedTab = tabTitle.replace("'", "''")
        return "'$escapedTab'!B$startRow:J$endRow"
    }

    private fun encodePathSegment(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private companion object {
        const val BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"
        const val WEALTH_TAB_TITLE = "Wealth"
        val WEALTH_HEADERS = listOf(
            "Month",
            "Savings (RM)",
            "Savings Target (RM)",
            "Investment (RM)",
            "Investment Target (RM)",
            "Note",
            "UpdatedAt"
        )
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
