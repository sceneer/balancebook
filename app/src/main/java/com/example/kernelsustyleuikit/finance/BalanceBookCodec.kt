package com.example.kernelsustyleuikit.finance

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.util.UUID

sealed interface DecodeResult {
    data class Success(val state: FinanceState, val version: Int) : DecodeResult
    data class Failure(val message: String) : DecodeResult
}

/**
 * 本地 JSON 编解码。导出 version 2；导入同时接受 version 1 / 2。
 * 所有字段读取都带默认值，缺字段不会崩溃。
 */
object BalanceBookCodec {

    const val APP_NAME = "BalanceBook"
    const val CURRENT_VERSION = 2

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val compactJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ------------------------------------------------------------------
    // 编码
    // ------------------------------------------------------------------

    fun encode(state: FinanceState, savedAt: Long = System.currentTimeMillis()): String {
        val root = buildJsonObject {
            put("app", APP_NAME)
            put("version", CURRENT_VERSION)
            put("savedAt", savedAt)
            put("settings", encodeSettings(state.settings))
            put("transactions", buildJsonArray {
                state.sortedTransactions.forEach { add(encodeTransaction(it)) }
            })
            put("loans", buildJsonArray {
                state.sortedLoans.forEach { add(encodeLoan(it)) }
            })
        }
        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }

    fun encodeCompact(state: FinanceState, savedAt: Long = System.currentTimeMillis()): String {
        val root = buildJsonObject {
            put("app", APP_NAME)
            put("version", CURRENT_VERSION)
            put("savedAt", savedAt)
            put("settings", encodeSettings(state.settings))
            put("transactions", buildJsonArray {
                state.sortedTransactions.forEach { add(encodeTransaction(it)) }
            })
            put("loans", buildJsonArray {
                state.sortedLoans.forEach { add(encodeLoan(it)) }
            })
        }
        return compactJson.encodeToString(JsonObject.serializer(), root)
    }

    private fun encodeSettings(s: AppSettings): JsonObject = buildJsonObject {
        put("monthlyBudget", s.monthlyBudgetCents)
        put("openingBalance", s.openingBalanceCents)
        put("theme", s.theme)
        put("loanReminders", s.loanReminders)
        put("onboarded", s.onboarded)
        put("budgetAlertPercent", s.budgetAlertPercent)
        put("monet", s.monet)
        put("blur", s.blur)
        put("floatingNav", s.floatingNav)
        put("liquidGlass", s.liquidGlass)
        put("navBadges", s.navBadges)
        put("predictiveBack", s.predictiveBack)
        put("autoBookkeeping", s.autoBookkeeping)
        put("autoBookNotify", s.autoBookNotify)
    }

    private fun encodeTransaction(t: Transaction): JsonObject = buildJsonObject {
        put("id", t.id)
        put("type", t.type.storageValue)
        put("amount", t.amountCents)
        put("category", t.category)
        put("note", t.note)
        put("date", t.date.toString())
        put("createdAt", t.createdAt)
        put("source", t.source.storageValue)
        put("autoKey", t.autoKey)
    }

    private fun encodeLoan(l: Loan): JsonObject = buildJsonObject {
        put("id", l.id)
        put("friend", l.friend)
        put("note", l.note)
        put("principal", l.principalCents)
        put("installmentCount", l.installmentCount)
        put("intervalUnit", l.intervalUnit.storageValue)
        put("intervalDays", l.intervalDays)
        put("startDate", l.startDate.toString())
        put("method", l.method.storageValue)
        put("interestMode", l.interestMode.storageValue)
        put("annualRate", l.annualRate)
        put("fixedInterestCents", l.fixedInterestCents)
        put("fixedInterestScope", l.fixedInterestScope.storageValue)
        put("interestEnabled", l.interestEnabled)
        put("includeInBalance", l.includeInBalance)
        put("reminderEnabled", l.reminderEnabled)
        put("createdAt", l.createdAt)
        put("payments", buildJsonArray {
            l.payments.sortedWith(compareBy({ it.date }, { it.period })).forEach { p ->
                add(buildJsonObject {
                    put("id", p.id)
                    put("period", p.period)
                    put("amount", p.amountCents)
                    put("principalPart", p.principalPartCents)
                    put("interestPart", p.interestPartCents)
                    put("note", p.note)
                    put("date", p.date.toString())
                })
            }
        })
    }

    // ------------------------------------------------------------------
    // 解码
    // ------------------------------------------------------------------

    fun decode(raw: String): DecodeResult {
        if (raw.isBlank()) return DecodeResult.Failure("文件为空")
        val text = raw.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) return DecodeResult.Failure("文件为空")

        val root = try {
            compactJson.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            return DecodeResult.Failure("无法解析 JSON：${e.message ?: "格式错误"}")
        } ?: return DecodeResult.Failure("JSON 顶层不是对象")

        if (!root.containsKey("settings") && !root.containsKey("transactions") && !root.containsKey("loans")) {
            return DecodeResult.Failure("不是余额簿备份文件")
        }

        val version = root.long("version", 1L).toInt().coerceAtLeast(1)

        val settings = root["settings"]?.asObject()?.let(::decodeSettings) ?: AppSettings()

        val transactions = ArrayList<Transaction>()
        root["transactions"]?.asArray()?.forEach { element ->
            val obj = element.asObject() ?: return@forEach
            decodeTransaction(obj)?.let { transactions += it }
        }

        val loans = ArrayList<Loan>()
        root["loans"]?.asArray()?.forEach { element ->
            val obj = element.asObject() ?: return@forEach
            decodeLoan(obj)?.let { loans += it }
        }

        return DecodeResult.Success(
            state = FinanceState(settings = settings, transactions = transactions, loans = loans),
            version = version,
        )
    }

    private fun decodeSettings(o: JsonObject): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            monthlyBudgetCents = o.long("monthlyBudget", defaults.monthlyBudgetCents).coerceAtLeast(0),
            openingBalanceCents = o.long("openingBalance", defaults.openingBalanceCents).coerceAtLeast(0),
            theme = o.string("theme", defaults.theme).takeIf { it == "light" || it == "dark" || it == "system" }
                ?: defaults.theme,
            loanReminders = o.bool("loanReminders", defaults.loanReminders),
            onboarded = o.bool("onboarded", defaults.onboarded),
            budgetAlertPercent = o.long("budgetAlertPercent", defaults.budgetAlertPercent.toLong())
                .toInt().coerceIn(10, 100),
            monet = o.bool("monet", defaults.monet),
            blur = o.bool("blur", defaults.blur),
            floatingNav = o.bool("floatingNav", defaults.floatingNav),
            liquidGlass = o.bool("liquidGlass", defaults.liquidGlass),
            navBadges = o.bool("navBadges", defaults.navBadges),
            predictiveBack = o.bool("predictiveBack", defaults.predictiveBack),
            autoBookkeeping = o.bool("autoBookkeeping", defaults.autoBookkeeping),
            autoBookNotify = o.bool("autoBookNotify", defaults.autoBookNotify),
        )
    }

    private fun decodeTransaction(o: JsonObject): Transaction? {
        val amount = o.long("amount", 0L)
        if (amount <= 0) return null
        val date = Money.parseDate(o.string("date", "")) ?: return null
        val type = TransactionType.fromStorage(o.long("type", 0L).toInt())
        val category = o.string("category", if (type == TransactionType.INCOME) "other_income" else "other") ?: "other"
        return Transaction(
            id = o.string("id", UUID.randomUUID().toString())!!,
            type = type,
            amountCents = amount,
            category = category,
            note = o.string("note", "") ?: "",
            date = date,
            createdAt = o.long("createdAt", System.currentTimeMillis()),
            source = TransactionSource.fromStorage(o.string("source", null)),
            autoKey = o.string("autoKey", "") ?: "",
        )
    }

    private fun decodeLoan(o: JsonObject): Loan? {
        val principal = o.long("principal", 0L)
        if (principal <= 0) return null
        val startDate = Money.parseDate(o.string("startDate", "")) ?: return null

        val legacyTermMonths = o.long("termMonths", 12L).toInt()
        val rawCount = if (o.containsKey("installmentCount")) {
            o.long("installmentCount", 12L).toInt()
        } else {
            legacyTermMonths
        }
        val count = rawCount.coerceIn(
            LoanCalculator.MIN_INSTALLMENT_COUNT,
            LoanCalculator.MAX_INSTALLMENT_COUNT,
        )

        // 旧 v1 备份没有 intervalUnit，必须按「按月」兼容。
        val unit = if (o.containsKey("intervalUnit")) {
            ScheduleUnit.fromStorage(o.string("intervalUnit", null))
        } else {
            ScheduleUnit.MONTHS
        }
        val intervalDays = if (unit == ScheduleUnit.MONTHS) 30 else {
            o.long("intervalDays", 30L).toInt()
                .coerceIn(LoanCalculator.MIN_INTERVAL_DAYS, LoanCalculator.MAX_INTERVAL_DAYS)
        }

        var method = LoanMethod.fromStorage(o.long("method", 0L).toInt())
        val interestMode = InterestMode.fromStorage(o.string("interestMode", null))
        val scope = FixedInterestScope.fromStorage(o.string("fixedInterestScope", null))
        if (interestMode == InterestMode.FIXED &&
            scope == FixedInterestScope.PER_INSTALLMENT &&
            method == LoanMethod.EQUAL_PAYMENT
        ) {
            method = LoanMethod.INTEREST_FIRST // 非法组合，导入时回到先息后本
        }

        val annualRate = o.double("annualRate", 0.0)
            .takeIf { it.isFinite() }?.coerceIn(0.0, LoanCalculator.MAX_ANNUAL_RATE) ?: 0.0

        val payments = ArrayList<LoanPayment>()
        o["payments"]?.asArray()?.forEach { element ->
            val po = element.asObject() ?: return@forEach
            val period = po.long("period", 0L).toInt()
            if (period < 0) return@forEach
            val amount = po.long("amount", 0L)
            if (amount <= 0) return@forEach
            payments += LoanPayment(
                period = period,
                amountCents = amount,
                principalPartCents = po.long("principalPart", 0L).coerceAtLeast(0),
                interestPartCents = po.long("interestPart", 0L).coerceAtLeast(0),
                date = Money.parseDate(po.string("date", null)) ?: startDate,
                id = po.string("id", UUID.randomUUID().toString()) ?: UUID.randomUUID().toString(),
                note = po.string("note", "") ?: "",
            )
        }

        // 自由还款（period = 0）可以有任意多笔；绑定到某一期的旧数据按期号去重。
        val freePayments = payments.filter { it.period == 0 }
        val fixedPayments = payments.filter { it.period > 0 }
            .associateBy { it.period }
            .values
            .filter { it.period <= count }
        val normalizedPayments = (freePayments + fixedPayments)
            .sortedWith(compareBy({ it.date }, { it.period }))

        return Loan(
            id = o.string("id", UUID.randomUUID().toString())!!,
            friend = o.string("friend", "好友") ?: "好友",
            note = o.string("note", "") ?: "",
            principalCents = principal,
            installmentCount = count,
            intervalUnit = unit,
            intervalDays = intervalDays,
            startDate = startDate,
            method = method,
            interestMode = interestMode,
            annualRate = annualRate,
            fixedInterestCents = o.long("fixedInterestCents", 0L).coerceAtLeast(0),
            fixedInterestScope = scope,
            interestEnabled = o.bool("interestEnabled", true),
            includeInBalance = o.bool("includeInBalance", true),
            reminderEnabled = o.bool("reminderEnabled", true),
            payments = normalizedPayments,
            createdAt = o.long("createdAt", System.currentTimeMillis()),
        )
    }

    // ------------------------------------------------------------------
    // JSON 辅助
    // ------------------------------------------------------------------

    private fun kotlinx.serialization.json.JsonElement.asObject(): JsonObject? = this as? JsonObject

    private fun kotlinx.serialization.json.JsonElement.asArray(): JsonArray? = this as? JsonArray

    private fun JsonObject.string(key: String, default: String?): String? {
        val primitive = this[key] as? JsonPrimitive ?: return default
        if (primitive.isString) return primitive.content
        return if (primitive.content == "null") default else primitive.content
    }

    private fun JsonObject.long(key: String, default: Long): Long {
        val primitive = this[key] as? JsonPrimitive ?: return default
        return primitive.content.toLongOrNull() ?: default
    }

    private fun JsonObject.double(key: String, default: Double): Double {
        val primitive = this[key] as? JsonPrimitive ?: return default
        return primitive.content.toDoubleOrNull() ?: default
    }

    private fun JsonObject.bool(key: String, default: Boolean): Boolean {
        val primitive = this[key] as? JsonPrimitive ?: return default
        return when (primitive.content.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default
        }
    }
}


