package com.akhielesh.datum.ui.screens.objects

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Dim
import com.akhielesh.datum.core.data.DimEstimate
import com.akhielesh.datum.core.data.EstimateSource
import com.akhielesh.datum.core.data.ShapeKind
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.Tool
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.components.ButtonStyle
import com.akhielesh.datum.ui.components.CircleButton
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.GlassSheet
import com.akhielesh.datum.ui.components.IconTile
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.NumberField
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.components.pressable
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.screens.ar.rememberArAvailability
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card

fun dimName(d: Dim, shape: ShapeKind) = when (shape) {
    ShapeKind.BOX -> when (d) {
        Dim.L -> "Length"
        Dim.W -> "Width"
        Dim.H -> "Height"
    }
    ShapeKind.CYLINDER -> if (d == Dim.H) "Height" else "Diameter"
    ShapeKind.SPHERE -> "Diameter"
}

fun sourceIcon(s: EstimateSource): ImageVector = when (s) {
    EstimateSource.AR -> DatumIcons.Ar
    EstimateSource.MOTION -> DatumIcons.Move
    EstimateSource.PHOTO -> DatumIcons.Photo
    EstimateSource.MANUAL -> DatumIcons.Pencil
}

@Composable
fun ObjectHubScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    val draft by app.objectDraft.draft.collectAsStateWithLifecycle()
    val fused = draft.fused
    val ar = rememberArAvailability()
    val camera = remember { OrbitCamera() }
    var showManual by remember { mutableStateOf(false) }
    Turntable(camera, enabled = true)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TopBar(
                "Object",
                subtitle = if (draft.isEmpty) "Choose how to measure" else "${draft.name} · ${draft.estimates.size + draft.ratios.size} measurements",
            ) {
                if (!draft.isEmpty) {
                    CircleButton(DatumIcons.Trash, onClick = {
                        app.objectDraft.clear()
                        app.haptics.warning()
                        hud.show(DatumIcons.Trash, "Cleared")
                    }, size = 38.dp, tint = c.red)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                // Live preview of what's known so far.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .card(Shapes.xxl, elevated = true)
                        .animateContentSize(),
                ) {
                    val l = fused.l?.value
                    val w = fused.w?.value
                    val h = fused.h?.value
                    val complete = fused.complete
                    ObjectViewport(
                        draft.shape,
                        l ?: 0.42, w ?: 0.31, h ?: 0.25,
                        camera,
                        Modifier.fillMaxSize().padding(bottom = if (complete) 54.dp else 18.dp),
                        style = if (complete) RenderStyle.Solid else RenderStyle.Blueprint,
                        material = draft.material,
                        units = settings.units,
                        showDimensions = !draft.isEmpty,
                        labels = Triple(
                            l?.let { Fmt.lengthShort(it, settings.units) } ?: "L ?",
                            w?.let { Fmt.lengthShort(it, settings.units) } ?: "W ?",
                            h?.let { Fmt.lengthShort(it, settings.units) } ?: "H ?",
                        ),
                    )
                    if (complete) {
                        PillButton(
                            "Open designer", icon = DatumIcons.Sparkles,
                            onClick = { nav.push(Route.ObjectDesign) },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
                            compact = true,
                        )
                    } else if (draft.isEmpty) {
                        DText(
                            "Your object appears here as you measure it",
                            Datum.type.footnote, c.labelSecondary,
                            Modifier.align(Alignment.BottomCenter).padding(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                SegmentedControl(
                    listOf("Box", "Cylinder", "Sphere"), draft.shape.ordinal,
                    onSelect = { app.objectDraft.setShape(ShapeKind.entries[it]) },
                    icons = listOf(DatumIcons.Cube, DatumIcons.Cylinder, DatumIcons.Sphere),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(22.dp))
                DText("MEASURE WITH", Datum.type.sectionLabel, c.labelSecondary, Modifier.padding(start = 6.dp, bottom = 8.dp))
                val arOk = ar?.isSupported == true
                MethodCard(
                    DatumIcons.Ar, Tool.ArMeasure.colors, "AR scan",
                    if (arOk) "Aim at the corners through the camera. Most accurate (about ±1 cm)." else "AR isn't available on this phone.",
                    badge = if (arOk) "Best" else null, enabled = arOk,
                ) { nav.push(Route.ObjectAr) }
                MethodCard(
                    DatumIcons.Corner, listOf(Color(0xFF8A87FF), Color(0xFF5856D6)), "Touch corners",
                    "Press the top of your phone to each corner in turn. Gyro + accelerometer trace the path.",
                ) { nav.push(Route.ObjectMotion(edges = false)) }
                MethodCard(
                    DatumIcons.Edges, Tool.Slide.colors, "Slide edges",
                    "Glide the phone along each edge — length, width, then height.",
                ) { nav.push(Route.ObjectMotion(edges = true)) }
                MethodCard(
                    DatumIcons.Photo, Tool.Height.colors, "Photos",
                    "Add photos with a card or A4 sheet for scale. They refine the size and texture the 3D model.",
                ) { nav.push(Route.ObjectPhoto) }
                MethodCard(
                    DatumIcons.Pencil, Tool.Settings.colors, "Enter manually",
                    "Type a known dimension to combine with your measurements.",
                ) { showManual = true }

                if (!draft.isEmpty) {
                    Spacer(Modifier.height(18.dp))
                    DText("MEASUREMENTS", Datum.type.sectionLabel, c.labelSecondary, Modifier.padding(start = 6.dp, bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().card(Shapes.lg).padding(vertical = 4.dp)) {
                        draft.estimates.forEach { e -> EstimateRow(e, draft.shape, settings.units) { app.objectDraft.removeEstimate(e) } }
                        draft.ratios.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                DIcon(DatumIcons.Photo, tint = c.accent, size = 18.dp)
                                Spacer(Modifier.width(10.dp))
                                DText(
                                    "${dimName(r.a, draft.shape)} = %.3f × ${dimName(r.b, draft.shape).lowercase()}".format(r.ratio),
                                    Datum.type.callout, modifier = Modifier.weight(1f),
                                )
                                CircleButton(DatumIcons.Close, onClick = { app.objectDraft.removeRatio(r) }, size = 26.dp, style = ButtonStyle.Tinted, tint = c.labelSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(130.dp))
            }
        }
        ManualSheet(showManual, onDismiss = { showManual = false })
    }
}

@Composable
private fun MethodCard(
    icon: ImageVector,
    colors: List<Color>,
    title: String,
    text: String,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Datum.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .card(Shapes.lg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, colors, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DText(title, Datum.type.headline)
                if (badge != null) {
                    DText(
                        badge, Datum.type.caption, c.green,
                        Modifier.card(Shapes.pill).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            DText(text, Datum.type.footnote, c.labelSecondary)
        }
        DIcon(DatumIcons.ChevronRight, tint = c.labelTertiary, size = 16.dp)
    }
}

@Composable
private fun EstimateRow(e: DimEstimate, shape: ShapeKind, units: UnitSystem, onRemove: () -> Unit) {
    val c = Datum.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        DIcon(sourceIcon(e.source), tint = c.accent, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            DText("${dimName(e.dim, shape)} · ${e.source.label}" + if (e.note.isNotBlank()) " · ${e.note}" else "", Datum.type.subhead, c.labelSecondary)
            DText("${Fmt.length(e.value, units)}  ± ${Fmt.length(e.sigma, units)}", Datum.type.headline.copy(fontFeatureSettings = "tnum"))
        }
        CircleButton(DatumIcons.Close, onClick = onRemove, size = 26.dp, style = ButtonStyle.Tinted, tint = c.labelSecondary)
    }
}

@Composable
private fun ManualSheet(visible: Boolean, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val draft by app.objectDraft.draft.collectAsStateWithLifecycle()
    val metric = settings.units == UnitSystem.METRIC
    var l by remember { mutableStateOf("") }
    var w by remember { mutableStateOf("") }
    var h by remember { mutableStateOf("") }
    GlassSheet(visible, onDismiss, title = "Enter dimensions") {
        DText("Leave any field empty to keep measuring it with sensors.", Datum.type.callout, Datum.colors.labelSecondary)
        Spacer(Modifier.height(12.dp))
        val dims = when (draft.shape) {
            ShapeKind.BOX -> listOf(Dim.L, Dim.W, Dim.H)
            ShapeKind.CYLINDER -> listOf(Dim.L, Dim.H)
            ShapeKind.SPHERE -> listOf(Dim.L)
        }
        dims.forEach { d ->
            DText(dimName(d, draft.shape).uppercase(), Datum.type.sectionLabel, Datum.colors.labelSecondary, Modifier.padding(top = 8.dp, bottom = 4.dp))
            val (value, set) = when (d) {
                Dim.L -> l to { s: String -> l = s }
                Dim.W -> w to { s: String -> w = s }
                Dim.H -> h to { s: String -> h = s }
            }
            NumberField(value, set, Modifier.fillMaxWidth(), suffix = if (metric) "cm" else "in")
        }
        Spacer(Modifier.height(16.dp))
        PillButton("Add to object", onClick = {
            val list = listOfNotNull(
                l.toDoubleOrNull()?.let { Dim.L to it },
                w.toDoubleOrNull()?.let { Dim.W to it },
                h.toDoubleOrNull()?.let { Dim.H to it },
            ).filter { it.first in dims }.map { (d, v) ->
                val m = if (metric) v / 100 else v * 0.0254
                DimEstimate(d, m, 0.0015 + 0.002 * m, EstimateSource.MANUAL)
            }
            if (list.isNotEmpty()) {
                app.objectDraft.replaceEstimates(EstimateSource.MANUAL, list)
                hud.show(DatumIcons.Sparkles, "Recalibrated")
            }
            l = ""; w = ""; h = ""
            onDismiss()
        }, modifier = Modifier.fillMaxWidth())
    }
}
