package cd.h360.pos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class H360InsightsCardsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        H360WidgetUpdater.refreshFromRemoteIfDue(context, force = true)
        refreshAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_CATEGORY_SALES -> setCategory(context, CATEGORY_SALES)
            ACTION_CATEGORY_STOCK -> setCategory(context, CATEGORY_STOCK)
            ACTION_CATEGORY_HEALTH -> setCategory(context, CATEGORY_HEALTH)
            ACTION_REFRESH -> H360WidgetUpdater.refreshFromRemoteIfDue(context, force = true)
        }
        H360WidgetUpdater.refreshFromRemoteIfDue(context, force = true)
        refreshAll(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        H360WidgetUpdater.refreshFromRemoteIfDue(context, force = true)
        H360WidgetUpdater.scheduleRealtimeRefresh(context)
    }

    companion object {
        const val ACTION_CATEGORY_SALES = "cd.h360.pos.ACTION_CATEGORY_SALES"
        const val ACTION_CATEGORY_STOCK = "cd.h360.pos.ACTION_CATEGORY_STOCK"
        const val ACTION_CATEGORY_HEALTH = "cd.h360.pos.ACTION_CATEGORY_HEALTH"
        const val ACTION_REFRESH = "cd.h360.pos.ACTION_INSIGHTS_REFRESH"

        private const val KEY_INSIGHTS_CATEGORY = "insights_category"
        private const val CATEGORY_SALES = "sales"
        private const val CATEGORY_STOCK = "stock"
        private const val CATEGORY_HEALTH = "health"
        private const val DASHBOARD_URL = "https://stack.git.h360.cd/home"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, H360InsightsCardsWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val prefs = context.getSharedPreferences(H360WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
            val cat = prefs.getString(KEY_INSIGHTS_CATEGORY, CATEGORY_SALES) ?: CATEGORY_SALES
            val syncState = prefs.getString(H360WidgetUpdater.KEY_REMOTE_SYNC_STATE, "idle") ?: "idle"
            val syncMessage = prefs.getString(H360WidgetUpdater.KEY_REMOTE_SYNC_MESSAGE, "En attente de sync") ?: "En attente de sync"
            val diagLabel = prefs.getString(H360WidgetUpdater.KEY_REMOTE_DIAG_LABEL, "").orEmpty()
            val hasSynced = prefs.getLong(H360WidgetUpdater.KEY_LAST_REMOTE_FETCH_MS, 0L) > 0L
            val periodLabel = prefs.getString(H360WidgetUpdater.KEY_INSIGHTS_PERIOD_LABEL, "") ?: ""
            val dateFrom = prefs.getString(H360WidgetUpdater.KEY_FILTER_DATE_FROM, "") ?: ""
            val dateTo = prefs.getString(H360WidgetUpdater.KEY_FILTER_DATE_TO, "") ?: ""
            val locationName = prefs.getString(H360WidgetUpdater.KEY_FILTER_LOCATION_NAME, context.getString(R.string.widget_all_locations))
                ?: context.getString(R.string.widget_all_locations)
            val salesToday = prefs.getString(H360WidgetUpdater.KEY_SALES_TODAY, "0") ?: "0"
            val salesYesterday = prefs.getString(H360WidgetUpdater.KEY_SALES_YESTERDAY, "0") ?: "0"
            val salesVsYesterday = if (prefs.contains(H360WidgetUpdater.KEY_SALES_VS_YESTERDAY_PCT)) {
                prefs.getInt(H360WidgetUpdater.KEY_SALES_VS_YESTERDAY_PCT, 0)
            } else null
            val ticketsToday = prefs.getInt(H360WidgetUpdater.KEY_TICKETS_TODAY, 0)
            val avgTicket = prefs.getString(H360WidgetUpdater.KEY_AVG_TICKET, "0") ?: "0"
            val salesChangePct = if (prefs.contains(H360WidgetUpdater.KEY_SALES_CHANGE_PCT)) {
                prefs.getInt(H360WidgetUpdater.KEY_SALES_CHANGE_PCT, 0)
            } else null
            val salesWeekChangePct = if (prefs.contains(H360WidgetUpdater.KEY_SALES_CHANGE_WEEK_PCT)) {
                prefs.getInt(H360WidgetUpdater.KEY_SALES_CHANGE_WEEK_PCT, 0)
            } else null
            val avgTicketChangePct = if (prefs.contains(H360WidgetUpdater.KEY_AVG_TICKET_CHANGE_PCT)) {
                prefs.getInt(H360WidgetUpdater.KEY_AVG_TICKET_CHANGE_PCT, 0)
            } else null
            val lastSaleMinutes = if (prefs.contains(H360WidgetUpdater.KEY_LAST_SALE_MINUTES)) {
                prefs.getInt(H360WidgetUpdater.KEY_LAST_SALE_MINUTES, 0)
            } else null
            val topProductName = prefs.getString(H360WidgetUpdater.KEY_TOP_PRODUCT_NAME, "") ?: ""
            val topProductQty = prefs.getInt(H360WidgetUpdater.KEY_TOP_PRODUCT_QTY, 0)
            val topProductChangePct = if (prefs.contains(H360WidgetUpdater.KEY_TOP_PRODUCT_CHANGE_PCT)) {
                prefs.getInt(H360WidgetUpdater.KEY_TOP_PRODUCT_CHANGE_PCT, 0)
            } else null
            val trendHint = prefs.getString(H360WidgetUpdater.KEY_TREND_HINT, "") ?: ""
            val suggestedAction = prefs.getString(H360WidgetUpdater.KEY_SUGGESTED_ACTION, "") ?: ""
            val salesTrend = prefs.getString(H360WidgetUpdater.KEY_SALES_TREND, "Stable") ?: "Stable"
            val lowStock = prefs.getInt(H360WidgetUpdater.KEY_LOW_STOCK, 0)
            val mismatch = prefs.getInt(H360WidgetUpdater.KEY_STOCK_MISMATCH, 0)
            val expense = prefs.getString(H360WidgetUpdater.KEY_EXPENSE_TODAY, "0") ?: "0"
            val profit = prefs.getString(H360WidgetUpdater.KEY_PROFIT_TODAY, "0") ?: "0"
            val overdue = prefs.getInt(H360WidgetUpdater.KEY_OVERDUE_INVOICES, 0)
            val collection = prefs.getString(H360WidgetUpdater.KEY_COLLECTION_RATE, "0%") ?: "0%"
            val adviceTitle = prefs.getString(H360WidgetUpdater.KEY_INSIGHTS_ADVICE_TITLE, "") ?: ""
            val adviceMessage = prefs.getString(H360WidgetUpdater.KEY_INSIGHTS_ADVICE_MESSAGE, "") ?: ""

            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_h360_insights_cards)
                views.setTextViewText(
                    R.id.widgetCardsTitle,
                    when (cat) {
                        CATEGORY_STOCK -> context.getString(R.string.insights_title_stock)
                        CATEGORY_HEALTH -> context.getString(R.string.insights_title_health)
                        else -> context.getString(R.string.insights_title_sales)
                    }
                )
                val periodText = periodLabel.ifBlank {
                    if (dateFrom.isNotBlank() && dateTo.isNotBlank()) "$dateFrom -> $dateTo" else ""
                }
                val subtitle = listOf(syncMessage, periodText, locationName)
                    .filter { it.isNotBlank() }
                    .joinToString(" | ")
                val subtitleWithDiag = if (diagLabel.isNotBlank()) "$subtitle\n$diagLabel" else subtitle
                views.setTextViewText(R.id.widgetCardsSubtitle, subtitleWithDiag)
                views.setTextColor(
                    R.id.widgetCardsSubtitle,
                    if (syncState == "ok") 0xFF8FD19E.toInt() else 0xFFFFB4B4.toInt()
                )

                val blocked = !hasSynced && syncState != "ok"
                val safeSalesToday = if (blocked) "--" else H360WidgetUpdater.formatMoneyDisplay(context, salesToday)
                val safeTicketsToday = if (blocked) "--" else ticketsToday.toString()
                val safeAvgTicket = if (blocked) "--" else H360WidgetUpdater.formatMoneyDisplay(context, avgTicket)
                val safeSalesChange = if (blocked || salesVsYesterday == null) "" else {
                    val sign = if (salesVsYesterday > 0) "+" else ""
                    " (${sign}${salesVsYesterday}% vs hier)"
                }
                val safeSalesWithChange = if (safeSalesChange.isNotBlank()) "$safeSalesToday$safeSalesChange" else safeSalesToday
                val safeYesterdaySales = if (blocked) "--" else H360WidgetUpdater.formatMoneyDisplay(context, salesYesterday)
                val safeSalesWeekChange = if (blocked || salesWeekChangePct == null) "" else {
                    val sign = if (salesWeekChangePct > 0) "+" else ""
                    " (${sign}${salesWeekChangePct}% vs S-1)"
                }
                val safeSalesWithWeek = if (safeSalesWeekChange.isNotBlank()) "$safeSalesToday$safeSalesWeekChange" else safeSalesToday
                val safeAvgTicketChange = if (blocked || avgTicketChangePct == null) "" else {
                    val sign = if (avgTicketChangePct > 0) "+" else ""
                    " ${sign}${avgTicketChangePct}%"
                }
                val safeLastSale = if (blocked || lastSaleMinutes == null) "--" else "${lastSaleMinutes} min"
                val safeTopProduct = if (blocked || topProductName.isBlank()) "--" else {
                    val qty = if (topProductQty > 0) " (${topProductQty})" else ""
                    "${topProductName}$qty"
                }
                val safeTopChange = if (blocked || topProductChangePct == null) "" else {
                    val sign = if (topProductChangePct > 0) "+" else ""
                    " ${sign}${topProductChangePct}%"
                }
                val safeLowStock = if (blocked) "--" else lowStock.toString()
                val safeMismatch = if (blocked) "--" else mismatch.toString()
                val safeExpense = if (blocked) "--" else H360WidgetUpdater.formatMoneyDisplay(context, expense)
                val safeProfit = if (blocked) "--" else H360WidgetUpdater.formatMoneyDisplay(context, profit)
                val safeOverdue = if (blocked) "--" else overdue.toString()
                val safeCollection = if (blocked) "--" else collection
                val safeTrend = if (blocked) "--" else salesTrend

                val safeTip = if (blocked || trendHint.isBlank()) "" else "${context.getString(R.string.insights_tip_prefix)} $trendHint"
                val safeAction = if (blocked || suggestedAction.isBlank()) "" else suggestedAction
                val safeAdvice = if (blocked || adviceMessage.isBlank()) "" else {
                    if (adviceTitle.isBlank()) adviceMessage else "$adviceTitle: $adviceMessage"
                }
                val tipText = safeAdvice.ifBlank { safeTip.ifBlank { safeAction } }

                val kpis: List<Pair<String, String>> = when (cat) {
                    CATEGORY_STOCK -> listOf(
                        context.getString(R.string.insights_kpi_low_stock) to safeLowStock,
                        context.getString(R.string.insights_kpi_mismatch) to safeMismatch,
                        context.getString(R.string.insights_kpi_top_product) to "${safeTopProduct}${safeTopChange}",
                        context.getString(R.string.insights_kpi_last_sale) to safeLastSale
                    )
                    CATEGORY_HEALTH -> listOf(
                        context.getString(R.string.insights_kpi_profit) to safeProfit,
                        context.getString(R.string.insights_kpi_expense) to safeExpense,
                        context.getString(R.string.insights_kpi_overdue) to safeOverdue,
                        context.getString(R.string.insights_kpi_collection) to safeCollection
                    )
                    else -> listOf(
                        context.getString(R.string.insights_kpi_sales_period) to safeSalesWithChange,
                        context.getString(R.string.insights_kpi_sales_yesterday) to safeYesterdaySales,
                        context.getString(R.string.insights_kpi_tickets_avg) to "${safeTicketsToday} | ${safeAvgTicket}${safeAvgTicketChange}",
                        context.getString(R.string.insights_kpi_low_stock) to safeLowStock
                    )
                }
                if (tipText.isNotBlank()) {
                    val tipColor = when {
                        safeAction.contains("Nouvelle vente", ignoreCase = true) -> 0xFFFFD166.toInt()
                        salesWeekChangePct != null && salesWeekChangePct < 0 -> 0xFFFF8FA3.toInt()
                        trendHint.contains("Baisse", ignoreCase = true) -> 0xFFFF8FA3.toInt()
                        else -> 0xFF8FD19E.toInt()
                    }
                    views.setTextViewText(R.id.widgetInsightTip, tipText)
                    views.setTextColor(R.id.widgetInsightTip, tipColor)
                    views.setViewVisibility(R.id.widgetInsightTip, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widgetInsightTip, android.view.View.GONE)
                }

                views.setTextViewText(R.id.card1Label, kpis[0].first)
                views.setTextViewText(R.id.card1Value, kpis[0].second)
                views.setTextViewText(R.id.card2Label, kpis[1].first)
                views.setTextViewText(R.id.card2Value, kpis[1].second)
                views.setTextViewText(R.id.card3Label, kpis[2].first)
                views.setTextViewText(R.id.card3Value, kpis[2].second)
                views.setTextViewText(R.id.card4Label, kpis[3].first)
                views.setTextViewText(R.id.card4Value, kpis[3].second)

                if (cat == CATEGORY_SALES && hasSynced) {
                    val chart = H360WidgetUpdater.renderSalesSparkline(context)
                    if (chart != null) {
                        views.setImageViewBitmap(R.id.widgetSalesChart, chart)
                        views.setViewVisibility(R.id.widgetSalesChart, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widgetSalesChart, android.view.View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.widgetSalesChart, android.view.View.GONE)
                }

                views.setTextColor(R.id.tabSales, if (cat == CATEGORY_SALES) 0xFFFFFFFF.toInt() else 0xFFB7C9E8.toInt())
                views.setTextColor(R.id.tabStock, if (cat == CATEGORY_STOCK) 0xFFFFFFFF.toInt() else 0xFFB7C9E8.toInt())
                views.setTextColor(R.id.tabHealth, if (cat == CATEGORY_HEALTH) 0xFFFFFFFF.toInt() else 0xFFB7C9E8.toInt())

                views.setOnClickPendingIntent(R.id.tabSales, tabPendingIntent(context, ACTION_CATEGORY_SALES, 401))
                views.setOnClickPendingIntent(R.id.tabStock, tabPendingIntent(context, ACTION_CATEGORY_STOCK, 402))
                views.setOnClickPendingIntent(R.id.tabHealth, tabPendingIntent(context, ACTION_CATEGORY_HEALTH, 403))
                views.setOnClickPendingIntent(R.id.widgetCardsRefresh, tabPendingIntent(context, ACTION_REFRESH, 404))
                views.setOnClickPendingIntent(R.id.widgetCardsOpen, openInsightsPage(context))

                manager.updateAppWidget(widgetId, views)
            }
        }

        private fun setCategory(context: Context, category: String) {
            val prefs = context.getSharedPreferences(H360WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_INSIGHTS_CATEGORY, category).apply()
        }

        private fun tabPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, H360InsightsCardsWidgetProvider::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openInsightsPage(context: Context): PendingIntent {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(DASHBOARD_URL),
                context,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                499,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
