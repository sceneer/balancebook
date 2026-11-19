package com.example.kernelsustyleuikit.ui.screen.finance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.kernelsustyleuikit.BuildConfig
import com.example.kernelsustyleuikit.data.BalanceBookRepository
import com.example.kernelsustyleuikit.finance.DecodeResult
import com.example.kernelsustyleuikit.finance.FinanceState
import com.example.kernelsustyleuikit.finance.Money
import com.example.kernelsustyleuikit.notify.NotificationAccess
import com.example.kernelsustyleuikit.reminder.LoanReminder
import com.example.kernelsustyleuikit.ui.component.dialog.rememberConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.time.LocalDate

@Composable
fun SettingsPage(
    state: FinanceState,
    bottomInnerPadding: Dp,
    onOpenTheme: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember { mutableStateOf(LoanReminder.canPostNotifications(context)) }
    var notificationAccessGranted by remember { mutableStateOf(NotificationAccess.isGranted(context)) }

    LifecycleResumeEffect(Unit) {
        permissionGranted = LoanReminder.canPostNotifications(context)
        notificationAccessGranted = NotificationAccess.isGranted(context)
        onPauseOrDispose { }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        Toast.makeText(context, if (granted) "已开启通知权限" else "未授予通知权限，提醒不会显示", Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val text = BalanceBookRepository.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")
            }
            withContextMain {
                Toast.makeText(
                    context,
                    if (result.isSuccess) "已导出余额簿备份" else "导出失败：${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            withContextMain {
                when {
                    text.isNullOrBlank() -> Toast.makeText(context, "读取文件失败", Toast.LENGTH_LONG).show()
                    else -> when (val parsed = com.example.kernelsustyleuikit.finance.BalanceBookCodec.decode(text)) {
                        is DecodeResult.Failure -> Toast.makeText(
                            context,
                            "导入失败：${parsed.message}（原数据未改变）",
                            Toast.LENGTH_LONG,
                        ).show()

                        is DecodeResult.Success -> pendingImport = text
                    }
                }
            }
        }
    }

    val importConfirm = rememberConfirmDialog(onConfirm = {
        val text = pendingImport
        pendingImport = null
        if (text != null) {
            when (val result = BalanceBookRepository.importJson(text)) {
                is DecodeResult.Success -> Toast.makeText(
                    context,
                    "导入成功：${result.state.transactions.size} 笔账单，${result.state.loans.size} 笔借款",
                    Toast.LENGTH_LONG,
                ).show()

                is DecodeResult.Failure -> Toast.makeText(
                    context,
                    "导入失败：${result.message}（原数据未改变）",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }, onDismiss = { pendingImport = null })

    androidx.compose.runtime.LaunchedEffect(pendingImport) {
        if (pendingImport != null) {
            importConfirm.showConfirm(
                title = "导入备份？",
                content = "导入会替换当前全部账单、借款和设置，原数据不会保留。",
                confirm = "导入",
            )
        }
    }
    val sampleConfirm = rememberConfirmDialog(onConfirm = {
        BalanceBookRepository.seedDemoData()
        Toast.makeText(context, "示例数据已载入", Toast.LENGTH_SHORT).show()
    })
    val clearConfirm = rememberConfirmDialog(onConfirm = {
        BalanceBookRepository.clearRecords()
        Toast.makeText(context, "账单与借款已清空", Toast.LENGTH_SHORT).show()
    })

    FinancePage(
        title = "设置",
        extraBottomPadding = bottomInnerPadding + 16.dp,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "借款到期提醒",
                    summary = "每天 9:00 检查 3 天内到期或已逾期的借款",
                    checked = state.settings.loanReminders,
                    startAction = {
                        Icon(
                            Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(22.dp),
                        )
                    },
                    onCheckedChange = { enabled ->
                        BalanceBookRepository.updateSettings { it.copy(loanReminders = enabled) }
                        Toast.makeText(
                            context,
                            if (enabled) "已开启借款到期提醒" else "已关闭借款到期提醒",
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !permissionGranted
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "自动记账（微信 / 支付宝）",
                    summary = if (notificationAccessGranted) {
                        "读取支付通知自动生成账单 · 通知使用权已授权"
                    } else {
                        "读取支付通知自动生成账单，开启后请允许「通知使用权」"
                    },
                    checked = state.settings.autoBookkeeping,
                    startAction = {
                        Icon(
                            Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(22.dp),
                        )
                    },
                    onCheckedChange = { enabled ->
                        BalanceBookRepository.updateSettings { it.copy(autoBookkeeping = enabled) }
                        if (enabled) {
                            if (!notificationAccessGranted) {
                                Toast.makeText(
                                    context,
                                    "请在系统设置中允许「余额簿」的通知使用权",
                                    Toast.LENGTH_LONG,
                                ).show()
                                NotificationAccess.openSettings(context)
                            }
                        } else {
                            Toast.makeText(context, "已关闭自动记账", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                val autoCount = state.transactions.count { it.source.isAuto }
                if (autoCount > 0) {
                    InfoRow("已自动记账", "$autoCount 笔")
                }
                Text(
                    text = "自动记账只读取通知标题与正文，数据仍只保存在本机；微信 / 支付宝通知需包含金额，营销、待支付、聊天消息会被忽略。建议在系统设置中允许自启动并取消省电限制，避免后台通知被清理。",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            SectionCard(title = "数据") {
                ArrowPreference(
                    title = "导出 JSON 备份",
                    summary = "导出为 version ${com.example.kernelsustyleuikit.finance.BalanceBookCodec.CURRENT_VERSION} 本地文件",
                    startAction = {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(22.dp),
                        )
                    },
                    onClick = {
                        exportLauncher.launch("余额簿备份_${LocalDate.now().toString().replace("-", "")}.json")
                    },
                )
                ArrowPreference(
                    title = "导入 JSON 备份",
                    summary = "支持 version 1（按月旧版）与 version 2",
                    startAction = {
                        Icon(
                            Icons.Rounded.Upload,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(22.dp),
                        )
                    },
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
                ArrowPreference(
                    title = "载入示例数据",
                    summary = "覆盖当前账单和借款（保留额度与初始余额）",
                    onClick = {
                        sampleConfirm.showConfirm(
                            title = "载入示例数据？",
                            content = "将覆盖当前全部账单和借款，建议先导出备份。",
                            confirm = "载入",
                        )
                    },
                )
                ArrowPreference(
                    title = "清空账单与借款",
                    summary = "保留预算、初始余额和外观设置",
                    onClick = {
                        clearConfirm.showConfirm(
                            title = "清空所有记录？",
                            content = "账单和借款会被删除，预算与外观设置保留，建议先导出备份。",
                            confirm = "清空",
                        )
                    },
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "主题 / 外观",
                    summary = "使用模板已有的 Miuix 配色与主题页",
                    startAction = {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(22.dp),
                        )
                    },
                    onClick = onOpenTheme,
                )
            }
        }

        item {
            SectionCard(title = "关于") {
                InfoRow("应用", "余额簿 BalanceBook")
                InfoRow("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("数据存储", "仅本机 files/balance_book.json")
                InfoRow("网络", "无 INTERNET 权限，完全离线")
                Text(
                    text = "隐私说明：所有账单、借款与设置只保存在本机，不会上传到任何服务器。",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

}


private suspend fun withContextMain(block: () -> Unit) {
    kotlinx.coroutines.withContext(Dispatchers.Main) { block() }
}


