package cd.h360.pos

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import com.google.firebase.messaging.FirebaseMessaging
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object H360FcmRegistrar {
    private const val TAG = "H360FcmRegistrar"
    private const val PREFS = "h360_fcm_prefs"
    private const val KEY_LAST_TOKEN = "last_token"
    private const val KEY_LAST_SYNC_MS = "last_sync_ms"
    private val executor = Executors.newSingleThreadExecutor()
    private val inProgress = AtomicBoolean(false)

    fun syncTokenIfPossible(context: Context, reason: String = "periodic") {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token.isNullOrBlank()) return@addOnSuccessListener
                registerTokenWithBackend(context.applicationContext, token, reason)
            }.addOnFailureListener {
                Log.w(TAG, "Unable to read FCM token: ${it.message}")
            }
        }.onFailure {
            Log.w(TAG, "Firebase unavailable for token sync: ${it.message}")
        }
    }

    fun registerTokenWithBackend(context: Context, token: String, reason: String = "token_refresh") {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastToken = prefs.getString(KEY_LAST_TOKEN, "").orEmpty()
        val lastSync = prefs.getLong(KEY_LAST_SYNC_MS, 0L)
        val shouldSkip = token == lastToken && (now - lastSync) < (6 * 60 * 60 * 1000L)
        if (shouldSkip) return
        if (!inProgress.compareAndSet(false, true)) return

        executor.execute {
            try {
                val endpoint = buildEndpoint("/h360/mobile/push/register-token", token, reason)
                if (endpoint.isBlank()) return@execute

                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Requested-With", "XMLHttpRequest")
                }
                val cookie = collectCookieHeader(endpoint)
                if (cookie.isNotBlank()) {
                    conn.setRequestProperty("Cookie", cookie)
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    prefs.edit()
                        .putString(KEY_LAST_TOKEN, token)
                        .putLong(KEY_LAST_SYNC_MS, now)
                        .apply()
                } else {
                    Log.w(TAG, "Token register failed HTTP:$code")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Token register exception: ${e.message}")
            } finally {
                inProgress.set(false)
            }
        }
    }

    private fun buildEndpoint(path: String, token: String, reason: String): String {
        return runCatching {
            val base = Uri.parse(BuildConfig.WEBVIEW_BASE_URL)
            val origin = "${base.scheme}://${base.host}"
            val encodedToken = URLEncoder.encode(token, "UTF-8")
            val encodedReason = URLEncoder.encode(reason, "UTF-8")
            val deviceName = URLEncoder.encode("${Build.MANUFACTURER} ${Build.MODEL}", "UTF-8")
            val appVersion = URLEncoder.encode(BuildConfig.VERSION_NAME, "UTF-8")
            "$origin$path?token=$encodedToken&platform=android&device_name=$deviceName&app_version=$appVersion&reason=$encodedReason"
        }.getOrElse { "" }
    }

    private fun collectCookieHeader(endpoint: String): String {
        val cm = CookieManager.getInstance()
        val candidates = linkedSetOf<String>()
        candidates.add(endpoint)
        val base = BuildConfig.WEBVIEW_BASE_URL.trim()
        if (base.isNotBlank()) {
            candidates.add(base)
            val uri = Uri.parse(base)
            candidates.add("${uri.scheme}://${uri.host}")
        }
        val merged = linkedSetOf<String>()
        candidates.forEach { candidate ->
            val cookie = cm.getCookie(candidate).orEmpty()
            if (cookie.isNotBlank()) {
                cookie.split(";").map { it.trim() }.filter { it.isNotBlank() }.forEach { merged.add(it) }
            }
        }
        return merged.joinToString("; ")
    }
}

