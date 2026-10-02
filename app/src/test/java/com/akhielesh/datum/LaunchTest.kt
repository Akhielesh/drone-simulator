package com.akhielesh.datum

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cold start through the real Application, manifest, theme and Activity, as a fresh sideloaded
 * install would. Driven by Compose's test clock: the UI has endless animations (the onboarding logo
 * spins), which never let a plain looper "idle" finish.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LaunchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun settle(ms: Long = 800) = rule.mainClock.advanceTimeBy(ms)

    @Test
    fun firstRunWalksThroughOnboardingAndSurvivesRecreate() {
        rule.mainClock.autoAdvance = false
        settle()
        rule.onNodeWithText("Welcome to Datum").assertExists()
        rule.onNodeWithText("Continue").performClick()
        settle()
        rule.onNodeWithText("Make it yours").assertExists()
        rule.onNodeWithText("Continue").performClick()
        settle()
        rule.onNodeWithText("One more thing").assertExists()
        rule.onNodeWithText("Start measuring").assertExists()

        rule.activityRule.scenario.recreate()
        settle()
        assertFalse(rule.activity.isFinishing)
        rule.onNodeWithText("Welcome to Datum").assertExists()
    }

    @Test
    fun volumeKeysOnlyActAsShutterWhileACaptureScreenListens() {
        rule.mainClock.autoAdvance = false
        settle(300)
        val activity = rule.activity
        val keys = (activity.application as DatumApp).container.keys
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN)
        // Nobody listening: the system keeps its normal volume behaviour.
        assertFalse(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, down))
        var shots = 0
        val unregister = keys.register { shots++; true }
        assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, down))
        assertTrue(activity.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN, up))
        assertEquals(1, shots)
        unregister()
    }
}
