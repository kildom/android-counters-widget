package com.example.helloworldwidget

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.Locale

class CounterRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CounterViewsFactory(applicationContext)
}

private class CounterViewsFactory(
    private val context: android.content.Context,
) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<Counter> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        items = try {
            CounterRepository.counters(context).sortedBy { it.percentRemaining() }
        } catch (exception: Exception) {
            CounterRepository.reportError(context, exception.message ?: "Could not read saved counters.")
            emptyList()
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val counter = items.getOrNull(position) ?: return null
        val row = RemoteViews(context.packageName, R.layout.counter_widget_row)
        row.setTextViewText(R.id.counter_name, counter.name)
        val percent = counter.percentRemaining()
        row.setTextViewText(R.id.counter_percent, String.format(Locale.getDefault(), "%.0f%%", percent))
        row.setTextColor(
            R.id.counter_percent,
            context.getColor(if (percent < 0) R.color.counter_negative else R.color.counter_normal),
        )
        val days = counter.daysRemaining()
        val dayText = if (days == 1L || days == -1L) "day" else "days"
        row.setTextViewText(R.id.counter_days, "($days $dayText)")

        val fillInIntent = Intent()
            .putExtra(CounterActionActivity.EXTRA_COUNTER_ID, counter.id)
            .putExtra(CounterActionActivity.EXTRA_COUNTER_NAME, counter.name)
            .putExtra(CounterActionActivity.EXTRA_COUNTER_PERIOD, counter.period)
            .putExtra(CounterActionActivity.EXTRA_COUNTER_TARGET, counter.targetDate)
        row.setOnClickFillInIntent(R.id.counter_menu_button, fillInIntent)
        return row
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
