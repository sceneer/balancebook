package com.example.kernelsustyleuikit.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BalanceBookCodecTest {

    private val v1Json = """
        {
          "app": "BalanceBook",
          "version": 1,
          "savedAt": 1794229000000,
          "settings": {
            "monthlyBudget": 420000,
            "openingBalance": 1860000,
            "theme": "dark",
            "loanReminders": false,
            "onboarded": true,
            "budgetAlertPercent": 90
          },
          "transactions": [
            {
              "id": "tx-1",
              "type": 0,
              "amount": 2550,
              "category": "food",
              "note": "午饭",
              "date": "2026-11-10",
              "createdAt": 1794229000000
            }
          ],
          "loans": [
            {
              "id": "loan-legacy",
              "friend": "小林",
              "note": "周转",
              "principal": 100000,
              "annualRate": 12,
              "termMonths": 12,
              "startDate": "2026-01-31",
              "method": 0,
              "includeInBalance": true,
              "createdAt": 1794229000000,
              "payments": [
                {
                  "period": 1,
                  "amount": 1000,
                  "principalPart": 0,
                  "interestPart": 1000,
                  "date": "2026-02-28"
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun importsLegacyV1BackupAndMigratesMonthlyLoans() {
        val result = BalanceBookCodec.decode(v1Json)
        assertTrue(result is DecodeResult.Success)
        val state = (result as DecodeResult.Success).state
        assertEquals(1, result.version)

        assertEquals(420_000L, state.settings.monthlyBudgetCents)
        assertEquals(1_860_000L, state.settings.openingBalanceCents)
        assertEquals("dark", state.settings.theme)
        assertEquals(false, state.settings.loanReminders)
        assertEquals(true, state.settings.onboarded)
        assertEquals(90, state.settings.budgetAlertPercent)

        assertEquals(1, state.transactions.size)
        val tx = state.transactions.single()
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(2_550L, tx.amountCents)
        assertEquals(LocalDate.of(2026, 11, 10), tx.date)

        val loan = state.loans.single()
        assertEquals("loan-legacy", loan.id)
        assertEquals(12, loan.installmentCount)
        assertEquals(ScheduleUnit.MONTHS, loan.intervalUnit)
        assertEquals(30, loan.intervalDays)
        assertEquals(InterestMode.RATE, loan.interestMode)
        assertEquals(0L, loan.fixedInterestCents)
        assertEquals(FixedInterestScope.TOTAL, loan.fixedInterestScope)
        assertEquals(1, loan.payments.size)
        assertEquals(true, loan.reminderEnabled)

        val plan = loan.schedule()
        assertEquals(12, plan.size)
        assertEquals(LocalDate.of(2026, 2, 28), plan[0].dueDate)
        assertEquals(LocalDate.of(2027, 1, 31), plan[11].dueDate)
        assertEquals(1_000L, plan[0].interestPartCents)
        assertEquals(101_000L, plan[11].dueCents)
        // 1 期已收，下一期是第 2 期
        assertEquals(2, loan.nextInstallment()?.period)
    }

    @Test
    fun roundTripsVersion2State() {
        val state = FinanceState(
            settings = AppSettings(
                monthlyBudgetCents = 300_000,
                openingBalanceCents = 12_345,
                theme = "system",
                loanReminders = true,
                onboarded = true,
                budgetAlertPercent = 80,
            ),
            transactions = listOf(
                Transaction(
                    id = "t1",
                    type = TransactionType.INCOME,
                    amountCents = 1_500_000,
                    category = "salary",
                    note = "工资",
                    date = LocalDate.of(2026, 11, 5),
                    createdAt = 100L,
                ),
            ),
            loans = listOf(
                Loan(
                    id = "l1",
                    friend = "小雨",
                    note = "生日礼物",
                    principalCents = 100_000,
                    installmentCount = 6,
                    intervalUnit = ScheduleUnit.DAYS,
                    intervalDays = 14,
                    startDate = LocalDate.of(2026, 11, 9),
                    method = LoanMethod.INTEREST_FIRST,
                    interestMode = InterestMode.FIXED,
                    annualRate = 0.0,
                    fixedInterestCents = 1_000,
                    fixedInterestScope = FixedInterestScope.PER_INSTALLMENT,
                    includeInBalance = true,
                    reminderEnabled = false,
                    payments = listOf(
                        LoanPayment(
                            period = 1,
                            amountCents = 1_000,
                            principalPartCents = 0,
                            interestPartCents = 1_000,
                            date = LocalDate.of(2026, 11, 23),
                            note = "微信",
                        ),
                    ),
                    createdAt = 200L,
                ),
            ),
        )

        val encoded = BalanceBookCodec.encode(state)
        assertTrue(encoded.contains("\"version\": 2"))
        assertTrue(encoded.contains("\"intervalUnit\": \"DAYS\""))

        val decoded = BalanceBookCodec.decode(encoded)
        assertTrue(decoded is DecodeResult.Success)
        assertEquals(state, (decoded as DecodeResult.Success).state)
    }

    @Test
    fun exportClearImportRestoresEverything() {
        val decoded = BalanceBookCodec.decode(v1Json) as DecodeResult.Success
        val encoded = BalanceBookCodec.encode(decoded.state)
        val restored = BalanceBookCodec.decode(encoded) as DecodeResult.Success

        assertEquals(decoded.state.transactions, restored.state.transactions)
        assertEquals(decoded.state.loans.size, restored.state.loans.size)
        assertEquals(decoded.state.settings.monthlyBudgetCents, restored.state.settings.monthlyBudgetCents)
        assertEquals(decoded.state.loans.single().payments, restored.state.loans.single().payments)
        assertEquals(2, restored.version)
    }

    @Test
    fun rejectsCorruptOrForeignJson() {
        assertTrue(BalanceBookCodec.decode("{") is DecodeResult.Failure)
        assertTrue(BalanceBookCodec.decode("") is DecodeResult.Failure)
        assertTrue(BalanceBookCodec.decode("null") is DecodeResult.Failure)
        assertTrue(BalanceBookCodec.decode("[]") is DecodeResult.Failure)
        assertTrue(BalanceBookCodec.decode("{\"foo\":1}") is DecodeResult.Failure)
        assertTrue(BalanceBookCodec.decode("{\"app\":\"OtherApp\"}") is DecodeResult.Failure)
    }

    @Test
    fun toleratesMissingFieldsAndInvalidRows() {
        val json = """
            {
              "transactions": [
                {"amount": 100, "date": "2026-11-01"},
                {"amount": 0, "date": "2026-11-02"},
                {"amount": 300, "date": "not-a-date"}
              ],
              "loans": [
                {"principal": 5000, "startDate": "2026-11-01"},
                {"principal": 0, "startDate": "2026-11-01"}
              ]
            }
        """.trimIndent()
        val result = BalanceBookCodec.decode(json)
        assertTrue(result is DecodeResult.Success)
        val state = (result as DecodeResult.Success).state
        assertEquals(1, state.transactions.size)
        assertEquals(1, state.loans.size)
        val loan = state.loans.single()
        assertEquals(12, loan.installmentCount)
        assertEquals(ScheduleUnit.MONTHS, loan.intervalUnit)
        assertEquals(InterestMode.RATE, loan.interestMode)
    }

    @Test
    fun normalizesIllegalFixedPerInstallmentEqualPayment() {
        val json = """
            {
              "loans": [
                {
                  "principal": 100000,
                  "startDate": "2026-11-01",
                  "installmentCount": 6,
                  "intervalUnit": "DAYS",
                  "intervalDays": 14,
                  "method": 1,
                  "interestMode": "FIXED",
                  "fixedInterestCents": 1000,
                  "fixedInterestScope": "PER_INSTALLMENT"
                }
              ]
            }
        """.trimIndent()
        val state = (BalanceBookCodec.decode(json) as DecodeResult.Success).state
        val loan = state.loans.single()
        assertEquals(LoanMethod.INTEREST_FIRST, loan.method)
        assertNotNull(loan.nextInstallment())
        assertEquals(6, loan.schedule().size)
    }

    @Test
    fun keepsMultipleFreePayments() {
        val json = """
            {
              "loans": [
                {
                  "principal": 100000,
                  "startDate": "2026-11-01",
                  "installmentCount": 6,
                  "intervalUnit": "DAYS",
                  "intervalDays": 14,
                  "reminderEnabled": false,
                  "payments": [
                    {"id":"p1","period":0,"amount":3000,"date":"2026-11-10","note":"微信"},
                    {"id":"p2","period":0,"amount":2500,"date":"2026-11-12","note":""}
                  ]
                }
              ]
            }
        """.trimIndent()
        val loan = (BalanceBookCodec.decode(json) as DecodeResult.Success).state.loans.single()
        assertEquals(2, loan.payments.size)
        assertEquals(5_500L, loan.paidCents)
        assertEquals(false, loan.reminderEnabled)
        assertEquals(listOf("p1", "p2"), loan.payments.map { it.id })
        assertEquals(listOf("微信", ""), loan.payments.map { it.note })

        val encoded = BalanceBookCodec.encode(FinanceState(loans = listOf(loan)))
        val restored = (BalanceBookCodec.decode(encoded) as DecodeResult.Success).state.loans.single()
        assertEquals(2, restored.payments.size)
        assertEquals(false, restored.reminderEnabled)
    }

    @Test
    fun clampsOutOfRangeValues() {
        val json = """
            {
              "settings": {"monthlyBudget": -1, "openingBalance": -5, "budgetAlertPercent": 500},
              "loans": [
                {
                  "principal": 100000,
                  "startDate": "2026-11-01",
                  "installmentCount": 9999,
                  "intervalUnit": "DAYS",
                  "intervalDays": 999,
                  "annualRate": 500
                }
              ]
            }
        """.trimIndent()
        val state = (BalanceBookCodec.decode(json) as DecodeResult.Success).state
        assertEquals(0L, state.settings.monthlyBudgetCents)
        assertEquals(0L, state.settings.openingBalanceCents)
        assertEquals(100, state.settings.budgetAlertPercent)
        val loan = state.loans.single()
        assertEquals(360, loan.installmentCount)
        assertEquals(365, loan.intervalDays)
        assertEquals(100.0, loan.annualRate, 0.0)
    }

    @Test
    fun roundTripsAutoBookkeepingMetadata() {
        val transaction = Transaction(
            id = "auto-wechat-1",
            type = TransactionType.EXPENSE,
            amountCents = 3200,
            category = "food",
            note = "微信支付 · 星巴克",
            date = LocalDate.of(2026, 11, 19),
            createdAt = 1795000000000,
            source = TransactionSource.WECHAT,
            autoKey = "wechat|EXPENSE|3200|5976",
        )
        val state = FinanceState(
            settings = AppSettings(autoBookkeeping = true, autoBookNotify = false),
            transactions = listOf(transaction),
        )
        val restored = (BalanceBookCodec.decode(BalanceBookCodec.encode(state)) as DecodeResult.Success).state
        val tx = restored.transactions.single()
        assertEquals(TransactionSource.WECHAT, tx.source)
        assertEquals("wechat|EXPENSE|3200|5976", tx.autoKey)
        assertTrue(restored.settings.autoBookkeeping)
        assertEquals(false, restored.settings.autoBookNotify)
    }

    @Test
    fun legacyJsonWithoutAutoBookkeepingFieldsUsesDefaults() {
        val state = (BalanceBookCodec.decode(v1Json) as DecodeResult.Success).state
        assertEquals(TransactionSource.MANUAL, state.transactions.single().source)
        assertEquals("", state.transactions.single().autoKey)
        assertEquals(false, state.settings.autoBookkeeping)
        assertEquals(true, state.settings.autoBookNotify)
    }
}


