package com.example.kernelsustyleuikit.notify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 「通知使用权」检测与跳转。
 *
 * 通知使用权不能像普通运行时权限那样弹窗申请，只能由用户在系统设置里手动开启；
 * 这里只负责判断当前是否已授权，以及把用户送到对应的系统页面。
 */
object NotificationAccess {

    fun componentName(context: Context): ComponentName =
        ComponentName(context.packageName, PayNotificationListenerService::class.java.name)

    /** 是否已在系统设置中允许「余额簿」读取通知。 */
    fun isGranted(context: Context): Boolean =
        runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }.getOrDefault(false)

    /**
     * 打开系统的通知使用权页面。
     * 优先直达本应用条目（Android 11+），失败则退回完整列表。
     */
    fun openSettings(context: Context) {
        val appContext = context.applicationContext
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                componentName(appContext).flattenToString(),
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { appContext.startActivity(detail) }.isSuccess ||
            runCatching { appContext.startActivity(fallback) }.isSuccess
        if (!opened) {
            // 极少数精简系统没有该设置页；提示用户去「设置 → 通知 → 通知使用权」手动开启。
            android.widget.Toast.makeText(
                appContext,
                "无法打开系统通知使用权页面，请在系统设置中手动开启",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

}
