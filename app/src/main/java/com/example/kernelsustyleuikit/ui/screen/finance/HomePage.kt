package com.example.kernelsustyleuikit.ui.screen.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kernelsustyleuikit.finance.FinanceCalculator
import com.example.kernelsustyleuikit.finance.FinanceState
import com.example.kernelsustyleuikit.finance.LoanCalculator
import com.example.kernelsustyleuikit.finance.LoanStatus
import com.example.kernelsustyleuikit.finance.Money
import com.example.kernelsustyleuikit.finance.Transaction
import com.example.kernelsustyleuikit.finance.TransactionType
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

@Composable
fun HomePage(
    state: FinanceState,
    today: LocalDate,
    bottomInnerPadding: Dp,
    onOpenBills: () -> Unit,
    onOpenLoans: () -> Unit,
    onAddTransaction: () -> Unit,
    onAddLoan: () -> Unit,
    onEditBudget: () -> Unit,
    onOpenLoanDetail: (String) -> Unit,
) {
    val month = YearMonth.from(today)
    val balance = FinanceCalculator.balanceCents(state, today)
    val income = FinanceCalculator.monthIncomeCents(state, month.year, month.monthValue)
    val expense = FinanceCalculator.monthExpenseCents(state, month.year, month.monthValue)
    val monthBalance = income - expense
    val budget = state.settings.monthlyBudgetCents
    val remaining = budget - expense
    val percent = FinanceCalculator.budgetPercent(state, month.year, month.monthValue)
    val percentText = budgetPercentLabel(state, month.year, month.monthValue)
    val summary = FinanceCalculator.loanSummary(state, today)
    val overdue = summary.overdueCount
    val recent = state.sortedTransactions.take(4)
    val categories = state.transactions
        .filter { it.type == TransactionType.EXPENSE && YearMonth.from(it.date) == month }
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        .toList()
        .sortedByDescending { it.second }
        .take(4)

    FinancePage(
        title = "余额簿",
        extraBottomPadding = bottomInnerPadding + 16.dp,
    ) {
        item {
            BalanceHeroCard(balance, monthBalance, summary.totalOutstandingCents)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(
                    title = "记一笔",
                    icon = { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp), tint = colorScheme.primary) },
                    onClick = onAddTransaction,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    title = "借出",
                    icon = { Icon(Icons.Rounded.Wallet, null, modifier = Modifier.size(20.dp), tint = colorScheme.primary) },
                    onClick = onAddLoan,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    title = "调额度",
                    icon = { Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(20.dp), tint = colorScheme.primary) },
                    onClick = onEditBudget,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (overdue > 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), onClick = onOpenLoans) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusBadge("逾期", ExpenseRed)
                        Text(
                            text = "有 $overdue 笔借款已逾期，点击查看",
                            fontSize = 14.sp,
                            color = colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                        )
                        Icon(Icons.Rounded.ArrowForward, null, tint = colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }

        item {
            BudgetCard(
                budget = budget,
                expense = expense,
                remaining = remaining,
                percent = percent,
                percentLabel = percentText,
                daysLeft = month.lengthOfMonth() - today.dayOfMonth,
                onEditBudget = onEditBudget,
            )
        }

        item {
            LoanSummaryCard(
                outstanding = summary.totalOutstandingCents,
                nextDue = summary.nextDueCents,
                active = summary.activeCount,
                overdue = summary.overdueCount,
                onOpenLoans = onOpenLoans,
            )
        }

        item {
            SixMonthChartCard(state, today)
        }

        if (categories.isNotEmpty()) {
            item {
                SectionCard(title = "本月支出分类") {
                    categories.forEach { (key, amount) ->
                        val share = if (expense > 0) (amount * 100 / expense).toInt() else 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Categories.icon(key),
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = Categories.label(key),
                                fontSize = 14.sp,
                                color = colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                            )
                            Text(
                                text = "${Money.formatCentsExact(amount)} · $share%",
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        ProgressBar(percent = share, height = 5.dp)
                    }
                }
            }
        }

        item {
            SectionCard(title = "最近流水") {
                if (recent.isEmpty()) {
                    Text(
                        text = "还没有账单，记第一笔吧",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    recent.forEach { TransactionRow(it) }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenBills)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(text = "查看全部账单", fontSize = 13.sp, color = colorScheme.primary)
                }
            }
        }

        if (summary.activeCount > 0) {
            item {
                val loans = state.sortedLoans
                    .filter { !LoanCalculator.settled(it) }
                    .sortedBy { LoanCalculator.nextDueDate(it) ?: LocalDate.MAX }
                    .take(3)
                SectionCard(title = "借款进度") {
                    loans.forEach { loan ->
                        val next = LoanCalculator.nextInstallment(loan)
                        val status = loan.status(today)
                        val nextRemaining = LoanCalculator.nextRemainingCents(loan)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenLoanDetail(loan.id) }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = loan.friend,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.width(72.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (loan.reminderEnabled) {
                                        "本期应还 ${Money.formatCentsExact(nextRemaining)}"
                                    } else {
                                        "分期提醒已关闭"
                                    },
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurface,
                                )
                                Text(
                                    text = "已还 ${loan.paidCount}/${loan.installmentCount} 期",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            StatusBadge(
                                text = when (status) {
                                    LoanStatus.OVERDUE -> "逾期"
                                    LoanStatus.DUE_TODAY -> "今天"
                                    LoanStatus.SETTLED -> "结清"
                                    LoanStatus.NORMAL -> "正常"
                                },
                                color = when (status) {
                                    LoanStatus.OVERDUE -> ExpenseRed
                                    LoanStatus.DUE_TODAY -> WarningOrange
                                    LoanStatus.SETTLED -> IncomeGreen
                                    LoanStatus.NORMAL -> colorScheme.primary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHeroCard(balance: Long, monthBalance: Long, outstanding: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "总余额", fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
            Text(
                text = Money.formatCentsExact(balance),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat(
                    label = "本月结余",
                    value = Money.formatCentsExact(monthBalance),
                    color = if (monthBalance >= 0) IncomeGreen else ExpenseRed,
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "借出待收",
                    value = Money.formatCentsExact(outstanding),
                    color = colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = label, fontSize = 11.sp, color = colorScheme.onSurfaceVariantSummary)
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickAction(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon()
            Text(text = title, fontSize = 12.sp, color = colorScheme.onSurface)
        }
    }
}

@Composable
private fun BudgetCard(
    budget: Long,
    expense: Long,
    remaining: Long,
    percent: Int,
    percentLabel: String,
    daysLeft: Int,
    onEditBudget: () -> Unit,
) {
    SectionCard(
        title = "本月预算",
        modifier = Modifier.clickable(onClick = onEditBudget),
    ) {
        if (budget <= 0) {
            Text(
                text = "未设置每月可用额度，点击设置",
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            return@SectionCard
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = if (remaining >= 0) "剩余" else "已超支",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = Money.formatCentsExact(if (remaining >= 0) remaining else -remaining),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (remaining >= 0) colorScheme.onSurface else ExpenseRed,
                )
            }
            Text(
                text = percentLabel,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    percent >= 100 -> ExpenseRed
                    percent >= 80 -> WarningOrange
                    else -> colorScheme.primary
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(
            percent = percent,
            color = when {
                percent >= 100 -> ExpenseRed
                percent >= 80 -> WarningOrange
                else -> colorScheme.primary
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "已花 ${Money.formatCents(expense)} / ${Money.formatCents(budget)}",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = "本月还剩 $daysLeft 天",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        if (remaining >= 0) {
            Text(
                text = "本月已省下 ${Money.formatCentsExact(remaining)}",
                fontSize = 12.sp,
                color = IncomeGreen,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun LoanSummaryCard(
    outstanding: Long,
    nextDue: Long,
    active: Int,
    overdue: Int,
    onOpenLoans: () -> Unit,
) {
    SectionCard(
        title = "借出概览",
        modifier = Modifier.clickable(onClick = onOpenLoans),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("下期应收", Money.formatCentsExact(nextDue), colorScheme.primary, Modifier.weight(1f))
            MiniStat(
                "进行中",
                "$active 笔",
                colorScheme.onSurface,
                Modifier.weight(1f),
            )
            MiniStat(
                "逾期",
                "$overdue 笔",
                if (overdue > 0) ExpenseRed else IncomeGreen,
                Modifier.weight(1f),
            )
        }
        Text(
            text = "待收总额 ${Money.formatCentsExact(outstanding)} · 每期应还按各自还款间隔计算",
            fontSize = 12.sp,
            color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun SixMonthChartCard(state: FinanceState, today: LocalDate) {
    val series = FinanceCalculator.recentMonthlyExpenses(state, 6, today)
    val maxValue = max(series.maxOfOrNull { it.second } ?: 0L, 1L)
    SectionCard(title = "近 6 个月支出") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            series.forEach { (month, value) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (value > 0) Money.formatCents(value, withSymbol = false) else "",
                        fontSize = 9.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height((max(6f, 74f * value / maxValue)).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (YearMonth.from(month) == YearMonth.from(today)) colorScheme.primary else colorScheme.primary.copy(alpha = 0.35f)),
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "${month.monthValue}月",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionRow(transaction: Transaction, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Categories.icon(transaction.category),
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = transaction.note.ifBlank { Categories.label(transaction.category) },
                fontSize = 14.sp,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${Money.formatMonthDay(transaction.date)} · ${Categories.label(transaction.category)}",
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        Text(
            text = (if (transaction.type == TransactionType.INCOME) "+" else "-") +
                Money.formatCents(transaction.amountCents),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (transaction.type == TransactionType.INCOME) IncomeGreen else colorScheme.onSurface,
        )
    }
}


