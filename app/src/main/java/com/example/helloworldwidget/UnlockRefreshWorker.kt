package com.example.helloworldwidget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class UnlockRefreshWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : Worker(context, workerParameters) {
    override fun doWork(): Result {
        if (CounterRepository.refreshAfterUnlock(applicationContext)) {
            CounterWidgetProvider.updateAllWidgets(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "refresh-counters-after-unlock"
    }
}
