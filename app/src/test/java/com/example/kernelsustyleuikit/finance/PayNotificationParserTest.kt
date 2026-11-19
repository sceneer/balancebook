package com.example.kernelsustyleuikit.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PayNotificationParser] 的 JVM 单测。
 *
 * 全部用例只依赖纯 Kotlin 数据类，不启动 Android，也不访问通知服务。
 */
class PayNotificationParserTest {

    // ==== 1. 微信：支付成功（bigText 才带金额与商户）====

    @Test
    fun parsesWechatExpenseFromBigText() {
        val raw = wechat(
            title = "微信支付",
            text = "微信支付凭证",
            bigText = "向 星巴克 付款成功 ¥32.00",
        )
        val parsed = parseOrFail(raw)
        assertEquals(TransactionSource.WECHAT, parsed.source)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(3200L, parsed.amountCents)
        assertEquals("星巴克", parsed.merchant)
        assertEquals("星巴克", parsed.note)
        assertEquals("food", parsed.category)
        assertEquals(raw.postedAtMillis, parsed.postedAtMillis)
        assertTrue(parsed.rawText.contains("星巴克"))
        assertTrue(parsed.transactionId.startsWith("auto-wechat-"))
    }

    // ==== 2. 微信：收款到账 ====

    @Test
    fun parsesWechatCollectionIncome() {
        val parsed = parseOrFail(
            wechat(title = "微信收款助手", text = "收款到账 ¥100.00"),
        )
        assertEquals(TransactionSource.WECHAT, parsed.source)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(10_000L, parsed.amountCents)
        assertEquals("other_income", parsed.category)
        assertEquals("", parsed.merchant)
    }

    // ==== 3. 微信：[转账] 已收款 ====

    @Test
    fun parsesWechatTransferReceived() {
        val parsed = parseOrFail(
            wechat(title = "张三", text = "[转账] 已收款 ¥50.00"),
        )
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(5_000L, parsed.amountCents)
        assertEquals("other_income", parsed.category)
    }

    // ==== 3 变体：没有其他微信标记、只有 [转账] + 已收款 ====

    @Test
    fun parsesWechatTransferWithoutOtherWechatMarkers() {
        val parsed = parseOrFail(
            wechat(title = "李四", text = "[转账] 已收款", bigText = "¥88.00"),
        )
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(8_800L, parsed.amountCents)
    }

    // ==== 3 变体：微信转账标题 ====

    @Test
    fun parsesWechatTransferTitleIncome() {
        val parsed = parseOrFail(wechat(title = "微信转账", text = "已收款 ¥66.00"))
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(6_600L, parsed.amountCents)
    }

    // ==== 4. 微信普通聊天不能记账 ====

    @Test
    fun ignoresWechatChatMessage() {
        assertNull(PayNotificationParser.parse(wechat(title = "妈妈", text = "妈妈: 12.00元")))
    }

    // ==== 5. 微信营销通知不能记账 ====

    @Test
    fun ignoresWechatMarketingNotification() {
        assertNull(
            PayNotificationParser.parse(
                wechat(title = "微信支付", text = "微信支付有优惠 最高立减 88 元"),
            ),
        )
    }

    // ==== 6. 支付宝：付款成功 ====

    @Test
    fun parsesAlipayExpense() {
        val parsed = parseOrFail(alipay(text = "支付宝成功付款12.00元"))
        assertEquals(TransactionSource.ALIPAY, parsed.source)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(1_200L, parsed.amountCents)
        assertTrue(parsed.transactionId.startsWith("auto-alipay-"))
    }

    // ==== 7. 支付宝：收款（千分位金额）====

    @Test
    fun parsesAlipayIncome() {
        val parsed = parseOrFail(alipay(text = "你已成功收款 1,234.56 元"))
        assertEquals(TransactionSource.ALIPAY, parsed.source)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(123_456L, parsed.amountCents)
    }

    // ==== 8. 支付宝：退款到账 ====

    @Test
    fun parsesAlipayRefundIncome() {
        val parsed = parseOrFail(alipay(text = "退款成功 到账 ¥20.00"))
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(2_000L, parsed.amountCents)
    }

    // ==== 9. 待支付订单提醒 ====

    @Test
    fun ignoresPendingOrderReminder() {
        assertNull(PayNotificationParser.parse(wechat(title = "微信支付", text = "待支付订单 请尽快支付 ¥30.00")))
    }

    // ==== 10. 支付失败 ====

    @Test
    fun ignoresFailedPayment() {
        assertNull(PayNotificationParser.parse(wechat(title = "微信支付", text = "微信支付 支付失败 ¥30.00")))
    }

    // ==== 11. 最小金额 0.01 元 ====

    @Test
    fun parsesOneCentAmount() {
        val parsed = parseOrFail(wechat(title = "微信支付", text = "微信支付凭证 付款成功 ¥0.01"))
        assertEquals(1L, parsed.amountCents)
    }

    // ==== 12. 千分位金额 1,280.50 ====

    @Test
    fun parsesThousandSeparatorAmount() {
        val parsed = parseOrFail(wechat(title = "微信支付", text = "付款成功 ¥1,280.50"))
        assertEquals(128_050L, parsed.amountCents)
    }

    // ==== 13. 金额为 0 或没有金额：不能记账 ====

    @Test
    fun ignoresZeroAmount() {
        assertNull(PayNotificationParser.parse(wechat(title = "微信支付", text = "微信支付凭证 付款成功 0.00元")))
    }

    @Test
    fun ignoresNotificationWithoutAmount() {
        assertNull(PayNotificationParser.parse(wechat(title = "微信支付", text = "微信支付凭证 付款成功")))
    }

    // ==== 14. 非微信 / 支付宝包名 ====

    @Test
    fun ignoresUnsupportedPackages() {
        val raw = RawPayNotification(
            packageName = "com.example.other",
            text = "支付成功 ¥10.00",
            postedAtMillis = BASE_TIME,
        )
        assertNull(PayNotificationParser.parse(raw))
        assertNull(PayNotificationParser.sourceFor("com.example.other"))
        assertNull(PayNotificationParser.sourceFor(null))
    }

    // ==== 15. 千分位与空格混排 ====

    @Test
    fun parsesAmountWithSeparatorAndSpaces() {
        val symbolAndSpace = parseOrFail(wechat(title = "微信支付", text = "付款成功 ¥ 1,234.56"))
        assertEquals(123_456L, symbolAndSpace.amountCents)

        val yuanSuffixSpace = parseOrFail(wechat(title = "微信支付", text = "付款成功 1,234.56 元"))
        assertEquals(123_456L, yuanSuffixSpace.amountCents)
    }

    // ==== 16. 支出分类映射 ====

    @Test
    fun mapsExpenseCategories() {
        val cases = listOf(
            "向 美团外卖 付款成功 ¥10.00" to "food",
            "向 滴滴出行 付款成功 ¥10.00" to "transport",
            "向 淘宝 付款成功 ¥10.00" to "shopping",
            "向 国家电网 付款成功 ¥100.00" to "home",
            "向 市立医院 付款成功 ¥120.00" to "health",
            "向 猫眼电影 付款成功 ¥40.00" to "fun",
            "向 新东方 付款成功 学费 ¥1,000.00" to "education",
        )
        for ((text, expected) in cases) {
            val parsed = parseOrFail(wechat(title = "微信支付", text = text))
            assertEquals("分类错误: $text", expected, parsed.category)
        }
    }

    // ==== 17. 收入分类映射 ====

    @Test
    fun mapsIncomeCategories() {
        assertEquals("salary", parseOrFail(wechat(title = "微信支付", text = "工资到账 ¥8,000.00")).category)
        assertEquals("bonus", parseOrFail(wechat(title = "微信支付", text = "奖金到账 ¥2,000.00")).category)
        assertEquals("invest", parseOrFail(wechat(title = "微信支付", text = "理财收益到账 ¥15.00")).category)
    }

    // ==== 18. 去重键：3 分钟窗口 + 来源 + 金额 ====

    @Test
    fun dedupeKeyUsesTimeWindowAndSource() {
        val base = wechat(title = "微信支付", text = "微信支付凭证 付款成功 ¥32.00")
        val fiveSecondsLater = wechat(
            title = "微信支付",
            text = "微信支付凭证 付款成功 ¥32.00",
            postedAtMillis = BASE_TIME + 5_000L,
        )
        val tenMinutesLater = wechat(
            title = "微信支付",
            text = "微信支付凭证 付款成功 ¥32.00",
            postedAtMillis = BASE_TIME + 600_000L,
        )
        assertEquals(parseOrFail(base).dedupeKey, parseOrFail(fiveSecondsLater).dedupeKey)
        assertNotEquals(parseOrFail(base).dedupeKey, parseOrFail(tenMinutesLater).dedupeKey)

        val alipaySameText = parseOrFail(alipay(text = "微信支付凭证 付款成功 ¥32.00"))
        assertNotEquals(parseOrFail(base).dedupeKey, alipaySameText.dedupeKey)

        val otherAmount = parseOrFail(wechat(title = "微信支付", text = "微信支付凭证 付款成功 ¥33.00"))
        assertNotEquals(parseOrFail(base).dedupeKey, otherAmount.dedupeKey)
    }

    // ==== 19. transactionId：稳定 + 前缀 + sha1 长度 ====

    @Test
    fun transactionIdIsStableAndPrefixed() {
        val raw = wechat(title = "微信支付", text = "微信支付凭证 付款成功 ¥32.00")
        val first = parseOrFail(raw)
        val second = parseOrFail(raw)
        assertEquals(first.transactionId, second.transactionId)
        assertTrue(Regex("auto-wechat-[0-9a-f]{40}").matches(first.transactionId))
        assertTrue(first.transactionId.removePrefix("auto-wechat-").length == 40)

        val otherAmount = parseOrFail(wechat(title = "微信支付", text = "微信支付凭证 付款成功 ¥33.00"))
        assertNotEquals(first.transactionId, otherAmount.transactionId)

        val alipay = parseOrFail(alipay(text = "支付宝成功付款12.00元"))
        assertTrue(Regex("auto-alipay-[0-9a-f]{40}").matches(alipay.transactionId))
    }

    // ==== 20. bigText 比 text 更完整 ====

    @Test
    fun parsesAmountFromBigText() {
        val parsed = parseOrFail(
            wechat(title = "微信支付", text = "微信支付凭证", bigText = "付款金额 ¥66.00"),
        )
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(6_600L, parsed.amountCents)
    }

    // ==== 21. textLines 提供明细 ====

    @Test
    fun parsesAmountFromTextLines() {
        val parsed = parseOrFail(
            wechat(
                title = "微信支付",
                textLines = listOf("向 美团外卖 付款成功", "¥45.00"),
            ),
        )
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(4_500L, parsed.amountCents)
        assertEquals("美团外卖", parsed.merchant)
        assertEquals("food", parsed.category)
    }

    // ==== 22. 余额宝收益 / 营销不误记 ====

    @Test
    fun ignoresBalanceBaoYieldAndMarketing() {
        assertNull(PayNotificationParser.parse(alipay(text = "余额宝收益 0.88元")))
        assertNull(PayNotificationParser.parse(alipay(text = "支付宝会员积分 抽奖 88 元")))
    }

    // ==== 额外：余额上下文里的金额不应被当成支付金额 ====

    @Test
    fun skipsBalanceAmountWhenAnotherPaymentAmountExists() {
        val parsed = parseOrFail(
            wechat(title = "微信支付", text = "余额 100.00元，支付 12.00元"),
        )
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(1_200L, parsed.amountCents)
    }

    // ==== 额外：包名映射 ====

    @Test
    fun mapsSupportedPackages() {
        assertEquals(TransactionSource.WECHAT, PayNotificationParser.sourceFor("com.tencent.mm"))
        assertEquals(
            TransactionSource.ALIPAY,
            PayNotificationParser.sourceFor("com.eg.android.AlipayGphone"),
        )
        assertEquals(
            TransactionSource.ALIPAY,
            PayNotificationParser.sourceFor("com.eg.android.AlipayGphoneRC"),
        )
        assertNull(PayNotificationParser.sourceFor("com.tencent.mobileqq"))
        assertNotNull(PayNotificationParser.ALIPAY_PACKAGES)
        assertEquals(PayNotificationParser.WECHAT_PACKAGE, "com.tencent.mm")
    }

    // ==== 辅助构造 ====

    private fun wechat(
        title: String? = null,
        text: String? = null,
        subText: String? = null,
        bigText: String? = null,
        textLines: List<String> = emptyList(),
        postedAtMillis: Long = BASE_TIME,
    ): RawPayNotification = RawPayNotification(
        packageName = PayNotificationParser.WECHAT_PACKAGE,
        title = title,
        text = text,
        subText = subText,
        bigText = bigText,
        textLines = textLines,
        postedAtMillis = postedAtMillis,
    )

    private fun alipay(
        title: String? = null,
        text: String? = null,
        subText: String? = null,
        bigText: String? = null,
        textLines: List<String> = emptyList(),
        postedAtMillis: Long = BASE_TIME,
    ): RawPayNotification = RawPayNotification(
        packageName = "com.eg.android.AlipayGphone",
        title = title,
        text = text,
        subText = subText,
        bigText = bigText,
        textLines = textLines,
        postedAtMillis = postedAtMillis,
    )

    private fun parseOrFail(raw: RawPayNotification): ParsedPayNotification =
        PayNotificationParser.parse(raw) ?: throw AssertionError("应解析成功: $raw")

    private companion object {
        /** 2023-11-14 22:14:00 UTC 附近的一个固定时间戳，便于验证 3 分钟去重窗口。 */
        const val BASE_TIME = 1_700_000_040_000L
    }
}
