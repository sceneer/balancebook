package com.example.kernelsustyleuikit.ui.screen.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kernelsustyleuikit.data.BalanceBookRepository
import com.example.kernelsustyleuikit.finance.FixedInterestScope
import com.example.kernelsustyleuikit.finance.Installment
import com.example.kernelsustyleuikit.finance.InterestMode
import com.example.kernelsustyleuikit.finance.LoanCalculator
import com.example.kernelsustyleuikit.finance.LoanPayment
import com.example.kernelsustyleuikit.finance.LoanStatus
import com.example.kernelsustyleuikit.finance.Money
import com.example.kernelsustyleuikit.finance.ScheduleUnit
import com.example.kernelsustyleuikit.ui.component.dialog.rememberConfirmDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.time.LocalDate

@Composable
fun LoanDetailPage(
    loanId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val state by BalanceBookRepository.state.collectAsStateWithLifecycle()
    val loan = state.loans.firstOrNull { it.id == loanId }
    if (loan == null) {
        androidx.compose.runtime.LaunchedEffect(loanId) { onBack() }
        return
    }
    val today = LocalDate.now()
    val status = loan.status(today)
    val schedule = LoanCalculator.schedule(loan.terms)
    val next = LoanCalculator.nextInstallment(loan)
    val nextRemaining = LoanCalculator.nextRemainingCents(loan)
    val statusColor = when (status) {
        LoanStatus.OVERDUE -> ExpenseRed
        LoanStatus.DUE_TODAY -> WarningOrange
        LoanStatus.SETTLED -> IncomeGreen
        LoanStatus.NORMAL -> colorScheme.primary
    }

    var showPaymentDialog by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(today.toString()) }
    var noteText by remember { mutableStateOf("") }
    var paymentError by remember { mutableStateOf<String?>(null) }
    var pendingDeletePaymentId by remember { mutableStateOf<String?>(null) }

    val undoDialog = rememberConfirmDialog(onConfirm = {
        BalanceBookRepository.undoLastPayment(loan.id)
    })
    val deleteDialog = rememberConfirmDialog(onConfirm = {
        BalanceBookRepository.deleteLoan(loan.id)
        onBack()
    })
    val clearDialog = rememberConfirmDialog(onConfirm = {
        BalanceBookRepository.clearPayments(loan.id)
    })
    val deletePaymentDialog = rememberConfirmDialog(
        onConfirm = {
            pendingDeletePaymentId?.let { BalanceBookRepository.deletePayment(loan.id, it) }
            pendingDeletePaymentId = null
        },
        onDismiss = { pendingDeletePaymentId = null },
    )

    FinancePage(
        title = "借款详情",
        onBack = onBack,
        extraBottomPadding = 32.dp,
        actions = { TopBarTextAction("编辑", onEdit) },
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = loan.friend,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (!loan.reminderEnabled && !loan.settled) {
                            StatusBadge("不提醒", colorScheme.onSurfaceVariantSummary)
                            Spacer(Modifier.size(6.dp))
                        }
                        StatusBadge(status.label, statusColor)
                    }
                    if (loan.note.isNotBlank()) {
                        Text(
                            text = loan.note,
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("待收", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                    Text(
                        text = Money.formatCentsExact(loan.outstandingCents),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(percent = loan.paidPercent, color = statusColor, height = 10.dp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "已还 ${loan.paidCount}/${loan.installmentCount} 期",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            "进度 ${paidPercentLabel(loan)}",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "金额") {
                InfoRow("总应收", Money.formatCentsExact(loan.totalReceivableCents))
                InfoRow("已收", Money.formatCentsExact(loan.paidCents), IncomeGreen)
                InfoRow("待收", Money.formatCentsExact(loan.outstandingCents))
                if (loan.settled) {
                    InfoRow("状态", "已结清", IncomeGreen)
                } else if (loan.reminderEnabled) {
                    InfoRow(
                        "下次还款日",
                        next?.let { Money.formatDate(it.dueDate) } ?: "—",
                        if (status == LoanStatus.OVERDUE) ExpenseRed else colorScheme.onSurface,
                    )
                    InfoRow(
                        "本期还应",
                        Money.formatCentsExact(nextRemaining),
                        colorScheme.primary,
                    )
                } else {
                    InfoRow("下次还款日", "分期提醒已关闭")
                }
            }
        }

        item {
            SectionCard(title = "借款信息") {
                InfoRow("本金", Money.formatCentsExact(loan.principalCents))
                InfoRow(
                    "利息方式",
                    when {
                        loan.interestMode == InterestMode.RATE -> "按年利率 ${Money.formatRate(loan.annualRate)}"
                        loan.fixedInterestScope == FixedInterestScope.PER_INSTALLMENT ->
                            "固定每期利息 ${Money.formatCentsExact(loan.fixedInterestCents)}"
                        else -> "固定总利息 ${Money.formatCentsExact(loan.fixedInterestCents)}"
                    },
                )
                InfoRow("总利息", Money.formatCentsExact(loan.totalInterestCents))
                InfoRow("还款方式", loan.method.label)
                InfoRow("起借日", Money.formatDate(loan.startDate))
                InfoRow(
                    "还款间隔",
                    if (loan.intervalUnit == ScheduleUnit.MONTHS) "每 1 个月（旧版）" else "每 ${loan.intervalDays} 天",
                )
                InfoRow("期数", "${loan.installmentCount} 期")
                InfoRow("分期提醒", if (loan.reminderEnabled) "开启" else "已关闭（仅按总待收）")
                InfoRow("计入总余额", if (loan.includeInBalance) "是" else "否")
            }
        }

        item {
            SectionCard(title = "操作") {
                TextButton(
                    text = if (loan.settled) "已结清" else "记一笔还款",
                    onClick = {
                        amountText = Money.centsToInput(
                            when {
                                nextRemaining > 0 -> nextRemaining
                                else -> loan.outstandingCents
                            },
                        )
                        dateText = today.toString()
                        noteText = ""
                        paymentError = null
                        showPaymentDialog = true
                    },
                    enabled = !loan.settled,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "撤销最后一笔",
                    onClick = {
                        undoDialog.showConfirm(
                            title = "撤销最后一笔还款？",
                            content = "将删除最近一笔还款记录，并把金额加回待收。",
                            confirm = "撤销",
                        )
                    },
                    enabled = loan.payments.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loan.payments.isNotEmpty()) {
                    TextButton(
                        text = "清空收款记录并重算",
                        onClick = {
                            clearDialog.showConfirm(
                                title = "清空全部收款记录？",
                                content = "清空后可以重新修改本金、利息、期数等条款；已收款金额会从余额中移除。",
                                confirm = "清空",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    text = "删除借款",
                    onClick = {
                        deleteDialog.showConfirm(
                            title = "删除这笔借款？",
                            content = "删除后本金和已收款对余额的影响会同步撤销。",
                            confirm = "删除",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (loan.payments.isNotEmpty()) {
            item {
                SectionCard(title = "还款记录") {
                    loan.payments
                        .sortedWith(compareByDescending<LoanPayment> { it.date }.thenByDescending { it.period })
                        .forEach { payment ->
                            PaymentRow(payment) {
                                pendingDeletePaymentId = payment.id
                                deletePaymentDialog.showConfirm(
                                    title = "删除这笔还款？",
                                    content = "${Money.formatDate(payment.date)} · ${Money.formatCentsExact(payment.amountCents)}。" +
                                        "删除后待收和进度会同步更新。",
                                    confirm = "删除",
                                )
                            }
                        }
                }
            }
        }

        item {
            SectionCard(title = if (loan.reminderEnabled) "还款计划（提醒参考）" else "还款计划（仅参考，不提醒）") {
                if (!loan.reminderEnabled) {
                    Text(
                        text = "分期计划只用于计算总应收和展示，不产生到期/逾期提醒；实际还款按下方记录自由输入。",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                var cumulative = 0L
                schedule.forEach { installment ->
                    cumulative += installment.dueCents
                    val installmentState = when {
                        loan.paidCents >= cumulative -> InstallmentPaidState.PAID
                        loan.paidCents > cumulative - installment.dueCents -> InstallmentPaidState.PARTIAL
                        loan.reminderEnabled && installment.dueDate.isBefore(today) -> InstallmentPaidState.OVERDUE
                        else -> InstallmentPaidState.DUE
                    }
                    ScheduleRow(installment, installmentState)
                }
            }
        }
    }

    if (showPaymentDialog) {
        WindowDialog(
            show = showPaymentDialog,
            title = "记一笔还款",
            onDismissRequest = { showPaymentDialog = false },
            content = {
                Column {
                    Text(
                        text = "可以按实际收到的金额和日期记录，不必与分期金额一致。",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    FormField(
                        title = "金额",
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            paymentError = null
                        },
                        hint = "0.00",
                        keyboardType = KeyboardType.Decimal,
                        isError = paymentError != null,
                    )
                    FormField(
                        title = "收款日期",
                        value = dateText,
                        onValueChange = {
                            dateText = it
                            paymentError = null
                        },
                        hint = "yyyy-MM-dd",
                        isError = Money.parseDate(dateText) == null,
                    )
                    FormField(
                        title = "备注",
                        value = noteText,
                        onValueChange = { noteText = it },
                        hint = "可选，如「微信转账」",
                    )
                    if (paymentError != null) {
                        Text(
                            text = paymentError!!,
                            color = ExpenseRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showPaymentDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "保存",
                            onClick = {
                                val cents = Money.parseYuanToCents(amountText)
                                val date = Money.parseDate(dateText)
                                when {
                                    cents == null || cents <= 0 -> paymentError = "请输入大于 0 的金额"
                                    date == null -> paymentError = "日期格式应为 yyyy-MM-dd"
                                    else -> {
                                        BalanceBookRepository.addPayment(
                                            loanId = loan.id,
                                            amountCents = cents,
                                            paidOn = date,
                                            note = noteText,
                                        )
                                        showPaymentDialog = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
        )
    }
}

private enum class InstallmentPaidState { PAID, PARTIAL, OVERDUE, DUE }

@Composable
private fun PaymentRow(payment: LoanPayment, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Money.formatCentsExact(payment.amountCents),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = Money.formatDate(payment.date),
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                if (payment.period > 0) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "第 ${payment.period} 期",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (payment.note.isNotBlank()) {
                Text(
                    text = payment.note,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceContainer)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "删除这笔还款",
                tint = ExpenseRed,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
@Composable
private fun ScheduleRow(installment: Installment, state: InstallmentPaidState) {
    val paid = state == InstallmentPaidState.PAID
    val color = when (state) {
        InstallmentPaidState.PAID -> IncomeGreen
        InstallmentPaidState.PARTIAL -> WarningOrange
        InstallmentPaidState.OVERDUE -> ExpenseRed
        InstallmentPaidState.DUE -> colorScheme.surfaceContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (paid) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text("${installment.period}", fontSize = 10.sp, color = colorScheme.onSurfaceVariantSummary)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = "第 ${installment.period} 期 · ${Money.formatDate(installment.dueDate)}",
                fontSize = 13.sp,
                color = colorScheme.onSurface,
            )
            Text(
                text = "本金 ${Money.formatCents(installment.principalPartCents)} · 利息 ${Money.formatCents(installment.interestPartCents)}",
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.formatCentsExact(installment.dueCents),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = when (state) {
                    InstallmentPaidState.PAID -> "已收"
                    InstallmentPaidState.PARTIAL -> "部分"
                    InstallmentPaidState.OVERDUE -> "逾期"
                    InstallmentPaidState.DUE -> "待收"
                },
                fontSize = 10.sp,
                color = when (state) {
                    InstallmentPaidState.PAID -> IncomeGreen
                    InstallmentPaidState.PARTIAL -> WarningOrange
                    InstallmentPaidState.OVERDUE -> ExpenseRed
                    InstallmentPaidState.DUE -> colorScheme.onSurfaceVariantSummary
                },
            )
        }
    }
}


