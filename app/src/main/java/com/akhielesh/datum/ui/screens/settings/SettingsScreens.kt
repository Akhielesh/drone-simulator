package com.akhielesh.datum.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.BuildConfig
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.sensors.LevelCalibration
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.Tool
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.components.AccentPicker
import com.akhielesh.datum.ui.components.AppearancePicker
import com.akhielesh.datum.ui.components.ButtonStyle
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.IconTile
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.MacSlider
import com.akhielesh.datum.ui.components.MacSwitch
import com.akhielesh.datum.ui.components.NumberField
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.SectionCard
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.SettingsRow
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.screens.ruler.CardCalibrationSheet
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card

private val blue = listOf(Color(0xFF4FA3FF), Color(0xFF2F6BF2))
private val green = listOf(Color(0xFF62D87A), Color(0xFF22B046))
private val orange = listOf(Color(0xFFFFB547), Color(0xFFFF8A00))
private val pink = listOf(Color(0xFFFF7A95), Color(0xFFFF2D55))
private val purple = listOf(Color(0xFFA77BFF), Color(0xFF6A3DF0))
private val gray = listOf(Color(0xFFA9A9AF), Color(0xFF6E6E73))

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val s = LocalSettings.current
    val c = Datum.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = { nav.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().card(Shapes.xl).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppMark(Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    DText("Datum", Datum.type.title2)
                    DText("Version ${BuildConfig.VERSION_NAME} · ${app.device.marketingName}", Datum.type.footnote, c.labelSecondary)
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionCard(title = "Appearance") {
                Column(Modifier.padding(16.dp)) {
                    AppearancePicker(s.themeMode, onChange = { m -> app.settings.update { it.copy(themeMode = m) } })
                    Spacer(Modifier.height(18.dp))
                    DText("Accent colour", Datum.type.subhead, c.labelSecondary, Modifier.padding(bottom = 10.dp))
                    AccentPicker(s.accent, onChange = { a -> app.settings.update { it.copy(accent = a) } })
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionCard(title = "Units") {
                Column(Modifier.padding(14.dp)) {
                    SegmentedControl(
                        listOf("Metric", "Imperial"), if (s.units == UnitSystem.METRIC) 0 else 1,
                        onSelect = { i -> app.settings.update { it.copy(units = if (i == 0) UnitSystem.METRIC else UnitSystem.IMPERIAL) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsRow("Inch fractions", subtitle = "Show 3⅜″ instead of 3.38″", icon = DatumIcons.Ruler, iconColors = orange, showDivider = false) {
                    MacSwitch(s.inchFractions, { v -> app.settings.update { it.copy(inchFractions = v) } })
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionCard(title = "Feedback") {
                SettingsRow("Haptics", subtitle = "Ruler detents, captures, level lock", icon = DatumIcons.Haptic, iconColors = pink) {
                    MacSwitch(s.haptics, { v -> app.settings.update { it.copy(haptics = v) } })
                }
                SettingsRow("Sounds", subtitle = "Soft capture and success tones", icon = DatumIcons.Speaker, iconColors = blue, showDivider = false) {
                    MacSwitch(s.sounds, { v -> app.settings.update { it.copy(sounds = v) } })
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionCard(title = "Capture", footer = "Pressing a volume key captures without touching (and shaking) the phone.") {
                SettingsRow("Auto-capture corners", subtitle = "Capture when you hold still", icon = DatumIcons.Corner, iconColors = purple) {
                    MacSwitch(s.autoCapture, { v -> app.settings.update { it.copy(autoCapture = v) } })
                }
                SettingsRow("Volume keys as shutter", icon = DatumIcons.Volume, iconColors = gray, showDivider = false) {
                    MacSwitch(s.volumeKeyCapture, { v -> app.settings.update { it.copy(volumeKeyCapture = v) } })
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionCard(title = "Accuracy") {
                SettingsRow("Calibration", subtitle = "Level, distance, ruler, sight, barometer, sound", icon = DatumIcons.Sparkles, iconColors = green, onClick = { nav.push(Route.Calibration) }, showDivider = false)
            }
            Spacer(Modifier.height(20.dp))
            SectionCard {
                SettingsRow("Sensors", subtitle = "Everything this phone can feel", icon = DatumIcons.Chip, iconColors = Tool.Sensors.colors, onClick = { nav.push(Route.Sensors) })
                SettingsRow("Library", icon = DatumIcons.Library, iconColors = Tool.Library.colors, onClick = { nav.push(Route.Library) })
                SettingsRow("About Datum", icon = DatumIcons.Info, iconColors = gray, onClick = { nav.push(Route.About) })
                SettingsRow("Show welcome again", icon = DatumIcons.Sparkles, iconColors = orange, showDivider = false, onClick = {
                    app.settings.update { it.copy(onboardingDone = false) }
                })
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
fun CalibrationScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val hud = LocalHud.current
    val s = LocalSettings.current
    val c = Datum.colors
    var showCard by remember { mutableStateOf(false) }
    var eye by remember { mutableFloatStateOf((s.eyeHeightM * 100).toFloat()) }
    var height by remember { mutableFloatStateOf(s.userHeightCm.toFloat()) }
    val device = app.device
    var bodyL by remember { mutableStateOf("%.1f".format(device.heightMm)) }
    var bodyW by remember { mutableStateOf("%.1f".format(device.widthMm)) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Calibration", onBack = { nav.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionCard(title = "Level", footer = "Two-point reversal: Level › ✦ calibrate.") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    val lc = s.levelCalibration
                    KeyValueRow("Surface offset", "%.4f · %.4f".format(lc.biasX, lc.biasY))
                    KeyValueRow("Edge offset", "%.2f°".format(lc.edgeOffsetDeg))
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Calibrate", onClick = { nav.switchTo(Route.Level) }, compact = true, style = ButtonStyle.Tinted)
                        PillButton("Reset", onClick = {
                            app.settings.update { it.copy(levelCalibration = LevelCalibration()) }
                            hud.show(DatumIcons.Reset, "Reset")
                        }, compact = true, style = ButtonStyle.Destructive)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Motion distance", footer = "Used by Slide, Height and Object. Calibrate in Slide › ✦ by sliding a known length.") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    KeyValueRow("Scale factor", "×%.3f".format(s.motionScale))
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Calibrate", onClick = { nav.switchTo(Route.Slide) }, compact = true, style = ButtonStyle.Tinted)
                        PillButton("Reset", onClick = { app.settings.update { it.copy(motionScale = 1.0) } }, compact = true, style = ButtonStyle.Destructive)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Screen ruler") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    KeyValueRow("Density", "%.0f ppi".format(app.physicalPpi), hint = "scale ×%.3f".format(s.screenScale))
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Match a bank card", onClick = { showCard = true }, compact = true, style = ButtonStyle.Tinted)
                        PillButton("Reset", onClick = { app.settings.update { it.copy(screenScale = 1.0) } }, compact = true, style = ButtonStyle.Destructive)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Sight measurements") {
                Column(Modifier.padding(16.dp)) {
                    Row { DText("Your height", Datum.type.body, modifier = Modifier.weight(1f)); DText(Fmt.length(height / 100.0, s.units).toString(), Datum.type.headline, c.accent) }
                    MacSlider(height, { height = it }, range = 120f..215f, steps = 95, onValueChangeFinished = { app.settings.update { it.copy(userHeightCm = height.toDouble(), eyeHeightOverrideCm = 0.0) } })
                    Spacer(Modifier.height(10.dp))
                    Row { DText("Phone (eye) height", Datum.type.body, modifier = Modifier.weight(1f)); DText(Fmt.length(eye / 100.0, s.units).toString(), Datum.type.headline, c.accent) }
                    MacSlider(eye, { eye = it }, range = 50f..220f, steps = 170, onValueChangeFinished = { app.settings.update { it.copy(eyeHeightOverrideCm = eye.toDouble()) } })
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Barometer") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    KeyValueRow("Sea-level pressure", "%.1f hPa".format(s.seaLevelPressureHpa))
                    PillButton("Standard atmosphere", onClick = { app.settings.update { it.copy(seaLevelPressureHpa = 1013.25) } }, compact = true, style = ButtonStyle.Tinted, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Sound meter") {
                Column(Modifier.padding(16.dp)) {
                    var off by remember { mutableFloatStateOf(s.soundOffsetDb.toFloat()) }
                    Row { DText("Offset", Datum.type.body, modifier = Modifier.weight(1f)); DText("%+.1f dB".format(off), Datum.type.headline, c.accent) }
                    MacSlider(off, { off = it }, range = -20f..20f, steps = 80, onValueChangeFinished = { app.settings.update { it.copy(soundOffsetDb = off.toDouble()) } })
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Phone body", footer = "Used for “+ phone length” and for the corner lever arm. Edit if you use a thick case.") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(bodyL, { bodyL = it }, Modifier.fillMaxWidth(), suffix = "mm long")
                    NumberField(bodyW, { bodyW = it }, Modifier.fillMaxWidth(), suffix = "mm wide")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Save", onClick = {
                            val l = bodyL.toDoubleOrNull()
                            val w = bodyW.toDoubleOrNull()
                            if (l != null && w != null && l in 50.0..300.0 && w in 30.0..200.0) {
                                app.settings.update { it.copy(bodyLengthOverrideMm = l, bodyWidthOverrideMm = w) }
                                hud.show(DatumIcons.Check, "Saved")
                            }
                        }, compact = true)
                        PillButton("Use profile", onClick = {
                            app.settings.update { it.copy(bodyLengthOverrideMm = 0.0, bodyWidthOverrideMm = 0.0) }
                        }, compact = true, style = ButtonStyle.Tinted)
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
    CardCalibrationSheet(showCard) { showCard = false }
}

@Composable
fun AboutScreen() {
    val nav = LocalNavigator.current
    val c = Datum.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("About", onBack = { nav.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            AppMark(Modifier.size(104.dp))
            Spacer(Modifier.height(14.dp))
            DText("Datum", Datum.type.largeTitle)
            DText("Version ${BuildConfig.VERSION_NAME}", Datum.type.subhead, c.labelSecondary)
            Spacer(Modifier.height(6.dp))
            DText("Measure the world with the sensors in your pocket.", Datum.type.body, c.labelSecondary, align = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            SectionCard(title = "How it measures", modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Fact(DatumIcons.Move, "Inertial odometry", "Accelerometer + gyroscope at up to 400 Hz, rest calibration and zero-velocity drift correction.")
                    Fact(DatumIcons.Ar, "Visual-inertial AR", "ARCore tracking with plane and depth hits for centimetre-level points.")
                    Fact(DatumIcons.Photo, "Photogrammetry", "Homographies from reference objects and perspective rectification without one.")
                    Fact(DatumIcons.Sparkles, "Sensor fusion", "Every measurement is weighted by its uncertainty and combined into one model.")
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Privacy", modifier = Modifier.fillMaxWidth()) {
                DText(
                    "Datum has no accounts, analytics or ads, and doesn't request internet access. Camera, microphone and location are used only while their tools are open, and nothing leaves your phone unless you share it.",
                    Datum.type.callout, modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Acknowledgements", modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    KeyValueRow("Inter typeface", "SIL OFL 1.1")
                    KeyValueRow("Jetpack Compose, AndroidX", "Apache 2.0")
                    KeyValueRow("Haze (glass blur)", "Apache 2.0")
                    KeyValueRow("ARCore SDK", "Google")
                    KeyValueRow("Kotlin", "Apache 2.0")
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun Fact(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row {
        IconTile(icon, listOf(Datum.colors.accent, Datum.colors.accent), size = 30.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            DText(title, Datum.type.headline)
            DText(text, Datum.type.footnote, Datum.colors.labelSecondary)
        }
    }
}

/** The app icon, drawn natively (matches the launcher icon). */
@Composable
fun AppMark(modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        val shape = com.akhielesh.datum.ui.theme.smoothRoundRect(size.width, size.height, size.width * 0.23f, 0.6f)
        drawPath(shape, Brush.linearGradient(listOf(Color(0xFF3F9BFF), Color(0xFF2F6BF2), Color(0xFF5A4FD8))))
        val ring = r * 0.38f
        drawCircle(Color.White, ring, center, style = Stroke(r * 0.065f))
        val t = r * 0.065f
        listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f)).forEach { d ->
            drawLine(Color.White, center + d * (ring + r * 0.1f), center + d * (ring + r * 0.24f), t, StrokeCap.Round)
        }
        drawCircle(Color.White, r * 0.14f, Offset(center.x + r * 0.11f, center.y - r * 0.11f))
    }
}
