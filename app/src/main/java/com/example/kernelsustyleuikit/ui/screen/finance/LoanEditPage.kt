package com.example.kernelsustyleuikit.ui.screen.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kernelsustyleuikit.data.BalanceBookRepository
import com.example.kernelsustyleuikit.finance.FixedInterestScope
import com.example.kernelsustyleuikit.finance.InterestMode
import com.example.kernelsustyleuikit.finance.Loan
import com.example.kernelsustyleuikit.finance.LoanCalculator
import com.example.kernelsustyleuikit.finance.LoanMethod
import com.example.kernelsustyleuikit.finance.LoanTerms
import com.example.kernelsustyleuikit.finance.Money
import com.example.kernelsustyleuikit.finance.ScheduleUnit
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.time.LocalDate

private val intervalDayPresets = listOf(7, 14, 15, 30, 60, 90)

@Composable
fun LoanEditPage(
    loanId: String?,
    onBack: () -> Unit,
) {
    val initial = remember(loanId) { loanId?.let { BalanceBookRepository.loanById(it) } }
    val locked = (initial?.payments?.isNotEmpty() == true)

    var friend by rememberSaveable { mutableStateOf(initial?.friend ?: "") }
    var principalText by rememberSaveable {
        mutableStateOf(initial?.let { Money.centsToInput(it.principalCents) } ?: "")
    }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    var startText by rememberSaveable {
        mutableStateOf((initial?.startDate ?: LocalDate.now()).toString())
    }
    var interestModeOrdinal by rememberSaveable {
        mutableIntStateOf(initial?.interestMode?.ordinal ?: InterestMode.RATE.ordinal)
    }
    var scopeOrdinal by rememberSaveable {
        mutableIntStateOf(initial?.fixedInterestScope?.ordinal ?: FixedInterestScope.TOTAL.ordinal)
    }
    var rateText by rememberSaveable {
        mutableStateOf(initial?.annualRate?.let { trimNumber(it) } ?: "0")
    }
    var fixedText by rememberSaveable {
        mutableStateOf(initial?.let { Money.centsToInput(it.fixedInterestCents) } ?: "0")
    }
    var daysText by rememberSaveable {
        mutableStateOf((initial?.intervalDays ?: 30).toString())
    }
    var countText by rememberSaveable {
        mutableStateOf((initial?.installmentCount ?: 12).toString())
    }
    var methodOrdinal by rememberSaveable {
        mutableIntStateOf(initial?.method?.ordinal ?: LoanMethod.INTEREST_FIRST.ordinal)
    }
    var unitOrdinal by rememberSaveable {
        mutableIntStateOf(initial?.intervalUnit?.ordinal ?: ScheduleUnit.DAYS.ordinal)
    }
    var includeInBalance by rememberSaveable { mutableStateOf(initial?.includeInBalance ?: true) }
    var reminderEnabled by rememberSaveable { mutableStateOf(initial?.reminderEnabled ?: true) }
    var interestEnabled by rememberSaveable { mutableStateOf(initial?.interestEnabled ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    val interestMode = InterestMode.entries[interestModeOrdinal]
    val scope = FixedInterestScope.entries[scopeOrdinal]
    val method = LoanMethod.entries[methodOrdinal]
    val unit = ScheduleUnit.entries[unitOrdinal]

    val startDate = Money.parseDate(startText)
    val principalCents = Money.parseYuanToCents(principalText) ?: 0
    val fixedCents = Money.parseYuanToCents(fixedText) ?: 0
    // 主开关：利息 / 分期提醒关闭时，使用无息、单期的简单条款。
    val effectiveMethod = if (reminderEnabled) method else LoanMethod.EQUAL_PAYMENT
    val effectiveUnit = if (reminderEnabled) unit else ScheduleUnit.DAYS
    val terms = LoanTerms(
        principalCents = principalCents,
        installmentCount = if (reminderEnabled) (countText.toIntOrNull() ?: 0) else 1,
        intervalUnit = effectiveUnit,
        intervalDays = if (reminderEnabled) {
            if (unit == ScheduleUnit.MONTHS) 30 else (daysText.toIntOrNull() ?: 0)
        } else {
            30
        },
        startDate = startDate ?: LocalDate.now(),
        method = effectiveMethod,
        interestMode = if (interestEnabled) interestMode else InterestMode.RATE,
        annualRate = if (interestEnabled) (rateText.toDoubleOrNull() ?: 0.0) else 0.0,
        fixedInterestCents = if (interestEnabled) fixedCents else 0,
        fixedInterestScope = scope,
    )
    val validation = LoanCalculator.validate(terms)
    val preview = if (validation == null) LoanCalculator.preview(terms) else null

    FinancePage(
        title = if (initial == null) "新增借款" else "编辑借款",
        onBack = onBack,
        extraBottomPadding = 40.dp,
    ) {
        if (locked) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("已存在收款记录", fontSize = 14.sp, color = WarningOrange)
                        Text(
                            text = "本金、利息、期数、间隔、起借日和还款方式已锁定；如需修改，请在详情页清空还款记录后重算。",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "基本信息") {
                FormField(
                    title = "好友昵称",
                    value = friend,
                    onValueChange = {
                        friend = it
                        error = null
                    },
                    hint = "必填",
                    summary = "借给谁",
                )
                FormField(
                    title = "本金",
                    value = principalText,
                    onValueChange = {
                        principalText = it
                        error = null
                    },
                    hint = "0.00",
                    keyboardType = KeyboardType.Decimal,
                    enabled = !locked,
                    isError = principalCents <= 0,
                )
                FormField(
                    title = "起借日期",
                    value = startText,
                    onValueChange = {
                        startText = it
                        error = null
                    },
                    hint = "yyyy-MM-dd",
                    enabled = !locked,
                    isError = startDate == null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("今天", false, enabled = !locked, onClick = { startText = LocalDate.now().toString() })
                    SelectChip("昨天", false, enabled = !locked, onClick = { startText = LocalDate.now().minusDays(1).toString() })
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "计算利息",
                    summary = "开启后填写计息方式；关闭按无息借款处理",
                    checked = interestEnabled,
                    enabled = !locked,
                    onCheckedChange = { interestEnabled = it },
                )
                SwitchPreference(
                    title = "分期提醒",
                    summary = "开启后设置还款间隔与期数并生成还款计划；关闭只跟踪总待收",
                    checked = reminderEnabled,
                    onCheckedChange = { reminderEnabled = it },
                )
            }
        }

        if (interestEnabled) {
        item {
            SectionCard(title = "利息方式") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip(
                        "按年利率",
                        interestMode == InterestMode.RATE,
                        enabled = !locked,
                        onClick = { interestModeOrdinal = InterestMode.RATE.ordinal },
                    )
                    SelectChip(
                        "固定利息金额",
                        interestMode == InterestMode.FIXED,
                        enabled = !locked,
                        onClick = { interestModeOrdinal = InterestMode.FIXED.ordinal },
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (interestMode == InterestMode.RATE) {
                    FormField(
                        title = "年利率",
                        value = rateText,
                        onValueChange = {
                            rateText = it
                            error = null
                        },
                        hint = "0–100",
                        keyboardType = KeyboardType.Decimal,
                        enabled = !locked,
                        summary = "%，按实际天数 / 365 计息",
                        isError = (rateText.toDoubleOrNull() ?: -1.0) !in 0.0..100.0,
                    )
                    val ratePresets = listOf("0", "5", "8", "10", "12", "24")
                    ChipRows(
                        options = ratePresets.map { "$it%" },
                        isSelected = { rateText == ratePresets[it] },
                        onSelected = { rateText = ratePresets[it] },
                        enabled = { !locked },
                        perRow = 3,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip(
                            "固定总利息",
                            scope == FixedInterestScope.TOTAL,
                            enabled = !locked,
                            onClick = { scopeOrdinal = FixedInterestScope.TOTAL.ordinal },
                        )
                        SelectChip(
                            "固定每期利息",
                            scope == FixedInterestScope.PER_INSTALLMENT,
                            enabled = !locked,
                            onClick = {
                                scopeOrdinal = FixedInterestScope.PER_INSTALLMENT.ordinal
                                methodOrdinal = LoanMethod.INTEREST_FIRST.ordinal
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FormField(
                        title = if (scope == FixedInterestScope.TOTAL) "总利息" else "每期利息",
                        value = fixedText,
                        onValueChange = {
                            fixedText = it
                            error = null
                        },
                        hint = "0.00",
                        keyboardType = KeyboardType.Decimal,
                        enabled = !locked,
                        summary = "元，可以为 0（无息）",
                    )
                    Text(
                        text = if (scope == FixedInterestScope.PER_INSTALLMENT) {
                            "固定每期利息只支持先息后本。"
                        } else {
                            "固定总利息支持先息后本和等额本息。"
                        },
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        }

        if (reminderEnabled) {
        item {
            SectionCard(title = "还款间隔") {
                if (unit == ScheduleUnit.MONTHS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "旧版按月借款（每月一期）",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f),
                        )
                        SelectChip("转为按天", false, enabled = !locked, onClick = {
                            unitOrdinal = ScheduleUnit.DAYS.ordinal
                            daysText = "30"
                        })
                    }
                } else {
                    FormField(
                        title = "每隔（天）",
                        value = daysText,
                        onValueChange = {
                            daysText = it.filter { c -> c.isDigit() }
                            error = null
                        },
                        hint = "1–365",
                        keyboardType = KeyboardType.Number,
                        enabled = !locked,
                        summary = "每隔 N 天还一次",
                    )
                    ChipRows(
                        options = intervalDayPresets.map { "$it 天" },
                        isSelected = { daysText == intervalDayPresets[it].toString() },
                        onSelected = { daysText = intervalDayPresets[it].toString() },
                        enabled = { !locked },
                        perRow = 3,
                    )
                }
                FormField(
                    title = "共（期）",
                    value = countText,
                    onValueChange = {
                        countText = it.filter { c -> c.isDigit() }
                        error = null
                    },
                    hint = "1–60",
                    keyboardType = KeyboardType.Number,
                    enabled = !locked,
                    summary = "第 i 期到期日 = 起借日 + 间隔天数 × i",
                )
                Text(
                    text = preview?.lastDueDate?.let { "预计最后还款日：${Money.formatDate(it)}" } ?: "填写本金后显示预计最后还款日",
                    fontSize = 13.sp,
                    color = colorScheme.onSurface,
                )
            }
        }

        item {
            SectionCard(title = "还款方式") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip(
                        "先息后本",
                        method == LoanMethod.INTEREST_FIRST,
                        enabled = !locked,
                        onClick = { methodOrdinal = LoanMethod.INTEREST_FIRST.ordinal },
                    )
                    SelectChip(
                        "等额本息",
                        method == LoanMethod.EQUAL_PAYMENT,
                        enabled = !locked && scope != FixedInterestScope.PER_INSTALLMENT,
                        onClick = { methodOrdinal = LoanMethod.EQUAL_PAYMENT.ordinal },
                    )
                }
                if (locked) {
                    Text(
                        text = "已有收款记录，还款方式已锁定。",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "计入总余额",
                    summary = "借出本金会从总余额中扣除，收到还款后回补",
                    checked = includeInBalance,
                    onCheckedChange = { includeInBalance = it },
                )
            }
        }

        item {
            SectionCard(title = "备注") {
                FormField(
                    title = "备注",
                    value = note,
                    onValueChange = { note = it },
                    hint = "可选",
                )
            }
        }

        if (reminderEnabled) {
        item {
            SectionCard(title = "实时预览") {
                if (preview == null) {
                    Text(
                        text = validation ?: "请先填写本金和期数",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    InfoRow("每期应还（首期）", Money.formatCentsExact(preview.firstDueCents))
                    InfoRow("末期应还", Money.formatCentsExact(preview.lastDueCents))
                    InfoRow("总应收", Money.formatCentsExact(preview.totalReceivableCents))
                    InfoRow("总利息", Money.formatCentsExact(preview.totalInterestCents))
                    InfoRow("利息占应收", "${preview.interestPercent}%")
                    InfoRow(
                        "还款安排",
                        "共 ${terms.installmentCount} 期 · 每 " +
                            (if (unit == ScheduleUnit.MONTHS) "1 个月" else "${terms.intervalDays} 天"),
                    )
                    if (unit == ScheduleUnit.DAYS && terms.installmentCount in 1..60) {
                        Text(
                            text = "第 1 期 ${Money.formatDate(preview.installments.first().dueDate)}，" +
                                "最后一期 ${preview.lastDueDate?.let { Money.formatDate(it) } ?: "—"}",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        }

        if (error != null) {
            item {
                Text(
                    text = error!!,
                    color = ExpenseRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = if (initial == null) "保存借款" else "保存修改",
                    onClick = {
                        val trimmedFriend = friend.trim()
                        val start = Money.parseDate(startText)
                        when {
                            trimmedFriend.isEmpty() -> error = "请填写好友昵称"
                            principalCents <= 0 -> error = "请输入大于 0 的本金"
                            start == null -> error = "起借日期格式应为 yyyy-MM-dd"
                            !locked && terms.installmentCount !in 1..60 -> error = "期数需在 1–60 之间"
                            validation != null -> error = validation
                            else -> {
                                val payments = initial?.payments ?: emptyList()
                                val saved = if (locked && initial != null) {
                                    initial.copy(
                                        friend = trimmedFriend,
                                        note = note.trim(),
                                        includeInBalance = includeInBalance,
                                        reminderEnabled = reminderEnabled,
                                    )
                                } else {
                                    Loan(
                                        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                                        friend = trimmedFriend,
                                        note = note.trim(),
                                        principalCents = principalCents,
                                        installmentCount = terms.installmentCount,
                                        intervalUnit = effectiveUnit,
                                        intervalDays = terms.intervalDays,
                                        startDate = start,
                                        method = terms.method,
                                        interestMode = terms.interestMode,
                                        annualRate = terms.annualRate,
                                        fixedInterestCents = terms.fixedInterestCents,
                                        fixedInterestScope = scope,
                                        interestEnabled = interestEnabled,
                                        includeInBalance = includeInBalance,
                                        reminderEnabled = reminderEnabled,
                                        payments = payments,
                                        createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                                    )
                                }
                                BalanceBookRepository.upsertLoan(saved)
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

private fun trimNumber(value: Double): String {
    val text = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.2f", value).trimEnd('0').trimEnd('.')
    }
    return text
}

