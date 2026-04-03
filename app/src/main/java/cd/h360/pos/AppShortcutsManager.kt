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
        val idsInOrder = when (normalizedRole) {
            "admin" -> listOf("copilot", "stock-mismatch", "sales-history", "pos", "new-sale")
            "cashier" -> listOf("copilot", "pos", "new-sale", "sales-history", "stock-mismatch")
            "storekeeper" -> listOf("copilot", "stock-mismatch", "pos", "sales-history", "new-sale")
            else -> listOf("copilot", "pos", "new-sale", "sales-history", "stock-mismatch")
        }

        val shortcuts = idsInOrder.mapIndexed { index, id ->
            when (id) {
                "copilot" -> buildShortcut(
                    context, id,
                    context.getString(R.string.shortcut_copilot),
                    context.getString(R.string.shortcut_copilot_long),
                    "h360://shortcut/copilot",
                    index
                )
                "new-sale" -> buildShortcut(
                    context, id,
                    context.getString(R.string.shortcut_new_sale),
                    context.getString(R.string.shortcut_new_sale_long),
                    "h360://shortcut/new-sale",
                    index
                )
                "pos" -> buildShortcut(
                    context, id,
                    context.getString(R.string.shortcut_pos),
                    context.getString(R.string.shortcut_pos_long),
                    "h360://shortcut/pos",
                    index
                )
                "sales-history" -> buildShortcut(
                    context, id,
                    context.getString(R.string.shortcut_sales_history),
                    context.getString(R.string.shortcut_sales_history_long),
                    "h360://shortcut/sales-history",
                    index
                )
                else -> buildShortcut(
                    context, "stock-mismatch",
                    context.getString(R.string.shortcut_stock_mismatch),
                    context.getString(R.string.shortcut_stock_mismatch_long),
                    "h360://shortcut/stock-mismatch",
                    index
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
