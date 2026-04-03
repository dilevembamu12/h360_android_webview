package cd.h360.pos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

object AppShortcutsManager {

    fun updateForRole(context: Context, role: String) {
        val normalizedRole = role.trim().lowercase()
        val shortcuts = mutableListOf<ShortcutInfoCompat>()

        // H360Copilot and H360Offline are always first-class actions.
        shortcuts += buildShortcut(
            context = context,
            id = "copilot",
            shortLabel = context.getString(R.string.shortcut_copilot),
            longLabel = context.getString(R.string.shortcut_copilot_long),
            deepLink = "h360://shortcut/copilot",
            rank = 0
        )
        shortcuts += buildShortcut(
            context = context,
            id = "offline",
            shortLabel = context.getString(R.string.shortcut_offline),
            longLabel = context.getString(R.string.shortcut_offline_long),
            deepLink = "h360://shortcut/offline",
            rank = 1
        )

        when (normalizedRole) {
            "admin" -> {
                shortcuts += buildShortcut(
                    context, "stock-mismatch",
                    context.getString(R.string.shortcut_stock_mismatch),
                    context.getString(R.string.shortcut_stock_mismatch_long),
                    "h360://shortcut/stock-mismatch",
                    2
                )
                shortcuts += buildShortcut(
                    context, "sales-history",
                    context.getString(R.string.shortcut_sales_history),
                    context.getString(R.string.shortcut_sales_history_long),
                    "h360://shortcut/sales-history",
                    3
                )
            }

            "cashier" -> {
                shortcuts += buildShortcut(
                    context, "new-sale",
                    context.getString(R.string.shortcut_new_sale),
                    context.getString(R.string.shortcut_new_sale_long),
                    "h360://shortcut/new-sale",
                    2
                )
                shortcuts += buildShortcut(
                    context, "sales-history",
                    context.getString(R.string.shortcut_sales_history),
                    context.getString(R.string.shortcut_sales_history_long),
                    "h360://shortcut/sales-history",
                    3
                )
            }

            "storekeeper" -> {
                shortcuts += buildShortcut(
                    context, "stock-mismatch",
                    context.getString(R.string.shortcut_stock_mismatch),
                    context.getString(R.string.shortcut_stock_mismatch_long),
                    "h360://shortcut/stock-mismatch",
                    2
                )
                shortcuts += buildShortcut(
                    context, "customers",
                    context.getString(R.string.shortcut_customers),
                    context.getString(R.string.shortcut_customers_long),
                    "h360://shortcut/customers",
                    3
                )
            }

            else -> {
                shortcuts += buildShortcut(
                    context, "new-sale",
                    context.getString(R.string.shortcut_new_sale),
                    context.getString(R.string.shortcut_new_sale_long),
                    "h360://shortcut/new-sale",
                    2
                )
                shortcuts += buildShortcut(
                    context, "sales-history",
                    context.getString(R.string.shortcut_sales_history),
                    context.getString(R.string.shortcut_sales_history_long),
                    "h360://shortcut/sales-history",
                    3
                )
            }
        }

        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun buildShortcut(
        context: Context,
        id: String,
        shortLabel: String,
        longLabel: String,
        deepLink: String,
        rank: Int
    ): ShortcutInfoCompat {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .setRank(rank)
            .build()
    }
}
