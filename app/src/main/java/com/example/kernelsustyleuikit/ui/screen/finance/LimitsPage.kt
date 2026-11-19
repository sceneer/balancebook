package com.example.kernelsustyleuikit.ui.screen.finance

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kernelsustyleuikit.data.BalanceBookRepository
import com.example.kernelsustyleuikit.finance.FinanceCalculator
import com.example.kernelsustyleuikit.finance.FinanceState
import com.example.kernelsustyleuikit.finance.Money
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.time.LocalDate

/**
 * 额度与余额：由首页「额度」按钮进入，集中管理初始余额和每月可用额度。
 * 从设置页移出后，设置页只保留提醒、自动记账、数据与外观等配置。
 */
@Composable
fun LimitsPage(
    state: FinanceState,
    onBack: () -> Unit,
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showOpeningDialog by remember { mutableStateOf(false) }
    val balance = FinanceCalculator.balanceCents(state, LocalDate.now())

    FinancePage(
        title = "额度与余额",
        onBack = onBack,
        extraBottomPadding = 40.dp,
    ) {
        item {
            SectionCard {
                Text(text = "当前余额", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                Text(
                    text = Money.formatCentsExact(balance),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "余额 = 初始余额 + 收入 − 支出 − 借出本金 + 已收还款",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        item {
            SectionCard(title = "额度设置") {
                ArrowPreference(
                    title = "每月可用额度",
                    summary = if (state.settings.monthlyBudgetCents > 0) {
                        Money.formatCentsExact(state.settings.monthlyBudgetCents)
                    } else {
                        "未设置"
                    },
                    startAction = {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(22.dp),
                        )
                    },
                    onClick = { showBudgetDialog = true },
                )
                ArrowPreference(
                    title = "初始余额",
                    summary = Money.formatCentsExact(state.settings.openingBalanceCents),
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
                    onClick = { showOpeningDialog = true },
                )
            }
        }

        item {
            SectionCard {
                Text(
                    text = "修改后立即刷新首页余额、本月预算和账单统计；初始余额不会计入本月收入。",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }

    if (showBudgetDialog) {
        AmountInputDialog(
            title = "每月可用额度",
            show = true,
            initialText = Money.centsToInput(state.settings.monthlyBudgetCents),
            allowZero = false,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { cents ->
                BalanceBookRepository.updateSettings { it.copy(monthlyBudgetCents = cents) }
                showBudgetDialog = false
            },
        )
    }
    if (showOpeningDialog) {
        AmountInputDialog(
            title = "初始余额",
            show = true,
            initialText = Money.centsToInput(state.settings.openingBalanceCents),
            allowZero = true,
            onDismiss = { showOpeningDialog = false },
            onConfirm = { cents ->
                BalanceBookRepository.updateSettings { it.copy(openingBalanceCents = cents) }
                showOpeningDialog = false
            },
        )
    }
}
