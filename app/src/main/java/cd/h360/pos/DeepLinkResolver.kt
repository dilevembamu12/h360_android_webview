package cd.h360.pos

import android.content.Intent
import android.net.Uri

object DeepLinkResolver {

    fun resolve(intent: Intent?, homeUrl: String, allowedHosts: Set<String>): String {
        val uri = intent?.data ?: return homeUrl
        val host = uri.host?.lowercase().orEmpty()

        if (uri.scheme == "h360" && host == "shortcut") {
            return shortcutToUrl(uri.lastPathSegment.orEmpty(), homeUrl)
        }

        return if (uri.scheme == "https" && allowedHosts.contains(host)) {
            uri.toString()
        } else {
            homeUrl
        }
    }

    private fun shortcutToUrl(shortcut: String, homeUrl: String): String {
        val appOrigin = Uri.parse(homeUrl).let { "${it.scheme}://${it.host}" }
        return when (shortcut) {
            "copilot" -> "$appOrigin/h360copilot"
            "offline" -> "$appOrigin/h360offline"
            "new-sale" -> "$appOrigin/pos/create"
            "sales-history" -> "$appOrigin/sell"
            "stock-mismatch" -> "$appOrigin/reports/stock-report?type=mismatch"
            "customers" -> "$appOrigin/contacts?type=customer"
            else -> homeUrl
        }
    }
}
