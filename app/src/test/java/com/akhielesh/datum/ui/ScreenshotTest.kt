package com.akhielesh.datum.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.akhielesh.datum.core.data.AppSettings
import com.akhielesh.datum.ui.components.DockItem
import com.akhielesh.datum.ui.components.MagnifyingDock
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.core.sensors.UpVector
import com.akhielesh.datum.ui.screens.home.HomeScreen
import com.akhielesh.datum.ui.screens.level.LevelScreen
import com.akhielesh.datum.ui.screens.ruler.RulerScreen
import com.akhielesh.datum.ui.screens.angle.AngleScreen
import com.akhielesh.datum.ui.screens.stud.StudScreen
import com.akhielesh.datum.ui.screens.compass.CompassScreen
import com.akhielesh.datum.ui.screens.light.LightScreen
import com.akhielesh.datum.ui.screens.sound.SoundScreen
import com.akhielesh.datum.ui.screens.vibration.VibrationScreen
import com.akhielesh.datum.ui.screens.sensors.SensorsScreen
import com.akhielesh.datum.ui.screens.settings.SettingsScreen
import com.akhielesh.datum.ui.screens.settings.CalibrationScreen
import com.akhielesh.datum.ui.screens.settings.AboutScreen
import com.akhielesh.datum.ui.screens.library.LibraryScreen
import com.akhielesh.datum.ui.screens.height.HeightScreen
import com.akhielesh.datum.ui.screens.objects.ObjectMotionScreen
import com.akhielesh.datum.ui.screens.objects.ObjectPhotoScreen
import com.akhielesh.datum.ui.nav.Navigator
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.screens.objects.ObjectDesignScreen
import com.akhielesh.datum.ui.screens.objects.ObjectHubScreen
import com.akhielesh.datum.core.data.ObjectDraft
import com.akhielesh.datum.core.data.DimEstimate
import com.akhielesh.datum.core.data.Dim
import com.akhielesh.datum.core.data.EstimateSource
import com.akhielesh.datum.DatumApp
import androidx.test.core.app.ApplicationProvider
import com.akhielesh.datum.ui.screens.objects.ObjectViewport
import com.akhielesh.datum.ui.screens.objects.OrbitCamera
import com.akhielesh.datum.ui.screens.objects.RenderStyle
import com.akhielesh.datum.core.data.ShapeKind
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.remember
import com.akhielesh.datum.ui.screens.slide.SlideScreen
import com.akhielesh.datum.core.sensors.MotionSnapshot
import com.akhielesh.datum.core.sensors.MotionTracker
import com.akhielesh.datum.core.math.Vec3
import com.akhielesh.datum.ui.screens.onboarding.OnboardingScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.ui.screens.objects.EdgeGuide
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild

/**
 * Renders key screens to PNG (app/build/screenshots) for visual review. Run with
 * `./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*' -Proborazzi.test.record=true`.
 * Device: Galaxy S22 logical size (411 × 891 dp at 420 dpi).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/screenshots/$name.png")

    /** Render as a Galaxy S22 so the catalogued device profile (name, body size, panel) is used. */
    @Before
    fun galaxyS22() {
        ShadowBuild.setManufacturer("samsung")
        ShadowBuild.setBrand("samsung")
        ShadowBuild.setModel("SM-S901B")
    }

    @Test
    fun homeLight() {
        compose.setContent { ScreenHarness(dark = false) { HomeScreen() } }
        shot("home_light")
    }

    @Test
    fun homeDark() {
        compose.setContent { ScreenHarness(dark = true) { HomeScreen() } }
        shot("home_dark")
    }

    @Test
    fun onboardingLight() {
        compose.setContent { ScreenHarness(dark = false, settings = AppSettings()) { OnboardingScreen() } }
        shot("onboarding_light")
    }

    private fun up(x: Double, y: Double, z: Double): UpVector {
        val n = kotlin.math.sqrt(x * x + y * y + z * z)
        return UpVector(x / n, y / n, z / n, 0L, 9.81)
    }

    @Test
    fun levelSurface() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ScreenHarness(dark = false) { LevelScreen(previewUp = up(0.045, 0.03, 1.0)) } }
        compose.mainClock.advanceTimeBy(2500)
        shot("level_surface_light")
    }

    @Test
    fun levelEdgeDark() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ScreenHarness(dark = true) { LevelScreen(previewUp = up(0.07, 1.0, 0.25)) } }
        compose.mainClock.advanceTimeBy(2500)
        shot("level_edge_dark")
    }

    @Test
    fun slideLight() {
        compose.mainClock.autoAdvance = false
        val snap = MotionSnapshot(
            phase = MotionTracker.Phase.STILL,
            position = Vec3(0.004, 0.234, 0.0),
            livePosition = Vec3(0.004, 0.234, 0.0),
            sigma = 0.0062,
            segmentCount = 2,
            sampleRateHz = 416.0,
            running = true,
        )
        compose.setContent { ScreenHarness(dark = false) { SlideScreen(preview = snap) } }
        compose.mainClock.advanceTimeBy(2000)
        shot("slide_light")
    }

    @Test
    fun renderer() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ScreenHarness(dark = false) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        ObjectViewport(ShapeKind.BOX, 0.42, 0.31, 0.25, remember { OrbitCamera() }, Modifier.weight(1f).fillMaxSize(), material = "Cardboard box")
                        ObjectViewport(ShapeKind.BOX, 0.42, 0.31, 0.25, remember { OrbitCamera() }, Modifier.weight(1f).fillMaxSize(), style = RenderStyle.Blueprint)
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        ObjectViewport(ShapeKind.CYLINDER, 0.12, 0.12, 0.30, remember { OrbitCamera() }, Modifier.weight(1f).fillMaxSize(), material = "Aluminium")
                        ObjectViewport(ShapeKind.BOX, 1.2, 0.6, 0.75, remember { OrbitCamera() }, Modifier.weight(1f).fillMaxSize(), style = RenderStyle.XRay)
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        ObjectViewport(ShapeKind.SPHERE, 0.22, 0.22, 0.22, remember { OrbitCamera() }, Modifier.weight(1f).fillMaxSize(), material = "Gold")
                        ObjectViewport(ShapeKind.CYLINDER, 0.4, 0.4, 0.1, remember { OrbitCamera() }, Modifier.weight(1f).fillMaxSize(), style = RenderStyle.Blueprint)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        shot("renderer")
    }

    private fun sampleDraft() {
        val app = ApplicationProvider.getApplicationContext<DatumApp>().container
        app.objectDraft.load(
            ObjectDraft(
                name = "Moving box",
                estimates = listOf(
                    DimEstimate(Dim.L, 0.423, 0.009, EstimateSource.AR),
                    DimEstimate(Dim.W, 0.312, 0.008, EstimateSource.AR),
                    DimEstimate(Dim.H, 0.248, 0.007, EstimateSource.AR),
                    DimEstimate(Dim.L, 0.417, 0.018, EstimateSource.MOTION, "corners"),
                    DimEstimate(Dim.H, 0.251, 0.004, EstimateSource.PHOTO, "Bank card"),
                ),
            ),
        )
    }

    @Test
    fun objectHub() {
        sampleDraft()
        compose.mainClock.autoAdvance = false
        compose.setContent { ScreenHarness(dark = false) { ObjectHubScreen() } }
        compose.mainClock.advanceTimeBy(1500)
        shot("object_hub_light")
    }

    @Test
    fun objectDesignLight() {
        sampleDraft()
        compose.mainClock.autoAdvance = false
        compose.setContent { ScreenHarness(dark = false) { ObjectDesignScreen() } }
        compose.mainClock.advanceTimeBy(1500)
        shot("object_design_light")
    }

    @Test
    fun objectDesignDark() {
        sampleDraft()
        compose.mainClock.autoAdvance = false
        compose.setContent { ScreenHarness(dark = true) { ObjectDesignScreen() } }
        compose.mainClock.advanceTimeBy(1500)
        shot("object_design_dark")
    }

    private fun render(name: String, dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { ScreenHarness(dark = dark) { content() } }
        compose.mainClock.advanceTimeBy(1200)
        shot(name)
    }

    @Test fun ruler() = render("ruler_light") { RulerScreen() }
    @Test fun angle() = render("angle_dark", dark = true) { AngleScreen() }
    @Test fun stud() = render("stud_light") { StudScreen() }
    @Test fun compass() = render("compass_light") { CompassScreen() }
    @Test fun light() = render("light_light") { LightScreen() }
    @Test fun sound() = render("sound_light") { SoundScreen() }
    @Test fun vibration() = render("vibration_dark", dark = true) { VibrationScreen() }
    @Test fun sensors() = render("sensors_light") { SensorsScreen() }
    @Test fun settings() = render("settings_light") { SettingsScreen() }
    @Test fun settingsDark() = render("settings_dark", dark = true) { SettingsScreen() }
    @Test fun calibration() = render("calibration_light") { CalibrationScreen() }
    @Test fun about() = render("about_light") { AboutScreen() }
    @Test fun library() = render("library_light") { LibraryScreen() }
    @Test fun height() = render("height_light") { HeightScreen() }
    @Test fun corners() = render("corners_light") { ObjectMotionScreen(edges = false) }
    @Test fun edges() = render("edges_dark", dark = true) { ObjectMotionScreen(edges = true) }
    @Test fun photo() = render("photo_light") { ObjectPhotoScreen() }

    @Test
    fun edgeGuides() = render("edge_guides") {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val mod = Modifier.fillMaxWidth().height(210.dp).card(Shapes.xl)
            EdgeGuide(ShapeKind.BOX, listOf(Dim.L, Dim.W, Dim.H), 1, mapOf(Dim.L to 0.62), null, com.akhielesh.datum.core.units.UnitSystem.METRIC, mod)
            EdgeGuide(ShapeKind.CYLINDER, listOf(Dim.L, Dim.H), 1, mapOf(Dim.L to 0.11), 0.164, com.akhielesh.datum.core.units.UnitSystem.METRIC, mod)
            EdgeGuide(ShapeKind.SPHERE, listOf(Dim.L), 1, mapOf(Dim.L to 0.22), null, com.akhielesh.datum.core.units.UnitSystem.IMPERIAL, mod)
        }
    }

    @Test
    fun appShellWithDock() {
        val app = ApplicationProvider.getApplicationContext<DatumApp>().container
        app.settings.update { it.copy(onboardingDone = true) }
        compose.waitUntil(5_000) { app.settings.settings.value.onboardingDone }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                com.akhielesh.datum.ui.screens.ar.LocalArChecker provides { _ -> com.google.ar.core.ArCoreApk.Availability.SUPPORTED_INSTALLED },
            ) {
                AppRoot(app, remember { Navigator(Route.Level) })
            }
        }
        compose.mainClock.advanceTimeBy(2500)
        shot("app_shell_level")
    }

    @Test
    fun dock() {
        compose.setContent {
            ScreenHarness(dark = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    MagnifyingDock(
                        listOf(
                            DockItem("Home", DatumIcons.Grid, ToolColors.home),
                            DockItem("Level", Tool.Level.icon, Tool.Level.colors),
                            DockItem("Slide", Tool.Slide.icon, Tool.Slide.colors),
                            DockItem("Height", Tool.Height.icon, Tool.Height.colors),
                            DockItem("Object", Tool.Object.icon, Tool.Object.colors),
                        ),
                        selected = 1,
                        onSelect = {},
                    )
                }
            }
        }
        shot("dock")
    }
}
