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

    fun refreshAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = ComponentName(context, H360WidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
        if (widgetIds.isEmpty()) return

        widgetIds.forEach { widgetId ->
            val views = buildRemoteViews(context)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
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

        val views = RemoteViews(context.packageName, R.layout.widget_h360_status)
        views.setTextViewText(R.id.widgetRoleValue, role.uppercase())
        views.setTextViewText(R.id.widgetPendingValue, pending.toString())
        views.setTextViewText(R.id.widgetLastSyncValue, lastSync)
        views.setTextViewText(R.id.widgetStatusValue, onlineStatus)
        views.setTextViewText(R.id.widgetOpenCountValue, appOpens.toString())
        views.setTextViewText(R.id.widgetLastPageValue, lastPage)
        views.setTextViewText(R.id.widgetLastUpdateValue, lastUpdate)
        views.setTextColor(
            R.id.widgetStatusValue,
            if (onlineStatus == "Online") 0xFF2ECC71.toInt() else 0xFFFF6B6B.toInt()
        )

        views.setOnClickPendingIntent(R.id.widgetBtnCopilot, deepLinkPendingIntent(context, "copilot", 201))
        views.setOnClickPendingIntent(R.id.widgetBtnPos, deepLinkPendingIntent(context, "pos", 202))
        views.setOnClickPendingIntent(R.id.widgetBtnNewSale, deepLinkPendingIntent(context, "new-sale", 203))
        views.setOnClickPendingIntent(R.id.widgetBtnHistory, deepLinkPendingIntent(context, "sales-history", 204))
        views.setOnClickPendingIntent(R.id.widgetBtnStockMismatch, deepLinkPendingIntent(context, "stock-mismatch", 205))

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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
