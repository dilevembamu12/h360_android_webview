package cd.h360.pos

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class H360FirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isBlank()) return
        H360FcmRegistrar.registerTokenWithBackend(applicationContext, token, "token_refresh")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val title = data["title"]
            ?: message.notification?.title
            ?: "H360"
        val body = data["message"]
            ?: data["body"]
            ?: message.notification?.body
            ?: ""
        if (body.isBlank()) return
        val deepLink = data["deeplink"] ?: data["deep_link"]
        H360NotificationDispatcher.notifyServerPush(
            context = applicationContext,
            title = title,
            message = body,
            deepLink = deepLink
        )
        H360WidgetUpdater.refreshFromRemoteIfDue(applicationContext, force = true)
        H360WidgetUpdater.refreshAllWidgets(applicationContext)
    }
}

