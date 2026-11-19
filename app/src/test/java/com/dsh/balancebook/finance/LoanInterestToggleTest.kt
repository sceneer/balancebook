package com.dsh.balancebook.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 「计算利息」主开关：关闭时按无息借款计算，开启时保留原计息逻辑。 */
class LoanInterestToggleTest {

    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun disabledInterestProducesZeroInterestAndPrincipalOnly() {
        val loan = Loan(
            principalCents = 120_000,
            installmentCount = 6,
            intervalUnit = ScheduleUnit.DAYS,
            intervalDays = 30,
            startDate = start,
            method = LoanMethod.INTEREST_FIRST,
            interestMode = InterestMode.RATE,
            annualRate = 12.0,
            interestEnabled = false,
        )
        assertEquals(LoanMethod.EQUAL_PAYMENT, loan.terms.method)
        assertEquals(0.0, loan.terms.annualRate, 0.0)
        assertEquals(0L, loan.totalInterestCents)
        assertEquals(120_000L, loan.totalReceivableCents)
        val schedule = loan.schedule()
        assertEquals(120_000L, schedule.sumOf { it.dueCents })
        assertEquals(0L, schedule.sumOf { it.interestPartCents })
        assertEquals(6, schedule.size)
    }

    @Test
    fun disabledInterestIgnoresFixedInterest() {
        val loan = Loan(
            principalCents = 100_000,
            startDate = start,
            interestMode = InterestMode.FIXED,
            fixedInterestCents = 5_000,
            interestEnabled = false,
        )
        assertEquals(0L, loan.totalInterestCents)
        assertEquals(100_000L, loan.totalReceivableCents)
    }

    @Test
    fun enabledInterestStillComputes() {
        val loan = Loan(
            principalCents = 100_000,
            installmentCount = 12,
            intervalDays = 30,
            startDate = start,
            method = LoanMethod.EQUAL_PAYMENT,
            interestMode = InterestMode.RATE,
            annualRate = 8.0,
            interestEnabled = true,
        )
        assertEquals(true, loan.interestEnabled)
        assertTrue(loan.totalInterestCents > 0)
        assertTrue(loan.totalReceivableCents > loan.principalCents)
    }

    @Test
    fun codecRoundTripsInterestFlagAndDefaultsForLegacyJson() {
        val loan = Loan(principalCents = 100_000, startDate = start, interestEnabled = false)
        val state = FinanceState(loans = listOf(loan))
        val restored = (BalanceBookCodec.decode(BalanceBookCodec.encode(state)) as DecodeResult.Success)
            .state.loans.single()
        assertEquals(false, restored.interestEnabled)

        val legacy = BalanceBookCodec.decode(
            """{"loans":[{"principal":100000,"startDate":"2026-01-01"}]}""",
        ) as DecodeResult.Success
        assertEquals(true, legacy.state.loans.single().interestEnabled)
    }
}
