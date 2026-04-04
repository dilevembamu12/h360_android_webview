package cd.h360.pos

import android.content.Intent
import android.net.Uri

object DeepLinkResolver {

    fun resolve(intent: Intent?, homeUrl: String, allowedHosts: Set<String>): String {
        val uri = intent?.data ?: return homeUrl
        val host = uri.host?.lowercase().orEmpty()

        if (uri.scheme == "h360" && host == "shortcut") {
            return shortcutToUrl(uri.lastPathSegment.orEmpty(), homeUrl, uri.getQueryParameter("prompt"))
        }

        return if (uri.scheme == "https" && allowedHosts.contains(host)) {
            uri.toString()
        } else {
            homeUrl
        }
    }

    private fun shortcutToUrl(shortcut: String, homeUrl: String, prompt: String?): String {
        val appOrigin = Uri.parse(homeUrl).let { "${it.scheme}://${it.host}" }
        return when (shortcut) {
            "copilot" -> {
                if (prompt.isNullOrBlank()) {
                    "$appOrigin/h360-copilot/chat"
                } else {
                    "$appOrigin/h360-copilot/chat?widget_prompt=${Uri.encode(prompt)}"
                }
            }
            "offline" -> "$appOrigin/h360offline"
            "new-sale" -> "$appOrigin/sells/create"
            "pos" -> "$appOrigin/pos/create"
            "sales-history" -> "$appOrigin/sells"
            "stock-mismatch" -> "$appOrigin/reports/product-stock-details"
            "customers" -> "$appOrigin/contacts?type=customer"
            else -> homeUrl
        }
    }
}
