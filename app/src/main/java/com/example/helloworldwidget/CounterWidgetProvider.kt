package com.example.helloworldwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
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

            val settingsIntent = Intent(context, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_OPENED_FROM_WIDGET, true)
            views.setOnClickPendingIntent(
                R.id.settings_button,
                PendingIntent.getActivity(
                    context,
                    widgetId * 10 + 4,
                    settingsIntent,
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
            val buttonBackground = CounterRepository.textColor(context)
            val buttonText = if (hasLightTextContrast(buttonBackground)) {
                android.graphics.Color.WHITE
            } else {
                android.graphics.Color.BLACK
            }
            listOf(R.id.add_button, R.id.settings_button, R.id.refresh_button).forEach { buttonId ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(
                        buttonId,
                        "setBackgroundTintList",
                        ColorStateList.valueOf(buttonBackground),
                    )
                } else {
                    views.setInt(buttonId, "setBackgroundColor", buttonBackground)
                }
                views.setTextColor(buttonId, buttonText)
            }
            views.setTextViewTextSize(R.id.settings_button, android.util.TypedValue.COMPLEX_UNIT_SP, 28f)
            views.setTextViewTextSize(R.id.refresh_button, android.util.TypedValue.COMPLEX_UNIT_SP, 28f)
            views.setInt(R.id.counter_widget_root, "setBackgroundColor", CounterRepository.backgroundColor(context))
            views.setTextColor(R.id.error_message, CounterRepository.overdueColor(context))
            views.setTextColor(R.id.empty_message, CounterRepository.textColor(context))

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

        private fun hasLightTextContrast(color: Int): Boolean {
            fun linear(channel: Int): Double {
                val normalized = channel / 255.0
                return if (normalized <= 0.04045) {
                    normalized / 12.92
                } else {
                    Math.pow((normalized + 0.055) / 1.055, 2.4)
                }
            }

            val luminance =
                0.2126 * linear(android.graphics.Color.red(color)) +
                    0.7152 * linear(android.graphics.Color.green(color)) +
                    0.0722 * linear(android.graphics.Color.blue(color))
            return luminance < 0.179
        }
    }
}
