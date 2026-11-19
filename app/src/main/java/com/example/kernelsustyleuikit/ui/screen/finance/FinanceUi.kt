package com.example.kernelsustyleuikit.ui.screen.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kernelsustyleuikit.finance.Money
import com.example.kernelsustyleuikit.ui.component.miuix.EditText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

val IncomeGreen = Color(0xFF2E9E5B)
val ExpenseRed = Color(0xFFE04B4B)
val WarningOrange = Color(0xFFE08A2B)

/** 带 TopAppBar 的 Miuix 页面骨架。 */
@Composable
fun FinancePage(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    extraBottomPadding: Dp = 24.dp,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (onBack != null) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = colorScheme.onBackground,
                            )
                        }
                    }
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                content()
                item { Spacer(Modifier.height(extraBottomPadding)) }
            },
        )
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            content()
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
fun ProgressBar(
    percent: Int,
    modifier: Modifier = Modifier,
    color: Color = colorScheme.primary,
    trackColor: Color = colorScheme.onSurface.copy(alpha = 0.08f),
    height: Dp = 8.dp,
) {
    val fraction = percent.coerceIn(0, 100) / 100f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}

/** 可选择的胶囊按钮（Miuix 主题色，非自定义 View）。 */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> colorScheme.surfaceContainer.copy(alpha = 0.4f)
        selected -> colorScheme.primary
        else -> colorScheme.surfaceContainer
    }
    val textColor = when {
        !enabled -> colorScheme.disabledOnSecondaryVariant
        selected -> colorScheme.onPrimary
        else -> colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 13.sp, color = textColor, maxLines = 1)
    }
}

@Composable
fun SelectChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (Int) -> Boolean = { true },
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            SelectChip(
                text = label,
                selected = selectedIndex == index,
                enabled = enabled(index),
                onClick = { onSelected(index) },
            )
        }
    }
}

/** 分多行排列的胶囊选项，避免窄屏溢出。 */
@Composable
fun ChipRows(
    options: List<String>,
    isSelected: (Int) -> Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    perRow: Int = 3,
    enabled: (Int) -> Boolean = { true },
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.chunked(perRow).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIndex, label ->
                    val index = rowIndex * perRow + colIndex
                    SelectChip(
                        text = label,
                        selected = isSelected(index),
                        enabled = enabled(index),
                        onClick = { onSelected(index) },
                    )
                }
            }
        }
    }
}

/** 预算百分比：整数就显示整数，否则保留一位小数（如 0.9%）。 */
fun budgetPercentLabel(state: com.example.kernelsustyleuikit.finance.FinanceState, year: Int, month: Int): String {
    val budget = state.settings.monthlyBudgetCents
    if (budget <= 0) return "—"
    val expense = com.example.kernelsustyleuikit.finance.FinanceCalculator.monthExpenseCents(state, year, month)
    val percent = expense.toDouble() * 100.0 / budget.toDouble()
    val rounded = kotlin.math.round(percent * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        "${rounded.toLong()}%"
    } else {
        String.format(java.util.Locale.CHINA, "%.1f%%", rounded)
    }
}

/** 借款已还金额百分比：整数就显示整数，否则保留一位小数（如 1.0%）。 */
fun paidPercentLabel(loan: com.example.kernelsustyleuikit.finance.Loan): String {
    val total = com.example.kernelsustyleuikit.finance.LoanCalculator.totalReceivableCents(loan)
    if (total <= 0) return "0%"
    val percent = loan.paidCents.toDouble() * 100.0 / total.toDouble()
    val rounded = kotlin.math.round(percent * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        "${rounded.toLong()}%"
    } else {
        String.format(java.util.Locale.CHINA, "%.1f%%", rounded)
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(text = actionText, onClick = onAction)
        }
    }
}

/** 表单输入：沿用模板 Miuix 风格 EditText。 */
@Composable
fun FormField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    isError: Boolean = false,
    summary: String? = null,
) {
    EditText(
        title = title,
        value = value,
        onValueChange = onValueChange,
        summary = summary,
        textHint = hint,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

/** 金额输入弹窗（Miuix WindowDialog）。 */
@Composable
fun AmountInputDialog(
    title: String,
    show: Boolean,
    initialText: String,
    allowZero: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var text by remember(show, initialText) { mutableStateOf(initialText) }
    var error by remember(show) { mutableStateOf(false) }
    if (!show) return
    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
        content = {
            Column {
                Text(
                    text = "以元为单位，最多两位小数",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                EditText(
                    title = "金额",
                    value = text,
                    onValueChange = {
                        text = it
                        error = false
                    },
                    textHint = "0.00",
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (error) {
                    Text(
                        text = if (allowZero) "请输入不小于 0 的金额" else "请输入大于 0 的金额",
                        color = ExpenseRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                    TextButton(
                        text = "确定",
                        onClick = {
                            val cents = Money.parseYuanToCents(text)
                            if (cents == null || (!allowZero && cents <= 0)) {
                                error = true
                            } else {
                                onConfirm(cents)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
fun QuickAmountChips(
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(1000L, 5000L, 10000L).forEach { yuan ->
            SelectChip(text = "¥$yuan", selected = false, onClick = { onPick(yuan * 100) })
        }
    }
}

@Composable
fun AmountDisplay(
    cents: Long,
    modifier: Modifier = Modifier,
    fontSize: Int = 24,
    color: Color = colorScheme.onSurface,
    showSign: Boolean = false,
) {
    val prefix = when {
        !showSign -> ""
        cents > 0 -> "+"
        else -> ""
    }
    Text(
        text = prefix + Money.formatCentsExact(cents),
        modifier = modifier,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            tint = colorScheme.onBackground,
        )
    }
}

@Composable
fun RowScope.TopBarTextAction(text: String, onClick: () -> Unit) {
    TextButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.padding(end = 4.dp),
    )
}

@Composable
fun PageTopSpacer(height: Dp = 4.dp) {
    Spacer(Modifier.height(height))
}

@Composable
fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.surfaceContainer),
    )
}
