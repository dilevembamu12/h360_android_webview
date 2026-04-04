package cd.h360.pos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object H360NotificationDispatcher {
    private const val CHANNEL_ID = "h360_insights"
    private const val PREFS = "h360_notifications"
    private const val THROTTLE_MS = 15 * 60 * 1000L

    fun notifyOfflinePending(context: Context, pending: Int) {
        if (pending < 5) return
        if (!canSend(context)) return
        if (isThrottled(context, "offline_pending")) return
        notify(
            context = context,
            id = 1001,
            title = context.getString(R.string.notif_offline_title),
            text = context.getString(R.string.notif_offline_text, pending),
            deepLink = "h360://shortcut/offline"
        )
    }

    fun notifyStockMismatch(context: Context, mismatch: Int) {
        if (mismatch <= 0) return
        if (!canSend(context)) return
        if (isThrottled(context, "stock_mismatch")) return
        notify(
            context = context,
            id = 1002,
            title = context.getString(R.string.notif_stock_title),
            text = context.getString(R.string.notif_stock_text, mismatch),
            deepLink = "h360://shortcut/stock-mismatch"
        )
    }

    fun notifyOverdueInvoices(context: Context, overdue: Int) {
        if (overdue <= 0) return
        if (!canSend(context)) return
        if (isThrottled(context, "overdue_invoices")) return
        notify(
            context = context,
            id = 1003,
            title = context.getString(R.string.notif_overdue_title),
            text = context.getString(R.string.notif_overdue_text, overdue),
            deepLink = "h360://shortcut/sales-history"
        )
    }

    fun notifyCopilotResponse(context: Context, response: String) {
        if (response.isBlank()) return
        if (!canSend(context)) return
        if (isThrottled(context, "copilot_response")) return
        val preview = if (response.length > 80) response.take(80) + "..." else response
        notify(
            context = context,
            id = 1004,
            title = context.getString(R.string.notif_copilot_title),
            text = preview,
            deepLink = "h360://shortcut/copilot"
        )
    }

    private fun notify(context: Context, id: Int, title: String, text: String, deepLink: String) {
        ensureChannel(context)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun canSend(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun isThrottled(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        val throttled = now - last < THROTTLE_MS
        if (!throttled) {
            prefs.edit().putLong(key, now).apply()
        }
        return throttled
    }
}
