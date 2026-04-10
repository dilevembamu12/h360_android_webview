package cd.h360.pos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class H360AdviceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        H360WidgetUpdater.refreshFromRemoteIfDue(context, force = true)
        refreshAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> H360WidgetUpdater.refreshFromRemoteIfDue(context, force = true)
        }
        H360WidgetUpdater.refreshFromRemoteIfDue(context, force = true)
        refreshAll(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        H360WidgetUpdater.refreshFromRemoteIfDue(context, force = true)
        H360WidgetUpdater.scheduleRealtimeRefresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "cd.h360.pos.ACTION_ADVICE_REFRESH"
        private const val DASHBOARD_URL = "https://stack.git.h360.cd/home"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, H360AdviceWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val prefs = context.getSharedPreferences(H360WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
            val syncMessage = prefs.getString(H360WidgetUpdater.KEY_REMOTE_SYNC_MESSAGE, "En attente de sync").orEmpty()
            val syncState = prefs.getString(H360WidgetUpdater.KEY_REMOTE_SYNC_STATE, "idle").orEmpty()
            val diagLabel = prefs.getString(H360WidgetUpdater.KEY_REMOTE_DIAG_LABEL, "").orEmpty()
            val title = prefs.getString(H360WidgetUpdater.KEY_INSIGHTS_ADVICE_TITLE, context.getString(R.string.insights_advice_default_title))
                .orEmpty()
                .ifBlank { context.getString(R.string.insights_advice_default_title) }
            val message = prefs.getString(H360WidgetUpdater.KEY_INSIGHTS_ADVICE_MESSAGE, context.getString(R.string.insights_advice_default_message))
                .orEmpty()
                .ifBlank { context.getString(R.string.insights_advice_default_message) }

            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_h360_advice)
                views.setTextViewText(R.id.widgetAdviceTitle, title)
                views.setTextViewText(R.id.widgetAdviceMessage, message)
                val footer = if (diagLabel.isBlank()) syncMessage else "$syncMessage\n$diagLabel"
                views.setTextViewText(R.id.widgetAdviceFooter, footer)
                views.setTextColor(
                    R.id.widgetAdviceFooter,
                    if (syncState == "ok") 0xFF8FD19E.toInt() else 0xFFFFB4B4.toInt()
                )
                views.setOnClickPendingIntent(R.id.widgetAdviceRefresh, refreshIntent(context))
                views.setOnClickPendingIntent(R.id.widgetAdviceOpen, openDashboardIntent(context))
                manager.updateAppWidget(widgetId, views)
            }
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, H360AdviceWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            return PendingIntent.getBroadcast(
                context,
                701,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openDashboardIntent(context: Context): PendingIntent {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(DASHBOARD_URL),
                context,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                702,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
