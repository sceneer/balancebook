package com.dsh.balancebook.ui.screen.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.balancebook.finance.FinanceCalculator
import com.dsh.balancebook.finance.FinanceState
import com.dsh.balancebook.finance.InterestMode
import com.dsh.balancebook.finance.Loan
import com.dsh.balancebook.finance.LoanCalculator
import com.dsh.balancebook.finance.LoanStatus
import com.dsh.balancebook.finance.Money
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.time.LocalDate

@Composable
fun LoansPage(
    state: FinanceState,
    today: LocalDate,
    bottomInnerPadding: Dp,
    onAddLoan: () -> Unit,
    onOpenLoan: (String) -> Unit,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 全部 1 进行中 2 已结清
    val summary = FinanceCalculator.loanSummary(state, today)
    val loans = state.sortedLoans
        .filter {
            when (filter) {
                1 -> !LoanCalculator.settled(it)
                2 -> LoanCalculator.settled(it)
                else -> true
            }
        }
        .sortedWith(
            compareBy<Loan> { LoanCalculator.settled(it) }
                .thenBy { if (LoanCalculator.status(it, today) == LoanStatus.OVERDUE) 0 else 1 }
                .thenBy { LoanCalculator.nextDueDate(it) ?: LocalDate.MAX }
                .thenByDescending { it.createdAt },
        )

    FinancePage(
        title = "借款",
        extraBottomPadding = bottomInnerPadding + 16.dp,
        actions = { TopBarTextAction("新增", onAddLoan) },
    ) {
        item {
            SectionCard(title = "汇总") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCell("待收总额", Money.formatCentsExact(summary.totalOutstandingCents), colorScheme.onSurface, Modifier.weight(1f))
                    SummaryCell("下期应收", Money.formatCentsExact(summary.nextDueCents), colorScheme.primary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCell("进行中", "${summary.activeCount} 笔", colorScheme.onSurface, Modifier.weight(1f))
                    SummaryCell(
                        "逾期",
                        "${summary.overdueCount} 笔",
                        if (summary.overdueCount > 0) ExpenseRed else IncomeGreen,
                        Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("全部", filter == 0, onClick = { filter = 0 })
                SelectChip("进行中", filter == 1, onClick = { filter = 1 })
                SelectChip("已结清", filter == 2, onClick = { filter = 2 })
            }
        }

        if (loans.isEmpty()) {
            item {
                SectionCard {
                    EmptyState(
                        title = if (filter == 0) "还没有借款记录" else "没有符合条件的借款",
                        subtitle = "记录借给好友的钱，按天分期、自动算息",
                        actionText = "新增借款",
                        onAction = onAddLoan,
                    )
                }
            }
        } else {
            loans.forEach { loan ->
                item(key = loan.id) {
                    LoanCard(loan, today) { onOpenLoan(loan.id) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 11.sp, color = colorScheme.onSurfaceVariantSummary)
        Text(
            text = value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun LoanCard(loan: Loan, today: LocalDate, onClick: () -> Unit) {
    val status = loan.status(today)
    val next = LoanCalculator.nextInstallment(loan)
    val nextRemaining = LoanCalculator.nextRemainingCents(loan)
    val statusColor = when (status) {
        LoanStatus.OVERDUE -> ExpenseRed
        LoanStatus.DUE_TODAY -> WarningOrange
        LoanStatus.SETTLED -> IncomeGreen
        LoanStatus.NORMAL -> colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = loan.friend,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusBadge(status.label, statusColor)
            }
            if (loan.note.isNotBlank()) {
                Text(
                    text = loan.note,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (loan.reminderEnabled) {
                    Text(
                        text = "已还 ${loan.paidCount}/${loan.installmentCount} 期",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    text = "进度 ${paidPercentLabel(loan)}",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(5.dp))
            ProgressBar(percent = loan.paidPercent, color = statusColor, height = 6.dp)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    if (loan.reminderEnabled) {
                        Text(
                            "本期应还",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = if (loan.settled) "—" else Money.formatCentsExact(nextRemaining),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface,
                        )
                    } else {
                        Text("已收", fontSize = 11.sp, color = colorScheme.onSurfaceVariantSummary)
                        Text(
                            text = Money.formatCentsExact(loan.paidCents),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = IncomeGreen,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("待收", fontSize = 11.sp, color = colorScheme.onSurfaceVariantSummary)
                    Text(
                        text = Money.formatCentsExact(loan.outstandingCents),
                        fontSize = 15.sp,
                        color = colorScheme.onSurface,
                    )
                }
                if (loan.reminderEnabled) {
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text("下次还款日", fontSize = 11.sp, color = colorScheme.onSurfaceVariantSummary)
                        Text(
                            text = when {
                                loan.settled -> "已结清"
                                else -> next?.let { Money.formatDate(it.dueDate) } ?: "—"
                            },
                            fontSize = 15.sp,
                            color = if (status == LoanStatus.OVERDUE) ExpenseRed else colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val detailParts = ArrayList<String>()
            if (loan.interestEnabled) {
                detailParts += when {
                    loan.interestMode == InterestMode.RATE -> "年利率 ${Money.formatRate(loan.annualRate)}"
                    loan.fixedInterestScope == com.dsh.balancebook.finance.FixedInterestScope.PER_INSTALLMENT ->
                        "固定每期利息 ${Money.formatCentsExact(loan.fixedInterestCents)}"
                    else -> "固定总利息 ${Money.formatCentsExact(loan.fixedInterestCents)}"
                }
            }
            if (loan.reminderEnabled) {
                detailParts += if (loan.intervalUnit == com.dsh.balancebook.finance.ScheduleUnit.MONTHS) {
                    "每 1 个月（旧版）"
                } else {
                    "每 ${loan.intervalDays} 天一期"
                }
                detailParts += loan.method.label
            }
            if (detailParts.isNotEmpty()) {
                Text(
                    text = detailParts.joinToString(" · "),
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}


