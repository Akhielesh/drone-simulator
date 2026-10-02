package com.akhielesh.datum.ui.screens

import androidx.compose.runtime.Composable
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.screens.angle.AngleScreen
import com.akhielesh.datum.ui.screens.ar.ArMeasureScreen
import com.akhielesh.datum.ui.screens.compass.CompassScreen
import com.akhielesh.datum.ui.screens.height.HeightScreen
import com.akhielesh.datum.ui.screens.home.HomeScreen
import com.akhielesh.datum.ui.screens.level.LevelScreen
import com.akhielesh.datum.ui.screens.library.LibraryDetailScreen
import com.akhielesh.datum.ui.screens.library.LibraryScreen
import com.akhielesh.datum.ui.screens.light.LightScreen
import com.akhielesh.datum.ui.screens.objects.ObjectArScreen
import com.akhielesh.datum.ui.screens.objects.ObjectDesignScreen
import com.akhielesh.datum.ui.screens.objects.ObjectHubScreen
import com.akhielesh.datum.ui.screens.objects.ObjectMotionScreen
import com.akhielesh.datum.ui.screens.objects.ObjectPhotoScreen
import com.akhielesh.datum.ui.screens.ruler.RulerScreen
import com.akhielesh.datum.ui.screens.sensors.SensorsScreen
import com.akhielesh.datum.ui.screens.settings.AboutScreen
import com.akhielesh.datum.ui.screens.settings.CalibrationScreen
import com.akhielesh.datum.ui.screens.settings.SettingsScreen
import com.akhielesh.datum.ui.screens.slide.SlideScreen
import com.akhielesh.datum.ui.screens.sound.SoundScreen
import com.akhielesh.datum.ui.screens.stud.StudScreen
import com.akhielesh.datum.ui.screens.vibration.VibrationScreen

@Composable
fun RouteContent(route: Route) {
    when (route) {
        Route.Home -> HomeScreen()
        Route.Level -> LevelScreen()
        Route.Slide -> SlideScreen()
        Route.Height -> HeightScreen()
        Route.ObjectHub -> ObjectHubScreen()
        Route.ObjectAr -> ObjectArScreen()
        is Route.ObjectMotion -> ObjectMotionScreen(route.edges)
        Route.ObjectPhoto -> ObjectPhotoScreen()
        Route.ObjectDesign -> ObjectDesignScreen()
        Route.ArMeasure -> ArMeasureScreen()
        Route.Ruler -> RulerScreen()
        Route.Angle -> AngleScreen()
        Route.Stud -> StudScreen()
        Route.Compass -> CompassScreen()
        Route.Light -> LightScreen()
        Route.Sound -> SoundScreen()
        Route.Vibration -> VibrationScreen()
        Route.Library -> LibraryScreen()
        is Route.LibraryDetail -> LibraryDetailScreen(route.id)
        Route.Sensors -> SensorsScreen()
        Route.Settings -> SettingsScreen()
        Route.Calibration -> CalibrationScreen()
        Route.About -> AboutScreen()
    }
}
