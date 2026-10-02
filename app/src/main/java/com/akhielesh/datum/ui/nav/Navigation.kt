package com.akhielesh.datum.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

enum class Tab { Home, Level, Slide, Height, Object }

sealed interface Route {
    /** Routes that live in the dock. */
    val tab: Tab? get() = null

    data object Home : Route { override val tab = Tab.Home }
    data object Level : Route { override val tab = Tab.Level }
    data object Slide : Route { override val tab = Tab.Slide }
    data object Height : Route { override val tab = Tab.Height }
    data object ObjectHub : Route { override val tab = Tab.Object }

    data object ObjectAr : Route
    data class ObjectMotion(val edges: Boolean) : Route
    data object ObjectPhoto : Route
    data object ObjectDesign : Route
    data object ArMeasure : Route
    data object Ruler : Route
    data object Angle : Route
    data object Stud : Route
    data object Compass : Route
    data object Light : Route
    data object Sound : Route
    data object Vibration : Route
    data object Library : Route
    data class LibraryDetail(val id: String) : Route
    data object Sensors : Route
    data object Settings : Route
    data object Calibration : Route
    data object About : Route
}

enum class NavDirection { Forward, Back, Tab }

/** Minimal back stack with direction-aware transitions (push slides in, tabs cross-fade). */
@Stable
class Navigator(start: Route = Route.Home) {
    val stack = mutableStateListOf(start)
    var direction by mutableStateOf(NavDirection.Tab)
        private set

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1 || current != Route.Home

    fun push(route: Route) {
        if (current == route) return
        direction = NavDirection.Forward
        stack.add(route)
    }

    fun back(): Boolean {
        if (stack.size > 1) {
            direction = NavDirection.Back
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (current != Route.Home) {
            direction = NavDirection.Back
            stack[0] = Route.Home
            return true
        }
        return false
    }

    /** Dock selection: replace the stack with the tab's root. */
    fun switchTo(route: Route) {
        if (stack.size == 1 && current == route) return
        direction = NavDirection.Tab
        stack.clear()
        stack.add(route)
    }

    /** Replace the top of the stack (e.g. capture → result). */
    fun replace(route: Route) {
        direction = NavDirection.Forward
        stack[stack.lastIndex] = route
    }
}

val LocalNavigator = staticCompositionLocalOf { Navigator() }

/** Shell chrome that screens may adjust while they are visible. */
@Stable
class ChromeState {
    /** Hide the dock (immersive capture flows). */
    var dockHidden by mutableStateOf(false)

    /** Force light status-bar icons (camera / AR screens are always dark). */
    var darkContent by mutableStateOf(false)
}

val LocalChrome = staticCompositionLocalOf { ChromeState() }
