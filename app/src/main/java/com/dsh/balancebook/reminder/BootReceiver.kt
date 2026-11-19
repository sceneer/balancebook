package com.dsh.balancebook.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dsh.balancebook.data.BalanceBookRepository

/** 开机 / 应用更新后重排每日提醒。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        BalanceBookRepository.ensureInitialized(context)
        val settings = BalanceBookRepository.currentState().settings
        LoanReminderScheduler.syncFromSettings(context, settings)
        if (settings.loanReminders) {
            LoanReminder.ensureChannel(context)
        }
    }
}
