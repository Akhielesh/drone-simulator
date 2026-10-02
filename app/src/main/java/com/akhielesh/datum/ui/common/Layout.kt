package com.akhielesh.datum.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.ui.components.ButtonStyle
import com.akhielesh.datum.ui.components.CircleButton
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.theme.Datum

/** Bottom padding that keeps controls clear of the floating dock (tab screens). */
@Composable
fun Modifier.aboveDock(): Modifier = this
    .windowInsetsPadding(WindowInsets.navigationBars)
    .padding(bottom = 96.dp)

/** Bottom padding for pushed screens without a dock. */
@Composable
fun Modifier.aboveNavBar(): Modifier = this
    .windowInsetsPadding(WindowInsets.navigationBars)
    .padding(bottom = 14.dp)

/** Toolbar button: round glass icon with a caption, tinted when active. */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = Datum.colors.accent,
    enabled: Boolean = true,
    onDark: Boolean = false,
) {
    val c = Datum.colors
    val labelColor by animateColorAsState(
        when {
            active -> activeColor
            onDark -> Color.White.copy(alpha = 0.85f)
            else -> c.labelSecondary
        },
        tween(200), label = "toolLabel",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        CircleButton(
            icon, onClick,
            size = 52.dp,
            style = if (active) ButtonStyle.Tinted else ButtonStyle.Glass,
            tint = if (active) activeColor else if (onDark) Color.White else null,
            enabled = enabled,
            iconSize = 23.dp,
        )
        DText(label, Datum.type.caption, labelColor, maxLines = 1)
    }
}
