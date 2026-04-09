package cd.h360.pos

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.widget.RemoteViews
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object H360WidgetUpdater {
    private const val TAG = "H360WidgetUpdater"
    const val ACTION_WIDGET_REALTIME_TICK = "cd.h360.pos.ACTION_WIDGET_REALTIME_TICK"
    const val PREFS_NAME = "h360_widget_prefs"
    const val KEY_ROLE = "role"
    const val KEY_OFFLINE_PENDING = "offline_pending"
    const val KEY_LAST_SYNC = "last_sync"
    const val KEY_LAST_PAGE = "last_page"
    const val KEY_ONLINE_STATUS = "online_status"
    const val KEY_APP_OPENS = "app_opens"
    const val KEY_LAST_UPDATE = "last_update"
    const val KEY_CAPABILITIES = "capabilities"
    const val KEY_ENABLED_MODULES = "enabled_modules"
    const val KEY_SALES_TODAY = "sales_today"
    const val KEY_TICKETS_TODAY = "tickets_today"
    const val KEY_AVG_TICKET = "avg_ticket"
    const val KEY_SALES_CHANGE_PCT = "sales_change_pct"
    const val KEY_SALES_CHANGE_WEEK_PCT = "sales_change_week_pct"
    const val KEY_AVG_TICKET_CHANGE_PCT = "avg_ticket_change_pct"
    const val KEY_LAST_SALE_MINUTES = "last_sale_minutes"
    const val KEY_TOP_PRODUCT_NAME = "top_product_name"
    const val KEY_TOP_PRODUCT_QTY = "top_product_qty"
    const val KEY_TOP_PRODUCT_CHANGE_PCT = "top_product_change_pct"
    const val KEY_TREND_HINT = "trend_hint"
    const val KEY_SUGGESTED_ACTION = "suggested_action"
    const val KEY_LOW_STOCK = "low_stock"
    const val KEY_STOCK_MISMATCH = "stock_mismatch"
    const val KEY_COPILOT_LAST_PROMPT = "copilot_last_prompt"
    const val KEY_COPILOT_LAST_RESPONSE = "copilot_last_response"
    const val KEY_SALES_TREND = "sales_trend"
    const val KEY_SALES_SERIES = "sales_series"
    const val KEY_EXPENSE_TODAY = "expense_today"
    const val KEY_PROFIT_TODAY = "profit_today"
    const val KEY_OVERDUE_INVOICES = "overdue_invoices"
    const val KEY_COLLECTION_RATE = "collection_rate"
    const val KEY_CURRENCY_SYMBOL = "currency_symbol"
    const val KEY_SALES_YESTERDAY = "sales_yesterday"
    const val KEY_SALES_VS_YESTERDAY_PCT = "sales_vs_yesterday_pct"
    const val KEY_INSIGHTS_PERIOD_LABEL = "insights_period_label"
    const val KEY_INSIGHTS_ADVICE_TITLE = "insights_advice_title"
    const val KEY_INSIGHTS_ADVICE_MESSAGE = "insights_advice_message"
    const val KEY_FILTER_DATE_FROM = "filter_date_from"
    const val KEY_FILTER_DATE_TO = "filter_date_to"
    const val KEY_FILTER_LOCATION_ID = "filter_location_id"
    const val KEY_FILTER_LOCATION_NAME = "filter_location_name"
    const val KEY_FILTER_LOCATION_OPTIONS_JSON = "filter_location_options_json"
    const val KEY_LAST_REMOTE_FETCH_MS = "last_remote_fetch_ms"
    private const val KEY_REMOTE_REFRESH_INTERVAL_SEC = "remote_refresh_interval_sec"
    private const val KEY_WIDGET_REALTIME_INTERVAL_SEC = "widget_realtime_interval_sec"
    const val KEY_REMOTE_SYNC_STATE = "remote_sync_state"
    const val KEY_REMOTE_SYNC_MESSAGE = "remote_sync_message"
    const val KEY_REMOTE_DIAG_LABEL = "remote_diag_label"
    const val KEY_REMOTE_DIAG_DETAIL = "remote_diag_detail"
    const val KEY_BACKEND_NOTIFICATIONS_JSON = "backend_notifications_json"
    const val KEY_BACKEND_NOTIFICATIONS_UNREAD = "backend_notifications_unread"
    const val KEY_ADVICE_ENABLED = "advice_enabled"
    private const val DEFAULT_REMOTE_REFRESH_INTERVAL_SEC = 300
    private const val DEFAULT_WIDGET_REALTIME_INTERVAL_SEC = 60

    private const val MODULE_SALES = "sales"
    private const val MODULE_STOCK = "stock"
    private const val MODULE_OFFLINE = "offline"
    private const val MODULE_ACTIVITY = "activity"
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val remoteFetchInProgress = AtomicBoolean(false)

    data class LocationOption(
        val id: Int,
        val name: String
    )

    data class BackendNotificationEntry(
        val key: String,
        val title: String,
        val message: String,
        val deepLink: String?
    )

    fun rememberRole(context: Context, role: String) {
        prefs(context).edit().putString(KEY_ROLE, role.ifBlank { "guest" }).apply()
    }

    fun rememberOfflinePending(context: Context, pending: Int) {
        prefs(context).edit().putInt(KEY_OFFLINE_PENDING, pending.coerceAtLeast(0)).apply()
    }

    fun rememberLastSync(context: Context, lastSync: String) {
        val value = lastSync.ifBlank { context.getString(R.string.widget_not_synced) }
        prefs(context).edit().putString(KEY_LAST_SYNC, value).apply()
    }

    fun rememberLastPage(context: Context, page: String) {
        val value = page.ifBlank { context.getString(R.string.widget_page_unknown) }
        prefs(context).edit().putString(KEY_LAST_PAGE, value).apply()
    }

    fun rememberOnline(context: Context, online: Boolean) {
        prefs(context).edit().putString(KEY_ONLINE_STATUS, if (online) "Online" else "Offline").apply()
    }

    fun incrementAppOpens(context: Context) {
        val p = prefs(context)
        val current = p.getInt(KEY_APP_OPENS, 0)
        p.edit().putInt(KEY_APP_OPENS, current + 1).apply()
    }

    fun rememberLastUpdate(context: Context, timestamp: String) {
        prefs(context).edit().putString(KEY_LAST_UPDATE, timestamp).apply()
    }

    fun rememberCapabilities(context: Context, capabilities: Set<String>) {
        prefs(context).edit().putString(KEY_CAPABILITIES, capabilities.joinToString(",")).apply()
    }

    fun rememberEnabledModules(context: Context, modules: Set<String>) {
        prefs(context).edit().putString(KEY_ENABLED_MODULES, modules.joinToString(",")).apply()
    }

    fun rememberAdviceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADVICE_ENABLED, enabled).apply()
    }

    fun readAdviceEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ADVICE_ENABLED, true)
    }

    fun rememberWidgetFilters(
        context: Context,
        dateFrom: String,
        dateTo: String,
        locationId: Int,
        locationName: String?
    ) {
        prefs(context).edit()
            .putString(KEY_FILTER_DATE_FROM, dateFrom.ifBlank { todayDateString() })
            .putString(KEY_FILTER_DATE_TO, dateTo.ifBlank { todayDateString() })
            .putInt(KEY_FILTER_LOCATION_ID, locationId.coerceAtLeast(0))
            .putString(KEY_FILTER_LOCATION_NAME, locationName?.ifBlank { null } ?: context.getString(R.string.widget_all_locations))
            .apply()
    }

    fun readFilterDateFrom(context: Context): String {
        return prefs(context).getString(KEY_FILTER_DATE_FROM, todayDateString()).orEmpty().ifBlank { todayDateString() }
    }

    fun readFilterDateTo(context: Context): String {
        return prefs(context).getString(KEY_FILTER_DATE_TO, todayDateString()).orEmpty().ifBlank { todayDateString() }
    }

    fun readFilterLocationId(context: Context): Int {
        return prefs(context).getInt(KEY_FILTER_LOCATION_ID, 0).coerceAtLeast(0)
    }

    fun readFilterLocationName(context: Context): String {
        return prefs(context).getString(KEY_FILTER_LOCATION_NAME, context.getString(R.string.widget_all_locations))
            .orEmpty()
            .ifBlank { context.getString(R.string.widget_all_locations) }
    }

    fun rememberLocationOptions(context: Context, locations: JSONArray?) {
        val serialized = locations?.toString().orEmpty()
        prefs(context).edit().putString(KEY_FILTER_LOCATION_OPTIONS_JSON, serialized).apply()
    }

    fun readLocationOptions(context: Context): List<LocationOption> {
        val fallback = mutableListOf(LocationOption(0, context.getString(R.string.widget_all_locations)))
        val raw = prefs(context).getString(KEY_FILTER_LOCATION_OPTIONS_JSON, "").orEmpty()
        if (raw.isBlank()) return fallback
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf(LocationOption(0, context.getString(R.string.widget_all_locations)))
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val id = row.optInt("id", 0)
                val name = row.optString("name").trim()
                if (id > 0 && name.isNotBlank()) {
                    out.add(LocationOption(id, name))
                }
            }
            out.distinctBy { it.id }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun rememberBackendNotifications(context: Context, unreadTotal: Int, list: JSONArray) {
        prefs(context).edit()
            .putInt(KEY_BACKEND_NOTIFICATIONS_UNREAD, unreadTotal.coerceAtLeast(0))
            .putString(KEY_BACKEND_NOTIFICATIONS_JSON, list.toString())
            .apply()
    }

    private fun readBackendNotificationEntries(context: Context): List<BackendNotificationEntry> {
        val raw = prefs(context).getString(KEY_BACKEND_NOTIFICATIONS_JSON, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<BackendNotificationEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val msg = obj.optString("message").trim()
                if (msg.isBlank()) continue
                val readAt = obj.optString("read_at").trim()
                if (readAt.isNotBlank()) {
                    continue
                }
                val title = obj.optString("title")
                    .ifBlank { obj.optString("subject") }
                    .trim()
                    .ifBlank { "Notification backend" }
                val deepLink = obj.optString("link").trim().ifBlank { null }
                val key = obj.optString("id").trim().ifBlank {
                    "${obj.optString("created_at").trim()}|$title|$msg"
                }
                out.add(
                    BackendNotificationEntry(
                        key = key,
                        title = title,
                        message = msg,
                        deepLink = deepLink
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readBackendUnreadTotal(context: Context): Int {
        return prefs(context).getInt(KEY_BACKEND_NOTIFICATIONS_UNREAD, 0).coerceAtLeast(0)
    }

    fun rememberRealtimeIntervalSec(context: Context, seconds: Int) {
        val safe = seconds.coerceIn(30, 120)
        prefs(context).edit().putInt(KEY_WIDGET_REALTIME_INTERVAL_SEC, safe).apply()
        scheduleRealtimeRefresh(context.applicationContext)
    }

    fun readRealtimeIntervalSec(context: Context): Int {
        return prefs(context)
            .getInt(KEY_WIDGET_REALTIME_INTERVAL_SEC, DEFAULT_WIDGET_REALTIME_INTERVAL_SEC)
            .coerceIn(30, 120)
    }

    fun readEnabledModules(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_ENABLED_MODULES, "").orEmpty()
        return raw.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    }

    fun rememberSalesInsights(context: Context, salesToday: String, ticketsToday: Int, avgTicket: String) {
        prefs(context).edit()
            .putString(KEY_SALES_TODAY, salesToday.ifBlank { "0" })
            .putInt(KEY_TICKETS_TODAY, ticketsToday.coerceAtLeast(0))
            .putString(KEY_AVG_TICKET, avgTicket.ifBlank { "0" })
            .apply()
    }

    fun rememberSalesExtras(
        context: Context,
        changePct: Int?,
        lastSaleMinutes: Int?,
        topProductName: String?,
        topProductQty: Int?
    ) {
        val editor = prefs(context).edit()
        if (changePct != null) {
            editor.putInt(KEY_SALES_CHANGE_PCT, changePct)
        } else {
            editor.remove(KEY_SALES_CHANGE_PCT)
        }
        if (lastSaleMinutes != null) {
            editor.putInt(KEY_LAST_SALE_MINUTES, lastSaleMinutes.coerceAtLeast(0))
        } else {
            editor.remove(KEY_LAST_SALE_MINUTES)
        }
        if (!topProductName.isNullOrBlank()) {
            editor.putString(KEY_TOP_PRODUCT_NAME, topProductName.trim())
            editor.putInt(KEY_TOP_PRODUCT_QTY, topProductQty ?: 0)
        } else {
            editor.remove(KEY_TOP_PRODUCT_NAME)
            editor.remove(KEY_TOP_PRODUCT_QTY)
        }
        editor.apply()
    }

    fun rememberSalesV2Extras(
        context: Context,
        weekChangePct: Int?,
        avgTicketChangePct: Int?,
        topProductChangePct: Int?,
        trendHint: String?,
        suggestedAction: String?
    ) {
        val editor = prefs(context).edit()
        if (weekChangePct != null) editor.putInt(KEY_SALES_CHANGE_WEEK_PCT, weekChangePct) else editor.remove(KEY_SALES_CHANGE_WEEK_PCT)
        if (avgTicketChangePct != null) editor.putInt(KEY_AVG_TICKET_CHANGE_PCT, avgTicketChangePct) else editor.remove(KEY_AVG_TICKET_CHANGE_PCT)
        if (topProductChangePct != null) editor.putInt(KEY_TOP_PRODUCT_CHANGE_PCT, topProductChangePct) else editor.remove(KEY_TOP_PRODUCT_CHANGE_PCT)
        if (!trendHint.isNullOrBlank()) editor.putString(KEY_TREND_HINT, trendHint.trim()) else editor.remove(KEY_TREND_HINT)
        if (!suggestedAction.isNullOrBlank()) editor.putString(KEY_SUGGESTED_ACTION, suggestedAction.trim()) else editor.remove(KEY_SUGGESTED_ACTION)
        editor.apply()
    }

    fun rememberSalesComparison(
        context: Context,
        salesYesterday: String?,
        salesVsYesterdayPct: Int?,
        periodLabel: String?
    ) {
        val editor = prefs(context).edit()
        if (!salesYesterday.isNullOrBlank()) editor.putString(KEY_SALES_YESTERDAY, salesYesterday) else editor.remove(KEY_SALES_YESTERDAY)
        if (salesVsYesterdayPct != null) editor.putInt(KEY_SALES_VS_YESTERDAY_PCT, salesVsYesterdayPct) else editor.remove(KEY_SALES_VS_YESTERDAY_PCT)
        if (!periodLabel.isNullOrBlank()) editor.putString(KEY_INSIGHTS_PERIOD_LABEL, periodLabel.trim()) else editor.remove(KEY_INSIGHTS_PERIOD_LABEL)
        editor.apply()
    }

    fun rememberAdvice(context: Context, title: String?, message: String?) {
        val editor = prefs(context).edit()
        if (!title.isNullOrBlank()) editor.putString(KEY_INSIGHTS_ADVICE_TITLE, title.trim()) else editor.remove(KEY_INSIGHTS_ADVICE_TITLE)
        if (!message.isNullOrBlank()) editor.putString(KEY_INSIGHTS_ADVICE_MESSAGE, message.trim()) else editor.remove(KEY_INSIGHTS_ADVICE_MESSAGE)
        editor.apply()
    }

    fun rememberStockInsights(context: Context, lowStock: Int, stockMismatch: Int) {
        prefs(context).edit()
            .putInt(KEY_LOW_STOCK, lowStock.coerceAtLeast(0))
            .putInt(KEY_STOCK_MISMATCH, stockMismatch.coerceAtLeast(0))
            .apply()
    }

    fun rememberCopilotPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_COPILOT_LAST_PROMPT, prompt.ifBlank { "-" }).apply()
    }

    fun rememberCopilotResponse(context: Context, response: String) {
        prefs(context).edit().putString(KEY_COPILOT_LAST_RESPONSE, response.ifBlank { "-" }).apply()
    }

    fun rememberFinanceInsights(
        context: Context,
        expenseToday: String,
        profitToday: String,
        overdueInvoices: Int,
        collectionRate: String
    ) {
        prefs(context).edit()
            .putString(KEY_EXPENSE_TODAY, expenseToday.ifBlank { "0" })
            .putString(KEY_PROFIT_TODAY, profitToday.ifBlank { "0" })
            .putInt(KEY_OVERDUE_INVOICES, overdueInvoices.coerceAtLeast(0))
            .putString(KEY_COLLECTION_RATE, collectionRate.ifBlank { "0%" })
            .apply()
    }

    fun rememberSalesTrend(context: Context, trend: String) {
        prefs(context).edit().putString(KEY_SALES_TREND, trend.ifBlank { "Stable" }).apply()
    }

    fun rememberSalesSeries(context: Context, series: List<Double>) {
        val serialized = series.joinToString(",") { "%.2f".format(Locale.US, it) }
        prefs(context).edit().putString(KEY_SALES_SERIES, serialized).apply()
    }

    fun rememberCurrencySymbol(context: Context, symbol: String) {
        if (symbol.isBlank()) return
        prefs(context).edit().putString(KEY_CURRENCY_SYMBOL, symbol.trim()).apply()
    }

    fun refreshAllWidgets(context: Context) {
        scheduleRealtimeRefresh(context.applicationContext)
        refreshFromRemoteIfDue(context, force = false)
        renderWidgets(context)
    }

    fun handleRealtimeTick(context: Context) {
        val appContext = context.applicationContext
        refreshFromRemoteIfDue(appContext, force = true)
        renderWidgets(appContext)
        scheduleRealtimeRefresh(appContext)
    }

    fun scheduleRealtimeRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = SystemClock.elapsedRealtime() + (readRealtimeIntervalSec(context) * 1000L)
        val pendingIntent = realtimeTickPendingIntent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to schedule widget realtime refresh", e)
        }
    }

    fun refreshFromRemoteIfDue(context: Context, force: Boolean) {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        val now = System.currentTimeMillis()
        val lastFetch = prefs.getLong(KEY_LAST_REMOTE_FETCH_MS, 0L)
        val intervalSec = prefs.getInt(KEY_REMOTE_REFRESH_INTERVAL_SEC, DEFAULT_REMOTE_REFRESH_INTERVAL_SEC)
            .coerceIn(60, 3600)
        val due = now - lastFetch >= intervalSec * 1000L
        if (!force && !due) {
            return
        }
        if (!remoteFetchInProgress.compareAndSet(false, true)) {
            return
        }

        runCatching { CookieManager.getInstance().flush() }

        networkExecutor.execute {
            try {
                fetchAndStoreRemoteInsights(appContext)
            } catch (e: Exception) {
                rememberRemoteSyncState(appContext, "error", "Sync error: ${e.message ?: "unknown"}")
                Log.w(TAG, "Remote insights sync failed", e)
                renderWidgets(appContext)
            } finally {
                remoteFetchInProgress.set(false)
            }
        }
    }

    private fun renderWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = ComponentName(context, H360WidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
        if (widgetIds.isNotEmpty()) {
            widgetIds.forEach { widgetId ->
                try {
                    val views = buildRemoteViews(context)
                    appWidgetManager.updateAppWidget(widgetId, views)
                } catch (e: Exception) {
                    Log.e(TAG, "Status widget update failed id=$widgetId", e)
                }
            }
        }
        try {
            H360CopilotWidgetProvider.refreshAll(context)
        } catch (e: Exception) {
            Log.e(TAG, "Copilot widget update failed", e)
        }
        try {
            H360InsightsCardsWidgetProvider.refreshAll(context)
        } catch (e: Exception) {
            Log.e(TAG, "Insights widget update failed", e)
        }
        try {
            H360AdviceWidgetProvider.refreshAll(context)
        } catch (e: Exception) {
            Log.e(TAG, "Advice widget update failed", e)
        }
        try {
            H360NotificationDispatcher.updatePersistentBackendNotifications(
                context = context,
                unreadTotal = readBackendUnreadTotal(context),
                entries = readBackendNotificationEntries(context)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Backend center notification update failed", e)
        }
    }

    private fun fetchAndStoreRemoteInsights(context: Context) {
        val urls = candidateInsightsUrls()
        if (urls.isEmpty()) return

        var lastError = "API insights indisponible"
        urls.forEach { url ->
            val result = requestInsights(context, url)
            rememberRemoteDiagnostic(
                context = context,
                state = "attempt",
                httpCode = result.code,
                endpoint = result.endpoint,
                hasCookie = result.hasCookie,
                detail = ""
            )
            when {
                result.code == HttpURLConnection.HTTP_UNAUTHORIZED || result.code == HttpURLConnection.HTTP_FORBIDDEN -> {
                    rememberRemoteSyncState(context, "auth_required", "Session expiree: reconnecte-toi")
                    rememberRemoteDiagnostic(
                        context = context,
                        state = "auth_required",
                        httpCode = result.code,
                        endpoint = result.endpoint,
                        hasCookie = result.hasCookie,
                        detail = "Session invalide"
                    )
                    return
                }
                result.code == -1 -> {
                    val detail = result.body.trim()
                    lastError = if (detail.isNotEmpty()) {
                        "API insights offline: $detail"
                    } else {
                        "API insights offline"
                    }
                    Log.w(TAG, "Insights endpoint failed: $url error=${result.body}")
                }
                result.code == HttpURLConnection.HTTP_NOT_FOUND -> {
                    lastError = "API insights HTTP 404"
                    Log.w(TAG, "Insights endpoint not found: $url")
                }
                result.code !in 200..299 -> {
                    lastError = "API insights HTTP ${result.code}"
                    Log.w(TAG, "Insights endpoint HTTP ${result.code} url=$url body=${result.body}")
                }
                !result.contentType.contains("application/json") -> {
                    lastError = "Session requise pour insights"
                    Log.w(TAG, "Insights endpoint non-JSON url=$url ct=${result.contentType} body=${result.body}")
                }
                result.body.isBlank() -> {
                    lastError = "API insights vide"
                }
                else -> {
                    val json = JSONObject(result.body)
                    if (json.optJSONObject("insights") != null) {
                        applyRemoteInsights(context, json)
                        fetchAndStoreBackendNotifications(context)
                        fetchAndStoreAdvicePreference(context)
                        rememberRemoteDiagnostic(
                            context = context,
                            state = "ok",
                            httpCode = result.code,
                            endpoint = result.endpoint,
                            hasCookie = result.hasCookie,
                            detail = "JSON OK"
                        )
                        return
                    }
                    lastError = "JSON insights invalide"
                }
            }
            rememberRemoteDiagnostic(
                context = context,
                state = "error",
                httpCode = result.code,
                endpoint = result.endpoint,
                hasCookie = result.hasCookie,
                detail = lastError
            )
        }
        rememberRemoteSyncState(context, "error", lastError)
    }

    private fun requestInsights(context: Context, url: String): InsightHttpResult {
        return try {
            val finalUrl = buildInsightsUrl(context, url)
            val conn = (URL(finalUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
            }
            val cookie = collectCookieHeader(finalUrl)
            if (!cookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", cookie)
            }
            val code = conn.responseCode
            val contentType = conn.contentType.orEmpty().lowercase()
            val body = runCatching {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            InsightHttpResult(
                code = code,
                contentType = contentType,
                body = body,
                endpoint = finalUrl,
                hasCookie = !cookie.isNullOrBlank()
            )
        } catch (e: Exception) {
            InsightHttpResult(
                code = -1,
                contentType = "",
                body = e.message.orEmpty(),
                endpoint = url,
                hasCookie = false
            )
        }
    }

    private fun collectCookieHeader(finalUrl: String): String {
        val cm = CookieManager.getInstance()
        val candidates = linkedSetOf<String>()
        candidates.add(finalUrl)
        val base = BuildConfig.WEBVIEW_BASE_URL.trim()
        if (base.isNotBlank()) {
            candidates.add(base)
            val uri = Uri.parse(base)
            val origin = "${uri.scheme}://${uri.host}"
            candidates.add(origin)
        }
        val direct = cm.getCookie(finalUrl).orEmpty().trim()
        if (direct.isNotBlank()) return direct

        val merged = linkedSetOf<String>()
        candidates.forEach { candidate ->
            val cookie = cm.getCookie(candidate).orEmpty()
            if (cookie.isNotBlank()) {
                cookie.split(";")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { merged.add(it) }
            }
        }
        return merged.joinToString("; ")
    }

    private fun buildInsightsUrl(context: Context, baseUrl: String): String {
        val uri = Uri.parse(baseUrl)
        val dateFrom = readFilterDateFrom(context)
        val dateTo = readFilterDateTo(context)
        val locationId = readFilterLocationId(context)
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameters(key).forEach { value ->
                builder.appendQueryParameter(key, value)
            }
        }
        builder.appendQueryParameter("date_from", dateFrom)
        builder.appendQueryParameter("date_to", dateTo)
        if (locationId > 0) {
            builder.appendQueryParameter("location_id", locationId.toString())
        }
        return builder.build().toString()
    }

    private fun candidateInsightsUrls(): List<String> {
        val urls = linkedSetOf<String>()
        val configured = BuildConfig.WIDGET_INSIGHTS_URL.trim()
        if (configured.isNotBlank()) {
            urls.add(configured)
        }
        val origin = runCatching {
            val base = Uri.parse(BuildConfig.WEBVIEW_BASE_URL)
            "${base.scheme}://${base.host}"
        }.getOrNull()
        if (!origin.isNullOrBlank()) {
            urls.add("$origin/h360/widgets/insights")
            urls.add("$origin/home/widgets/insights")
            urls.add("$origin/widgets/insights")
        }
        return urls.toList()
    }

    private fun candidateNotificationUrls(): List<String> {
        val urls = linkedSetOf<String>()
        val configured = BuildConfig.WIDGET_INSIGHTS_URL.trim()
        if (configured.isNotBlank()) {
            urls.add(configured.replace("/widgets/insights", "/widgets/notifications"))
        }
        val origin = runCatching {
            val base = Uri.parse(BuildConfig.WEBVIEW_BASE_URL)
            "${base.scheme}://${base.host}"
        }.getOrNull()
        if (!origin.isNullOrBlank()) {
            urls.add("$origin/h360/widgets/notifications")
            urls.add("$origin/home/widgets/notifications")
            urls.add("$origin/widgets/notifications")
        }
        return urls.toList()
    }

    private fun candidateAdvicePreferenceUrls(): List<String> {
        val urls = linkedSetOf<String>()
        val origin = runCatching {
            val base = Uri.parse(BuildConfig.WEBVIEW_BASE_URL)
            "${base.scheme}://${base.host}"
        }.getOrNull()
        if (!origin.isNullOrBlank()) {
            urls.add("$origin/h360/advice/preferences")
        }
        return urls.toList()
    }

    private fun fetchAndStoreBackendNotifications(context: Context) {
        val urls = candidateNotificationUrls()
        if (urls.isEmpty()) return

        urls.forEach { url ->
            val result = requestInsights(context, url)
            if (result.code !in 200..299) return@forEach
            if (!result.contentType.contains("application/json")) return@forEach
            if (result.body.isBlank()) return@forEach
            runCatching {
                val json = JSONObject(result.body)
                if (json.optString("status") != "ok") return@runCatching
                val unread = json.optInt("unread_total", 0)
                val list = json.optJSONArray("notifications_list") ?: JSONArray()
                rememberBackendNotifications(context, unread, list)
                H360NotificationDispatcher.updatePersistentBackendNotifications(
                    context = context,
                    unreadTotal = unread,
                    entries = readBackendNotificationEntries(context)
                )
                return
            }
        }
    }

    private fun fetchAndStoreAdvicePreference(context: Context) {
        val urls = candidateAdvicePreferenceUrls()
        if (urls.isEmpty()) return

        urls.forEach { url ->
            val result = requestInsights(context, url)
            if (result.code !in 200..299) return@forEach
            if (!result.contentType.contains("application/json")) return@forEach
            if (result.body.isBlank()) return@forEach
            runCatching {
                val json = JSONObject(result.body)
                val pref = json.optJSONObject("preferences") ?: return@runCatching
                rememberAdviceEnabled(context, pref.optBoolean("enabled", true))
                return
            }
        }
    }

    private fun applyRemoteInsights(context: Context, json: JSONObject) {
        val refreshSec = json.optInt("refresh_interval_sec", DEFAULT_REMOTE_REFRESH_INTERVAL_SEC).coerceIn(60, 3600)
        val role = json.optString("role").ifBlank { "guest" }.lowercase()
        rememberRole(context, role)

        val permissions = json.optJSONObject("permissions")
        val caps = mutableSetOf<String>()
        if (permissions != null) {
            if (permissions.optBoolean("can_sell", false)) caps.add("can_sell")
            if (permissions.optBoolean("can_stock", false)) caps.add("can_view_stock")
            if (permissions.optBoolean("can_copilot", false)) caps.add("can_use_copilot")
            if (permissions.optBoolean("can_finance", false)) caps.add("can_view_finance")
        }
        if (caps.isNotEmpty()) {
            rememberCapabilities(context, caps)
        }

        val modules = mutableSetOf<String>()
        val modulesJson = json.optJSONArray("modules")
        if (modulesJson != null) {
            for (i in 0 until modulesJson.length()) {
                val module = modulesJson.optString(i).trim().lowercase()
                if (module.isNotBlank()) {
                    modules.add(module)
                }
            }
        }
        if (modules.isNotEmpty()) {
            rememberEnabledModules(context, modules)
        }

        val insights = json.optJSONObject("insights")
        val filters = json.optJSONObject("filters")
        if (filters != null) {
            val dateFrom = filters.optString("date_from").ifBlank { readFilterDateFrom(context) }
            val dateTo = filters.optString("date_to").ifBlank { readFilterDateTo(context) }
            val locationId = if (filters.has("location_id")) filters.optInt("location_id", 0) else 0
            val locationName = filters.optString("location_name").ifBlank { context.getString(R.string.widget_all_locations) }
            rememberWidgetFilters(context, dateFrom, dateTo, locationId, locationName)
            rememberLocationOptions(context, filters.optJSONArray("available_locations"))
            rememberSalesComparison(context, null, null, filters.optString("period_label").ifBlank { null })
        }
        val advice = json.optJSONObject("advice")
        var adviceTitleForNotif = ""
        var adviceMessageForNotif = ""
        var adviceQuotaInsufficient = false
        var adviceQuotaMessage: String? = null
        if (advice != null) {
            adviceTitleForNotif = advice.optString("title").ifBlank { context.getString(R.string.insights_advice_default_title) }
            adviceMessageForNotif = advice.optString("message").ifBlank { "" }
            val quotaState = advice.optString("quota_status").trim().lowercase(Locale.US)
            val adviceStatus = advice.optString("status").trim().lowercase(Locale.US)
            val errorCode = advice.optString("error_code").trim().lowercase(Locale.US)
            val blockedReason = advice.optString("blocked_reason").trim().lowercase(Locale.US)
            val quotaObj = advice.optJSONObject("quota_h")
            val quotaObjStatus = quotaObj?.optString("status").orEmpty().trim().lowercase(Locale.US)
            val messageLower = adviceMessageForNotif.lowercase(Locale.US)
            adviceQuotaInsufficient =
                quotaState in setOf("insufficient_h", "failed", "no_h")
                    || adviceStatus in setOf("insufficient_h", "quota_failed", "no_h")
                    || errorCode in setOf("insufficient_h", "quota_failed", "wallet_insufficient")
                    || blockedReason in setOf("insufficient_h", "quota_failed", "wallet_insufficient")
                    || quotaObjStatus in setOf("insufficient_h", "failed", "no_h")
                    || messageLower.contains("h insuff")
                    || messageLower.contains("credit h insuff")
            if (adviceQuotaInsufficient) {
                adviceQuotaMessage = if (adviceMessageForNotif.isBlank()) {
                    context.getString(R.string.notif_advice_h_insufficient_text)
                } else {
                    adviceMessageForNotif
                }
            }
            rememberAdvice(
                context,
                adviceTitleForNotif.ifBlank { null },
                adviceMessageForNotif.ifBlank { null }
            )
        } else {
            rememberAdvice(
                context,
                context.getString(R.string.insights_advice_default_title),
                context.getString(R.string.insights_advice_default_message)
            )
            adviceTitleForNotif = context.getString(R.string.insights_advice_default_title)
            adviceMessageForNotif = context.getString(R.string.insights_advice_default_message)
        }
        val currency = json.optJSONObject("currency")
        val currencySymbol = when {
            currency != null && currency.optString("symbol").isNotBlank() -> currency.optString("symbol")
            json.optString("currency_symbol").isNotBlank() -> json.optString("currency_symbol")
            else -> ""
        }
        if (currencySymbol.isNotBlank()) {
            rememberCurrencySymbol(context, currencySymbol)
        }
        val sales = insights?.optJSONObject("sales")
        if (sales != null) {
            rememberSalesInsights(
                context,
                sales.optString("sales_today", "0"),
                sales.optInt("tickets_today", 0),
                sales.optString("avg_ticket", "0")
            )
            rememberSalesTrend(context, sales.optString("sales_trend", "Stable"))
            rememberSalesExtras(
                context,
                if (sales.has("sales_vs_yesterday_pct")) sales.optInt("sales_vs_yesterday_pct")
                else if (sales.has("sales_change_pct")) sales.optInt("sales_change_pct")
                else null,
                if (sales.has("last_sale_minutes")) sales.optInt("last_sale_minutes") else null,
                sales.optString("top_product_name", "").ifBlank { null },
                if (sales.has("top_product_qty")) sales.optInt("top_product_qty") else null
            )
            rememberSalesComparison(
                context,
                sales.optString("sales_yesterday", "").ifBlank { null },
                if (sales.has("sales_vs_yesterday_pct")) sales.optInt("sales_vs_yesterday_pct")
                else if (sales.has("sales_change_pct")) sales.optInt("sales_change_pct")
                else null,
                null
            )
            rememberSalesV2Extras(
                context,
                if (sales.has("sales_change_week_pct")) sales.optInt("sales_change_week_pct") else null,
                if (sales.has("avg_ticket_change_pct")) sales.optInt("avg_ticket_change_pct") else null,
                if (sales.has("top_product_change_pct")) sales.optInt("top_product_change_pct") else null,
                sales.optString("trend_hint", "").ifBlank { null },
                sales.optString("suggested_action", "").ifBlank { null }
            )
            val seriesArray = sales.optJSONArray("series")
            if (seriesArray != null) {
                val series = mutableListOf<Double>()
                for (i in 0 until seriesArray.length()) {
                    series.add(seriesArray.optDouble(i, 0.0))
                }
                rememberSalesSeries(context, series)
            }
        }

        val stock = insights?.optJSONObject("stock")
        if (stock != null) {
            rememberStockInsights(
                context,
                stock.optInt("low_stock", 0),
                stock.optInt("mismatch", 0)
            )
        }

        val health = insights?.optJSONObject("health")
        if (health != null) {
            rememberFinanceInsights(
                context,
                health.optString("expense_today", "0"),
                health.optString("profit_today", "0"),
                health.optInt("overdue_invoices", 0),
                health.optString("collection_rate", "0%")
            )
        }

        rememberLastUpdate(context, json.optString("generated_at").ifBlank { nowStamp() })
        prefs(context).edit()
            .putLong(KEY_LAST_REMOTE_FETCH_MS, System.currentTimeMillis())
            .putInt(KEY_REMOTE_REFRESH_INTERVAL_SEC, refreshSec)
            .apply()
        rememberRemoteSyncState(context, "ok", "Sync OK")
        if (readAdviceEnabled(context)) {
            H360NotificationDispatcher.notifyDailyAdvice(context, adviceTitleForNotif, adviceMessageForNotif)
            if (adviceQuotaInsufficient) {
                H360NotificationDispatcher.notifyAdviceQuotaInsufficient(context, adviceQuotaMessage)
                rememberRemoteSyncState(context, "warning", "Sync OK | quota h insuffisant")
                rememberRemoteDiagnostic(
                    context = context,
                    state = "quota_h",
                    httpCode = 200,
                    endpoint = "advice",
                    hasCookie = true,
                    detail = "wallet_h_insufficient"
                )
            }
        }

        renderWidgets(context)
    }

    private fun rememberRemoteSyncState(context: Context, state: String, message: String) {
        prefs(context).edit()
            .putString(KEY_REMOTE_SYNC_STATE, state)
            .putString(KEY_REMOTE_SYNC_MESSAGE, message.take(120))
            .apply()
    }

    private fun rememberRemoteDiagnostic(
        context: Context,
        state: String,
        httpCode: Int,
        endpoint: String,
        hasCookie: Boolean,
        detail: String
    ) {
        val epPath = runCatching {
            val uri = Uri.parse(endpoint)
            val path = uri.path.orEmpty().ifBlank { endpoint }
            if (uri.query.isNullOrBlank()) path else "$path?..."
        }.getOrDefault(endpoint)
        val code = if (httpCode > 0) httpCode.toString() else "--"
        val label = "Diag: ${state.uppercase()} | HTTP:$code | cookie:${if (hasCookie) "ok" else "missing"} | ep:$epPath"
        prefs(context).edit()
            .putString(KEY_REMOTE_DIAG_LABEL, label.take(180))
            .putString(KEY_REMOTE_DIAG_DETAIL, detail.take(180))
            .apply()
    }

    private fun buildRemoteViews(context: Context): RemoteViews {
        val prefs = prefs(context)
        val role = prefs.getString(KEY_ROLE, "guest") ?: "guest"
        val pending = prefs.getInt(KEY_OFFLINE_PENDING, 0)
        val rawLastSync = prefs.getString(KEY_LAST_SYNC, context.getString(R.string.widget_not_synced))
            ?: context.getString(R.string.widget_not_synced)
        val lastSync = if (rawLastSync == "N/A") context.getString(R.string.widget_not_synced) else rawLastSync
        val onlineStatus = prefs.getString(KEY_ONLINE_STATUS, "Offline") ?: "Offline"
        val appOpens = prefs.getInt(KEY_APP_OPENS, 0)
        val lastPage = prefs.getString(KEY_LAST_PAGE, context.getString(R.string.widget_page_unknown))
            ?: context.getString(R.string.widget_page_unknown)
        val rawLastUpdate = prefs.getString(KEY_LAST_UPDATE, context.getString(R.string.widget_not_available))
            ?: context.getString(R.string.widget_not_available)
        val lastUpdate = if (rawLastUpdate == "N/A") context.getString(R.string.widget_not_available) else rawLastUpdate
        val diag = prefs.getString(KEY_REMOTE_DIAG_LABEL, "").orEmpty()
        val caps = prefs.getString(KEY_CAPABILITIES, "").orEmpty()
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        val selectedModules = resolveModules(prefs, role, caps)
        val roleLc = role.lowercase()
        val canSell = roleLc == "admin" || roleLc == "cashier" || caps.contains("can_sell")
        val canStock = roleLc == "admin" || roleLc == "storekeeper" || caps.contains("can_view_stock")
        val canCopilot = roleLc == "admin" || caps.contains("can_use_copilot")
        val salesToday = prefs.getString(KEY_SALES_TODAY, "0") ?: "0"
        val ticketsToday = prefs.getInt(KEY_TICKETS_TODAY, 0)
        val avgTicket = prefs.getString(KEY_AVG_TICKET, "0") ?: "0"
        val lowStock = prefs.getInt(KEY_LOW_STOCK, 0)
        val stockMismatch = prefs.getInt(KEY_STOCK_MISMATCH, 0)
        val salesTodayDisplay = formatMoneyDisplay(context, salesToday)
        val avgTicketDisplay = formatMoneyDisplay(context, avgTicket)

        val views = try {
            RemoteViews(context.packageName, R.layout.widget_h360_status)
        } catch (_: Exception) {
            RemoteViews(context.packageName, R.layout.widget_h360_status_fallback)
        }
        views.setTextViewText(R.id.widgetRoleValue, role.uppercase())
        views.setTextViewText(R.id.widgetPendingValue, pending.toString())
        views.setTextViewText(R.id.widgetLastSyncValue, lastSync)
        views.setTextViewText(R.id.widgetStatusValue, onlineStatus)
        views.setTextViewText(R.id.widgetOpenCountValue, appOpens.toString())
        views.setTextViewText(R.id.widgetLastPageValue, lastPage)
        val lastUpdateWithDiag = if (diag.isNotBlank()) "$lastUpdate | $diag" else lastUpdate
        views.setTextViewText(R.id.widgetLastUpdateValue, lastUpdateWithDiag)
        views.setTextViewText(R.id.widgetSalesTodayValue, salesTodayDisplay)
        views.setTextViewText(R.id.widgetTicketsTodayValue, ticketsToday.toString())
        views.setTextViewText(R.id.widgetAvgTicketValue, avgTicketDisplay)
        views.setTextViewText(R.id.widgetLowStockValue, lowStock.toString())
        views.setTextViewText(R.id.widgetMismatchValue, stockMismatch.toString())
        views.setTextColor(
            R.id.widgetStatusValue,
            if (onlineStatus == "Online") 0xFF2ECC71.toInt() else 0xFFFF6B6B.toInt()
        )
        views.setViewVisibility(
            R.id.widgetSalesBlock,
            if (selectedModules.contains(MODULE_SALES) && canSell) android.view.View.VISIBLE else android.view.View.GONE
        )
        views.setViewVisibility(
            R.id.widgetStockBlock,
            if (selectedModules.contains(MODULE_STOCK) && canStock) android.view.View.VISIBLE else android.view.View.GONE
        )
        views.setViewVisibility(
            R.id.widgetOfflineBlock,
            if (selectedModules.contains(MODULE_OFFLINE)) android.view.View.VISIBLE else android.view.View.GONE
        )
        views.setViewVisibility(
            R.id.widgetActivityBlock,
            if (selectedModules.contains(MODULE_ACTIVITY)) android.view.View.VISIBLE else android.view.View.GONE
        )

        views.setOnClickPendingIntent(R.id.widgetBtnCopilot, deepLinkPendingIntent(context, "copilot", 201))
        views.setOnClickPendingIntent(R.id.widgetBtnPos, deepLinkPendingIntent(context, "pos", 202))
        views.setOnClickPendingIntent(R.id.widgetBtnNewSale, deepLinkPendingIntent(context, "new-sale", 203))
        views.setOnClickPendingIntent(R.id.widgetBtnHistory, deepLinkPendingIntent(context, "sales-history", 204))
        views.setOnClickPendingIntent(R.id.widgetBtnStockMismatch, deepLinkPendingIntent(context, "stock-mismatch", 205))
        views.setOnClickPendingIntent(R.id.widgetBtnCustomize, openWidgetSettingsIntent(context))
        views.setViewVisibility(R.id.widgetBtnCopilot, if (canCopilot) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widgetBtnPos, if (canSell) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widgetBtnNewSale, if (canSell) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widgetBtnHistory, if (canSell) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widgetBtnStockMismatch, if (canStock) android.view.View.VISIBLE else android.view.View.GONE)

        return views
    }

    private fun deepLinkPendingIntent(context: Context, shortcut: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("h360://shortcut/$shortcut"), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun realtimeTickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, H360RealtimeRefreshReceiver::class.java).apply {
            action = ACTION_WIDGET_REALTIME_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            9901,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openWidgetSettingsIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetSettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            901,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun resolveModules(
        prefs: android.content.SharedPreferences,
        role: String,
        capabilities: Set<String>
    ): Set<String> {
        val explicit = prefs.getString(KEY_ENABLED_MODULES, "").orEmpty()
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        if (explicit.isNotEmpty()) return explicit

        val defaults = linkedSetOf(MODULE_ACTIVITY, MODULE_OFFLINE)
        val roleLc = role.lowercase()
        if (roleLc == "admin" || roleLc == "cashier" || capabilities.contains("can_sell")) {
            defaults.add(MODULE_SALES)
        }
        if (roleLc == "admin" || roleLc == "storekeeper" || capabilities.contains("can_view_stock")) {
            defaults.add(MODULE_STOCK)
        }
        return defaults
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun nowStamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }

    private fun todayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun formatMoneyDisplay(context: Context, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed == "--" || trimmed.contains("%")) return trimmed
        val hasLetters = trimmed.any { it.isLetter() }
        val hasCurrency = trimmed.any { it in "$€£¥₦₵₣₱₹" }
        if (hasLetters || hasCurrency) return trimmed
        val prefsSymbol = prefs(context).getString(KEY_CURRENCY_SYMBOL, "").orEmpty().trim()
        val symbol = if (prefsSymbol.isNotBlank()) prefsSymbol else context.getString(R.string.currency_symbol).trim()
        if (symbol.isEmpty()) return trimmed
        return "$symbol $trimmed"
    }

    fun renderSalesSparkline(context: Context): Bitmap? {
        val raw = prefs(context).getString(KEY_SALES_SERIES, "").orEmpty()
        if (raw.isBlank()) return null
        val points = raw.split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }
            .map { it.toFloat() }
        if (points.isEmpty()) return null

        val width = 320
        val height = 64
        val padding = 8f
        val max = points.maxOrNull() ?: 0f
        val min = points.minOrNull() ?: 0f
        val range = (max - min).takeIf { it > 0f } ?: 1f

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF111A31.toInt())

        val paintLine = Paint().apply {
            color = 0xFF5CC8FF.toInt()
            strokeWidth = 2.5f
            isAntiAlias = true
            style = Paint.Style.STROKE
        }
        val paintFill = Paint().apply {
            shader = LinearGradient(
                0f,
                padding,
                0f,
                height.toFloat(),
                0x335CC8FF,
                0x005CC8FF,
                Shader.TileMode.CLAMP
            )
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val paintBase = Paint().apply {
            color = 0xFF22345A.toInt()
            strokeWidth = 1f
            isAntiAlias = true
        }

        val count = points.size
        val step = (width - padding * 2) / (count - 1).coerceAtLeast(1)
        val scaled = points.mapIndexed { idx, value ->
            val x = padding + step * idx
            val y = padding + (height - padding * 2) * (1f - ((value - min) / range))
            x to y
        }

        val fillPath = android.graphics.Path()
        val linePath = android.graphics.Path()
        scaled.forEachIndexed { index, (x, y) ->
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, height - padding)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        val lastX = scaled.last().first
        fillPath.lineTo(lastX, height - padding)
        fillPath.close()

        canvas.drawLine(padding, height - padding, width - padding, height - padding, paintBase)
        canvas.drawPath(fillPath, paintFill)
        canvas.drawPath(linePath, paintLine)
        return bmp
    }

    private data class InsightHttpResult(
        val code: Int,
        val contentType: String,
        val body: String,
        val endpoint: String,
        val hasCookie: Boolean
    )
}
