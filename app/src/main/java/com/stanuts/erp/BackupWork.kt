package com.stanuts.erp

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class BackupReminderWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        Prefs.get(applicationContext).edit().putBoolean("due", true).apply()
        return Result.success()
    }
}

object BackupScheduler {
    fun apply(c: Context, on: Boolean) {
        val wm = WorkManager.getInstance(c)
        if (on) wm.enqueueUniquePeriodicWork(
            "weekly_backup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupReminderWorker>(7, TimeUnit.DAYS).build()
        ) else wm.cancelUniqueWork("weekly_backup")
    }
}
