package cd.h360.pos

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class H360WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        H360WidgetUpdater.refreshAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        H360WidgetUpdater.refreshAllWidgets(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        H360WidgetUpdater.refreshFromRemoteIfDue(context, force = true)
        H360WidgetUpdater.scheduleRealtimeRefresh(context)
    }
}
