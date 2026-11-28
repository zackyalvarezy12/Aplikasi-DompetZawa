package com.example.worker

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.NusaKasDatabase
import com.example.data.local.TxTypes
import com.example.data.prefs.AppPreferences
import com.example.domain.BillReminderAlert
import com.example.domain.BillReminderEvaluator
import com.example.domain.CurrencyFormatter
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class BillReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        val settings = prefs.settingsFlow.first()
        if (!settings.isBillReminderEnabled) {
            return Result.success()
        }

        val dao = NusaKasDatabase.getInstance(applicationContext).dao()
        val activeRules = dao.getActiveReminderRulesSnapshot()
        val alerts = BillReminderEvaluator.findPendingReminders(
            rules = activeRules,
            alreadyNotifiedKeys = settings.notifiedBillKeys
        )

        if (alerts.isNotEmpty()) {
            ensureNotificationChannel(applicationContext)
            if (hasNotificationPermission(applicationContext)) {
                alerts.forEach { alert ->
                    postReminderNotification(applicationContext, alert)
                }
                prefs.addNotifiedBillKeys(alerts.map { it.dedupKey }.toSet())
            }
        }

        // --- BUDGET 80% & 95% THRESHOLD NOTIFICATIONS ---
        val monthKey = CurrencyFormatter.currentMonthKey()
        val budgets = dao.getAllBudgetsSnapshot().filter { it.workspaceId == settings.activeWorkspaceId && it.monthYear == monthKey }
        val transactions = dao.getAllTransactionsSnapshot().filter { it.workspaceId == settings.activeWorkspaceId }

        val monthTxs = transactions.filter {
            (it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT) &&
                CurrencyFormatter.currentMonthKey(it.dateMillis) == monthKey
        }
        val totalSpent = monthTxs.sumOf { it.amount }
        val totalLimit = budgets.sumOf { it.limitAmount }

        if (totalLimit > 0L) {
            val ratio = totalSpent.toDouble() / totalLimit.toDouble()
            val notifiedKeys = settings.notifiedBillKeys

            if (ratio >= 0.95) {
                val alertKey = "budget_95_$monthKey"
                if (!notifiedKeys.contains(alertKey)) {
                    ensureNotificationChannel(applicationContext)
                    if (hasNotificationPermission(applicationContext)) {
                        postBudgetAlertNotification(applicationContext, 95, totalSpent, totalLimit)
                        prefs.addNotifiedBillKeys(setOf(alertKey))
                    }
                }
            } else if (ratio >= 0.80) {
                val alertKey = "budget_80_$monthKey"
                if (!notifiedKeys.contains(alertKey)) {
                    ensureNotificationChannel(applicationContext)
                    if (hasNotificationPermission(applicationContext)) {
                        postBudgetAlertNotification(applicationContext, 80, totalSpent, totalLimit)
                        prefs.addNotifiedBillKeys(setOf(alertKey))
                    }
                }
            }
        }

        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "dompet_zawa_bill_reminders"
        private const val PERIODIC_WORK_NAME = "bill_reminder_periodic_check"
        private const val IMMEDIATE_WORK_NAME = "bill_reminder_immediate_check"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<BillReminderWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun triggerImmediateCheck(context: Context) {
            val request = OneTimeWorkRequestBuilder<BillReminderWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        }

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Pengingat Tagihan & Anggaran",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifikasi otomatis tagihan dan peringatan batas anggaran 80% & 95%"
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }

        fun postReminderNotification(context: Context, alert: BillReminderAlert) {
            ensureNotificationChannel(context)
            if (!hasNotificationPermission(context)) return

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, "WEALTH")
                putExtra(MainActivity.EXTRA_OPEN_SUBTAB, 3)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                alert.dedupKey.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val urgencyPrefix = if (alert.daysUntilDue == 1) "Besok Jatuh Tempo" else "3 Hari Lagi Jatuh Tempo"
            val title = "$urgencyPrefix: ${alert.rule.title}"
            val body = "Siapkan ${CurrencyFormatter.formatIdr(alert.rule.amount)} sebelum ${alert.dueDateLabel}."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_cat_bills)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(alert.dedupKey.hashCode(), notification)
            } catch (_: SecurityException) {}
        }

        fun postBudgetAlertNotification(context: Context, percentage: Int, spent: Long, limit: Long) {
            ensureNotificationChannel(context)
            if (!hasNotificationPermission(context)) return

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, "ADVISOR")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                "budget_$percentage".hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (percentage >= 95) "Peringatan: Anggaran Bulanan Hampir Habis (95%)!" else "Perhatian: Pengeluaran Bulanan Mencapai 80%!"
            val body = "Pengeluaran Anda saat ini ${CurrencyFormatter.formatIdr(spent)} dari batas ${CurrencyFormatter.formatIdr(limit)}."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_advisor_ai)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify("budget_$percentage".hashCode(), notification)
            } catch (_: SecurityException) {}
        }

        fun sendSampleTestNotification(context: Context): Boolean {
            if (!hasNotificationPermission(context)) return false
            ensureNotificationChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, "WEALTH")
                putExtra(MainActivity.EXTRA_OPEN_SUBTAB, 3)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                99001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID) // Wait, NotificationCompat.Builder
                .setSmallIcon(R.drawable.ic_logo_cat_bills)
                .setContentTitle("Pengingat Anggaran & Tagihan Aktif ✨")
                .setContentText("Dompet Zawa akan memberitahu Anda otomatis saat anggaran mencapai 80% & 95% serta pengingat tagihan.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            return try {
                NotificationManagerCompat.from(context).notify(99001, notification)
                true
            } catch (_: SecurityException) {
                false
            }
        }
    }
}
