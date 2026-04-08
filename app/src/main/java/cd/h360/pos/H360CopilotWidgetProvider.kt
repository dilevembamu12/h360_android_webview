package cd.h360.pos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class H360CopilotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        H360WidgetUpdater.refreshFromRemoteIfDue(context, force = true)
        refreshAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_QUICK_PROMPT -> {
                val prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty()
                if (prompt.isNotBlank()) {
                    H360WidgetUpdater.rememberCopilotPrompt(context, prompt)
                    H360WidgetUpdater.rememberCopilotResponse(context, context.getString(R.string.copilot_response_pending))
                    openCopilot(context, prompt)
                }
            }
            ACTION_OPEN_COMPOSER -> {
                val activityIntent = Intent(context, CopilotComposerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(activityIntent)
            }
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
        const val ACTION_QUICK_PROMPT = "cd.h360.pos.ACTION_QUICK_PROMPT"
        const val ACTION_OPEN_COMPOSER = "cd.h360.pos.ACTION_OPEN_COMPOSER"
        const val EXTRA_PROMPT = "prompt"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, H360CopilotWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val prefs = context.getSharedPreferences(H360WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
            val lastPrompt = prefs.getString(H360WidgetUpdater.KEY_COPILOT_LAST_PROMPT, "").orEmpty()
                .ifBlank { context.getString(R.string.copilot_placeholder_prompt) }
            val rawResponse = prefs.getString(H360WidgetUpdater.KEY_COPILOT_LAST_RESPONSE, "").orEmpty()
                .ifBlank { context.getString(R.string.copilot_placeholder_response) }
            val diag = prefs.getString(H360WidgetUpdater.KEY_REMOTE_DIAG_LABEL, "").orEmpty()
            val lastResponse = if (diag.isBlank()) rawResponse else "$rawResponse\n$diag"

            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_h360_copilot)
                views.setTextViewText(R.id.widgetCopilotLastPrompt, lastPrompt)
                views.setTextViewText(R.id.widgetCopilotLastResponse, lastResponse)

                views.setOnClickPendingIntent(
                    R.id.widgetPromptSales,
                    openCopilotWithPromptPendingIntent(context, "Resume les ventes du jour", 301)
                )
                views.setOnClickPendingIntent(
                    R.id.widgetPromptStock,
                    openCopilotWithPromptPendingIntent(context, "Donne les alertes stock critiques", 302)
                )
                views.setOnClickPendingIntent(
                    R.id.widgetPromptMismatch,
                    openCopilotWithPromptPendingIntent(context, "Explique les mismatch stock", 303)
                )
                views.setOnClickPendingIntent(
                    R.id.widgetBtnOpenChat,
                    openComposerPendingIntent(context, 304)
                )

                manager.updateAppWidget(widgetId, views)
            }
        }

        private fun openCopilotWithPromptPendingIntent(context: Context, prompt: String, requestCode: Int): PendingIntent {
            val uri = Uri.parse("h360://shortcut/copilot?prompt=${Uri.encode(prompt)}")
            val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun quickPromptPendingIntent(context: Context, prompt: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, H360CopilotWidgetProvider::class.java).apply {
                action = ACTION_QUICK_PROMPT
                putExtra(EXTRA_PROMPT, prompt)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openComposerPendingIntent(context: Context, requestCode: Int): PendingIntent {
            val intent = Intent(context, H360CopilotWidgetProvider::class.java).apply {
                action = ACTION_OPEN_COMPOSER
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openCopilot(context: Context, prompt: String) {
            val uri = Uri.parse("h360://shortcut/copilot?prompt=${Uri.encode(prompt)}")
            val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }
}
