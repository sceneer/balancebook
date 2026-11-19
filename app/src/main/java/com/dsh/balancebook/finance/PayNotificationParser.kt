package com.dsh.balancebook.finance

import java.security.MessageDigest

/**
 * 一条原始通知摘要（不依赖 Android API，便于 JVM 单测）。
 *
 * Android 侧的 `PayNotificationListenerService` 只负责把 [android.app.Notification] 的字段
 * 搬运到这里；所有金额、方向、商户、分类的判断都留在本文件的纯函数里完成。
 *
 * @property packageName 发出通知的应用包名，用于区分微信 / 支付宝。
 * @property title 通知标题（通常是联系人名或「微信支付」「微信收款助手」）。
 * @property text 通知正文。
 * @property subText 通知副标题。
 * @property bigText 展开后的长文本，通常比 [text] 更完整。
 * @property textLines 多行通知的每一行明细。
 * @property postedAtMillis 通知投递时间（毫秒），用于 3 分钟窗口去重。
 */
data class RawPayNotification(
    val packageName: String,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    val textLines: List<String> = emptyList(),
    val postedAtMillis: Long,
)

/**
 * 解析结果：一条可以直接写入账单库的自动记账数据。
 *
 * @property source 通知来源（只会是 [TransactionSource.WECHAT] 或 [TransactionSource.ALIPAY]）。
 * @property type 收支方向。
 * @property amountCents 金额，单位「分」，全程用 [Long]，不使用 [Double]。
 * @property merchant 识别出的商户 / 对方，尽力而为，失败时为空串。
 * @property category 建议分类 key，一定对应该 [type] 合法（见 `Categories`）。
 * @property note 建议备注，等于 [merchant]（为空则空串）。
 * @property rawText 实际参与解析的合并文本（已折叠空白，最长 300 字符）。
 * @property postedAtMillis 原始通知时间。
 * @property dedupeKey 去重键：来源 + 方向 + 金额 + 3 分钟时间窗口。
 * @property transactionId 自动账单 id：`auto-<source>-<sha1(dedupeKey)>`，稳定可复现。
 */
data class ParsedPayNotification(
    val source: TransactionSource,
    val type: TransactionType,
    val amountCents: Long,
    val merchant: String,
    val category: String,
    val note: String,
    val rawText: String,
    val postedAtMillis: Long,
    val dedupeKey: String,
    val transactionId: String,
)

/**
 * 微信 / 支付宝支付通知解析器。
 *
 * 纯 Kotlin、无 Android / Compose 依赖：输入一条 [RawPayNotification]，输出一条
 * [ParsedPayNotification]；非支付通知、营销通知、无法识别金额或方向的通知一律返回 `null`。
 *
 * 解析顺序：
 * 1. 包名 → 来源；微信还要求文本里出现微信支付标记，避免把普通聊天当成账单。
 * 2. 过滤待支付 / 支付失败 / 营销 / 账单提醒等非支付通知。
 * 3. 从合并文本里挑出最可信的金额（优先取前面 16 个字符内含动作关键字的候选）。
 * 4. 按「最后出现的关键字」判定收支方向（收款关键字优先于更早的支付关键字）。
 * 5. 尽力提取商户，再按关键字给出分类建议，最后生成去重键与稳定交易 id。
 */
object PayNotificationParser {

    /** 微信包名。 */
    const val WECHAT_PACKAGE = "com.tencent.mm"

    /** 支付宝包名（含 RC 测试版）。 */
    val ALIPAY_PACKAGES: Set<String> =
        setOf("com.eg.android.AlipayGphone", "com.eg.android.AlipayGphoneRC")

    /** 合并文本最大长度，超出部分直接截断，避免超长营销文案拖慢解析。 */
    private const val MAX_RAW_TEXT_LENGTH = 300

    /** 金额上限：1 亿元，单位「分」。 */
    private const val MAX_AMOUNT_CENTS = 10_000_000_000L

    /** 去重时间窗口：3 分钟。 */
    private const val DEDUPE_WINDOW_MILLIS = 180_000L

    /** 判断金额候选「前面的动作关键字」时回看的字符数。 */
    private const val AMOUNT_CONTEXT_CHARS = 16

    /** 空白折叠。 */
    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * 数值模式：`12` / `12.00` / `1,234.56` / `1,280.50`。
     *
     * 必须让「带千分位」的分支排在前面并整体匹配（`\d{1,3}(?:,\d{3})+`），
     * 否则 `¥1234.56` 会被前缀匹配成 `123`。
     */
    private const val NUMBER_PATTERN = "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?"

    /**
     * 金额正则：`¥12.00` / `￥12.00` / `12.00元` / `1,234.56元`。
     * 第 1 组是「货币符号开头」的金额，第 2 组是「元结尾」的金额。
     */
    private val AMOUNT_REGEX = Regex("""[¥￥]\s*($NUMBER_PATTERN)|($NUMBER_PATTERN)\s*元""")

    /** 金额上下文中出现即放弃该候选的关键字（余额、营销文案等）。 */
    private val AMOUNT_SKIP_WORDS = listOf("余额", "红包封面", "优惠", "立减", "最高", "可用")

    /** 收入方向关键字（越靠后的出现位置优先级越高）。 */
    private val INCOME_KEYWORDS = listOf(
        "收款到账", "收款", "到账", "退款", "收入", "转入", "收到", "已收款", "向你转账", "红包",
    )

    /** 支出方向关键字（越靠后的出现位置优先级越高）。 */
    private val EXPENSE_KEYWORDS = listOf(
        "支付成功", "付款成功", "已支付", "支付", "付款", "消费", "扣款", "支出", "转出", "转账给", "向你付款",
    )

    /** 方向判定用的全部动作关键字，金额候选的上下文里出现任意一个就算「有动作」。 */
    private val DIRECTION_KEYWORDS = INCOME_KEYWORDS + EXPENSE_KEYWORDS

    /** 非支付 / 营销 / 提醒类通知关键字，命中即整条忽略。 */
    private val NON_PAYMENT_KEYWORDS = listOf(
        "待支付", "待付款", "未支付", "请尽快支付", "请及时支付", "去支付", "待收款",
        "支付失败", "付款失败", "已关闭", "已过期", "失效", "验证码", "校验码", "登录",
        "免密支付开通", "签约", "抽奖", "活动", "优惠", "立减", "红包封面", "领红包",
        "领取", "积分", "会员", "邀请", "账单提醒", "本月账单", "年度账单", "还款成功",
        // 余额宝收益类通知不是消费/收入流水。
        "余额宝",
    )

    /** 微信支付类通知必须带上的标记之一。 */
    private val WECHAT_MARKERS = listOf("微信支付", "微信收款", "微信转账", "微信支付凭证", "微信零钱")

    /** 只有「[转账]」时还要配合这些动作词才认账（避免把转账聊天消息当账单）。 */
    private val TRANSFER_ACTION_WORDS = listOf("已收款", "收款到账", "已支付", "付款成功", "向你转账")

    /** 商户提取模式，按优先级从高到低尝试。 */
    private val MERCHANT_PATTERNS = listOf(
        Regex("""向\s*(.{1,20}?)(?:付款|支付|转账)"""),
        Regex("""在\s*(.{1,20}?)(?:消费|付款|支付)"""),
        Regex("""收款方[:：]?\s*(.{1,20})"""),
        Regex("""商家[:：]\s*(.{1,20})"""),
        Regex("""来自\s*(.{1,20})"""),
        Regex("""(.{1,20}?)收款"""),
    )

    /** 从商户候选里剔除金额片段。 */
    private val MERCHANT_AMOUNT_REGEX = Regex("""[¥￥]\s*[\d,]+(?:\.\d{1,2})?|[\d,]+(?:\.\d{1,2})?\s*元""")

    /** 商户首尾需要去掉的标点与空白。 */
    private val MERCHANT_TRIM_CHARS =
        "，。,.、；;：:！!？?（）()[]【】《》<>\"'“”‘’-—_/\\| \t".toSet()

    /** 商户候选里不应该出现的动作 / 流程词（含服务号名称，避免把「助手」「凭证」当商户）。 */
    private val MERCHANT_REJECT_WORDS = listOf(
        "成功", "已", "请", "支付", "付款", "收款", "转账", "到账", "金额", "订单", "你", "助手",
        "助手", "凭证", "通知", "服务", "账单", "提醒", "零钱", "公众号",
    )

    /** 平台自身名字不算商户。 */
    private val GENERIC_MERCHANTS = setOf("微信", "支付宝", "微信支付", "支付宝支付", "微信收款", "微信转账")

    /** 支出分类关键字，顺序即优先级。 */
    private val EXPENSE_CATEGORY_RULES: List<Pair<String, List<String>>> = listOf(
        "food" to listOf(
            "美团", "饿了么", "外卖", "餐厅", "饭店", "咖啡", "星巴克", "瑞幸", "奶茶",
            "麦当劳", "肯德基", "食堂", "早餐", "午餐", "晚餐", "火锅", "烧烤",
        ),
        "transport" to listOf(
            "滴滴", "打车", "地铁", "公交", "出租", "高铁", "12306", "加油", "停车", "交通", "单车", "哈啰",
        ),
        "shopping" to listOf("淘宝", "天猫", "京东", "拼多多", "超市", "商场", "优衣库", "网购", "唯品会", "服饰"),
        "home" to listOf("水电", "电费", "水费", "燃气", "物业", "房租", "国家电网", "宽带"),
        "fun" to listOf("电影", "游戏", "娱乐", "KTV", "演出", "票务", "猫眼"),
        "health" to listOf("医院", "药", "挂号", "体检", "诊所", "口腔"),
        "education" to listOf("学费", "书", "教育", "培训", "网课"),
    )

    /**
     * 包名 → 账单来源；不支持的包名返回 `null`。
     */
    fun sourceFor(packageName: String?): TransactionSource? = when {
        packageName == WECHAT_PACKAGE -> TransactionSource.WECHAT
        packageName != null && packageName in ALIPAY_PACKAGES -> TransactionSource.ALIPAY
        else -> null
    }

    /**
     * 解析一条通知；不是支付通知或无法识别时返回 `null`。
     */
    fun parse(raw: RawPayNotification): ParsedPayNotification? {
        val source = sourceFor(raw.packageName) ?: return null
        val rawText = buildRawText(raw).take(MAX_RAW_TEXT_LENGTH)
        if (rawText.isBlank()) return null
        if (source == TransactionSource.WECHAT && !looksLikeWechatPayment(rawText)) return null
        if (NON_PAYMENT_KEYWORDS.any { rawText.contains(it) }) return null
        val amountCents = extractAmountCents(rawText) ?: return null
        val type = resolveType(rawText) ?: return null
        val merchant = extractMerchant(rawText)
        val category = suggestCategory(rawText, type)
        val dedupeKey = buildDedupeKey(source, type, amountCents, raw.postedAtMillis)
        return ParsedPayNotification(
            source = source,
            type = type,
            amountCents = amountCents,
            merchant = merchant,
            category = category,
            note = merchant,
            rawText = rawText,
            postedAtMillis = raw.postedAtMillis,
            dedupeKey = dedupeKey,
            transactionId = "auto-" + source.storageValue + "-" + sha1Hex(dedupeKey),
        )
    }

    /** 把 title / subText / text / bigText / textLines 合并成一段带空格的文本，并折叠空白。 */
    private fun buildRawText(raw: RawPayNotification): String {
        val parts = ArrayList<String>(4 + raw.textLines.size)
        raw.title?.let { parts += it }
        raw.subText?.let { parts += it }
        raw.text?.let { parts += it }
        raw.bigText?.let { parts += it }
        parts += raw.textLines
        return parts.joinToString(" ").replace(WHITESPACE_REGEX, " ").trim()
    }

    /** 微信通知是否像一笔支付：要么带微信支付标记，要么带「[转账]」加明确动作词。 */
    private fun looksLikeWechatPayment(text: String): Boolean {
        if (WECHAT_MARKERS.any { text.contains(it) }) return true
        return text.contains("[转账]") && TRANSFER_ACTION_WORDS.any { text.contains(it) }
    }

    /** 在全部金额候选中挑出最可信的一个。 */
    private fun extractAmountCents(text: String): Long? {
        val candidates = ArrayList<AmountCandidate>()
        var previousEnd = 0
        for (match in AMOUNT_REGEX.findAll(text)) {
            val start = match.range.first
            val value = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val cents = parseCents(value)
            if (cents != null && cents > 0L && cents <= MAX_AMOUNT_CENTS) {
                // 只回看「上一个金额之后」的 16 个字符，避免被更早的余额 / 营销文案污染。
                val contextStart = maxOf(previousEnd, start - AMOUNT_CONTEXT_CHARS).coerceAtMost(start)
                val context = text.substring(contextStart, start)
                if (AMOUNT_SKIP_WORDS.none { context.contains(it) }) {
                    candidates += AmountCandidate(cents, DIRECTION_KEYWORDS.any { context.contains(it) })
                }
            }
            previousEnd = match.range.last + 1
        }
        return (candidates.firstOrNull { it.hasAction } ?: candidates.firstOrNull())?.cents
    }

    /** 把「1,234.56」这类金额字符串转成「分」；非法或超限返回 `null`。 */
    private fun parseCents(raw: String): Long? {
        val cleaned = raw.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val dot = cleaned.indexOf('.')
        val yuanPart = if (dot >= 0) cleaned.substring(0, dot) else cleaned
        val centPart = if (dot >= 0) cleaned.substring(dot + 1) else ""
        if (yuanPart.isEmpty() || centPart.length > 2) return null
        if (yuanPart.any { !it.isDigit() } || centPart.any { !it.isDigit() }) return null
        val yuan = yuanPart.toLongOrNull() ?: return null
        if (yuan > MAX_AMOUNT_CENTS / 100 + 1) return null
        val fraction = when (centPart.length) {
            0 -> 0L
            1 -> centPart.toLong() * 10L
            else -> centPart.toLong()
        }
        return yuan * 100L + fraction
    }

    /**
     * 收支方向：收入 / 支出关键字都命中时，取在文本中更靠后的那个方向。
     * 都未命中返回 `null`（无法判断就不记账）。
     */
    private fun resolveType(text: String): TransactionType? {
        val incomeIndex = lastKeywordIndex(text, INCOME_KEYWORDS)
        val expenseIndex = lastKeywordIndex(text, EXPENSE_KEYWORDS)
        return when {
            incomeIndex < 0 && expenseIndex < 0 -> null
            incomeIndex >= 0 && expenseIndex >= 0 ->
                if (incomeIndex > expenseIndex) TransactionType.INCOME else TransactionType.EXPENSE
            incomeIndex >= 0 -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }
    }

    /** 返回任意关键字在文本中最后出现的下标，未出现返回 -1。 */
    private fun lastKeywordIndex(text: String, keywords: List<String>): Int =
        keywords.maxOfOrNull { text.lastIndexOf(it) } ?: -1

    /** 尽力提取商户 / 对方，识别不出时返回空串。 */
    private fun extractMerchant(text: String): String {
        for (pattern in MERCHANT_PATTERNS) {
            for (match in pattern.findAll(text)) {
                val cleaned = cleanMerchant(match.groupValues.getOrNull(1).orEmpty())
                if (cleaned.isNotEmpty()) return cleaned
            }
        }
        return ""
    }

    /** 清理商户候选：去金额、去首尾标点、限制 2..24 字符且必须含字母或汉字。 */
    private fun cleanMerchant(candidate: String): String {
        val withoutAmount = MERCHANT_AMOUNT_REGEX.replace(candidate, " ")
        val trimmed = withoutAmount
            .replace(WHITESPACE_REGEX, " ")
            .trim { it in MERCHANT_TRIM_CHARS || it.isWhitespace() }
        if (trimmed.length !in 2..24) return ""
        if (trimmed.none { it.isLetter() }) return ""
        if (MERCHANT_REJECT_WORDS.any { trimmed.contains(it) }) return ""
        if (trimmed in GENERIC_MERCHANTS) return ""
        return trimmed
    }

    /** 按关键字给出分类建议；无命中时支出 other、收入 other_income。 */
    private fun suggestCategory(text: String, type: TransactionType): String {
        if (type == TransactionType.INCOME) {
            return when {
                text.contains("工资") -> "salary"
                text.contains("奖金") -> "bonus"
                text.contains("收益") || text.contains("理财") || text.contains("利息") -> "invest"
                else -> "other_income"
            }
        }
        return EXPENSE_CATEGORY_RULES.firstOrNull { (_, keywords) ->
            keywords.any { text.contains(it) }
        }?.first ?: "other"
    }

    /** 去重键：来源 + 方向 + 金额 + 3 分钟时间窗口。 */
    private fun buildDedupeKey(
        source: TransactionSource,
        type: TransactionType,
        amountCents: Long,
        postedAtMillis: Long,
    ): String = buildString {
        append(source.storageValue)
        append('|')
        append(type.name)
        append('|')
        append(amountCents)
        append('|')
        append(postedAtMillis / DEDUPE_WINDOW_MILLIS)
    }

    /** SHA-1 十六进制小写，用于生成稳定交易 id。 */
    private fun sha1Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val int = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[int ushr 4]).append(HEX_DIGITS[int and 0x0F])
        }
        return builder.toString()
    }

    private const val HEX_DIGITS = "0123456789abcdef"

    /** 一个金额候选及其上下文是否带动作关键字。 */
    private data class AmountCandidate(val cents: Long, val hasAction: Boolean)
}
