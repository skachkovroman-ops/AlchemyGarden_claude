package com.example.alchemygarden.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "expeditions"
private const val WORK_NAME = "expedition_done"
private const val KEY_BIOME = "biome"

class ExpeditionNotifier(private val context: Context) {

    fun schedule(biomeName: String, finishAtMillis: Long) {
        val delay = (finishAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ExpeditionWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_BIOME to biomeName))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(CHANNEL_ID, "Экспедиции", NotificationManager.IMPORTANCE_DEFAULT)
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }
    }
}

class ExpeditionWorker(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        val biome = inputData.getString(KEY_BIOME) ?: "острова"
        val manager = NotificationManagerCompat.from(ctx)
        if (!manager.areNotificationsEnabled()) return Result.success()

        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle("Алхимик вернулся!")
            .setContentText("Я добрался до «$biome» и нашёл кое-что интересное!")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(1001, notification)
        return Result.success()
    }
}
