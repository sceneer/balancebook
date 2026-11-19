package com.example.kernelsustyleuikit.data

import android.content.Context
import com.example.kernelsustyleuikit.finance.AppSettings
import com.example.kernelsustyleuikit.finance.BalanceBookCodec
import com.example.kernelsustyleuikit.finance.DecodeResult
import com.example.kernelsustyleuikit.finance.FinanceState
import com.example.kernelsustyleuikit.finance.InterestMode
import com.example.kernelsustyleuikit.finance.Loan
import com.example.kernelsustyleuikit.finance.LoanMethod
import com.example.kernelsustyleuikit.finance.LoanPayment
import com.example.kernelsustyleuikit.finance.ScheduleUnit
import com.example.kernelsustyleuikit.finance.Transaction
import com.example.kernelsustyleuikit.finance.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * 单例数据仓：唯一数据源，StateFlow<FinanceState>。
 * 写操作先改内存并 emit，再异步原子持久化到 filesDir/balance_book.json。
 */
object BalanceBookRepository {

    const val FILE_NAME = "balance_book.json"

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val _state = MutableStateFlow(FinanceState())
    val state: StateFlow<FinanceState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            _state.value = loadFromDisk()
            initialized = true
        }
        com.example.kernelsustyleuikit.reminder.LoanReminderScheduler.syncFromSettings(appContext, _state.value.settings)
    }

    /** 供进程重启 / 开机后确保已加载。 */
    fun ensureInitialized(context: Context) {
        if (!initialized) init(context)
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun postMessage(text: String) {
        _message.value = text
    }

    fun currentState(): FinanceState = _state.value

    fun loanById(id: String): Loan? = _state.value.loans.firstOrNull { it.id == id }

    fun transactionById(id: String): Transaction? = _state.value.transactions.firstOrNull { it.id == id }

    // ------------------------------------------------------------------
    // 账单
    // ------------------------------------------------------------------

    fun upsertTransaction(transaction: Transaction) {
        val current = _state.value
        val exists = current.transactions.any { it.id == transaction.id }
        val transactions = if (exists) {
            current.transactions.map { if (it.id == transaction.id) transaction else it }
        } else {
            current.transactions + transaction
        }
        commit(current.copy(transactions = transactions))
    }

    fun deleteTransaction(id: String) {
        val current = _state.value
        commit(current.copy(transactions = current.transactions.filterNot { it.id == id }))
    }

    // ------------------------------------------------------------------
    // 借款
    // ------------------------------------------------------------------

    fun upsertLoan(loan: Loan) {
        val current = _state.value
        val exists = current.loans.any { it.id == loan.id }
        val loans = if (exists) {
            current.loans.map { if (it.id == loan.id) loan else it }
        } else {
            current.loans + loan
        }
        commit(current.copy(loans = loans))
    }

    fun deleteLoan(id: String) {
        val current = _state.value
        commit(current.copy(loans = current.loans.filterNot { it.id == id }))
    }

    fun markNextPaid(loanId: String, paidOn: LocalDate = LocalDate.now()): Boolean {
        val current = _state.value
        val loan = current.loans.firstOrNull { it.id == loanId } ?: return false
        val updated = loan.withNextPayment(paidOn)
        if (updated == loan) return false
        commit(current.copy(loans = current.loans.map { if (it.id == loanId) updated else it }))
        return true
    }

    fun undoLastPayment(loanId: String): Boolean {
        val current = _state.value
        val loan = current.loans.firstOrNull { it.id == loanId } ?: return false
        if (loan.payments.isEmpty()) return false
        val updated = loan.withoutLastPayment()
        commit(current.copy(loans = current.loans.map { if (it.id == loanId) updated else it }))
        return true
    }

    /** 自由还款：任意金额、任意日期，不要求与分期计划金额一致。 */
    fun addPayment(
        loanId: String,
        amountCents: Long,
        paidOn: LocalDate = LocalDate.now(),
        note: String = "",
    ): Boolean {
        if (amountCents <= 0) return false
        val current = _state.value
        val loan = current.loans.firstOrNull { it.id == loanId } ?: return false
        val payment = LoanPayment(
            period = 0,
            amountCents = amountCents,
            principalPartCents = 0,
            interestPartCents = 0,
            date = paidOn,
            note = note.trim(),
        )
        val updated = loan.copy(payments = loan.payments + payment)
        commit(current.copy(loans = current.loans.map { if (it.id == loanId) updated else it }))
        return true
    }

    fun deletePayment(loanId: String, paymentId: String): Boolean {
        val current = _state.value
        val loan = current.loans.firstOrNull { it.id == loanId } ?: return false
        if (loan.payments.none { it.id == paymentId }) return false
        val updated = loan.copy(payments = loan.payments.filterNot { it.id == paymentId })
        commit(current.copy(loans = current.loans.map { if (it.id == loanId) updated else it }))
        return true
    }

    fun clearPayments(loanId: String): Boolean {
        val current = _state.value
        val loan = current.loans.firstOrNull { it.id == loanId } ?: return false
        if (loan.payments.isEmpty()) return false
        val updated = loan.copy(payments = emptyList())
        commit(current.copy(loans = current.loans.map { if (it.id == loanId) updated else it }))
        return true
    }

    fun replaceLoanWithoutPayments(loan: Loan): Boolean {        val current = _state.value
        val existing = current.loans.firstOrNull { it.id == loan.id } ?: return false
        val reset = loan.copy(payments = emptyList(), createdAt = existing.createdAt)
        commit(current.copy(loans = current.loans.map { if (it.id == loan.id) reset else it }))
        return true
    }

    // ------------------------------------------------------------------
    // 设置
    // ------------------------------------------------------------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = _state.value
        commit(current.copy(settings = transform(current.settings)))
    }

    fun completeOnboarding(openingBalanceCents: Long, monthlyBudgetCents: Long) {
        updateSettings {
            it.copy(
                openingBalanceCents = openingBalanceCents.coerceAtLeast(0),
                monthlyBudgetCents = monthlyBudgetCents.coerceAtLeast(0),
                onboarded = true,
            )
        }
    }

    fun clearRecords() {
        val current = _state.value
        commit(current.copy(transactions = emptyList(), loans = emptyList()))
    }

    // ------------------------------------------------------------------
    // 导入 / 导出
    // ------------------------------------------------------------------

    fun exportJson(): String = BalanceBookCodec.encode(_state.value)

    /** 校验并导入；成功才替换现有数据。 */
    fun importJson(raw: String): DecodeResult {
        val result = BalanceBookCodec.decode(raw)
        if (result is DecodeResult.Success) {
            commit(result.state)
        }
        return result
    }

    // ------------------------------------------------------------------
    // 示例数据
    // ------------------------------------------------------------------

    fun seedDemoData() {
        val today = LocalDate.now()
        val tx = ArrayList<Transaction>()

        fun add(daysAgo: Long, type: TransactionType, amount: Long, category: String, note: String, hour: Int) {
            val date = today.minusDays(daysAgo)
            tx += Transaction(
                type = type,
                amountCents = amount,
                category = category,
                note = note,
                date = date,
                createdAt = date.atTime(hour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
        }

        add(0, TransactionType.EXPENSE, 2_850, "food", "早餐 · 豆浆油条", 8)
        add(0, TransactionType.EXPENSE, 4_680, "food", "和同事吃午饭", 12)
        add(1, TransactionType.EXPENSE, 12_000, "shopping", "超市采购", 19)
        add(1, TransactionType.EXPENSE, 600, "transport", "地铁", 8)
        add(2, TransactionType.INCOME, 1_500_000, "salary", "月度工资", 9)
        add(2, TransactionType.EXPENSE, 29_800, "fun", "电影 & 爆米花", 20)
        add(3, TransactionType.EXPENSE, 3_500, "transport", "打车", 22)
        add(4, TransactionType.EXPENSE, 8_900, "health", "感冒药", 11)
        add(5, TransactionType.EXPENSE, 68_000, "education", "技术书", 15)
        add(6, TransactionType.EXPENSE, 4_200, "food", "咖啡", 10)
        add(8, TransactionType.EXPENSE, 158_000, "shopping", "换季外套", 16)
        add(10, TransactionType.EXPENSE, 120_000, "home", "水电燃气", 9)
        for (m in 1..5) {
            val month = today.minusMonths(m.toLong())
            tx += Transaction(
                type = TransactionType.EXPENSE,
                amountCents = 38_000L + m * 6_200L,
                category = "food",
                note = "餐饮月结",
                date = month.withDayOfMonth(minOf(12, month.lengthOfMonth())),
                createdAt = month.withDayOfMonth(12).atTime(10, 0)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
            tx += Transaction(
                type = TransactionType.EXPENSE,
                amountCents = 76_000L + m * 4_300L,
                category = "shopping",
                note = "生活采购",
                date = month.withDayOfMonth(minOf(20, month.lengthOfMonth())),
                createdAt = month.withDayOfMonth(20).atTime(18, 0)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
            tx += Transaction(
                type = TransactionType.INCOME,
                amountCents = 1_500_000,
                category = "salary",
                note = "月度工资",
                date = month.withDayOfMonth(5),
                createdAt = month.withDayOfMonth(5).atTime(9, 0)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
        }

        var loan1 = Loan(
            friend = "小林",
            note = "换电脑周转",
            principalCents = 500_000,
            installmentCount = 12,
            intervalUnit = ScheduleUnit.DAYS,
            intervalDays = 30,
            startDate = today.minusDays(95),
            method = LoanMethod.EQUAL_PAYMENT,
            interestMode = InterestMode.RATE,
            annualRate = 8.0,
            includeInBalance = true,
        )
        repeat(3) { loan1 = loan1.withNextPayment() }

        var loan2 = Loan(
            friend = "阿杰",
            note = "临时周转",
            principalCents = 200_000,
            installmentCount = 6,
            intervalUnit = ScheduleUnit.DAYS,
            intervalDays = 14,
            startDate = today.minusDays(35),
            method = LoanMethod.INTEREST_FIRST,
            interestMode = InterestMode.FIXED,
            fixedInterestCents = 1_000,
            fixedInterestScope = com.example.kernelsustyleuikit.finance.FixedInterestScope.PER_INSTALLMENT,
            includeInBalance = true,
        )
        repeat(2) { loan2 = loan2.withNextPayment() }

        val loan3 = Loan(
            friend = "小雨",
            note = "生日礼物垫付",
            principalCents = 120_000,
            installmentCount = 4,
            intervalUnit = ScheduleUnit.DAYS,
            intervalDays = 30,
            startDate = today.minusDays(20),
            method = LoanMethod.EQUAL_PAYMENT,
            interestMode = InterestMode.RATE,
            annualRate = 0.0,
            includeInBalance = true,
        )

        // 固定总利息 + 等额本息，已逾期
        val loan4 = Loan(
            friend = "老陈",
            note = "货款垫付",
            principalCents = 80_000,
            installmentCount = 3,
            intervalUnit = ScheduleUnit.DAYS,
            intervalDays = 7,
            startDate = today.minusDays(20),
            method = LoanMethod.EQUAL_PAYMENT,
            interestMode = InterestMode.FIXED,
            fixedInterestCents = 3_000,
            fixedInterestScope = com.example.kernelsustyleuikit.finance.FixedInterestScope.TOTAL,
            includeInBalance = true,
        )

        commit(
            FinanceState(
                settings = AppSettings(
                    monthlyBudgetCents = 420_000,
                    openingBalanceCents = 1_860_000,
                    onboarded = true,
                    loanReminders = _state.value.settings.loanReminders,
                ),
                transactions = tx,
                loans = listOf(loan1, loan2, loan3, loan4),
            ),
        )
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    private fun commit(next: FinanceState) {
        _state.value = next
        val snapshot = next
        scope.launch {
            writeMutex.withLock {
                try {
                    writeAtomic(BalanceBookCodec.encode(snapshot))
                } catch (e: Exception) {
                    _message.value = "保存失败：${e.message ?: "无法写入本地文件"}"
                }
            }
        }
        if (initialized) {
            com.example.kernelsustyleuikit.reminder.LoanReminderScheduler
                .syncFromSettings(appContext, next.settings)
        }
    }

    private fun storageFile(): File = File(appContext.filesDir, FILE_NAME)

    private fun writeAtomic(text: String) {
        val file = storageFile()
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (file.exists()) {
            runCatching { file.copyTo(File(file.parentFile, "$FILE_NAME.bak"), overwrite = true) }
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    private fun loadFromDisk(): FinanceState {
        val file = storageFile()
        if (!file.exists()) return FinanceState()
        val raw = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return FinanceState()
        }
        return when (val result = BalanceBookCodec.decode(raw)) {
            is DecodeResult.Success -> result.state
            is DecodeResult.Failure -> {
                val corrupt = File(file.parentFile, "$FILE_NAME.corrupt-${System.currentTimeMillis()}")
                runCatching { file.renameTo(corrupt) }
                _message.value = "数据文件损坏，已备份为 ${corrupt.name}，使用默认数据"
                FinanceState()
            }
        }
    }
}

