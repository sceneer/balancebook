package com.dsh.balancebook.reminder

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
import com.dsh.balancebook.R
import com.dsh.balancebook.data.BalanceBookRepository
import com.dsh.balancebook.finance.Loan
import com.dsh.balancebook.finance.LoanCalculator
import com.dsh.balancebook.finance.Money
import com.dsh.balancebook.ui.MainActivity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 本地借款到期提醒。每天 9:00 由 AlarmManager 触发，检查 3 天内到期（含逾期）的未结清借款。
 * 同一笔借款的同一期在同一天最多通知一次。
 */
object LoanReminder {

    const val CHANNEL_ID = "loan_reminders"
    const val NOTIFICATION_ID = 2001
    private const val PREFS = "reminder_state"
    private const val KEY_NOTIFIED = "notified_keys"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "借款到期提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "每天提醒 3 天内到期或已逾期的借款"
            enableLights(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 检查并发送提醒；返回是否真的发出了通知。 */
    fun notifyDueLoans(context: Context, today: LocalDate = LocalDate.now()): Boolean {
        val state = BalanceBookRepository.currentState()
        if (!state.settings.loanReminders) return false
        if (!canPostNotifications(context)) return false

        val dueSoon = ArrayList<Pair<Loan, Long>>() // loan to daysUntilDue
        for (loan in state.loans) {
            if (LoanCalculator.settled(loan)) continue
            if (!loan.reminderEnabled) continue
            val next = LoanCalculator.nextInstallment(loan) ?: continue
            val days = ChronoUnit.DAYS.between(today, next.dueDate)
            if (days <= 3) dueSoon += loan to days
        }
        if (dueSoon.isEmpty()) return false

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val todayPrefix = "$today|"
        val already = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
            .filter { it.startsWith(todayPrefix) }
            .toMutableSet()

        val newOnes = dueSoon.filter { (loan, _) ->
            val next = LoanCalculator.nextInstallment(loan)
            next != null && !already.contains(todayPrefix + loan.id + ":" + next.period)
        }
        if (newOnes.isEmpty()) return false

        for ((loan, _) in newOnes) {
            val next = LoanCalculator.nextInstallment(loan) ?: continue
            already += todayPrefix + loan.id + ":" + next.period
        }
        prefs.edit().putStringSet(KEY_NOTIFIED, already).apply()

        val overdueCount = newOnes.count { it.second < 0 }
        val todayCount = newOnes.count { it.second == 0L }
        val futureCount = newOnes.count { it.second > 0 }
        val title = when {
            overdueCount > 0 && (todayCount + futureCount) > 0 ->
                "逾期 $overdueCount 笔 / ${todayCount + futureCount} 笔即将到期"
            overdueCount > 0 -> "逾期 $overdueCount 笔"
            else -> "${todayCount + futureCount} 笔借款即将到期"
        }

        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)

        val style = NotificationCompat.InboxStyle()
        newOnes.sortedBy { it.second }.forEach { (loan, days) ->
            val next = LoanCalculator.nextInstallment(loan) ?: return@forEach
            val status = when {
                days < 0 -> "已逾期 ${-days} 天"
                days == 0L -> "今天到期"
                else -> "还有 $days 天到期"
            }
            val line = "${loan.friend} · 本期应还 ${Money.formatCents(LoanCalculator.nextRemainingCents(loan))} · $status"
            style.addLine(line)
        }
        builder.setStyle(style)
        // 正文也放一行摘要，兼容不展示收件箱样式的系统。
        val summary = newOnes.sortedBy { it.second }.take(4).joinToString("\n") { (loan, days) ->
            val next = LoanCalculator.nextInstallment(loan)
            val status = when {
                days < 0 -> "已逾期 ${-days} 天"
                days == 0L -> "今天到期"
                else -> "$days 天后到期"
            }
            "${loan.friend} · 本期应还 ${Money.formatCents(LoanCalculator.nextRemainingCents(loan))} · $status"
        }
        builder.setContentText(summary)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.dsh.balancebook.OPEN_LOANS"
            putExtra(MainActivity.EXTRA_OPEN_LOANS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

