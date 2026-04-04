package cd.h360.pos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

object H360WidgetUpdater {
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
    const val KEY_LOW_STOCK = "low_stock"
    const val KEY_STOCK_MISMATCH = "stock_mismatch"
    const val KEY_COPILOT_LAST_PROMPT = "copilot_last_prompt"
    const val KEY_COPILOT_LAST_RESPONSE = "copilot_last_response"
    const val KEY_SALES_TREND = "sales_trend"
    const val KEY_EXPENSE_TODAY = "expense_today"
    const val KEY_PROFIT_TODAY = "profit_today"
    const val KEY_OVERDUE_INVOICES = "overdue_invoices"
    const val KEY_COLLECTION_RATE = "collection_rate"

    private const val MODULE_SALES = "sales"
    private const val MODULE_STOCK = "stock"
    private const val MODULE_OFFLINE = "offline"
    private const val MODULE_ACTIVITY = "activity"

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

    fun refreshAllWidgets(context: Context) {
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

        val views = RemoteViews(context.packageName, R.layout.widget_h360_status)
        views.setTextViewText(R.id.widgetRoleValue, role.uppercase())
        views.setTextViewText(R.id.widgetPendingValue, pending.toString())
        views.setTextViewText(R.id.widgetLastSyncValue, lastSync)
        views.setTextViewText(R.id.widgetStatusValue, onlineStatus)
        views.setTextViewText(R.id.widgetOpenCountValue, appOpens.toString())
        views.setTextViewText(R.id.widgetLastPageValue, lastPage)
        views.setTextViewText(R.id.widgetLastUpdateValue, lastUpdate)
        views.setTextViewText(R.id.widgetSalesTodayValue, salesToday)
        views.setTextViewText(R.id.widgetTicketsTodayValue, ticketsToday.toString())
        views.setTextViewText(R.id.widgetAvgTicketValue, avgTicket)
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
}
