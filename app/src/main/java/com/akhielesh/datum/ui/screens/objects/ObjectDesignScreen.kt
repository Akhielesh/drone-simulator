package com.akhielesh.datum.ui.screens.objects

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Detail
import com.akhielesh.datum.core.data.Dim
import com.akhielesh.datum.core.data.Face
import com.akhielesh.datum.core.data.MeasureKind
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.data.ShapeKind
import com.akhielesh.datum.core.geometry.Containers
import com.akhielesh.datum.core.geometry.Materials
import com.akhielesh.datum.core.geometry.Measured
import com.akhielesh.datum.core.geometry.ShapeMath
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.components.ButtonStyle
import com.akhielesh.datum.ui.components.Chip
import com.akhielesh.datum.ui.components.CircleButton
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.EmptyState
import com.akhielesh.datum.ui.components.GlassSheet
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.SectionCard
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.TextInput
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.components.pressable
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ObjectDesignScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val draft by app.objectDraft.draft.collectAsStateWithLifecycle()
    val fused = draft.fused
    if (!fused.complete) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Designer", onBack = { nav.back() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val missing = listOf(Dim.L to fused.l, Dim.W to fused.w, Dim.H to fused.h).filter { it.second == null }.map { dimName(it.first, draft.shape).lowercase() }.distinct()
                EmptyState(
                    DatumIcons.Cube, "Almost there",
                    "Still missing: ${missing.joinToString(", ")}. Measure it with any method and the model builds itself.",
                    action = { PillButton("Choose a method", onClick = { nav.push(Route.ObjectHub) }) },
                )
            }
        }
        return
    }
    DesignContent()
}

@Composable
private fun DesignContent() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Datum.colors
    val draft by app.objectDraft.draft.collectAsStateWithLifecycle()
    val fused = draft.fused
    val camera = remember { OrbitCamera() }
    var style by remember { mutableStateOf(RenderStyle.Solid) }
    var preset by remember { mutableStateOf(ViewPreset.Iso) }
    var spin by remember { mutableStateOf(true) }
    var detailDim by remember { mutableStateOf<Dim?>(null) }
    var showRename by remember { mutableStateOf(false) }
    Turntable(camera, enabled = spin && preset == ViewPreset.Iso)
    LaunchedEffect(preset) { camera.goTo(preset) }

    // Dimensions glide to their new values whenever fusion changes ("auto-calibration").
    val l = fused.l!!
    val w = fused.w!!
    val h = fused.h!!
    val aL = remember { Animatable(l.value.toFloat()) }
    val aW = remember { Animatable(w.value.toFloat()) }
    val aH = remember { Animatable(h.value.toFloat()) }
    LaunchedEffect(l.value) { aL.animateTo(l.value.toFloat(), spring(dampingRatio = 0.75f, stiffness = 110f)) }
    LaunchedEffect(w.value) { aW.animateTo(w.value.toFloat(), spring(dampingRatio = 0.75f, stiffness = 110f)) }
    LaunchedEffect(h.value) { aH.animateTo(h.value.toFloat(), spring(dampingRatio = 0.75f, stiffness = 110f)) }

    val textures by produceState<Map<Face, ImageBitmap>>(emptyMap(), draft.textures) {
        value = withContext(Dispatchers.IO) {
            draft.textures.mapNotNull { (f, path) -> ImageTools.loadFile(path, 768)?.let { f to it.asImageBitmap() } }.toMap()
        }
    }
    val layer = rememberGraphicsLayer()

    fun save() {
        val model = draft.toModel() ?: return
        val m = ShapeMath.metrics(model.shape, model.length, model.width, model.height, model.sigmaL, model.sigmaW, model.sigmaH)
        val measurement = Measurement(
            id = draft.savedId ?: java.util.UUID.randomUUID().toString(),
            kind = MeasureKind.OBJECT,
            title = draft.name,
            value = m.volume,
            quantity = Quantity.VOLUME,
            method = draft.estimates.map { it.source.label }.distinct().joinToString(" + "),
            details = listOf(
                Detail(dimName(Dim.L, model.shape), model.length, Quantity.LENGTH),
                Detail(dimName(Dim.W, model.shape), model.width, Quantity.LENGTH),
                Detail(dimName(Dim.H, model.shape), model.height, Quantity.LENGTH),
                Detail("Surface area", m.surfaceArea, Quantity.AREA),
                Detail("Diagonal", m.spaceDiagonal, Quantity.LENGTH),
                Detail("Weight", ShapeMath.weightKg(Materials.byName(model.material), model.shape, model.length, model.width, model.height), Quantity.MASS),
            ),
            objectModel = model,
        )
        if (draft.savedId != null) app.library.update(measurement) else app.library.add(measurement)
        app.objectDraft.markSaved(measurement.id)
        app.haptics.success()
        hud.show(DatumIcons.Check, "Saved to Library")
    }

    fun shareImage() {
        scope.launch {
            val shot = runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull() ?: return@launch
            val f = { v: Double -> Fmt.length(v, settings.units).toString() }
            val dims = when (draft.shape) {
                ShapeKind.BOX -> "${f(l.value)} × ${f(w.value)} × ${f(h.value)}"
                ShapeKind.CYLINDER -> "Ø ${f(l.value)} × ${f(h.value)}"
                ShapeKind.SPHERE -> "Ø ${f(l.value)}"
            }
            val bg = c.background.toArgb()
            val fg = c.label.toArgb()
            val sub = c.labelSecondary.toArgb()
            val card = Exporters.render(shot.width, shot.height + 240) { cv ->
                cv.drawColor(bg)
                cv.drawBitmap(shot, 0f, 160f, null)
                val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; textSize = 64f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
                val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sub; textSize = 42f }
                cv.drawText(draft.name, 56f, 96f, title)
                cv.drawText(dims, 56f, 150f, body)
                cv.drawText("Measured with Datum", 56f, shot.height + 200f, body)
            }
            Exporters.share(context, Exporters.exportPng(context, card, draft.name), "image/png", "Share object")
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 640.dp
        val viewportHeight = maxHeight * 0.48f
        val viewport = @Composable { mod: Modifier ->
            Box(mod) {
                ObjectViewport(
                    draft.shape, aL.value.toDouble(), aW.value.toDouble(), aH.value.toDouble(), camera,
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                    style = style, material = draft.material, textures = textures, units = settings.units,
                )
                Row(
                    Modifier.align(Alignment.TopStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RenderStyle.entries.forEach { s ->
                        Chip(s.name.replace("XRay", "X-ray"), s == style, onClick = { style = s })
                    }
                }
                CircleButton(
                    if (spin) DatumIcons.Pause else DatumIcons.Play, onClick = { spin = !spin },
                    Modifier.align(Alignment.TopEnd).padding(12.dp), size = 34.dp,
                )
                SegmentedControl(
                    ViewPreset.entries.map { it.label }, preset.ordinal, onSelect = { preset = ViewPreset.entries[it] },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(0.82f),
                )
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1.25f).fillMaxHeight()) {
                    TopBar(draft.name, subtitle = sourcesSummary(draft.estimates.size, draft.ratios.size), onBack = { nav.back() }) {
                        CircleButton(DatumIcons.Pencil, onClick = { showRename = true }, size = 38.dp)
                    }
                    viewport(Modifier.weight(1f).fillMaxWidth())
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    MetricsPanel(l, w, h, { detailDim = it }, ::save, ::shareImage)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar(draft.name, subtitle = sourcesSummary(draft.estimates.size, draft.ratios.size), onBack = { nav.back() }) {
                    CircleButton(DatumIcons.Pencil, onClick = { showRename = true }, size = 38.dp)
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    viewport(Modifier.fillMaxWidth().height(viewportHeight))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        MetricsPanel(l, w, h, { detailDim = it }, ::save, ::shareImage)
                    }
                }
            }
        }
        DimensionSheet(detailDim) { detailDim = null }
        RenameSheet(showRename) { showRename = false }
    }
}

private fun sourcesSummary(estimates: Int, ratios: Int) = "Auto-calibrated from ${estimates + ratios} measurement" + if (estimates + ratios == 1) "" else "s"

@Composable
private fun MetricsPanel(l: Measured, w: Measured, h: Measured, onDim: (Dim) -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val c = Datum.colors
    val draft by app.objectDraft.draft.collectAsStateWithLifecycle()
    val units = settings.units
    val shape = draft.shape
    val metrics = ShapeMath.metrics(shape, l.value, w.value, h.value, l.sigma, w.sigma, h.sigma)
    val material = Materials.byName(draft.material)

    Spacer(Modifier.height(14.dp))
    SegmentedControl(
        listOf("Box", "Cylinder", "Sphere"), shape.ordinal, onSelect = { app.objectDraft.setShape(ShapeKind.entries[it]) },
        icons = listOf(DatumIcons.Cube, DatumIcons.Cylinder, DatumIcons.Sphere), modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    SectionCard(title = "Dimensions", footer = "Tap a dimension to see how each measurement contributed.") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            val dims = when (shape) {
                ShapeKind.BOX -> listOf(Dim.L to l, Dim.W to w, Dim.H to h)
                ShapeKind.CYLINDER -> listOf(Dim.L to l, Dim.H to h)
                ShapeKind.SPHERE -> listOf(Dim.L to l)
            }
            dims.forEach { (d, m) ->
                Row(
                    Modifier.fillMaxWidth().pressable(haptic = true) { onDim(d) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Same colour coding as the callouts in the 3D view.
                    val bar = when (d) {
                        Dim.L -> c.accent
                        Dim.W -> c.green
                        Dim.H -> c.orange
                    }
                    Box(Modifier.width(4.dp).height(30.dp).clip(Shapes.pill).background(bar))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        DText(dimName(d, shape), Datum.type.callout, c.labelSecondary)
                        DText("${m.sources} source" + if (m.sources == 1) "" else "s", Datum.type.caption, c.labelTertiary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        DText(Fmt.length(m.value, units, settings.inchFractions).toString(), Datum.type.title3.copy(fontFeatureSettings = "tnum"))
                        DText("± " + Fmt.length(m.sigma, units), Datum.type.caption, c.labelSecondary)
                    }
                    Spacer(Modifier.width(6.dp))
                    DIcon(DatumIcons.ChevronRight, tint = c.labelTertiary, size = 14.dp)
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionCard(title = "Size") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            val v = Fmt.volume(metrics.volume, units)
            KeyValueRow("Volume", v.value, unit = v.unit, hint = "± " + Fmt.volume(metrics.volumeSigma, units))
            val other = if (units == com.akhielesh.datum.core.units.UnitSystem.METRIC) {
                com.akhielesh.datum.core.units.UnitSystem.IMPERIAL
            } else {
                com.akhielesh.datum.core.units.UnitSystem.METRIC
            }
            val alt = Fmt.volume(metrics.volume, other)
            KeyValueRow("In ${if (other == com.akhielesh.datum.core.units.UnitSystem.METRIC) "metric" else "imperial"}", alt.value, unit = alt.unit, hint = "≈ %.1f US gal · %.1f L".format(metrics.volume * 264.172, metrics.volume * 1000))
            val sa = Fmt.area(metrics.surfaceArea, units)
            KeyValueRow("Surface area", sa.value, unit = sa.unit, hint = "paint or wrap, +10 % overlap: " + Fmt.area(metrics.surfaceArea * 1.1, units))
            KeyValueRow("Space diagonal", Fmt.length(metrics.spaceDiagonal, units).toString(), hint = "longest thing that fits inside")
            metrics.faceDiagonals.forEach { (k, d) -> KeyValueRow("$k diagonal", Fmt.length(d, units).toString()) }
            val fp = Fmt.area(metrics.footprint, units)
            KeyValueRow("Footprint", fp.value, unit = fp.unit)
            KeyValueRow(if (shape == ShapeKind.BOX) "Base perimeter" else "Circumference", Fmt.length(metrics.basePerimeter, units).toString())
            if (shape == ShapeKind.BOX) KeyValueRow("All 12 edges", Fmt.length(metrics.totalEdgeLength, units).toString(), hint = "e.g. wood for a frame")
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionCard(title = "Weight") {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Materials.all.forEach { m -> Chip(m.name, m.name == material.name, onClick = { app.objectDraft.setMaterial(m.name) }) }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                val kg = ShapeMath.weightKg(material, shape, l.value, w.value, h.value)
                val wt = Fmt.mass(kg, units)
                KeyValueRow(
                    "Estimated weight", wt.value, unit = wt.unit,
                    hint = if (material.isHollow) "empty box, ~${(material.hollowArealKgM2 * 1000).toInt()} g/m² board" else "solid, ${material.densityKgM3.toInt()} kg/m³",
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionCard(title = "Shipping", footer = "Couriers bill the greater of actual and volumetric weight.") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            KeyValueRow("Volumetric (÷5000)", "%.2f".format(metrics.dimWeightKg), unit = "kg", hint = "DHL, FedEx, UPS international")
            KeyValueRow("DIM weight (÷139)", "%.1f".format(metrics.dimWeightLb), unit = "lb", hint = "US domestic")
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionCard(title = "Fits in") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Containers.common.forEach { ct ->
                val ok = Containers.fits(l.value, w.value, h.value, ct)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    DIcon(if (ok) DatumIcons.Check else DatumIcons.Close, tint = if (ok) c.green else c.labelTertiary, size = 17.dp)
                    Spacer(Modifier.width(10.dp))
                    DText(ct.name, Datum.type.callout, if (ok) c.label else c.labelSecondary, Modifier.weight(1f))
                    DText(
                        "${Fmt.length(ct.l, units)} × ${Fmt.length(ct.w, units)} × ${Fmt.length(ct.h, units)}",
                        Datum.type.caption, c.labelTertiary,
                    )
                }
            }
            val door = Containers.fitsThroughOpening(l.value, w.value, h.value, 0.81, 2.03)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                DIcon(if (door) DatumIcons.Check else DatumIcons.Close, tint = if (door) c.green else c.orange, size = 17.dp)
                Spacer(Modifier.width(10.dp))
                DText("Through a standard door", Datum.type.callout, modifier = Modifier.weight(1f))
                DText("81 × 203 cm", Datum.type.caption, c.labelTertiary)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionCard(title = "Improve accuracy", footer = "Every extra measurement is weighted by its precision and fused automatically.") {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Add photo", onClick = { nav.push(Route.ObjectPhoto) }, icon = DatumIcons.Photo, style = ButtonStyle.Tinted, compact = true, modifier = Modifier.weight(1f))
                PillButton("AR scan", onClick = { nav.push(Route.ObjectAr) }, icon = DatumIcons.Ar, style = ButtonStyle.Tinted, compact = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Corners", onClick = { nav.push(Route.ObjectMotion(false)) }, icon = DatumIcons.Corner, style = ButtonStyle.Tinted, compact = true, modifier = Modifier.weight(1f))
                PillButton("Edges", onClick = { nav.push(Route.ObjectMotion(true)) }, icon = DatumIcons.Edges, style = ButtonStyle.Tinted, compact = true, modifier = Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    PillButton(if (draft.savedId == null) "Save to Library" else "Update in Library", onClick = onSave, icon = DatumIcons.Download, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Blueprint PDF", onClick = {
            draft.toModel()?.let { Exporters.share(context, Exporters.exportPdf(context, it, units), "application/pdf", "Blueprint & template") }
        }, icon = DatumIcons.Doc, style = ButtonStyle.Tinted, compact = true, modifier = Modifier.weight(1f))
        PillButton("3D model", onClick = {
            draft.toModel()?.let { Exporters.share(context, Exporters.exportObj(context, it), "model/obj", "3D model (OBJ)") }
        }, icon = DatumIcons.Cube, style = ButtonStyle.Tinted, compact = true, modifier = Modifier.weight(1f))
        PillButton("Image", onClick = onShare, icon = DatumIcons.Share, style = ButtonStyle.Tinted, compact = true, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(48.dp))
}

/** How each source contributed to one dimension (inverse-variance weights). */
@Composable
private fun DimensionSheet(dim: Dim?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val c = Datum.colors
    val draft by app.objectDraft.draft.collectAsStateWithLifecycle()
    val list = draft.estimates.filter { it.dim == dim || (draft.shape != ShapeKind.BOX && dim == Dim.L && it.dim == Dim.W) }
    val weights = list.map { 1.0 / (it.sigma * it.sigma).coerceAtLeast(1e-8) }
    val total = weights.sum()
    GlassSheet(dim != null, onDismiss, title = dim?.let { dimName(it, draft.shape) } ?: "") {
        DText(
            "Datum combines measurements by their precision: a tight AR reading outweighs a quick motion sweep. Remove any that look wrong.",
            Datum.type.callout, c.labelSecondary,
        )
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) DText("Derived from photo proportions.", Datum.type.body)
        list.forEachIndexed { i, e ->
            val share = if (total > 0) weights[i] / total else 0.0
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                DIcon(sourceIcon(e.source), tint = c.accent, size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    DText("${e.source.label}" + if (e.note.isNotBlank()) " · ${e.note}" else "", Datum.type.subhead, c.labelSecondary)
                    DText("${Fmt.length(e.value, settings.units)}  ± ${Fmt.length(e.sigma, settings.units)}", Datum.type.headline)
                    Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).clip(Shapes.pill).background(c.fillTertiary)) {
                        Box(Modifier.fillMaxWidth(share.toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(Shapes.pill).background(c.accent))
                    }
                }
                Spacer(Modifier.width(10.dp))
                DText("%.0f%%".format(share * 100), Datum.type.headline, c.accent)
                Spacer(Modifier.width(6.dp))
                CircleButton(DatumIcons.Close, onClick = { app.objectDraft.removeEstimate(e) }, size = 26.dp, style = ButtonStyle.Tinted, tint = c.labelSecondary)
            }
        }
    }
}

@Composable
private fun RenameSheet(visible: Boolean, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val draft by app.objectDraft.draft.collectAsStateWithLifecycle()
    var name by remember(visible) { mutableStateOf(draft.name) }
    GlassSheet(visible, onDismiss, title = "Name") {
        TextInput(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Object name")
        Spacer(Modifier.height(12.dp))
        PillButton("Done", onClick = {
            app.objectDraft.setName(name.ifBlank { "Object" })
            onDismiss()
        }, modifier = Modifier.fillMaxWidth())
    }
}
