package com.dsh.balancebook.finance

import java.time.DateTimeException
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 借款还款计划计算。全部金额单位为「分」，纯 Kotlin，无 Android 依赖，可直接 JVM 单测。
 *
 * 规则见 FUNCTION_SPEC_FOR_KIT_AGENT.md 第 3.3 节：
 * - DAYS:   到期日 = startDate + intervalDays * i；周期利率 = 年利率 / 365 × N
 * - MONTHS: 到期日 = startDate + i 个月（仅旧版 v1 备份兼容）；周期利率 = 年利率 / 12
 * - 先息后本 / 等额本息；最后一期抹平差额。
 */
object LoanCalculator {

    const val MIN_INSTALLMENT_COUNT = 1
    const val MAX_INSTALLMENT_COUNT = 360
    const val MIN_INTERVAL_DAYS = 1
    const val MAX_INTERVAL_DAYS = 365
    const val MAX_ANNUAL_RATE = 100.0
    const val DAYS_PER_YEAR = 365.0

    /** 计息周期利率（小数，例如 0.006575 = 0.6575%）。 */
    fun periodRate(terms: LoanTerms): Double {
        val annual = terms.annualRate.coerceIn(0.0, MAX_ANNUAL_RATE) / 100.0
        return when (terms.intervalUnit) {
            ScheduleUnit.DAYS -> annual / DAYS_PER_YEAR * terms.intervalDays
            ScheduleUnit.MONTHS -> annual / 12.0
        }
    }

    fun dueDate(terms: LoanTerms, period: Int): LocalDate {
        val start = terms.startDate
        return try {
            when (terms.intervalUnit) {
                ScheduleUnit.DAYS ->
                    start.plusDays(terms.intervalDays.toLong() * period.toLong())
                ScheduleUnit.MONTHS ->
                    start.plusMonths(period.toLong())
            }
        } catch (_: DateTimeException) {
            LocalDate.MAX
        }
    }

    /** 生成完整还款计划；非法条款返回空列表（调用方应先 validate）。 */
    fun schedule(terms: LoanTerms): List<Installment> {
        val n = terms.installmentCount
        if (n !in MIN_INSTALLMENT_COUNT..MAX_INSTALLMENT_COUNT) return emptyList()
        val principal = terms.principalCents
        if (principal <= 0) return emptyList()
        if (validate(terms) != null) return emptyList()

        return when (terms.interestMode) {
            InterestMode.RATE -> when (terms.method) {
                LoanMethod.INTEREST_FIRST -> interestFirstByRate(terms, principal, n)
                LoanMethod.EQUAL_PAYMENT -> equalPaymentByRate(terms, principal, n)
            }

            InterestMode.FIXED -> when (terms.method) {
                LoanMethod.INTEREST_FIRST -> when (terms.fixedInterestScope) {
                    FixedInterestScope.TOTAL -> interestFirstFixedTotal(terms, principal, n)
                    FixedInterestScope.PER_INSTALLMENT ->
                        interestFirstFixedPerInstallment(terms, principal, n)
                }

                LoanMethod.EQUAL_PAYMENT -> equalPaymentFixedTotal(terms, principal, n)
            }
        }
    }

    private fun interestFirstByRate(terms: LoanTerms, principal: Long, n: Int): List<Installment> {
        val rate = periodRate(terms)
        val interestEach = roundCents(principal.toDouble() * rate)
        return (1..n).map { i ->
            val principalPart = if (i == n) principal else 0L
            val interestPart = interestEach
            installment(terms, i, principalPart, interestPart)
        }
    }

    private fun interestFirstFixedTotal(terms: LoanTerms, principal: Long, n: Int): List<Installment> {
        val totalInterest = terms.fixedInterestCents.coerceAtLeast(0)
        val base = totalInterest / n
        return (1..n).map { i ->
            val interestPart = if (i == n) totalInterest - base * (n - 1) else base
            val principalPart = if (i == n) principal else 0L
            installment(terms, i, principalPart, interestPart)
        }
    }

    private fun interestFirstFixedPerInstallment(terms: LoanTerms, principal: Long, n: Int): List<Installment> {
        val interestEach = terms.fixedInterestCents.coerceAtLeast(0)
        return (1..n).map { i ->
            val principalPart = if (i == n) principal else 0L
            installment(terms, i, principalPart, interestEach)
        }
    }

    private fun equalPaymentByRate(terms: LoanTerms, principal: Long, n: Int): List<Installment> {
        val rate = periodRate(terms)
        if (rate <= RATE_EPSILON) {
            val base = principal / n
            var remaining = principal
            return (1..n).map { i ->
                val principalPart = if (i == n) remaining else minOf(remaining, base)
                remaining -= principalPart
                installment(terms, i, principalPart, 0L)
            }
        }

        val factor = (1.0 + rate).pow(n)
        val denominator = factor - 1.0
        val pmt = if (factor.isFinite() && denominator > 0.0) {
            roundCents(principal.toDouble() * rate * factor / denominator).coerceAtLeast(1L)
        } else {
            // 极端参数下退回均摊本金，避免产生 NaN / Infinity。
            (principal / n).coerceAtLeast(1L)
        }

        var remaining = principal
        val out = ArrayList<Installment>(n)
        for (i in 1..n) {
            val interestPart = roundCents(remaining.toDouble() * rate)
            val principalPart = if (i == n) {
                remaining
            } else {
                val planned = pmt - interestPart
                planned.coerceIn(0L, remaining)
            }
            out += installment(terms, i, principalPart, interestPart)
            remaining -= principalPart
        }
        return out
    }

    private fun equalPaymentFixedTotal(terms: LoanTerms, principal: Long, n: Int): List<Installment> {
        val totalInterest = terms.fixedInterestCents.coerceAtLeast(0)
        val total = principal + totalInterest
        val baseDue = total / n
        val interestBase = totalInterest / n
        return (1..n).map { i ->
            val due = if (i == n) total - baseDue * (n - 1) else baseDue
            val interestPart = if (i == n) totalInterest - interestBase * (n - 1) else interestBase
            val principalPart = due - interestPart
            Installment(
                period = i,
                dueDate = dueDate(terms, i),
                dueCents = due,
                principalPartCents = principalPart,
                interestPartCents = interestPart,
            )
        }
    }

    private fun installment(terms: LoanTerms, period: Int, principalPart: Long, interestPart: Long) =
        Installment(
            period = period,
            dueDate = dueDate(terms, period),
            dueCents = principalPart + interestPart,
            principalPartCents = principalPart,
            interestPartCents = interestPart,
        )

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    /** 返回第一条错误文案；null 表示合法。 */
    fun validate(terms: LoanTerms): String? {
        if (terms.principalCents <= 0) return "本金必须大于 0"
        if (terms.installmentCount !in MIN_INSTALLMENT_COUNT..MAX_INSTALLMENT_COUNT) {
            return "期数必须在 $MIN_INSTALLMENT_COUNT–$MAX_INSTALLMENT_COUNT 之间"
        }
        if (terms.intervalUnit == ScheduleUnit.DAYS &&
            terms.intervalDays !in MIN_INTERVAL_DAYS..MAX_INTERVAL_DAYS
        ) {
            return "间隔天数必须在 $MIN_INTERVAL_DAYS–$MAX_INTERVAL_DAYS 之间"
        }
        if (!terms.annualRate.isFinite() || terms.annualRate < 0.0 || terms.annualRate > MAX_ANNUAL_RATE) {
            return "年利率必须在 0–100% 之间"
        }
        if (terms.fixedInterestCents < 0) return "固定利息不能为负数"
        if (terms.interestMode == InterestMode.FIXED &&
            terms.fixedInterestScope == FixedInterestScope.PER_INSTALLMENT &&
            terms.method == LoanMethod.EQUAL_PAYMENT
        ) {
            return "固定每期利息只支持先息后本"
        }
        if (terms.interestMode == InterestMode.FIXED &&
            terms.fixedInterestScope == FixedInterestScope.PER_INSTALLMENT
        ) {
            val n = terms.installmentCount.toLong()
            if (terms.fixedInterestCents != 0L && terms.fixedInterestCents > Long.MAX_VALUE / n) {
                return "固定每期利息金额过大"
            }
        }
        if (terms.interestMode == InterestMode.FIXED &&
            terms.fixedInterestScope == FixedInterestScope.TOTAL
        ) {
            if (terms.fixedInterestCents > Long.MAX_VALUE - terms.principalCents) {
                return "固定总利息金额过大"
            }
        }
        // 日期溢出检查
        try {
            when (terms.intervalUnit) {
                ScheduleUnit.DAYS ->
                    terms.startDate.plusDays(terms.intervalDays.toLong() * terms.installmentCount.toLong())
                ScheduleUnit.MONTHS -> terms.startDate.plusMonths(terms.installmentCount.toLong())
            }
        } catch (_: DateTimeException) {
            return "还款日期超出可表示范围"
        }
        return null
    }

    fun isValid(terms: LoanTerms): Boolean = validate(terms) == null

    // ------------------------------------------------------------------
    // 进度
    // ------------------------------------------------------------------

    fun installments(loan: Loan): List<Installment> = schedule(loan.terms)

    fun totalReceivableCents(loan: Loan): Long = schedule(loan.terms).sumOf { it.dueCents }

    fun totalInterestCents(loan: Loan): Long =
        (totalReceivableCents(loan) - loan.principalCents).coerceAtLeast(0)

    fun paidCount(loan: Loan): Int = coveredCount(loan)

    /**
     * 已被还款覆盖的期数：按「累计已还 ≥ 累计应还」计算，允许任意金额、部分还款、提前还。
     * 旧数据里显式标记了 period 的还款仍按该期已还处理，保证 v1 备份的「已还 x/y 期」不倒退。
     */
    fun coveredCount(loan: Loan): Int {
        val n = loan.installmentCount.coerceIn(MIN_INSTALLMENT_COUNT, MAX_INSTALLMENT_COUNT)
        val list = schedule(loan.terms)
        if (list.isEmpty()) return 0
        val paid = paidCents(loan)
        var cumulative = 0L
        var covered = 0
        for (installment in list) {
            cumulative += installment.dueCents
            if (paid >= cumulative) covered++ else break
        }
        val explicitPeriod = loan.payments
            .mapNotNull { payment -> payment.period.takeIf { it in 1..n } }
            .maxOrNull() ?: 0
        return maxOf(covered, explicitPeriod).coerceIn(0, n)
    }

    fun paidCents(loan: Loan): Long = loan.payments.sumOf { it.amountCents }

    fun outstandingCents(loan: Loan): Long =
        (totalReceivableCents(loan) - paidCents(loan)).coerceAtLeast(0)

    fun paidPercent(loan: Loan): Int {
        val total = totalReceivableCents(loan)
        if (total <= 0) return 0
        val paid = paidCents(loan)
        return ((paid * 100) / total).coerceIn(0, 100).toInt()
    }

    fun settled(loan: Loan): Boolean =
        paidCount(loan) >= loan.installmentCount || outstandingCents(loan) <= 0

    fun nextInstallment(loan: Loan): Installment? {
        if (settled(loan)) return null
        val list = schedule(loan.terms)
        return list.getOrNull(coveredCount(loan))
    }

    fun nextDueDate(loan: Loan): LocalDate? = nextInstallment(loan)?.dueDate

    /** 下一期还差多少（考虑部分还款）；已结清返回 0。 */
    fun nextRemainingCents(loan: Loan): Long {
        if (settled(loan)) return 0
        val list = schedule(loan.terms)
        val index = coveredCount(loan)
        val next = list.getOrNull(index) ?: return 0
        val cumulativeBefore = list.take(index).sumOf { it.dueCents }
        return (cumulativeBefore + next.dueCents - paidCents(loan)).coerceIn(0, next.dueCents)
    }

    fun status(loan: Loan, today: LocalDate = LocalDate.now()): LoanStatus {
        if (settled(loan)) return LoanStatus.SETTLED
        // 关闭分期提醒后，借款只按总待收跟踪，不再按到期日判定逾期。
        if (!loan.reminderEnabled) return LoanStatus.NORMAL
        val due = nextDueDate(loan) ?: return LoanStatus.SETTLED
        return when {
            due.isBefore(today) -> LoanStatus.OVERDUE
            due.isEqual(today) -> LoanStatus.DUE_TODAY
            else -> LoanStatus.NORMAL
        }
    }

    fun preview(terms: LoanTerms): LoanPreview {
        val list = schedule(terms)
        val total = list.sumOf { it.dueCents }
        val totalInterest = (total - terms.principalCents).coerceAtLeast(0)
        return LoanPreview(
            installments = list,
            firstDueCents = list.firstOrNull()?.dueCents ?: 0,
            lastDueCents = list.lastOrNull()?.dueCents ?: 0,
            totalReceivableCents = total,
            totalInterestCents = totalInterest,
            interestPercent = if (total > 0) ((totalInterest * 100) / total).toInt() else 0,
            lastDueDate = list.lastOrNull()?.dueDate,
        )
    }

    /** 四舍五入到分；金额均为非负数。 */
    private fun roundCents(value: Double): Long {
        if (!value.isFinite()) return 0
        return value.roundToLong()
    }

    private const val RATE_EPSILON = 1e-12
}

/** 示例数据与状态查询所需的汇总信息。 */
data class LoanSummary(
    val totalOutstandingCents: Long,
    val nextDueCents: Long,
    val activeCount: Int,
    val overdueCount: Int,
    val dueSoonCount: Int,
)

/** 余额 / 月度统计，纯 Kotlin。 */
object FinanceCalculator {

    /** 余额（分）：
     * opening + Σ收入 − Σ支出 − Σ借出本金(计入余额且起借日<=今天) + Σ已收借款。
     */
    fun balanceCents(state: FinanceState, today: LocalDate = LocalDate.now()): Long {
        var balance = state.settings.openingBalanceCents
        for (t in state.transactions) {
            balance += if (t.type == TransactionType.INCOME) t.amountCents else -t.amountCents
        }
        for (loan in state.loans) {
            if (!loan.includeInBalance) continue
            if (!loan.startDate.isAfter(today)) {
                balance -= loan.principalCents
            }
            balance += loan.payments.sumOf { it.amountCents }
        }
        return balance
    }

    fun monthExpenseCents(state: FinanceState, year: Int, month: Int): Long =
        state.transactions
            .filter { it.type == TransactionType.EXPENSE && it.date.year == year && it.date.monthValue == month }
            .sumOf { it.amountCents }

    fun monthIncomeCents(state: FinanceState, year: Int, month: Int): Long =
        state.transactions
            .filter { it.type == TransactionType.INCOME && it.date.year == year && it.date.monthValue == month }
            .sumOf { it.amountCents }

    fun budgetRemainingCents(state: FinanceState, year: Int, month: Int): Long =
        state.settings.monthlyBudgetCents - monthExpenseCents(state, year, month)

    fun budgetPercent(state: FinanceState, year: Int, month: Int): Int {
        val budget = state.settings.monthlyBudgetCents
        if (budget <= 0) return 0
        return ((monthExpenseCents(state, year, month) * 100) / budget).toInt()
    }

    fun loanSummary(state: FinanceState, today: LocalDate = LocalDate.now()): LoanSummary {
        var outstanding = 0L
        var nextDue = 0L
        var active = 0
        var overdue = 0
        var dueSoon = 0
        for (loan in state.loans) {
            outstanding += LoanCalculator.outstandingCents(loan)
            if (!LoanCalculator.settled(loan)) {
                active++
                if (loan.reminderEnabled) {
                    val next = LoanCalculator.nextInstallment(loan)
                    if (next != null) {
                        nextDue += LoanCalculator.nextRemainingCents(loan)
                        if (next.dueDate.isBefore(today)) overdue++
                        if (!next.dueDate.isAfter(today.plusDays(3))) dueSoon++
                    }
                }
            }
        }
        return LoanSummary(outstanding, nextDue, active, overdue, dueSoon)
    }

    /** 未来 [months] 个月（含本月）的支出，按时间正序。 */
    fun recentMonthlyExpenses(state: FinanceState, months: Int = 6, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Long>> {
        val out = ArrayList<Pair<LocalDate, Long>>(months)
        for (i in months - 1 downTo 0) {
            val month = today.minusMonths(i.toLong()).withDayOfMonth(1)
            out += month to monthExpenseCents(state, month.year, month.monthValue)
        }
        return out
    }
}
