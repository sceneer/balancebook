package com.dsh.balancebook.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LoanProgressTest {

    private val start = LocalDate.of(2026, 11, 9)

    // 8.2-4 本金 1000，固定总利息 60，10 期，每 7 天，等额本息
    private fun loan(payments: Int = 0): Loan {
        var l = Loan(
            id = "loan-1",
            friend = "小林",
            principalCents = 100_000,
            installmentCount = 10,
            intervalUnit = ScheduleUnit.DAYS,
            intervalDays = 7,
            startDate = start,
            method = LoanMethod.EQUAL_PAYMENT,
            interestMode = InterestMode.FIXED,
            annualRate = 0.0,
            fixedInterestCents = 6_000,
            fixedInterestScope = FixedInterestScope.TOTAL,
            includeInBalance = true,
            createdAt = 1L,
        )
        repeat(payments) { l = l.withNextPayment(start.plusDays(7L * (it + 1))) }
        return l
    }

    // 8.3-7：记录 2 期
    @Test
    fun paidProgressAndUndo() {
        val l = loan(2)

        assertEquals(2, l.paidCount)
        assertEquals(2, l.payments.size)
        assertEquals(21_200L, l.paidCents)
        assertEquals(20, l.paidPercent)
        assertEquals(3, l.nextInstallment()?.period)
        assertEquals(LocalDate.of(2026, 11, 30), l.nextInstallment()?.dueDate)
        assertEquals(84_800L, l.outstandingCents)
        assertFalse(l.settled)

        val undone = l.withoutLastPayment()
        assertEquals(1, undone.paidCount)
        assertEquals(10, undone.paidPercent)
        assertEquals(2, undone.nextInstallment()?.period)
    }

    @Test
    fun settledWhenAllInstallmentsPaid() {
        val l = loan(10)
        assertTrue(l.settled)
        assertNull(l.nextInstallment())
        assertEquals(0L, l.outstandingCents)
        assertEquals(100, l.paidPercent)
    }

    @Test
    fun settleWhenOutstandingIsZero() {
        val zeroInterest = Loan(
            principalCents = 30_000,
            installmentCount = 3,
            intervalUnit = ScheduleUnit.DAYS,
            intervalDays = 30,
            startDate = start,
            method = LoanMethod.EQUAL_PAYMENT,
            interestMode = InterestMode.RATE,
            annualRate = 0.0,
        )
        assertFalse(zeroInterest.settled)
        assertEquals(0, zeroInterest.paidPercent)
    }

    @Test
    fun statusLifecycle() {
        val l = loan(0)
        val due = l.nextInstallment()!!.dueDate
        assertEquals(LoanStatus.NORMAL, l.status(due.minusDays(1)))
        assertEquals(LoanStatus.DUE_TODAY, l.status(due))
        assertEquals(LoanStatus.OVERDUE, l.status(due.plusDays(1)))
        assertEquals(LoanStatus.SETTLED, loan(10).status(due))
    }

    @Test
    fun markingPaidRecordsTheScheduledAmounts() {
        val l = loan(1)
        val p = l.payments.single()
        assertEquals(1, p.period)
        assertEquals(10_600L, p.amountCents)
        assertEquals(10_000L, p.principalPartCents)
        assertEquals(600L, p.interestPartCents)
        assertEquals(LocalDate.of(2026, 11, 16), p.date)
    }

    @Test
    fun cannotPayBeyondLastInstallment() {
        val l = loan(10)
        assertEquals(l, l.withNextPayment())
    }

    @Test
    fun partialPaymentKeepsRemainingAndNextPeriod() {
        val l = loan(0).copy(
            payments = listOf(
                LoanPayment(
                    period = 0,
                    amountCents = 5_000,
                    principalPartCents = 0,
                    interestPartCents = 0,
                    date = LocalDate.of(2026, 11, 10),
                ),
            ),
        )
        assertEquals(0, l.paidCount)
        assertEquals(5_000L, l.paidCents)
        assertEquals(5_600L, l.nextDueRemainingCents)
        assertEquals(101_000L, l.outstandingCents)
        assertEquals(1, l.nextInstallment()?.period)
    }

    @Test
    fun overpaymentSettlesLoan() {
        val l = loan(0).copy(
            payments = listOf(
                LoanPayment(
                    period = 0,
                    amountCents = 200_000,
                    principalPartCents = 0,
                    interestPartCents = 0,
                    date = LocalDate.of(2026, 11, 10),
                ),
            ),
        )
        assertTrue(l.settled)
        assertEquals(0L, l.outstandingCents)
        assertEquals(100, l.paidPercent)
        assertNull(l.nextInstallment())
    }

    @Test
    fun disablingReminderStopsOverdueStatusButKeepsBalance() {
        val overdue = Loan(
            principalCents = 100_000,
            installmentCount = 2,
            intervalUnit = ScheduleUnit.DAYS,
            intervalDays = 7,
            startDate = start.minusDays(30),
            method = LoanMethod.EQUAL_PAYMENT,
            interestMode = InterestMode.RATE,
            annualRate = 0.0,
            reminderEnabled = true,
        )
        assertEquals(LoanStatus.OVERDUE, overdue.status(start))
        val quiet = overdue.copy(reminderEnabled = false)
        assertEquals(LoanStatus.NORMAL, quiet.status(start))
        assertEquals(quiet.outstandingCents, overdue.outstandingCents)
        assertEquals(quiet.paidPercent, overdue.paidPercent)
    }

    @Test
    fun nextDueAmountIncludesPrincipalOnLastInterestFirstPeriod() {
        val l = Loan(
            friend = "阿杰",
            principalCents = 200_000,
            installmentCount = 6,
            intervalUnit = ScheduleUnit.DAYS,
            intervalDays = 30,
            startDate = start,
            method = LoanMethod.INTEREST_FIRST,
            interestMode = InterestMode.RATE,
            annualRate = 6.0,
        )
        var paid = l
        repeat(5) { paid = paid.withNextPayment() }
        assertEquals(200_986L, paid.nextInstallment()?.dueCents)
    }
}


