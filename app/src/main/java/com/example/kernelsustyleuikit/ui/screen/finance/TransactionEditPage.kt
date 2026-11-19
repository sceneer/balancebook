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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kernelsustyleuikit.data.BalanceBookRepository
import com.example.kernelsustyleuikit.finance.Money
import com.example.kernelsustyleuikit.finance.Transaction
import com.example.kernelsustyleuikit.finance.TransactionType
import com.example.kernelsustyleuikit.ui.component.dialog.rememberConfirmDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.time.LocalDate

@Composable
fun TransactionEditPage(
    transactionId: String?,
    onBack: () -> Unit,
) {
    val existing = remember(transactionId) {
        transactionId?.let { BalanceBookRepository.transactionById(it) }
    }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.centsToInput(it.amountCents) } ?: "") }
    var category by rememberSaveable {
        mutableStateOf(existing?.category ?: "food")
    }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var dateText by rememberSaveable { mutableStateOf((existing?.date ?: LocalDate.now()).toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    val categories = Categories.listFor(type == TransactionType.INCOME)
    if (categories.none { it.key == category }) {
        category = categories.first().key
    }

    val deleteDialog = rememberConfirmDialog(onConfirm = {
        existing?.let { BalanceBookRepository.deleteTransaction(it.id) }
        onBack()
    })

    FinancePage(
        title = if (existing == null) "记一笔" else "编辑账单",
        onBack = onBack,
        actions = {
            if (existing != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            deleteDialog.showConfirm(
                                title = "删除这笔账单？",
                                content = "删除后预算和统计会立即更新。",
                                confirm = "删除",
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Delete, "删除", tint = ExpenseRed)
                }
            }
        },
        extraBottomPadding = 40.dp,
    ) {
        item {
            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("支出", type == TransactionType.EXPENSE, onClick = {
                        type = TransactionType.EXPENSE
                        if (!Categories.isValidFor(category, false)) category = "food"
                    })
                    SelectChip("收入", type == TransactionType.INCOME, onClick = {
                        type = TransactionType.INCOME
                        if (!Categories.isValidFor(category, true)) category = "salary"
                    })
                }
                Spacer(Modifier.height(8.dp))
                FormField(
                    title = "金额",
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = null
                    },
                    hint = "0.00",
                    keyboardType = KeyboardType.Decimal,
                    isError = error != null && Money.parseYuanToCents(amountText) == null,
                    summary = "元，最多两位小数",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1000L, 5000L, 10000L, 20000L).forEach { yuan ->
                        SelectChip("¥$yuan", false, onClick = { amountText = yuan.toString() })
                    }
                }
            }
        }

        item {
            SectionCard(title = "分类") {
                val rows = categories.chunked(4)
                rows.forEach { rowCategories ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowCategories.forEach { info ->
                            val selected = info.key == category
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) colorScheme.primary.copy(alpha = 0.15f) else colorScheme.surfaceContainer)
                                    .clickable { category = info.key }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = info.icon,
                                    contentDescription = null,
                                    tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = info.label,
                                    fontSize = 11.sp,
                                    color = if (selected) colorScheme.primary else colorScheme.onSurface,
                                )
                            }
                        }
                        repeat(4 - rowCategories.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        item {
            SectionCard(title = "详情") {
                FormField(
                    title = "备注",
                    value = note,
                    onValueChange = { note = it },
                    hint = "可选",
                )
                FormField(
                    title = "日期",
                    value = dateText,
                    onValueChange = {
                        dateText = it
                        error = null
                    },
                    hint = "yyyy-MM-dd",
                    isError = Money.parseDate(dateText) == null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("今天", false, onClick = { dateText = LocalDate.now().toString() })
                    SelectChip("昨天", false, onClick = { dateText = LocalDate.now().minusDays(1).toString() })
                }
                if (error != null) {
                    Text(
                        text = error!!,
                        color = ExpenseRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = "保存",
                    onClick = {
                        val cents = Money.parseYuanToCents(amountText)
                        val date = Money.parseDate(dateText)
                        when {
                            cents == null || cents <= 0 -> error = "请输入大于 0 的金额"
                            date == null -> error = "日期格式应为 yyyy-MM-dd"
                            else -> {
                                val saved = Transaction(
                                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                    type = type,
                                    amountCents = cents,
                                    category = category,
                                    note = note.trim(),
                                    date = date,
                                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                )
                                BalanceBookRepository.upsertTransaction(saved)
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
