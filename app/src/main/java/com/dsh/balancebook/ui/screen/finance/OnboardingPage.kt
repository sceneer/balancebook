package com.dsh.balancebook.ui.screen.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.balancebook.finance.Money
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun OnboardingPage(onDone: (openingBalanceCents: Long, monthlyBudgetCents: Long) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var openingText by remember { mutableStateOf("0") }
    var budgetText by remember { mutableStateOf("3000") }
    var error by remember { mutableStateOf<String?>(null) }
    var remindersEnabled by remember { mutableStateOf(false) }
    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        remindersEnabled = granted
        if (granted) {
            com.dsh.balancebook.data.BalanceBookRepository.updateSettings {
                it.copy(loanReminders = true)
            }
        }
        android.widget.Toast.makeText(
            context,
            if (granted) "已开启借款提醒" else "未授予通知权限，之后可在设置中开启",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "余额簿",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )
        Text(
            text = "离线记账 + 借款管理。数据只保存在本机，可随时导出 JSON 备份。",
            fontSize = 14.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )

        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "先设置两个数字",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                FormField(
                    title = "初始余额",
                    value = openingText,
                    onValueChange = {
                        openingText = it
                        error = null
                    },
                    hint = "0.00",
                    keyboardType = KeyboardType.Decimal,
                    summary = "元，可以为 0",
                )
                FormField(
                    title = "每月可用额度",
                    value = budgetText,
                    onValueChange = {
                        budgetText = it
                        error = null
                    },
                    hint = "3000.00",
                    keyboardType = KeyboardType.Decimal,
                    summary = "元，之后可在设置里修改",
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = ExpenseRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "借款支持按天分期",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "可以按年利率、固定总利息或固定每期利息计息；到期日按「每隔 N 天」精确计算。",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            TextButton(
                text = if (remindersEnabled) "借款提醒已开启" else "开启借款到期提醒（可选）",
                onClick = {
                    if (!remindersEnabled) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = "开始记账",
                onClick = {
                    val opening = Money.parseYuanToCents(openingText)
                    val budget = Money.parseYuanToCents(budgetText)
                    when {
                        opening == null -> error = "初始余额格式不正确"
                        budget == null || budget <= 0 -> error = "每月可用额度需要大于 0"
                        else -> onDone(opening, budget)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "没有登录、没有服务器、没有 INTERNET 权限。",
            fontSize = 11.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}
