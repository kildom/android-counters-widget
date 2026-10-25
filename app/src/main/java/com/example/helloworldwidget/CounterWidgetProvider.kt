package com.example.helloworldwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class CounterWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val pendingResult = goAsync()
            CounterRepository.refreshIfStale(context) {
                updateAllWidgets(context)
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.helloworldwidget.REFRESH"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CounterWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.counter_widget)

            val addIntent = Intent(context, CounterActionActivity::class.java)
                .putExtra(CounterActionActivity.EXTRA_ACTION, CounterActionActivity.ACTION_ADD)
            views.setOnClickPendingIntent(
                R.id.add_button,
                PendingIntent.getActivity(
                    context,
                    widgetId * 10 + 1,
                    addIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

            val refreshIntent = Intent(context, CounterWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
            views.setOnClickPendingIntent(
                R.id.refresh_button,
                PendingIntent.getBroadcast(
                    context,
                    widgetId * 10 + 2,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

            val serviceIntent = Intent(context, CounterRemoteViewsService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(android.net.Uri.parse("widget://counter/$widgetId"))
            views.setRemoteAdapter(R.id.counter_list, serviceIntent)
            views.setEmptyView(R.id.counter_list, R.id.empty_message)

            val actionIntent = Intent(context, CounterActionActivity::class.java)
            views.setPendingIntentTemplate(
                R.id.counter_list,
                PendingIntent.getActivity(
                    context,
                    widgetId * 10 + 3,
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            )

            val error = CounterRepository.lastError(context)
            if (error.isNullOrBlank()) {
                views.setTextViewText(R.id.error_message, "")
            } else {
                views.setTextViewText(R.id.error_message, error)
            }

            manager.updateAppWidget(widgetId, views)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.counter_list)
        }
    }
}
