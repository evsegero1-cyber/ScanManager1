package com.example.warehouseapp.reminder

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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.warehouseapp.MainActivity
import com.example.warehouseapp.R
import com.example.warehouseapp.domain.RouteItem
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL_ID = "route_reminder"
    const val EXTRA_ROUTE_ID = "open_route_id"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL_ID, "Напоминания маршрутов", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Напоминания пополнить товар из маршрута"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun workName(itemId: String) = "reminder_$itemId"

    /** Ставит напоминание. Уникальное имя — повторная постановка заменяет старую, а не дублирует. */
    fun schedule(context: Context, item: RouteItem, routeName: String) {
        val at = item.reminderAt ?: return
        val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "itemId" to item.id, "routeId" to item.routeId, "routeName" to routeName,
                    "product" to item.productName, "cell" to item.cellNumber, "qty" to item.quantity,
                )
            )
            .addTag("reminders")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(item.id), ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(context: Context, itemId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(itemId))
        NotificationManagerCompat.from(context).cancel(itemId.hashCode())
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val itemId = inputData.getString("itemId") ?: return Result.failure()
        val routeId = inputData.getString("routeId").orEmpty()
        val product = inputData.getString("product").orEmpty()
        val cell = inputData.getString("cell").orEmpty()
        val qty = inputData.getString("qty").orEmpty()
        val routeName = inputData.getString("routeName").orEmpty()
        if (!Reminders.canNotify(applicationContext)) return Result.success()

        val open = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Reminders.EXTRA_ROUTE_ID, routeId)
        }
        val pi = PendingIntent.getActivity(
            applicationContext, itemId.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = buildString {
            append(product.ifBlank { "Товар" })
            if (qty.isNotBlank()) append(" × ").append(qty)
            if (cell.isNotBlank()) append(" · ячейка ").append(cell)
        }
        val n = NotificationCompat.Builder(applicationContext, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (routeName.isNotBlank()) "Пора пополнить · $routeName" else "Пора пополнить товар")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(itemId.hashCode(), n)
        } catch (_: SecurityException) { }
        return Result.success()
    }
}
