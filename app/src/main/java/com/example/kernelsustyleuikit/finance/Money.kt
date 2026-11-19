package com.example.kernelsustyleuikit.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** 金额 / 日期格式化与解析。金额一律 Long 分。 */
object Money {

    private val MONEY_INPUT = Regex("^\\d{1,12}(\\.\\d{1,2})?$")

    /** 解析「元」输入为分；非法返回 null。最多两位小数，不接受 NaN/Infinity。 */
    fun parseYuanToCents(input: String?): Long? {
        if (input == null) return null
        val cleaned = input.trim()
            .replace(",", "")
            .replace("¥", "")
            .replace("￥", "")
            .replace(" ", "")
        if (cleaned.isEmpty() || !MONEY_INPUT.matches(cleaned)) return null
        return try {
            BigDecimal(cleaned)
                .setScale(2, RoundingMode.UNNECESSARY)
                .multiply(BigDecimal(100))
                .longValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** 分 → 元字符串，不含货币符号，用于输入框回填（如 1234.56）。 */
    fun centsToInput(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val integer = abs / 100
        val fraction = (abs % 100).toString().padStart(2, '0')
        val text = "$integer.$fraction"
        return if (negative) "-$text" else text
    }

    /** 分 → 展示文本（如 ¥1,234.56）。 */
    fun formatCents(cents: Long, withSymbol: Boolean = true, showZeroDecimals: Boolean = false): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val integer = abs / 100
        val fraction = abs % 100
        val grouped = groupThousands(integer)
        val body = when {
            fraction == 0L && showZeroDecimals -> "$grouped.00"
            fraction == 0L -> grouped
            else -> "$grouped.${fraction.toString().padStart(2, '0')}"
        }
        val sign = if (negative) "-" else ""
        return if (withSymbol) "$sign¥$body" else "$sign$body"
    }

    /** 分 → 带两位小数的元文本（统计页用，如 ¥1,234.56）。 */
    fun formatCentsExact(cents: Long): String = formatCents(cents, withSymbol = true, showZeroDecimals = true)

    private fun groupThousands(value: Long): String {
        val raw = value.toString()
        if (raw.length <= 3) return raw
        val builder = StringBuilder()
        var count = 0
        for (i in raw.lastIndex downTo 0) {
            builder.append(raw[i])
            count++
            if (count % 3 == 0 && i != 0) builder.append(',')
        }
        return builder.reverse().toString()
    }

    /** 年利率展示：8 → "8%"，8.5 → "8.5%"。 */
    fun formatRate(rate: Double): String {
        if (!rate.isFinite()) return "0%"
        val rounded = BigDecimal(rate).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        return "${rounded.toPlainString()}%"
    }

    fun parseDate(text: String?): LocalDate? {
        if (text == null) return null
        return try {
            LocalDate.parse(text.trim())
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun formatDate(date: LocalDate): String =
        "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)

    fun formatMonthDay(date: LocalDate): String =
        "%d月%d日".format(date.monthValue, date.dayOfMonth)

    fun formatMonth(date: LocalDate): String =
        "%d年%d月".format(date.year, date.monthValue)

    fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
}
