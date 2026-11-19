package com.dsh.balancebook.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MoneyTest {

    @Test
    fun parsesYuanInput() {
        assertEquals(1234L, Money.parseYuanToCents("12.34"))
        assertEquals(10L, Money.parseYuanToCents("0.1"))
        assertEquals(100_000L, Money.parseYuanToCents("1,000"))
        assertEquals(100_000L, Money.parseYuanToCents("¥1000.00"))
        assertEquals(1L, Money.parseYuanToCents("0.01"))
        assertEquals(0L, Money.parseYuanToCents("0"))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(Money.parseYuanToCents(null))
        assertNull(Money.parseYuanToCents(""))
        assertNull(Money.parseYuanToCents("1.234"))
        assertNull(Money.parseYuanToCents("-1"))
        assertNull(Money.parseYuanToCents("abc"))
        assertNull(Money.parseYuanToCents("NaN"))
        assertNull(Money.parseYuanToCents("Infinity"))
        assertNull(Money.parseYuanToCents("1e3"))
        assertNull(Money.parseYuanToCents("12."))
    }

    @Test
    fun formatsCents() {
        assertEquals("¥1,234.56", Money.formatCentsExact(123_456))
        assertEquals("¥1,234", Money.formatCents(123_400))
        assertEquals("¥1,234.00", Money.formatCents(123_400, showZeroDecimals = true))
        assertEquals("-¥5.05", Money.formatCentsExact(-505))
        assertEquals("1234.56", Money.centsToInput(123_456))
        assertEquals("0.05", Money.centsToInput(5))
        assertEquals("-2.00", Money.centsToInput(-200))
    }

    @Test
    fun formatsRate() {
        assertEquals("8%", Money.formatRate(8.0))
        assertEquals("8.5%", Money.formatRate(8.5))
        assertEquals("0%", Money.formatRate(0.0))
    }

    @Test
    fun parsesDates() {
        assertEquals(LocalDate.of(2026, 11, 10), Money.parseDate("2026-11-10"))
        assertNull(Money.parseDate("2026-13-01"))
        assertNull(Money.parseDate(""))
        assertNull(Money.parseDate(null))
    }
}

class FinanceCalculatorTest {

    private val today = LocalDate.of(2026, 11, 10)

    private fun state(
        budget: Long = 300_000,
        opening: Long = 0,
        transactions: List<Transaction> = emptyList(),
        loans: List<Loan> = emptyList(),
    ) = FinanceState(
        settings = AppSettings(monthlyBudgetCents = budget, openingBalanceCents = opening),
        transactions = transactions,
        loans = loans,
    )

    private fun expense(amount: Long, date: LocalDate) = Transaction(
        type = TransactionType.EXPENSE,
        amountCents = amount,
        category = "food",
        date = date,
    )

    // 8.4-8 预算 3000，支出 2550 -> 剩余 450，85%
    @Test
    fun budgetMath() {
        val s = state(transactions = listOf(expense(255_000, today)))
        assertEquals(255_000L, FinanceCalculator.monthExpenseCents(s, 2026, 11))
        assertEquals(45_000L, FinanceCalculator.budgetRemainingCents(s, 2026, 11))
        assertEquals(85, FinanceCalculator.budgetPercent(s, 2026, 11))
    }

    @Test
    fun zeroBudgetDoesNotDivideByZero() {
        val s = state(budget = 0, transactions = listOf(expense(100, today)))
        assertEquals(0, FinanceCalculator.budgetPercent(s, 2026, 11))
        assertEquals(-100L, FinanceCalculator.budgetRemainingCents(s, 2026, 11))
    }

    @Test
    fun overspendKeepsActualPercent() {
        val s = state(budget = 100_000, transactions = listOf(expense(128_000, today)))
        assertEquals(128, FinanceCalculator.budgetPercent(s, 2026, 11))
        assertEquals(-28_000L, FinanceCalculator.budgetRemainingCents(s, 2026, 11))
    }

    // 8.4-9 跨月账单只进入对应月份
    @Test
    fun monthlyIsolation() {
        val s = state(
            transactions = listOf(
                expense(1_000, LocalDate.of(2026, 11, 1)),
                expense(2_000, LocalDate.of(2026, 10, 31)),
                Transaction(type = TransactionType.INCOME, amountCents = 5_000, date = LocalDate.of(2026, 11, 2)),
            ),
        )
        assertEquals(1_000L, FinanceCalculator.monthExpenseCents(s, 2026, 11))
        assertEquals(2_000L, FinanceCalculator.monthExpenseCents(s, 2026, 10))
        assertEquals(5_000L, FinanceCalculator.monthIncomeCents(s, 2026, 11))
    }

    @Test
    fun balanceSubtractsLoanPrincipalOnlyFromStartDate() {
        val future = Loan(
            principalCents = 100_000,
            startDate = today.plusDays(10),
            installmentCount = 1,
            intervalDays = 30,
            method = LoanMethod.INTEREST_FIRST,
            interestMode = InterestMode.RATE,
            annualRate = 0.0,
            includeInBalance = true,
        )
        val active = Loan(
            principalCents = 50_000,
            startDate = today.minusDays(10),
            installmentCount = 1,
            intervalDays = 30,
            method = LoanMethod.INTEREST_FIRST,
            interestMode = InterestMode.RATE,
            annualRate = 0.0,
            includeInBalance = true,
        )
        val excluded = Loan(
            principalCents = 999_999,
            startDate = today.minusDays(10),
            includeInBalance = false,
        )
        val s = state(
            opening = 1_000_000,
            transactions = listOf(Transaction(type = TransactionType.INCOME, amountCents = 100_000, date = today)),
            loans = listOf(future, active, excluded),
        )
        // 1,000,000 + 100,000 - 50,000；未来借款不扣，excluded 不影响
        assertEquals(1_050_000L, FinanceCalculator.balanceCents(s, today))
    }

    @Test
    fun balanceAddsReceivedPayments() {
        var loan = Loan(
            principalCents = 100_000,
            startDate = today.minusDays(10),
            installmentCount = 2,
            intervalDays = 7,
            method = LoanMethod.EQUAL_PAYMENT,
            interestMode = InterestMode.FIXED,
            fixedInterestCents = 0,
            fixedInterestScope = FixedInterestScope.TOTAL,
            includeInBalance = true,
        )
        loan = loan.withNextPayment(today)
        val s = state(opening = 200_000, loans = listOf(loan))
        assertEquals(200_000L - 100_000L + 50_000L, FinanceCalculator.balanceCents(s, today))
    }

    @Test
    fun loanSummaryCountsActiveAndOverdue() {
        val overdue = Loan(
            principalCents = 100_000,
            startDate = today.minusDays(30),
            installmentCount = 2,
            intervalDays = 7,
            method = LoanMethod.EQUAL_PAYMENT,
            interestMode = InterestMode.RATE,
            annualRate = 0.0,
        )
        val future = Loan(
            principalCents = 100_000,
            startDate = today.plusDays(5),
            installmentCount = 2,
            intervalDays = 30,
            method = LoanMethod.EQUAL_PAYMENT,
            interestMode = InterestMode.RATE,
            annualRate = 0.0,
        )
        val state = state(loans = listOf(overdue, future))
        val summary = FinanceCalculator.loanSummary(state, today)
        assertEquals(2, summary.activeCount)
        assertEquals(1, summary.overdueCount)
        assertEquals(200_000L, summary.totalOutstandingCents)
        // 每期应还 50,000：逾期一笔 + 未来一笔
        assertEquals(100_000L, summary.nextDueCents)
    }

    @Test
    fun recentMonthlyExpensesAreChronological() {
        val s = state(
            transactions = listOf(
                expense(1_000, LocalDate.of(2026, 11, 1)),
                expense(2_000, LocalDate.of(2026, 9, 15)),
            ),
        )
        val series = FinanceCalculator.recentMonthlyExpenses(s, 3, today)
        assertEquals(3, series.size)
        assertEquals(LocalDate.of(2026, 9, 1), series[0].first)
        assertEquals(2_000L, series[0].second)
        assertEquals(0L, series[1].second)
        assertEquals(1_000L, series[2].second)
    }
}
