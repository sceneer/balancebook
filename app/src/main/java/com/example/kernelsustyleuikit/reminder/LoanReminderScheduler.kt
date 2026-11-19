package com.example.kernelsustyleuikit.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.kernelsustyleuikit.finance.AppSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 每天 9:00 的本地提醒调度（非精确闹钟，Doze 下可能延后）。 */
object LoanReminderScheduler {

    private const val REQUEST_CODE = 1001

    fun syncFromSettings(context: Context, settings: AppSettings) {
        if (settings.loanReminders) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        val next = nextNineAmMillis()
        runCatching {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                next,
                AlarmManager.INTERVAL_DAY,
                pi,
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(pendingIntent(context)) }
    }

    /** 立即检查一次（应用回到前台时调用，不改变每日闹钟）。 */
    fun checkNow(context: Context) {
        if (LoanReminder.canPostNotifications(context)) {
            LoanReminder.notifyDueLoans(context)
        }
    }

    private fun nextNineAmMillis(): Long {
        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        var target = now.toLocalDate().atTime(LocalTime.of(9, 0)).atZone(zone)
        if (!target.isAfter(now)) {
            target = LocalDate.now().plusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone)
        }
        return target.toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LoanReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
