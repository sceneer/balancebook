package com.example.kernelsustyleuikit.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kernelsustyleuikit.data.BalanceBookRepository

/** 每日 9:00 的提醒触发。 */
class LoanReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BalanceBookRepository.ensureInitialized(context)
        if (BalanceBookRepository.currentState().settings.loanReminders) {
            LoanReminder.ensureChannel(context)
            LoanReminder.notifyDueLoans(context)
            LoanReminderScheduler.schedule(context)
        }
    }
}
