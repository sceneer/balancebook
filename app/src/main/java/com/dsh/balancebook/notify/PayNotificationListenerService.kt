package com.dsh.balancebook.notify

import android.app.Notification
import android.content.ComponentName
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.dsh.balancebook.BuildConfig
import com.dsh.balancebook.finance.PayNotificationParser
import com.dsh.balancebook.finance.RawPayNotification
import com.dsh.balancebook.finance.TransactionSource

/**
 * 通知使用权服务：只在系统授予「通知使用权」后才会被绑定。
 *
 * 只关心微信 / 支付宝这两个包名的通知，把标题与正文交给纯 Kotlin 的
 * [PayNotificationParser] 解析；识别为支付/收款后再走 [AutoBookkeeping] 入库。
 * 解析与入库失败都只记录日志，不影响系统通知服务本身。
 */
class PayNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "PayNotificationListener"

        /** adb shell 投递通知时的包名，仅 debug 构建用于端到端自测。 */
        private const val SHELL_PACKAGE = "com.android.shell"
    }

    /**
     * 通知包名 → 账单来源。
     *
     * debug 构建额外接受 adb shell 投递的测试通知并当作微信通知，
     * 便于在没有真实支付的情况下验证「通知 → 解析 → 自动记账」全链路；
     * release 构建中 BuildConfig.DEBUG 为 false，该分支不会生效。
     */
    private fun resolveSource(packageName: String): TransactionSource? {
        PayNotificationParser.sourceFor(packageName)?.let { return it }
        if (BuildConfig.DEBUG && packageName == SHELL_PACKAGE) return TransactionSource.WECHAT
        return null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "通知使用权已连接，开始监听微信 / 支付宝支付通知")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "通知使用权已断开，尝试重新绑定")
        runCatching { requestRebind(ComponentName(this, PayNotificationListenerService::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (BuildConfig.DEBUG) {
            val ex = notification.notification.extras
            Log.d(
                TAG,
                "收到通知：pkg=${notification.packageName} flags=${notification.notification.flags} " +
                    "title=${ex?.getCharSequence(Notification.EXTRA_TITLE)} " +
                    "text=${ex?.getCharSequence(Notification.EXTRA_TEXT)} " +
                    "big=${ex?.getCharSequence(Notification.EXTRA_BIG_TEXT)}",
            )
        }
        try {
            handleNotification(notification)
        } catch (e: Throwable) {
            // 系统回调里绝不能把异常抛回去。
            Log.w(TAG, "处理通知失败：${e.message}")
        }
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        if (sbn.user != Process.myUserHandle()) return
        val source = resolveSource(sbn.packageName) ?: return
        val effectivePackage = if (source == TransactionSource.WECHAT && sbn.packageName == SHELL_PACKAGE) {
            // debug 自测：shell 通知按微信通知解析，包名要一起替换，否则解析器会再判一次包名。
            PayNotificationParser.WECHAT_PACKAGE
        } else {
            sbn.packageName
        }

        val notification = sbn.notification ?: return
        val flags = notification.flags
        // 分组摘要 / 常驻前台通知通常是聚合文案，跳过避免重复记账。
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = notification.extras ?: return
        val raw = RawPayNotification(
            packageName = effectivePackage,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString() }
                ?: emptyList(),
            postedAtMillis = sbn.postTime,
        )
        val parsed = PayNotificationParser.parse(raw) ?: return
        Log.i(
            TAG,
            "识别到${source.label}支付通知：${parsed.type} ${parsed.amountCents} 分 · ${parsed.merchant}",
        )
        AutoBookkeeping.onPaymentNotification(applicationContext, parsed)
    }
}
