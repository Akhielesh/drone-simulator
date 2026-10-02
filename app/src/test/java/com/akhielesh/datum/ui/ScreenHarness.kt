package com.akhielesh.datum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.test.core.app.ApplicationProvider
import com.akhielesh.datum.AppContainer
import com.akhielesh.datum.DatumApp
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Accent
import com.akhielesh.datum.core.data.AppSettings
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.components.HudHost
import com.akhielesh.datum.ui.components.HudState
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.nav.ChromeState
import com.akhielesh.datum.ui.screens.ar.LocalArChecker
import com.google.ar.core.ArCoreApk
import com.akhielesh.datum.ui.nav.LocalChrome
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.nav.Navigator
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.DatumTheme

/** Renders a screen with every CompositionLocal the app provides, without the activity. */
@Composable
fun ScreenHarness(
    dark: Boolean,
    settings: AppSettings = AppSettings(onboardingDone = true),
    accent: Accent = Accent.MULTICOLOR,
    hud: HudState = remember { HudState() },
    content: @Composable () -> Unit,
) {
    val container: AppContainer = (ApplicationProvider.getApplicationContext<DatumApp>()).container
    DatumTheme(dark = dark, accent = accent) {
        CompositionLocalProvider(
            LocalApp provides container,
            LocalSettings provides settings,
            LocalHud provides hud,
            LocalNavigator provides remember { Navigator() },
            LocalChrome provides remember { ChromeState() },
            LocalArChecker provides { _ -> ArCoreApk.Availability.SUPPORTED_INSTALLED },
        ) {
            val c = Datum.colors
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.backgroundTop, c.background)))) {
                content()
                HudHost(hud)
            }
        }
    }
}
