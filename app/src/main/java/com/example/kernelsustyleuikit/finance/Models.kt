package com.example.kernelsustyleuikit.finance

import java.time.LocalDate
import java.util.UUID

/** 账单类型：0 支出，1 收入。 */
enum class TransactionType(val storageValue: Int) {
    EXPENSE(0),
    INCOME(1);

    companion object {
        fun fromStorage(value: Int): TransactionType = if (value == 1) INCOME else EXPENSE
    }
}

/** 还款方式。 */
enum class LoanMethod(val storageValue: Int, val label: String) {
    INTEREST_FIRST(0, "先息后本"),
    EQUAL_PAYMENT(1, "等额本息");

    companion object {
        fun fromStorage(value: Int): LoanMethod = if (value == 1) EQUAL_PAYMENT else INTEREST_FIRST
    }
}

/** 计息方式。 */
enum class InterestMode(val storageValue: String, val label: String) {
    RATE("RATE", "按年利率"),
    FIXED("FIXED", "固定利息");

    companion object {
        fun fromStorage(value: String?): InterestMode =
            if (value == FIXED.storageValue) FIXED else RATE
    }
}

/** 固定利息范围。 */
enum class FixedInterestScope(val storageValue: String, val label: String) {
    TOTAL("TOTAL", "总利息"),
    PER_INSTALLMENT("PER_INSTALLMENT", "每期利息");

    companion object {
        fun fromStorage(value: String?): FixedInterestScope =
            if (value == PER_INSTALLMENT.storageValue) PER_INSTALLMENT else TOTAL
    }
}

/** 还款间隔单位。旧版备份为 MONTHS，只做兼容。 */
enum class ScheduleUnit(val storageValue: String) {
    DAYS("DAYS"),
    MONTHS("MONTHS");

    companion object {
        fun fromStorage(value: String?): ScheduleUnit =
            if (value == MONTHS.storageValue) MONTHS else DAYS
    }
}

/** 借款状态。 */
enum class LoanStatus(val label: String) {
    SETTLED("已结清"),
    OVERDUE("逾期"),
    DUE_TODAY("今天到期"),
    NORMAL("正常")
}

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val type: TransactionType = TransactionType.EXPENSE,
    val amountCents: Long = 0,
    val category: String = "other",
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class LoanPayment(
    val period: Int,
    val amountCents: Long,
    val principalPartCents: Long,
    val interestPartCents: Long,
    val date: LocalDate,
    /** 0 表示自由还款（不绑定某一期）；旧数据为 1-based 期数。 */
    val id: String = UUID.randomUUID().toString(),
    val note: String = "",
)

data class Loan(
    val id: String = UUID.randomUUID().toString(),
    val friend: String = "",
    val note: String = "",
    val principalCents: Long = 0,
    val installmentCount: Int = 12,
    val intervalUnit: ScheduleUnit = ScheduleUnit.DAYS,
    val intervalDays: Int = 30,
    val startDate: LocalDate = LocalDate.now(),
    val method: LoanMethod = LoanMethod.INTEREST_FIRST,
    val interestMode: InterestMode = InterestMode.RATE,
    val annualRate: Double = 0.0,
    val fixedInterestCents: Long = 0,
    val fixedInterestScope: FixedInterestScope = FixedInterestScope.TOTAL,
    val includeInBalance: Boolean = true,
    /** 是否用分期计划做到期提醒；关闭后只按总待收跟踪，不产生逾期/到期提醒。 */
    val reminderEnabled: Boolean = true,
    val payments: List<LoanPayment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val terms: LoanTerms
        get() = LoanTerms(
            principalCents = principalCents,
            installmentCount = installmentCount,
            intervalUnit = intervalUnit,
            intervalDays = intervalDays,
            startDate = startDate,
            method = method,
            interestMode = interestMode,
            annualRate = annualRate,
            fixedInterestCents = fixedInterestCents,
            fixedInterestScope = fixedInterestScope,
        )

    val paidCount: Int get() = LoanCalculator.paidCount(this)
    val paidCents: Long get() = LoanCalculator.paidCents(this)
    val totalReceivableCents: Long get() = LoanCalculator.totalReceivableCents(this)
    val totalInterestCents: Long get() = LoanCalculator.totalInterestCents(this)
    val outstandingCents: Long get() = LoanCalculator.outstandingCents(this)
    val paidPercent: Int get() = LoanCalculator.paidPercent(this)
    val settled: Boolean get() = LoanCalculator.settled(this)
    val nextDueRemainingCents: Long get() = LoanCalculator.nextRemainingCents(this)

    fun schedule(): List<Installment> = LoanCalculator.schedule(terms)

    fun nextInstallment(): Installment? = LoanCalculator.nextInstallment(this)

    fun status(today: LocalDate = LocalDate.now()): LoanStatus = LoanCalculator.status(this, today)

    /** 按下一期计划金额记录一笔完整还款（仍可自由改金额，详情页用 addPayment）。 */
    fun withNextPayment(paidOn: LocalDate = LocalDate.now()): Loan {
        if (settled) return this
        val next = nextInstallment() ?: return this
        val payment = LoanPayment(
            period = next.period,
            amountCents = next.dueCents,
            principalPartCents = next.principalPartCents,
            interestPartCents = next.interestPartCents,
            date = paidOn,
        )
        return copy(payments = payments + payment)
    }

    /** 撤销最后一笔收款。 */
    fun withoutLastPayment(): Loan =
        if (payments.isEmpty()) this else copy(payments = payments.dropLast(1))
}

/** 纯计算入参，方便表单实时预览。 */
data class LoanTerms(
    val principalCents: Long,
    val installmentCount: Int,
    val intervalUnit: ScheduleUnit,
    val intervalDays: Int,
    val startDate: LocalDate,
    val method: LoanMethod,
    val interestMode: InterestMode,
    val annualRate: Double,
    val fixedInterestCents: Long,
    val fixedInterestScope: FixedInterestScope,
)

data class Installment(
    val period: Int,
    val dueDate: LocalDate,
    val dueCents: Long,
    val principalPartCents: Long,
    val interestPartCents: Long,
)

/** 借款实时预览。 */
data class LoanPreview(
    val installments: List<Installment>,
    val firstDueCents: Long,
    val lastDueCents: Long,
    val totalReceivableCents: Long,
    val totalInterestCents: Long,
    val interestPercent: Int,
    val lastDueDate: LocalDate?,
)

data class AppSettings(
    val monthlyBudgetCents: Long = 300_000,
    val openingBalanceCents: Long = 0,
    val theme: String = "system",
    val loanReminders: Boolean = true,
    val onboarded: Boolean = false,
    val budgetAlertPercent: Int = 80,
    val monet: Boolean = false,
    val blur: Boolean = true,
    val floatingNav: Boolean = true,
    val liquidGlass: Boolean = true,
    val navBadges: Boolean = true,
    val predictiveBack: Boolean = true,
)

data class FinanceState(
    val settings: AppSettings = AppSettings(),
    val transactions: List<Transaction> = emptyList(),
    val loans: List<Loan> = emptyList(),
) {
    val sortedTransactions: List<Transaction>
        get() = transactions.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })

    val sortedLoans: List<Loan>
        get() = loans.sortedWith(compareByDescending<Loan> { it.createdAt })
}
