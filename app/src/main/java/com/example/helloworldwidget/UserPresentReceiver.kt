package com.example.helloworldwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT ||
            !CounterRepository.isRefreshDueAfterUnlock(context)
        ) {
            return
        }

        val request = OneTimeWorkRequestBuilder<UnlockRefreshWorker>()
            .setInitialDelay(20, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UnlockRefreshWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
