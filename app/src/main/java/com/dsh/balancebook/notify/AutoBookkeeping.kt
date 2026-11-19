package com.dsh.balancebook.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dsh.balancebook.R
import com.dsh.balancebook.data.BalanceBookRepository
import com.dsh.balancebook.finance.Money
import com.dsh.balancebook.finance.ParsedPayNotification
import com.dsh.balancebook.finance.Transaction
import com.dsh.balancebook.finance.TransactionType
import com.dsh.balancebook.reminder.LoanReminder
import com.dsh.balancebook.ui.MainActivity
import java.time.Instant
import java.time.ZoneId

/**
 * 自动记账写入口：把解析出的支付通知写入余额簿，并做去重。
 *
 * 去重依赖 [ParsedPayNotification.transactionId]（同一笔支付的不同通知得到同一个 id）：
 * 重复通知在仓库里已存在同 id 账单时直接忽略，因此不需要额外维护已读列表。
 * 写库走 [BalanceBookRepository] 的原子持久化，和手动记账完全一致。
 */
object AutoBookkeeping {

    private const val TAG = "AutoBookkeeping"
    const val CHANNEL_ID = "auto_bookkeeping"
    private const val NOTIFICATION_ID = 2101

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "自动记账",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "记录微信 / 支付宝支付通知后发送静默提示"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    /** 当前是否开启自动记账（设置页开关，默认关闭）。 */
    fun isEnabled(context: Context): Boolean {
        BalanceBookRepository.ensureInitialized(context)
        return BalanceBookRepository.currentState().settings.autoBookkeeping
    }

    /**
     * 处理一条已解析的支付通知。
     * @return true 表示本次新建了一笔账单；false 表示关闭、重复或已存在。
     */
    fun onPaymentNotification(context: Context, parsed: ParsedPayNotification): Boolean {
        val appContext = context.applicationContext
        BalanceBookRepository.ensureInitialized(appContext)
        val settings = BalanceBookRepository.currentState().settings
        if (!settings.autoBookkeeping) return false

        if (BalanceBookRepository.transactionById(parsed.transactionId) != null) {
            Log.d(TAG, "重复通知，忽略：${parsed.dedupeKey}")
            return false
        }
        // 防御：用户可能改过账单 id，但 autoKey 仍能识别同一笔支付。
        val duplicated = BalanceBookRepository.currentState().transactions.any {
            it.autoKey.isNotEmpty() && it.autoKey == parsed.dedupeKey
        }
        if (duplicated) return false

        val date = Instant.ofEpochMilli(parsed.postedAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val transaction = Transaction(
            id = parsed.transactionId,
            type = parsed.type,
            amountCents = parsed.amountCents,
            category = parsed.category,
            note = parsed.note.ifBlank { "${parsed.source.label}自动记账" },
            date = date,
            createdAt = parsed.postedAtMillis,
            source = parsed.source,
            autoKey = parsed.dedupeKey,
        )
        BalanceBookRepository.upsertTransaction(transaction)
        Log.i(TAG, "自动记账：${parsed.source.label} ${parsed.type} ${parsed.amountCents} 分，备注=${transaction.note}")

        if (settings.autoBookNotify) {
            notifyRecorded(appContext, parsed)
        }
        return true
    }

    private fun notifyRecorded(context: Context, parsed: ParsedPayNotification) {
        if (!LoanReminder.canPostNotifications(context)) return
        ensureChannel(context)
        val amount = Money.formatCentsExact(parsed.amountCents)
        val title = if (parsed.type == TransactionType.INCOME) {
            "${parsed.source.label}收款 ${amount} 已记账"
        } else {
            "${parsed.source.label}支付 ${amount} 已记账"
        }
        val detail = parsed.note.ifBlank {
            if (parsed.type == TransactionType.INCOME) "已自动记入收入" else "已自动记入支出"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(openBillsIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // 没有通知权限时静默失败即可，账单已经写入。
        }
    }

    private fun openBillsIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.dsh.balancebook.OPEN_BILLS"
            putExtra(MainActivity.EXTRA_OPEN_BILLS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            2101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
