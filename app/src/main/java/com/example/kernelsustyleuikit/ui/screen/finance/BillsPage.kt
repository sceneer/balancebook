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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kernelsustyleuikit.finance.FinanceCalculator
import com.example.kernelsustyleuikit.finance.FinanceState
import com.example.kernelsustyleuikit.finance.Money
import com.example.kernelsustyleuikit.finance.Transaction
import com.example.kernelsustyleuikit.finance.TransactionType
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun BillsPage(
    state: FinanceState,
    today: LocalDate,
    bottomInnerPadding: Dp,
    onAddTransaction: () -> Unit,
    onEditTransaction: (String) -> Unit,
) {
    // 用 year * 12 + month - 1 保存，rememberSaveable 可直接存 Int。
    var monthIndex by rememberSaveable { mutableIntStateOf(today.year * 12 + today.monthValue - 1) }
    val year = monthIndex / 12
    val month = monthIndex % 12 + 1
    val selected = YearMonth.of(year, month)

    val transactions = state.transactions
        .filter { YearMonth.from(it.date) == selected }
        .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })
    val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
    val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
    val budget = state.settings.monthlyBudgetCents
    val percent = if (budget > 0) ((expense * 100) / budget).toInt() else 0
    val categories = transactions
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        .toList()
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    val incomeCategories = transactions
        .filter { it.type == TransactionType.INCOME }
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        .toList()
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    val groups = transactions.groupBy { it.date }.toList().sortedByDescending { it.first }

    FinancePage(
        title = "账单",
        extraBottomPadding = bottomInnerPadding + 16.dp,
        actions = { TopBarTextAction("记一笔", onAddTransaction) },
    ) {
        item {
            MonthSwitcher(
                year = year,
                month = month,
                onPrev = { monthIndex -= 1 },
                onNext = { if (monthIndex < today.year * 12 + today.monthValue - 1) monthIndex += 1 },
            )
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("支出", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                        Text(
                            Money.formatCentsExact(expense),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("收入", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                        Text(
                            Money.formatCentsExact(income),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("结余", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                        Text(
                            Money.formatCentsExact(income - expense),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                        )
                    }
                }
                if (budget > 0) {
                    Spacer(Modifier.height(12.dp))
                    ProgressBar(
                        percent = percent,
                        color = if (percent >= 100) ExpenseRed else if (percent >= 80) WarningOrange else colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "预算 ${Money.formatCents(budget)} · 已用 ${budgetPercentLabel(state, year, month)}",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        if (categories.isNotEmpty()) {
            item {
                SectionCard(title = "支出分类") {
                    categories.forEach { (key, amount) ->
                        val share = if (expense > 0) (amount * 100 / expense).toInt() else 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Categories.icon(key),
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(17.dp),
                            )
                            Text(
                                Categories.label(key),
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 9.dp),
                                color = colorScheme.onSurface,
                            )
                            Text(
                                "${Money.formatCentsExact(amount)}  $share%",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        ProgressBar(percent = share, height = 4.dp)
                    }
                }
            }
        }

        if (incomeCategories.isNotEmpty()) {
            item {
                SectionCard(title = "收入分类") {
                    incomeCategories.forEach { (key, amount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(Categories.label(key), fontSize = 14.sp, color = colorScheme.onSurface)
                            Text(
                                "+" + Money.formatCentsExact(amount),
                                fontSize = 14.sp,
                                color = IncomeGreen,
                            )
                        }
                    }
                }
            }
        }

        if (transactions.isEmpty()) {
            item {
                SectionCard {
                    EmptyState(
                        title = "这个月还没有账单",
                        subtitle = "记录第一笔，开始看清钱花在哪里",
                        actionText = "记第一笔",
                        onAction = onAddTransaction,
                    )
                }
            }
        } else {
            groups.forEach { (date, list) ->
                item(key = "header-$date") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${Money.formatMonthDay(date)} ${Money.weekdayLabel(date)}",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        val dayExpense = list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
                        Text(
                            text = "支出 ${Money.formatCents(dayExpense)}",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                item(key = "group-$date") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            list.forEach { transaction ->
                                TransactionRow(transaction) { onEditTransaction(transaction.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButtonBox(onClick = onPrev) {
                Icon(Icons.Rounded.ChevronLeft, "上个月", modifier = Modifier.size(22.dp), tint = colorScheme.onSurface)
            }
            Text(
                text = "${year}年${month}月",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            IconButtonBox(onClick = onNext) {
                Icon(Icons.Rounded.ChevronRight, "下个月", modifier = Modifier.size(22.dp), tint = colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun IconButtonBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

