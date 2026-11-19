package com.example.kernelsustyleuikit.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LoanCalculatorTest {

    private fun rateTerms(
        principal: Long,
        rate: Double,
        count: Int,
        days: Int,
        method: LoanMethod = LoanMethod.EQUAL_PAYMENT,
        start: LocalDate = LocalDate.of(2026, 11, 9),
    ) = LoanTerms(
        principalCents = principal,
        installmentCount = count,
        intervalUnit = ScheduleUnit.DAYS,
        intervalDays = days,
        startDate = start,
        method = method,
        interestMode = InterestMode.RATE,
        annualRate = rate,
        fixedInterestCents = 0,
        fixedInterestScope = FixedInterestScope.TOTAL,
    )

    private fun fixedTerms(
        principal: Long,
        interest: Long,
        count: Int,
        days: Int,
        method: LoanMethod,
        scope: FixedInterestScope,
        start: LocalDate = LocalDate.of(2026, 11, 9),
    ) = LoanTerms(
        principalCents = principal,
        installmentCount = count,
        intervalUnit = ScheduleUnit.DAYS,
        intervalDays = days,
        startDate = start,
        method = method,
        interestMode = InterestMode.FIXED,
        annualRate = 0.0,
        fixedInterestCents = interest,
        fixedInterestScope = scope,
    )

    // 8.1-1 年利率 8%，12 期，每 30 天，等额本息
    @Test
    fun rateEqualPaymentDaily() {
        val terms = rateTerms(500_000, 8.0, 12, 30)
        val plan = LoanCalculator.schedule(terms)

        assertEquals(12, plan.size)
        assertEquals(43469L, plan.first().dueCents)
        assertEquals(43467L, plan.last().dueCents)
        assertEquals(521_626L, plan.sumOf { it.dueCents })
        assertEquals(500_000L, plan.sumOf { it.principalPartCents })
        assertEquals(21_626L, plan.sumOf { it.interestPartCents })
        // 到期日 = 起借日 + 30 × i 天
        assertEquals(LocalDate.of(2026, 12, 9), plan[0].dueDate)
        assertEquals(LocalDate.of(2026, 11, 9).plusDays(30L * 12), plan[11].dueDate)
    }

    // 8.1-2 年利率 6%，6 期，每 30 天，先息后本
    @Test
    fun rateInterestFirstDaily() {
        val terms = rateTerms(200_000, 6.0, 6, 30, LoanMethod.INTEREST_FIRST)
        val plan = LoanCalculator.schedule(terms)

        assertEquals(6, plan.size)
        assertEquals(986L, plan[0].interestPartCents)
        assertEquals(986L, plan[4].dueCents)
        assertEquals(200_986L, plan[5].dueCents)
        assertEquals(200_000L, plan[5].principalPartCents)
        assertEquals(205_916L, plan.sumOf { it.dueCents })
    }

    // 8.1-3 年利率 0%，4 期，等额本息
    @Test
    fun zeroRateEqualPayment() {
        val terms = rateTerms(120_000, 0.0, 4, 30)
        val plan = LoanCalculator.schedule(terms)

        assertEquals(4, plan.size)
        assertTrue(plan.all { it.dueCents == 30_000L && it.interestPartCents == 0L })
        assertEquals(120_000L, plan.sumOf { it.dueCents })
    }

    // 8.2-4 固定总利息 60 元，10 期，每 7 天，等额本息
    @Test
    fun fixedTotalEqualPaymentDaily() {
        val terms = fixedTerms(100_000, 6_000, 10, 7, LoanMethod.EQUAL_PAYMENT, FixedInterestScope.TOTAL)
        val plan = LoanCalculator.schedule(terms)

        assertEquals(10, plan.size)
        assertTrue(plan.all { it.dueCents == 10_600L })
        assertEquals(106_000L, plan.sumOf { it.dueCents })
        assertEquals(LocalDate.of(2026, 11, 16), plan[0].dueDate)
        assertEquals(LocalDate.of(2027, 1, 18), plan[9].dueDate) // +70 天
        assertEquals(LocalDate.of(2026, 11, 9).plusDays(7L * 10), plan[9].dueDate)
    }

    // 8.2-5 固定每期利息 10 元，6 期，每 14 天，先息后本
    @Test
    fun fixedPerInstallmentInterestFirst() {
        val terms = fixedTerms(
            100_000, 1_000, 6, 14,
            LoanMethod.INTEREST_FIRST, FixedInterestScope.PER_INSTALLMENT,
        )
        val plan = LoanCalculator.schedule(terms)

        assertEquals(6, plan.size)
        assertTrue(plan.take(5).all { it.dueCents == 1_000L && it.principalPartCents == 0L })
        assertEquals(101_000L, plan[5].dueCents)
        assertEquals(100_000L, plan[5].principalPartCents)
        assertEquals(106_000L, plan.sumOf { it.dueCents })
        assertEquals(LocalDate.of(2026, 11, 23), plan[0].dueDate) // +14
        assertEquals(LocalDate.of(2026, 11, 9).plusDays(14L * 6), plan[5].dueDate)
    }

    // 8.2-6 固定总利息 60 元，6 期，先息后本
    @Test
    fun fixedTotalInterestFirst() {
        val terms = fixedTerms(100_000, 6_000, 6, 14, LoanMethod.INTEREST_FIRST, FixedInterestScope.TOTAL)
        val plan = LoanCalculator.schedule(terms)

        assertEquals(6, plan.size)
        assertTrue(plan.all { it.interestPartCents == 1_000L })
        assertEquals(101_000L, plan[5].dueCents)
        assertEquals(106_000L, plan.sumOf { it.dueCents })
    }

    @Test
    fun legacyMonthlySchedule() {
        val terms = LoanTerms(
            principalCents = 120_000,
            installmentCount = 12,
            intervalUnit = ScheduleUnit.MONTHS,
            intervalDays = 30,
            startDate = LocalDate.of(2026, 1, 31),
            method = LoanMethod.INTEREST_FIRST,
            interestMode = InterestMode.RATE,
            annualRate = 12.0,
            fixedInterestCents = 0,
            fixedInterestScope = FixedInterestScope.TOTAL,
        )
        val plan = LoanCalculator.schedule(terms)

        assertEquals(12, plan.size)
        assertEquals(1_200L, plan[0].dueCents)
        assertEquals(121_200L, plan[11].dueCents)
        assertEquals(LocalDate.of(2026, 2, 28), plan[0].dueDate)
        assertEquals(134_400L, plan.sumOf { it.dueCents })
    }

    @Test
    fun fixedTotalAbsorbsRoundingInLastInstallment() {
        val terms = fixedTerms(100_000, 1_000, 3, 7, LoanMethod.INTEREST_FIRST, FixedInterestScope.TOTAL)
        val plan = LoanCalculator.schedule(terms)

        // 1000 / 3 -> 333, 333, 334
        assertEquals(333L, plan[0].interestPartCents)
        assertEquals(333L, plan[1].interestPartCents)
        assertEquals(334L, plan[2].interestPartCents)
        assertEquals(100_334L, plan[2].dueCents)
        assertEquals(101_000L, plan.sumOf { it.dueCents })
    }

    @Test
    fun equalPaymentFixedTotalSplitsPrincipalAndInterest() {
        val terms = fixedTerms(100_000, 1_000, 3, 7, LoanMethod.EQUAL_PAYMENT, FixedInterestScope.TOTAL)
        val plan = LoanCalculator.schedule(terms)

        assertEquals(100_000L, plan.sumOf { it.principalPartCents })
        assertEquals(1_000L, plan.sumOf { it.interestPartCents })
        assertEquals(101_000L, plan.sumOf { it.dueCents })
        assertEquals(101_000L, plan.last().dueCents + plan[0].dueCents + plan[1].dueCents)
    }

    @Test
    fun invalidCombinationFixedPerInstallmentWithEqualPayment() {
        val terms = fixedTerms(
            100_000, 1_000, 6, 14,
            LoanMethod.EQUAL_PAYMENT, FixedInterestScope.PER_INSTALLMENT,
        )
        assertEquals("固定每期利息只支持先息后本", LoanCalculator.validate(terms))
        assertTrue(LoanCalculator.schedule(terms).isEmpty())
    }

    @Test
    fun validationRejectsOutOfRangeValues() {
        assertTrue(LoanCalculator.validate(rateTerms(0, 8.0, 12, 30)) != null)
        assertTrue(LoanCalculator.validate(rateTerms(100_000, 101.0, 12, 30)) != null)
        assertTrue(LoanCalculator.validate(rateTerms(100_000, 8.0, 0, 30)) != null)
        assertTrue(LoanCalculator.validate(rateTerms(100_000, 8.0, 361, 30)) != null)
        assertTrue(LoanCalculator.validate(rateTerms(100_000, 8.0, 12, 0)) != null)
        assertTrue(LoanCalculator.validate(rateTerms(100_000, 8.0, 12, 366)) != null)
        assertNull(LoanCalculator.validate(rateTerms(100_000, 0.0, 1, 1)))
    }

    @Test
    fun periodRateUsesActualDaysForDailyLoans() {
        val terms = rateTerms(500_000, 8.0, 12, 30)
        assertEquals(8.0 / 100.0 / 365.0 * 30.0, LoanCalculator.periodRate(terms), 1e-12)
        assertEquals(8.0 / 100.0 / 12.0, LoanCalculator.periodRate(terms.copy(intervalUnit = ScheduleUnit.MONTHS)), 1e-12)
    }

    @Test
    fun noInterestLoanOnlyReturnsPrincipal() {
        val terms = fixedTerms(50_000, 0, 3, 7, LoanMethod.INTEREST_FIRST, FixedInterestScope.TOTAL)
        val plan = LoanCalculator.schedule(terms)
        assertEquals(0L, plan.sumOf { it.interestPartCents })
        assertEquals(50_000L, plan.sumOf { it.dueCents })
    }

    @Test
    fun previewMatchesSchedule() {
        val preview = LoanCalculator.preview(fixedTerms(100_000, 6_000, 10, 7, LoanMethod.EQUAL_PAYMENT, FixedInterestScope.TOTAL))
        assertEquals(10_600L, preview.firstDueCents)
        assertEquals(10_600L, preview.lastDueCents)
        assertEquals(106_000L, preview.totalReceivableCents)
        assertEquals(6_000L, preview.totalInterestCents)
        assertEquals(LocalDate.of(2026, 11, 9).plusDays(70), preview.lastDueDate)
    }
}
