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
    private const val BACKEND_CENTER_ID = 1010
    private const val PREFS = "h360_notifications"
    private const val THROTTLE_MS = 15 * 60 * 1000L
    private const val ADVICE_THROTTLE_MS = 6 * 60 * 60 * 1000L
    private const val ADVICE_H_INSUFFICIENT_THROTTLE_MS = 2 * 60 * 60 * 1000L

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

    fun notifyDailyAdvice(context: Context, title: String, message: String) {
        val safeTitle = title.trim().ifBlank { context.getString(R.string.notif_advice_title) }
        val safeMessage = message.trim()
        if (safeMessage.isBlank()) return
        if (!canSend(context)) return
        if (isThrottled(context, "daily_advice", ADVICE_THROTTLE_MS)) return

        val deepLink = runCatching {
            val base = Uri.parse(BuildConfig.WEBVIEW_BASE_URL)
            "${base.scheme}://${base.host}/home"
        }.getOrDefault("h360://shortcut/pos")

        notify(
            context = context,
            id = 1005,
            title = safeTitle,
            text = safeMessage,
            deepLink = deepLink
        )
    }

    fun notifyAdviceQuotaInsufficient(context: Context, message: String? = null) {
        if (!canSend(context)) return
        if (isThrottled(context, "advice_h_insufficient", ADVICE_H_INSUFFICIENT_THROTTLE_MS)) return
        val safeMessage = message?.trim().orEmpty().ifBlank {
            context.getString(R.string.notif_advice_h_insufficient_text)
        }
        val deepLink = runCatching {
            val base = Uri.parse(BuildConfig.WEBVIEW_BASE_URL)
            "${base.scheme}://${base.host}/h360-copilot/usage-history"
        }.getOrDefault("h360://shortcut/copilot")

        notify(
            context = context,
            id = 1006,
            title = context.getString(R.string.notif_advice_h_insufficient_title),
            text = safeMessage,
            deepLink = deepLink
        )
    }

    fun notifyServerPush(context: Context, title: String, message: String, deepLink: String? = null, notificationId: Int? = null) {
        val safeTitle = title.trim().ifBlank { context.getString(R.string.notif_copilot_title) }
        val safeMessage = message.trim()
        if (safeMessage.isBlank()) return
        if (!canSend(context)) return
        val safeDeepLink = deepLink?.trim().orEmpty().ifBlank {
            runCatching {
                val base = Uri.parse(BuildConfig.WEBVIEW_BASE_URL)
                "${base.scheme}://${base.host}/home"
            }.getOrDefault("h360://shortcut/pos")
        }
        val id = notificationId ?: ((System.currentTimeMillis() % 100000).toInt() + 2000)
        notify(
            context = context,
            id = id,
            title = safeTitle,
            text = safeMessage,
            deepLink = safeDeepLink
        )
    }

    fun updatePersistentBackendNotifications(context: Context, unreadTotal: Int, messages: List<String>) {
        if (!canSend(context)) return
        ensureChannel(context)

        val cleaned = messages.map { it.trim() }.filter { it.isNotBlank() }.take(6)
        if (cleaned.isEmpty() && unreadTotal <= 0) {
            NotificationManagerCompat.from(context).cancel(BACKEND_CENTER_ID)
            return
        }

        val title = if (unreadTotal > 0) {
            context.getString(R.string.notif_backend_center_title_with_count, unreadTotal)
        } else {
            context.getString(R.string.notif_backend_center_title)
        }
        val text = cleaned.firstOrNull() ?: context.getString(R.string.notif_backend_center_empty)
        val deepLink = runCatching {
            val base = Uri.parse(BuildConfig.WEBVIEW_BASE_URL)
            "${base.scheme}://${base.host}/load-more-notifications"
        }.getOrDefault("h360://shortcut/pos")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            BACKEND_CENTER_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val inbox = NotificationCompat.InboxStyle().also { style ->
            cleaned.forEach { style.addLine(it) }
            if (unreadTotal > cleaned.size) {
                style.setSummaryText("+${unreadTotal - cleaned.size} autres")
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(inbox)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(context).notify(BACKEND_CENTER_ID, notification)
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

    private fun isThrottled(context: Context, key: String, throttleMs: Long = THROTTLE_MS): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        val throttled = now - last < throttleMs
        if (!throttled) {
            prefs.edit().putLong(key, now).apply()
        }
        return throttled
    }
}
