package com.akhielesh.datum.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.Route

/** Every tool in the app, with its identity (icon, gradient) used consistently everywhere. */
enum class Tool(
    val title: String,
    val tagline: String,
    val iconRef: () -> ImageVector,
    val colors: List<Color>,
    val route: Route,
) {
    Object("Object", "3D size from corners, edges & photos", { DatumIcons.Cube }, listOf(Color(0xFFA77BFF), Color(0xFF6A3DF0)), Route.ObjectHub),
    ArMeasure("AR Measure", "Tape, path, area & height in AR", { DatumIcons.Ar }, listOf(Color(0xFFFF7A95), Color(0xFFFF2D55)), Route.ArMeasure),
    Slide("Slide", "Slide along a surface to measure", { DatumIcons.Slide }, listOf(Color(0xFFFFB547), Color(0xFFFF8A00)), Route.Slide),
    Height("Height", "Sight, slide-up or barometer", { DatumIcons.Height }, listOf(Color(0xFF5AC8FA), Color(0xFF1E8FEA)), Route.Height),
    Ruler("Ruler", "True-scale screen ruler & calipers", { DatumIcons.Ruler }, listOf(Color(0xFFFFC233), Color(0xFFF29D00)), Route.Ruler),
    Level("Level", "Surface & edge spirit level", { DatumIcons.Level }, listOf(Color(0xFF62D87A), Color(0xFF22B046)), Route.Level),
    Angle("Angle", "Bevel gauge, inclinometer, protractor", { DatumIcons.Angle }, listOf(Color(0xFF4FD1E8), Color(0xFF1BA3C0)), Route.Angle),
    Compass("Compass", "Heading, bearing & field strength", { DatumIcons.Compass }, listOf(Color(0xFFFF6B63), Color(0xFFE8352B)), Route.Compass),
    Stud("Stud Finder", "Find screws & nails behind walls", { DatumIcons.Magnet }, listOf(Color(0xFFA1A1A8), Color(0xFF5E5E64)), Route.Stud),
    Light("Light", "Lux, exposure & plant light", { DatumIcons.Light }, listOf(Color(0xFFFFD34D), Color(0xFFFF9F0A)), Route.Light),
    Sound("Sound", "Noise level meter in dB", { DatumIcons.Sound }, listOf(Color(0xFF6CCBFF), Color(0xFF0A84FF)), Route.Sound),
    Vibration("Vibration", "Seismograph & frequency analyser", { DatumIcons.Vibration }, listOf(Color(0xFFCB7BF5), Color(0xFF8E44D9)), Route.Vibration),
    Library("Library", "Everything you've saved", { DatumIcons.Library }, listOf(Color(0xFF8A87FF), Color(0xFF5856D6)), Route.Library),
    Sensors("Sensors", "What this phone can feel", { DatumIcons.Chip }, listOf(Color(0xFF7FD1C9), Color(0xFF1F9E94)), Route.Sensors),
    Settings("Settings", "Appearance, units & calibration", { DatumIcons.Settings }, listOf(Color(0xFFA9A9AF), Color(0xFF6E6E73)), Route.Settings),
    ;

    val icon: ImageVector get() = iconRef()
}

object ToolColors {
    val home = listOf(Color(0xFF4FA3FF), Color(0xFF2F6BF2))
}
