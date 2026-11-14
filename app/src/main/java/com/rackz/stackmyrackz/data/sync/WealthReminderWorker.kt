package com.rackz.stackmyrackz.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rackz.stackmyrackz.MainActivity
import com.rackz.stackmyrackz.R
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Posts a configurable Wealth check-in reminder notification and schedules the next occurrence. */
@HiltWorker
class WealthReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncPreferences: SyncPreferencesRepository,
    private val reminderScheduler: WealthReminderScheduler
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = syncPreferences.getSnapshot()
        if (!prefs.wealthReminderEnabled) {
            return Result.success()
        }

        val canNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (canNotify) {
            ensureNotificationChannel()
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_destination", "wealth")
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("Time to update your Wealth targets")
                .setContentText("Log this month's savings & investment progress in Racks.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }

        reminderScheduler.scheduleOrCancel(
            enabled = prefs.wealthReminderEnabled,
            frequency = prefs.wealthReminderFrequency,
            dayOfMonth = prefs.wealthReminderDayOfMonth,
            dayOfWeek = prefs.wealthReminderDayOfWeek,
            hour = prefs.wealthReminderHour,
            minute = prefs.wealthReminderMinute
        )

        return Result.success()
    }

    private fun ensureNotificationChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wealth Check-in Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled reminders to review and log monthly savings and investments."
            }
            manager.createNotificationChannel(channel)
        }
    }

    private companion object {
        const val CHANNEL_ID = "racks_wealth_reminders"
        const val NOTIFICATION_ID = 4102
    }
}
