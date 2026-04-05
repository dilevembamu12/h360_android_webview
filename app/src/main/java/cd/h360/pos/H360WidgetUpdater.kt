package cd.h360.pos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.widget.RemoteViews
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
    const val KEY_LAST_SALE_MINUTES = "last_sale_minutes"
    const val KEY_TOP_PRODUCT_NAME = "top_product_name"
    const val KEY_TOP_PRODUCT_QTY = "top_product_qty"
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
    const val KEY_LAST_REMOTE_FETCH_MS = "last_remote_fetch_ms"
    private const val KEY_REMOTE_REFRESH_INTERVAL_SEC = "remote_refresh_interval_sec"
    const val KEY_REMOTE_SYNC_STATE = "remote_sync_state"
    const val KEY_REMOTE_SYNC_MESSAGE = "remote_sync_message"
    private const val DEFAULT_REMOTE_REFRESH_INTERVAL_SEC = 300

    private const val MODULE_SALES = "sales"
    private const val MODULE_STOCK = "stock"
    private const val MODULE_OFFLINE = "offline"
    private const val MODULE_ACTIVITY = "activity"
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val remoteFetchInProgress = AtomicBoolean(false)

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
        refreshFromRemoteIfDue(context, force = false)
        renderWidgets(context)
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
                val views = buildRemoteViews(context)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
        H360CopilotWidgetProvider.refreshAll(context)
        H360InsightsCardsWidgetProvider.refreshAll(context)
    }

    private fun fetchAndStoreRemoteInsights(context: Context) {
        val urls = candidateInsightsUrls()
        if (urls.isEmpty()) return

        var lastError = "API insights indisponible"
        urls.forEach { url ->
            val result = requestInsights(url)
            when {
                result.code == HttpURLConnection.HTTP_UNAUTHORIZED || result.code == HttpURLConnection.HTTP_FORBIDDEN -> {
                    rememberRemoteSyncState(context, "auth_required", "Session expiree: reconnecte-toi")
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
                        return
                    }
                    lastError = "JSON insights invalide"
                }
            }
        }
        rememberRemoteSyncState(context, "error", lastError)
    }

    private fun requestInsights(url: String): InsightHttpResult {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/json")
            }
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", cookie)
            }
            val code = conn.responseCode
            val contentType = conn.contentType.orEmpty().lowercase()
            val body = runCatching {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            InsightHttpResult(code, contentType, body)
        } catch (e: Exception) {
            InsightHttpResult(-1, "", e.message.orEmpty())
        }
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
                if (sales.has("sales_change_pct")) sales.optInt("sales_change_pct") else null,
                if (sales.has("last_sale_minutes")) sales.optInt("last_sale_minutes") else null,
                sales.optString("top_product_name", "").ifBlank { null },
                if (sales.has("top_product_qty")) sales.optInt("top_product_qty") else null
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

        renderWidgets(context)
    }

    private fun rememberRemoteSyncState(context: Context, state: String, message: String) {
        prefs(context).edit()
            .putString(KEY_REMOTE_SYNC_STATE, state)
            .putString(KEY_REMOTE_SYNC_MESSAGE, message.take(120))
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

        val views = RemoteViews(context.packageName, R.layout.widget_h360_status)
        views.setTextViewText(R.id.widgetRoleValue, role.uppercase())
        views.setTextViewText(R.id.widgetPendingValue, pending.toString())
        views.setTextViewText(R.id.widgetLastSyncValue, lastSync)
        views.setTextViewText(R.id.widgetStatusValue, onlineStatus)
        views.setTextViewText(R.id.widgetOpenCountValue, appOpens.toString())
        views.setTextViewText(R.id.widgetLastPageValue, lastPage)
        views.setTextViewText(R.id.widgetLastUpdateValue, lastUpdate)
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
        val body: String
    )
}
