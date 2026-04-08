package cd.h360.pos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class H360RealtimeRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            H360WidgetUpdater.ACTION_WIDGET_REALTIME_TICK -> {
                H360WidgetUpdater.handleRealtimeTick(context.applicationContext)
            }
            else -> {
                H360WidgetUpdater.scheduleRealtimeRefresh(context.applicationContext)
            }
        }
    }
}
