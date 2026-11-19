package com.example.kernelsustyleuikit.ui.screen.finance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryInfo(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

object Categories {

    val expense: List<CategoryInfo> = listOf(
        CategoryInfo("food", "餐饮", Icons.Rounded.Restaurant),
        CategoryInfo("transport", "交通", Icons.Rounded.DirectionsBus),
        CategoryInfo("shopping", "购物", Icons.Rounded.ShoppingBag),
        CategoryInfo("home", "居家", Icons.Rounded.Home),
        CategoryInfo("fun", "娱乐", Icons.Rounded.SportsEsports),
        CategoryInfo("health", "医疗", Icons.Rounded.LocalHospital),
        CategoryInfo("education", "教育", Icons.Rounded.School),
        CategoryInfo("other", "其他", Icons.Rounded.MoreHoriz),
    )

    val income: List<CategoryInfo> = listOf(
        CategoryInfo("salary", "工资", Icons.Rounded.Payments),
        CategoryInfo("bonus", "奖金", Icons.Rounded.CardGiftcard),
        CategoryInfo("invest", "理财", Icons.Rounded.TrendingUp),
        CategoryInfo("other_income", "其他收入", Icons.Rounded.AccountBalanceWallet),
    )

    private val all: Map<String, CategoryInfo> = (expense + income).associateBy { it.key }

    fun byKey(key: String): CategoryInfo =
        all[key] ?: CategoryInfo(key, key, Icons.Rounded.MoreHoriz)

    fun label(key: String): String = byKey(key).label

    fun icon(key: String): ImageVector = byKey(key).icon

    fun listFor(incomeMode: Boolean): List<CategoryInfo> = if (incomeMode) income else expense

    fun isValidFor(key: String, incomeMode: Boolean): Boolean =
        listFor(incomeMode).any { it.key == key }
}
