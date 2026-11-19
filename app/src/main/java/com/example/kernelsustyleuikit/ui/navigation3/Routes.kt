package com.example.kernelsustyleuikit.ui.navigation3

import android.os.Parcelable
import top.yukonga.miuix.kmp.nav.core.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for miuix-nav.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
@Serializable
sealed interface Route : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data class AddTransaction(val id: String? = null) : Route

    @Parcelize
    @Serializable
    data class AddLoan(val id: String? = null) : Route

    @Parcelize
    @Serializable
    data class LoanDetail(val id: String) : Route

    @Parcelize
    @Serializable
    data object ColorPalette : Route

    // 保留模板原有路由，供模板自带页面引用。
    @Parcelize
    @Serializable
    data object Home : Route

    @Parcelize
    @Serializable
    data object Settings : Route

    @Parcelize
    @Serializable
    data object About : Route

    @Parcelize
    @Serializable
    data object Permissions : Route
}
