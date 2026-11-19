package com.example.kernelsustyleuikit.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.example.kernelsustyleuikit.data.BalanceBookRepository
import com.example.kernelsustyleuikit.finance.FinanceState
import com.example.kernelsustyleuikit.reminder.LoanReminder
import com.example.kernelsustyleuikit.ui.component.bottombar.BottomBar
import com.example.kernelsustyleuikit.ui.component.bottombar.MainPagerState
import com.example.kernelsustyleuikit.ui.component.bottombar.rememberMainPagerState
import com.example.kernelsustyleuikit.ui.navigation3.LocalNavigator
import com.example.kernelsustyleuikit.ui.navigation3.Navigator
import com.example.kernelsustyleuikit.ui.navigation3.Route
import com.example.kernelsustyleuikit.ui.navigation3.rememberNavigator
import com.example.kernelsustyleuikit.ui.screen.colorpalette.ColorPaletteScreen
import com.example.kernelsustyleuikit.ui.screen.finance.BillsPage
import com.example.kernelsustyleuikit.ui.screen.finance.HomePage
import com.example.kernelsustyleuikit.ui.screen.finance.LoanDetailPage
import com.example.kernelsustyleuikit.ui.screen.finance.LoanEditPage
import com.example.kernelsustyleuikit.ui.screen.finance.LoansPage
import com.example.kernelsustyleuikit.ui.screen.finance.OnboardingPage
import com.example.kernelsustyleuikit.ui.screen.finance.SettingsPage
import com.example.kernelsustyleuikit.ui.screen.finance.TransactionEditPage
import com.example.kernelsustyleuikit.ui.theme.AppSettings
import com.example.kernelsustyleuikit.ui.theme.LocalColorMode
import com.example.kernelsustyleuikit.ui.theme.LocalEnableBlur
import com.example.kernelsustyleuikit.ui.theme.LocalEnableFloatingBottomBar
import com.example.kernelsustyleuikit.ui.theme.LocalEnableFloatingBottomBarBlur
import com.example.kernelsustyleuikit.ui.theme.TemplateTheme
import com.example.kernelsustyleuikit.ui.theme.ThemeController
import com.example.kernelsustyleuikit.ui.util.rememberBlurBackdrop
import com.example.kernelsustyleuikit.ui.util.rememberContentReady
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_LOANS = "open_loans"
    }

    private val openLoansTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BalanceBookRepository.init(applicationContext)
        LoanReminder.ensureChannel(this)
        if (intent?.getBooleanExtra(EXTRA_OPEN_LOANS, false) == true) {
            openLoansTick.intValue += 1
        }
        setContent {
            BalanceBookApp(openLoansTick = openLoansTick.intValue)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_LOANS, false)) {
            openLoansTick.intValue += 1
        }
    }
}

@Composable
private fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(ThemeController.getAppSettings(context)) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            settings = ThemeController.getAppSettings(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}

/** 主题页里的模糊 / 悬浮底栏 / 液态玻璃开关（与 SettingsRepositoryImpl 使用同一组 key）。 */
private data class UiPrefs(
    val blur: Boolean = true,
    val floating: Boolean = true,
    val glass: Boolean = true,
)

private fun readUiPrefs(context: android.content.Context): UiPrefs {
    val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    return UiPrefs(
        blur = prefs.getBoolean("enable_blur", true),
        floating = prefs.getBoolean("enable_floating_bottom_bar", true),
        glass = prefs.getBoolean("enable_floating_bottom_bar_blur", true),
    )
}

@Composable
private fun rememberUiPrefs(): UiPrefs {
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(readUiPrefs(context)) }
    DisposableEffect(context) {
        val shared = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null ||
                key == "enable_blur" ||
                key == "enable_floating_bottom_bar" ||
                key == "enable_floating_bottom_bar_blur"
            ) {
                prefs = readUiPrefs(context)
            }
        }
        shared.registerOnSharedPreferenceChangeListener(listener)
        onDispose { shared.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return prefs
}

@Composable
fun BalanceBookApp(openLoansTick: Int) {
    val context = LocalContext.current
    val state by BalanceBookRepository.state.collectAsStateWithLifecycle()
    val message by BalanceBookRepository.message.collectAsStateWithLifecycle()
    val appSettings = rememberAppSettings()
    val uiPrefs = rememberUiPrefs()
    val navigator = rememberNavigator(Route.Main)
    val darkMode = appSettings.colorMode.isDark ||
        (appSettings.colorMode.isSystem && isSystemInDarkTheme())

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
        BalanceBookRepository.consumeMessage()
    }

    DisposableEffect(darkMode) {
        val activity = context as? ComponentActivity
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { darkMode },
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { darkMode },
        )
        onDispose { }
    }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalUiMode provides UiMode.Miuix,
        LocalColorMode provides appSettings.colorMode.value,
        LocalEnableBlur provides uiPrefs.blur,
        LocalEnableFloatingBottomBar provides uiPrefs.floating,
        LocalEnableFloatingBottomBarBlur provides uiPrefs.glass,
    ) {
        TemplateTheme(appSettings = appSettings, uiMode = UiMode.Miuix) {
            if (!state.settings.onboarded) {
                OnboardingPage { opening, budget ->
                    BalanceBookRepository.completeOnboarding(opening, budget)
                }
            } else {
                val swipeDismiss = NavSwipeDirection.LeftToRight
                // 根 Scaffold 提供 popupHost：主题页的 OverlayDropdownPreference / 强调色等
                // 多级菜单必须挂在它上面，否则只出现遮罩、菜单不显示。
                top.yukonga.miuix.kmp.basic.Scaffold {
                    NavDisplay(                        backStack = navigator.backStack,
                        effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
                        onBack = { navigator.pop() },
                    ) {
                        entry<Route.Main>(swipeDismiss = swipeDismiss) {
                            MainScreen(state = state, openLoansTick = openLoansTick)
                        }
                        entry<Route.AddTransaction>(swipeDismiss = swipeDismiss) { key ->
                            TransactionEditPage(transactionId = key.id, onBack = { navigator.pop() })
                        }
                        entry<Route.AddLoan>(swipeDismiss = swipeDismiss) { key ->
                            LoanEditPage(loanId = key.id, onBack = { navigator.pop() })
                        }
                        entry<Route.LoanDetail>(swipeDismiss = swipeDismiss) { key ->
                            LoanDetailPage(
                                loanId = key.id,
                                onBack = { navigator.pop() },
                                onEdit = { navigator.push(Route.AddLoan(key.id)) },
                            )
                        }
                        entry<Route.ColorPalette>(swipeDismiss = swipeDismiss) {
                            ColorPaletteScreen()
                        }
                    }
                }
            }
        }
    }

    // 回到前台时顺带检查一次到期借款（不改变每日闹钟）。
    LaunchedEffect(state.loans.size, state.settings.loanReminders) {
        if (state.settings.loanReminders) {
            LoanReminder.notifyDueLoans(context)
        }
    }
}

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> { error("LocalMainPagerState not provided") }

@Composable
fun MainScreen(
    state: FinanceState,
    openLoansTick: Int,
) {
    val navigator = LocalNavigator.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val mainPagerState = rememberMainPagerState(pagerState)
    val today = LocalDate.now()

    LaunchedEffect(openLoansTick) {
        if (openLoansTick > 0) {
            mainPagerState.animateToPage(2)
        }
    }

    // 页面动画被中断时（例如弹窗、返回手势），让底栏选中态与真实页面对齐。
    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val contentReady = rememberContentReady()

    MainScreenBackHandler(mainPagerState, navigator)

    CompositionLocalProvider(LocalMainPagerState provides mainPagerState) {
        Scaffold(
            bottomBar = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    BottomBar(
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                    )
                }
            },
            popupHost = { },
        ) { innerPadding ->
            val bottomPadding = innerPadding.calculateBottomPadding()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 仅常规 NavigationBar 的模糊需要这层 backdrop；悬浮底栏使用自己的 backdrop，
                    // 避免同时录制两层全屏纹理造成掉帧。
                    .then(
                        if (blurBackdrop != null && !enableFloatingBottomBar) {
                            Modifier.layerBackdrop(blurBackdrop)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = if (contentReady) 1 else 0,
                    modifier = Modifier
                        .fillMaxSize()
                        // 悬浮底栏 / 液态玻璃需要采样页面内容，否则 backdrop 为空会渲染成黑色。
                        .then(
                            if (enableFloatingBottomBar && enableFloatingBottomBarBlur) {
                                Modifier.layerBackdrop(backdrop)
                            } else {
                                Modifier
                            },
                        ),
                ) { page ->
                    when (page) {
                        0 -> HomePage(
                            state = state,
                            today = today,
                            bottomInnerPadding = bottomPadding,
                            onOpenBills = { mainPagerState.animateToPage(1) },
                            onOpenLoans = { mainPagerState.animateToPage(2) },
                            onAddTransaction = { navigator.push(Route.AddTransaction()) },
                            onAddLoan = { navigator.push(Route.AddLoan()) },
                            onEditBudget = { mainPagerState.animateToPage(3) },
                            onOpenLoanDetail = { navigator.push(Route.LoanDetail(it)) },
                        )

                        1 -> BillsPage(
                            state = state,
                            today = today,
                            bottomInnerPadding = bottomPadding,
                            onAddTransaction = { navigator.push(Route.AddTransaction()) },
                            onEditTransaction = { navigator.push(Route.AddTransaction(it)) },
                        )

                        2 -> LoansPage(
                            state = state,
                            today = today,
                            bottomInnerPadding = bottomPadding,
                            onAddLoan = { navigator.push(Route.AddLoan()) },
                            onOpenLoan = { navigator.push(Route.LoanDetail(it)) },
                        )

                        else -> SettingsPage(
                            state = state,
                            bottomInnerPadding = bottomPadding,
                            onOpenTheme = { navigator.push(Route.ColorPalette) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navController: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navController.current() is Route.Main &&
                navController.backStackSize() == 1 &&
                mainState.selectedPage != 0
        }
    }
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = { mainState.animateToPage(0) },
    )
}
