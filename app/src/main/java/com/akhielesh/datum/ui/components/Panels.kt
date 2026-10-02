package com.akhielesh.datum.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.GlassWeight
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import com.akhielesh.datum.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Window-style toolbar: back chevron, centred title (+ subtitle), trailing actions. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 14.dp),
    ) {
        if (onBack != null) {
            CircleButton(DatumIcons.ChevronLeft, onBack, Modifier.align(Alignment.CenterStart), size = 38.dp)
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            DText(title, Datum.type.headline, maxLines = 1)
            if (subtitle != null) {
                AnimatedContent(subtitle, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "sub") {
                    DText(it, Datum.type.caption, Datum.colors.labelSecondary, maxLines = 1)
                }
            }
        }
        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    weight: GlassWeight = GlassWeight.Regular,
    shape: androidx.compose.ui.graphics.Shape = Shapes.xl,
    padding: Dp = 16.dp,
    elevation: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .glass(shape, weight, elevation = elevation)
            .padding(padding),
        content = content,
    )
}

/** Grouped list section (System Settings style), with an optional header and footer. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        if (title != null) {
            DText(title.uppercase(), Datum.type.sectionLabel, Datum.colors.labelSecondary, Modifier.padding(start = 16.dp, bottom = 7.dp))
        }
        Column(Modifier.fillMaxWidth().card(Shapes.lg), content = content)
        if (footer != null) {
            DText(footer, Datum.type.footnote, Datum.colors.labelSecondary, Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))
        }
    }
}

/** Row inside a [SectionCard]: colored icon tile, title/subtitle, trailing control or chevron. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColors: List<Color>? = null,
    subtitle: String? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Datum.colors
    Column {
        Row(
            modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick) else it }
                .heightIn(min = 50.dp)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconTile(icon, iconColors ?: listOf(c.accent, c.accent), size = 30.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                DText(title, Datum.type.body, maxLines = 1)
                if (subtitle != null) DText(subtitle, Datum.type.footnote, c.labelSecondary, maxLines = 2)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = trailing)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                DIcon(DatumIcons.ChevronRight, tint = c.labelTertiary, size = 15.dp)
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .padding(start = if (icon != null) 56.dp else 14.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(c.separator),
            )
        }
    }
}

/** Label/value line for metric tables. */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, unit: String = "", accent: Color? = null, hint: String? = null) {
    val c = Datum.colors
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            DText(label, Datum.type.callout, c.labelSecondary, maxLines = 1)
            if (hint != null) DText(hint, Datum.type.caption, c.labelTertiary, maxLines = 1)
        }
        DText(value, Datum.type.headline.copy(fontFeatureSettings = "tnum"), accent ?: c.label, maxLines = 1)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            DText(unit, Datum.type.callout, c.labelSecondary, maxLines = 1)
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(Datum.colors.separator))
}

/** Instruction bubble that cross-fades between steps. */
@Composable
fun CoachBubble(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = Datum.colors.accent) {
    AnimatedContent(
        targetState = text to icon,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }) togetherWith
                (fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 3 })
        },
        modifier = modifier,
        label = "coach",
    ) { (t, ic) ->
        Row(
            Modifier
                .glass(Shapes.pill, GlassWeight.Thick, elevation = 6.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (ic != null) DIcon(ic, tint = tint, size = 18.dp)
            DText(t, Datum.type.subhead, maxLines = 2)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Datum.colors
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(64.dp).clip(Shapes.pill).background(c.fillTertiary), contentAlignment = Alignment.Center) {
            DIcon(icon, tint = c.labelSecondary, size = 30.dp)
        }
        DText(title, Datum.type.title3, align = TextAlign.Center)
        DText(message, Datum.type.callout, c.labelSecondary, align = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** Numeric text field styled like a macOS input. */
@Composable
fun NumberField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String = "", placeholder: String = "0") {
    val c = Datum.colors
    Row(
        modifier
            .clip(Shapes.sm)
            .background(c.fillTertiary)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) DText(placeholder, Datum.type.body, c.labelTertiary)
            BasicTextField(
                value = value,
                onValueChange = { s -> onValueChange(s.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')) },
                textStyle = Datum.type.body.copy(color = c.label, fontFeatureSettings = "tnum"),
                singleLine = true,
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (suffix.isNotEmpty()) DText(suffix, Datum.type.callout, c.labelSecondary)
    }
}

@Composable
fun TextInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") {
    val c = Datum.colors
    Box(
        modifier
            .clip(Shapes.sm)
            .background(c.fillTertiary)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty()) DText(placeholder, Datum.type.body, c.labelTertiary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = Datum.type.body.copy(color = c.label),
            singleLine = true,
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ------------------------------------------------------------------------------------------------
// HUD — the translucent rounded square macOS shows for volume/brightness, reused for confirmations.

data class HudMessage(val icon: ImageVector, val text: String, val id: Long = System.nanoTime())

@Stable
class HudState {
    var message by mutableStateOf<HudMessage?>(null)
        private set

    fun show(icon: ImageVector, text: String) {
        message = HudMessage(icon, text)
    }

    fun dismiss() {
        message = null
    }
}

val LocalHud = staticCompositionLocalOf { HudState() }

@Composable
fun HudHost(state: HudState, modifier: Modifier = Modifier) {
    val msg = state.message
    LaunchedEffect(msg?.id) {
        if (msg != null) {
            delay(1400)
            state.dismiss()
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = msg != null,
            enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.65f, stiffness = 500f), initialScale = 0.8f),
            exit = fadeOut(tween(280)) + scaleOut(tween(280), targetScale = 0.92f),
        ) {
            val shown = remember { mutableStateOf(msg) }
            if (msg != null) shown.value = msg
            val m = shown.value ?: return@AnimatedVisibility
            Column(
                Modifier
                    .size(168.dp)
                    .glass(Shapes.xl, GlassWeight.Thick, elevation = 18.dp)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DIcon(m.icon, tint = Datum.colors.label, size = 54.dp)
                Spacer(Modifier.height(14.dp))
                DText(m.text, Datum.type.headline, align = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Bottom sheet — frosted panel with a grabber; drag down or tap outside to dismiss.

@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    if (visible) BackHandler(onBack = onDismiss)
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(tween(150)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
        ) {
            Column(
                Modifier
                    .offset { IntOffset(0, drag.value.roundToInt()) }
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .glass(Shapes.xxl, GlassWeight.Thick, elevation = 24.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (drag.value > 120f) {
                                        onDismiss()
                                    } else {
                                        drag.animateTo(0f, spring(dampingRatio = 0.7f))
                                    }
                                }
                            },
                        ) { _, dy -> scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } }
                    }
                    .padding(horizontal = 18.dp),
            ) {
                Box(
                    Modifier
                        .padding(top = 9.dp, bottom = 8.dp)
                        .size(38.dp, 5.dp)
                        .clip(Shapes.pill)
                        .background(Datum.colors.labelTertiary)
                        .align(Alignment.CenterHorizontally),
                )
                if (title != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DText(title, Datum.type.title3, modifier = Modifier.weight(1f))
                        CircleButton(DatumIcons.Close, onDismiss, size = 30.dp, style = ButtonStyle.Tinted, tint = Datum.colors.labelSecondary)
                    }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 18.dp), content = content)
            }
        }
    }
}
