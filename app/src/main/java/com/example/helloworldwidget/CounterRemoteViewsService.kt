package com.example.helloworldwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.view.Gravity
import kotlin.math.abs
import kotlin.math.roundToInt
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
            if (percent < 0) CounterRepository.overdueColor(context) else CounterRepository.textColor(context),
        )
        row.setTextColor(R.id.counter_name, CounterRepository.textColor(context))
        val days = counter.daysRemaining()
        row.setTextViewText(
            R.id.counter_days,
            context.getString(R.string.remaining_days_of_period, days, counter.period),
        )
        row.setTextColor(R.id.counter_days, CounterRepository.progressTextColor(context))
        row.setInt(
            R.id.counter_days,
            "setGravity",
            if (abs(percent) > 50.0) Gravity.LEFT or Gravity.CENTER_VERTICAL
            else Gravity.RIGHT or Gravity.CENTER_VERTICAL,
        )
        row.setImageViewBitmap(
            R.id.counter_progress_bar,
            progressBitmap(
                percent,
                if (percent < 0) CounterRepository.negativeProgressColor(context)
                else CounterRepository.positiveProgressColor(context),
                CounterRepository.progressBackgroundColor(context),
            ),
        )

        val fillInIntent = Intent()
            .putExtra(CounterActionActivity.EXTRA_COUNTER_ID, counter.id)
            .putExtra(CounterActionActivity.EXTRA_COUNTER_NAME, counter.name)
            .putExtra(CounterActionActivity.EXTRA_COUNTER_PERIOD, counter.period)
            .putExtra(CounterActionActivity.EXTRA_COUNTER_TARGET, counter.targetDate)
        row.setOnClickFillInIntent(R.id.counter_row, fillInIntent)
        return row
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun progressBitmap(percent: Double, progressColor: Int, backgroundColor: Int): Bitmap {
        val width = 512
        val height = 4
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = height / 2f
        paint.color = backgroundColor
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        val progressWidth = width * (abs(percent).coerceIn(0.0, 100.0) / 100.0).toFloat()
        if (progressWidth > 0f) {
            paint.color = progressColor
            canvas.drawRoundRect(0f, 0f, progressWidth, height.toFloat(), radius, radius, paint)
        }
        return bitmap
    }
}
