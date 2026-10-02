package com.akhielesh.datum.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.AppContainer
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.findActivity
import com.akhielesh.datum.ui.components.DockItem
import com.akhielesh.datum.ui.components.HudHost
import com.akhielesh.datum.ui.components.HudState
import com.akhielesh.datum.ui.components.LocalHaptics
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.LocalTones
import com.akhielesh.datum.ui.components.MagnifyingDock
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.ChromeState
import com.akhielesh.datum.ui.nav.LocalChrome
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.nav.NavDirection
import com.akhielesh.datum.ui.nav.Navigator
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.nav.Tab
import com.akhielesh.datum.ui.screens.RouteContent
import com.akhielesh.datum.ui.screens.onboarding.OnboardingScreen
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.DatumTheme
import com.akhielesh.datum.ui.theme.LocalHazeState
import com.akhielesh.datum.ui.theme.isDarkTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun AppRoot(container: AppContainer, navigator: Navigator = remember { Navigator() }) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val dark = isDarkTheme(settings.themeMode)
    val hud = remember { HudState() }
    val chrome = remember { ChromeState() }
    DatumTheme(dark = dark, accent = settings.accent) {
        SystemBars(lightIcons = dark || chrome.darkContent)
        CompositionLocalProvider(
            LocalApp provides container,
            LocalSettings provides settings,
            LocalHaptics provides container.haptics,
            LocalTones provides container.tones,
            LocalHud provides hud,
            LocalNavigator provides navigator,
            LocalChrome provides chrome,
        ) {
            val c = Datum.colors
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(c.backgroundTop, c.background))),
            ) {
                AnimatedContent(
                    targetState = settings.onboardingDone,
                    transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                    label = "onboarding",
                ) { done ->
                    if (done) MainShell(navigator, chrome) else OnboardingScreen()
                }
                HudHost(hud)
            }
        }
    }
}

private val dockRoutes = listOf(Route.Home, Route.Level, Route.Slide, Route.Height, Route.ObjectHub)

@Composable
private fun MainShell(navigator: Navigator, chrome: ChromeState) {
    val haze = rememberHazeState()
    BackHandler(enabled = navigator.canGoBack) { navigator.back() }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = navigator.current,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            transitionSpec = { transitionFor(navigator.direction) },
            label = "nav",
        ) { route ->
            RouteContent(route)
        }
        val tab = navigator.current.tab
        AnimatedVisibility(
            visible = tab != null && !chrome.dockHidden,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 10.dp),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it * 2 } + fadeIn(),
            exit = slideOutVertically(tween(220)) { it * 2 } + fadeOut(tween(180)),
        ) {
            val items = remember {
                listOf(
                    DockItem("Home", DatumIcons.Grid, ToolColors.home),
                    DockItem("Level", Tool.Level.icon, Tool.Level.colors),
                    DockItem("Slide", Tool.Slide.icon, Tool.Slide.colors),
                    DockItem("Height", Tool.Height.icon, Tool.Height.colors),
                    DockItem("Object", Tool.Object.icon, Tool.Object.colors),
                )
            }
            val selected = when (tab) {
                Tab.Home -> 0
                Tab.Level -> 1
                Tab.Slide -> 2
                Tab.Height -> 3
                Tab.Object -> 4
                null -> 0
            }
            CompositionLocalProvider(LocalHazeState provides haze) {
                MagnifyingDock(items, selected, onSelect = { navigator.switchTo(dockRoutes[it]) })
            }
        }
    }
}

private fun transitionFor(direction: NavDirection): ContentTransform = when (direction) {
    NavDirection.Forward ->
        (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 3 } + fadeIn(tween(220)))
            .togetherWith(slideOutHorizontally(tween(260)) { -it / 8 } + fadeOut(tween(200)))
    NavDirection.Back ->
        (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { -it / 8 } + fadeIn(tween(220)))
            .togetherWith(slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(180)))
    NavDirection.Tab ->
        (fadeIn(tween(240, delayMillis = 40)) + scaleIn(tween(280), initialScale = 0.975f))
            .togetherWith(fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 1.01f))
}

@Composable
private fun SystemBars(lightIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
        }
    }
}
