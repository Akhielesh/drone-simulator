package com.akhielesh.datum.ui.screens.objects

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Dim
import com.akhielesh.datum.core.data.DimEstimate
import com.akhielesh.datum.core.data.EstimateSource
import com.akhielesh.datum.core.data.Face
import com.akhielesh.datum.core.data.RatioConstraint
import com.akhielesh.datum.core.data.ShapeKind
import com.akhielesh.datum.core.geometry.CornerSnapper
import com.akhielesh.datum.core.geometry.Quad
import com.akhielesh.datum.core.geometry.measureWithReference
import com.akhielesh.datum.core.geometry.rectangleAspect
import com.akhielesh.datum.core.math.Vec2
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.aboveNavBar
import com.akhielesh.datum.ui.common.rememberPermissionState
import com.akhielesh.datum.ui.components.ButtonStyle
import com.akhielesh.datum.ui.components.Chip
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.IconTile
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.NumberField
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.ProgressRing
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Reference(val label: String, val longMm: Double, val shortMm: Double) {
    CARD("Bank card", 85.60, 53.98),
    A4("A4 sheet", 297.0, 210.0),
    LETTER("US Letter", 279.4, 215.9),
    PHONE("This phone", 0.0, 0.0),
    CUSTOM("Custom", 0.0, 0.0),
    NONE("No reference", 0.0, 0.0),
}

private enum class PhotoFace(val label: String, val face: Face, val dimW: Dim, val dimH: Dim) {
    FRONT("Front", Face.FRONT, Dim.L, Dim.H),
    SIDE("Side", Face.RIGHT, Dim.W, Dim.H),
    TOP("Top", Face.TOP, Dim.L, Dim.W),
}

private enum class Handle { REF, OBJ }

@Composable
fun ObjectPhotoScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft by app.objectDraft.draft.collectAsStateWithLifecycle()
    var image by remember { mutableStateOf<LoadedImage?>(null) }
    var loading by remember { mutableStateOf(false) }
    var pendingCapture by remember { mutableStateOf<Uri?>(null) }

    fun open(uri: Uri) {
        loading = true
        scope.launch {
            image = ImageTools.load(context, uri)
            loading = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { open(it) } }
    val taker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pendingCapture?.let { open(it) } }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var wantCamera by remember { mutableStateOf(false) }
    fun launchCamera() {
        val file = File(context.cacheDir, "captures/photo_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pendingCapture = uri
        taker.launch(uri)
    }
    LaunchedEffect(cameraPermission.granted, wantCamera) {
        if (wantCamera && cameraPermission.granted) {
            wantCamera = false
            launchCamera()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "Photos", subtitle = if (image == null) "Add a picture of a face" else "Adjust the corners",
            onBack = { if (image != null) image = null else nav.back() },
        )
        val img = image
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ProgressRing(0.35f, Modifier.size(40.dp)) }
            img == null -> PhotoPicker(
                textures = draft.textures,
                onCamera = {
                    if (cameraPermission.granted) launchCamera() else {
                        wantCamera = true
                        cameraPermission.request()
                    }
                },
                onGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onDone = { nav.replace(Route.ObjectDesign) },
                hasModel = draft.fused.complete,
            )
            else -> PhotoEditor(img, draft.shape, onAdded = { image = null })
        }
    }
}

@Composable
private fun PhotoPicker(textures: Map<Face, String>, onCamera: () -> Unit, onGallery: () -> Unit, onDone: () -> Unit, hasModel: Boolean) {
    val c = Datum.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().card(Shapes.xxl).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconTile(DatumIcons.Photo, listOf(Color(0xFF5AC8FA), Color(0xFF1E8FEA)), size = 64.dp)
            Spacer(Modifier.height(12.dp))
            DText("Photograph one face", Datum.type.title2)
            Spacer(Modifier.height(4.dp))
            DText(
                "Put a bank card or A4 sheet flat against the same face for exact scale — or skip it and Datum recovers the face's true proportions from perspective.",
                Datum.type.callout, c.labelSecondary,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Take photo", onClick = onCamera, icon = DatumIcons.Camera, modifier = Modifier.weight(1f))
                PillButton("Choose", onClick = onGallery, icon = DatumIcons.Photo, style = ButtonStyle.Tinted, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(18.dp))
        DText("TIPS", Datum.type.sectionLabel, c.labelSecondary, Modifier.padding(start = 6.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().card(Shapes.lg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tip(DatumIcons.Target, "Shoot as straight-on as you can and fill the frame.")
            Tip(DatumIcons.Layers, "The reference must lie flat on the same face you measure.")
            Tip(DatumIcons.Sparkles, "Each photo is fused with your other measurements and textures the 3D model.")
        }
        if (textures.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            DText("ADDED", Datum.type.sectionLabel, c.labelSecondary, Modifier.padding(start = 6.dp, bottom = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                textures.forEach { (face, path) ->
                    val bmp = remember(path) { ImageTools.loadFile(path, 256)?.asImageBitmap() }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bmp != null) {
                            Image(bmp, face.name, Modifier.size(84.dp).clip(Shapes.md), contentScale = ContentScale.Crop)
                        }
                        DText(face.name.lowercase().replaceFirstChar { it.uppercase() }, Datum.type.caption, c.labelSecondary)
                    }
                }
            }
        }
        if (hasModel) {
            Spacer(Modifier.height(18.dp))
            PillButton("Open designer", onClick = onDone, icon = DatumIcons.Cube, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Tip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DIcon(icon, tint = Datum.colors.accent, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        DText(text, Datum.type.callout)
    }
}

@Composable
private fun PhotoEditor(img: LoadedImage, shape: ShapeKind, onAdded: () -> Unit) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    val density = LocalDensity.current
    val faces = if (shape == ShapeKind.BOX) PhotoFace.entries else listOf(PhotoFace.FRONT)
    var face by remember { mutableStateOf(PhotoFace.FRONT) }
    var reference by remember { mutableStateOf(Reference.CARD) }
    var customW by remember { mutableStateOf("") }
    var customH by remember { mutableStateOf("") }
    val bw = img.bitmap.width.toDouble()
    val bh = img.bitmap.height.toDouble()
    var objQuad by remember(img) { mutableStateOf(Quad.centered(bw * 0.5, bh * 0.42, bw * 0.56, bh * 0.4)) }
    var refQuad by remember(img) { mutableStateOf(Quad.centered(bw * 0.5, bh * 0.8, bw * 0.24, bw * 0.24 / 1.586)) }
    var drag by remember { mutableStateOf<Pair<Handle, Int>?>(null) }
    val imageBitmap: ImageBitmap = remember(img) { img.bitmap.asImageBitmap() }
    val snapper = remember(img) { CornerSnapper(img.gray, img.grayW, img.grayH) }

    // Reference size (long, short) in metres.
    val refSize: Pair<Double, Double>? = when (reference) {
        Reference.NONE -> null
        Reference.PHONE -> app.device.heightMm / 1000 to app.device.widthMm / 1000
        Reference.CUSTOM -> {
            val a = customW.toDoubleOrNull()
            val b = customH.toDoubleOrNull()
            if (a == null || b == null || a <= 0 || b <= 0) null else {
                val k = if (settings.units == UnitSystem.METRIC) 0.01 else 0.0254
                max(a, b) * k to min(a, b) * k
            }
        }
        else -> reference.longMm / 1000 to reference.shortMm / 1000
    }
    // Width/height along the quad's own TL→TR / TL→BL sides.
    val refWH = refSize?.let { (long, short) ->
        val top = (refQuad.tr - refQuad.tl).length + (refQuad.br - refQuad.bl).length
        val side = (refQuad.bl - refQuad.tl).length + (refQuad.br - refQuad.tr).length
        if (top >= side) long to short else short to long
    }
    val measured = refWH?.let { (w, h) -> measureWithReference(refQuad, w, h, objQuad) }
    val aspect = if (refWH == null) rectangleAspect(objQuad, Vec2(bw / 2, bh / 2), img.focalPx) else null

    Column(Modifier.fillMaxSize()) {
        if (faces.size > 1) {
            SegmentedControl(
                faces.map { it.label }, faces.indexOf(face), onSelect = { face = faces[it] },
                modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Reference.entries.forEach { r -> Chip(r.label, r == reference, onClick = { reference = r }) }
        }
        if (reference == Reference.CUSTOM) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val unit = if (settings.units == UnitSystem.METRIC) "cm" else "in"
                NumberField(customW, { customW = it }, Modifier.weight(1f), suffix = unit, placeholder = "Long side")
                NumberField(customH, { customH = it }, Modifier.weight(1f), suffix = unit, placeholder = "Short side")
            }
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)) {
            val vw = with(density) { maxWidth.toPx() }
            val vh = with(density) { maxHeight.toPx() }
            val scale = min(vw / bw, vh / bh).toFloat()
            val ox = ((vw - bw * scale) / 2).toFloat()
            val oy = ((vh - bh * scale) / 2).toFloat()
            fun toView(p: Vec2) = Offset(ox + p.x.toFloat() * scale, oy + p.y.toFloat() * scale)
            fun toImage(o: Offset) = Vec2(((o.x - ox) / scale).toDouble().coerceIn(0.0, bw), ((o.y - oy) / scale).toDouble().coerceIn(0.0, bh))
            val showRef = reference != Reference.NONE
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(img, showRef) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val candidates = buildList {
                                objQuad.points.forEachIndexed { i, p -> add(Triple(Handle.OBJ, i, toView(p))) }
                                if (showRef) refQuad.points.forEachIndexed { i, p -> add(Triple(Handle.REF, i, toView(p))) }
                            }
                            val nearest = candidates.minByOrNull { (it.third - down.position).getDistance() }
                            if (nearest == null || (nearest.third - down.position).getDistance() > 44.dp.toPx()) return@awaitEachGesture
                            drag = nearest.first to nearest.second
                            app.haptics.tick(0.5f)
                            val grab = nearest.third - down.position
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                val p = toImage(ch.position + grab)
                                if (nearest.first == Handle.OBJ) objQuad = objQuad.withPoint(nearest.second, p) else refQuad = refQuad.withPoint(nearest.second, p)
                                ch.consume()
                            }
                            // Magnetic corners: snap to a nearby strong corner in the photo.
                            val q = if (nearest.first == Handle.OBJ) objQuad else refQuad
                            val gp = q.points[nearest.second].let { Vec2(it.x * img.grayScale, it.y * img.grayScale) }
                            snapper.snap(gp, 9)?.let { s ->
                                val snapped = Vec2(s.x / img.grayScale, s.y / img.grayScale)
                                if (nearest.first == Handle.OBJ) objQuad = objQuad.withPoint(nearest.second, snapped) else refQuad = refQuad.withPoint(nearest.second, snapped)
                                app.haptics.click()
                            }
                            drag = null
                        }
                    },
            ) {
                drawImage(
                    imageBitmap,
                    dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                    dstSize = IntSize((bw * scale).roundToInt(), (bh * scale).roundToInt()),
                )
                fun quadPath(q: Quad) = Path().apply {
                    val p = q.points.map { toView(it) }
                    moveTo(p[0].x, p[0].y)
                    for (i in 1 until 4) lineTo(p[i].x, p[i].y)
                    close()
                }
                if (showRef) {
                    drawPath(quadPath(refQuad), c.orange.copy(alpha = 0.2f))
                    drawPath(quadPath(refQuad), c.orange, style = Stroke(2.dp.toPx()))
                }
                drawPath(quadPath(objQuad), c.accent.copy(alpha = 0.16f))
                drawPath(quadPath(objQuad), c.accent, style = Stroke(2.dp.toPx()))
                val handles = objQuad.points.map { Handle.OBJ to it } + if (showRef) refQuad.points.map { Handle.REF to it } else emptyList()
                handles.forEachIndexed { idx, (kind, p) ->
                    val o = toView(p)
                    val i = if (kind == Handle.OBJ) idx else idx - 4
                    val active = drag == kind to i
                    val col = if (kind == Handle.OBJ) c.accent else c.orange
                    drawCircle(Color.Black.copy(alpha = 0.25f), (if (active) 14f else 11f) * density.density, o)
                    drawCircle(Color.White, (if (active) 12f else 9f) * density.density, o)
                    drawCircle(col, (if (active) 6f else 5f) * density.density, o)
                }
                // Loupe: 3× magnifier above the finger while dragging.
                drag?.let { (kind, i) ->
                    val p = (if (kind == Handle.OBJ) objQuad else refQuad).points[i]
                    val o = toView(p)
                    val r = 56.dp.toPx()
                    val lc = Offset(o.x, if (o.y > r * 2.6f) o.y - r * 1.9f else o.y + r * 1.9f)
                    val zoom = 3f
                    val srcSide = (2 * r / (scale * zoom)).toDouble()
                    val sx = (p.x - srcSide / 2).roundToInt()
                    val sy = (p.y - srcSide / 2).roundToInt()
                    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(lc, r)) }
                    drawCircle(Color.Black.copy(alpha = 0.3f), r + 3.dp.toPx(), lc)
                    clipPath(circle) {
                        drawRect(Color.Black, Offset(lc.x - r, lc.y - r), Size(2 * r, 2 * r))
                        drawImage(
                            imageBitmap,
                            srcOffset = IntOffset(sx, sy),
                            srcSize = IntSize(srcSide.roundToInt().coerceAtLeast(1), srcSide.roundToInt().coerceAtLeast(1)),
                            dstOffset = IntOffset((lc.x - r).roundToInt(), (lc.y - r).roundToInt()),
                            dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()),
                        )
                    }
                    drawCircle(Color.White, r, lc, style = Stroke(2.5.dp.toPx()))
                    drawLine(Color.White, Offset(lc.x - 10.dp.toPx(), lc.y), Offset(lc.x + 10.dp.toPx(), lc.y), 1.5.dp.toPx())
                    drawLine(Color.White, Offset(lc.x, lc.y - 10.dp.toPx()), Offset(lc.x, lc.y + 10.dp.toPx()), 1.5.dp.toPx())
                }
            }
        }
        // Result + add
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).card(Shapes.xl).padding(14.dp)) {
            val dimW = if (shape == ShapeKind.BOX) face.dimW else Dim.L
            val dimH = if (shape == ShapeKind.BOX) face.dimH else Dim.H
            val text = when {
                measured != null -> "${dimName(dimW, shape)} ${Fmt.length(measured.width, settings.units)}  ×  ${dimName(dimH, shape).lowercase()} ${Fmt.length(measured.height, settings.units)}"
                aspect != null -> "${dimName(dimW, shape)} : ${dimName(dimH, shape).lowercase()} = %.3f".format(aspect.ratio)
                reference == Reference.NONE -> "Drag the blue corners onto the face"
                else -> "Line up the orange corners with the reference"
            }
            DText(text, Datum.type.headline)
            DText(
                when {
                    measured != null -> "± %.1f %%  ·  scaled by %s".format(measured.relativeSigma * 100, reference.label.lowercase())
                    aspect != null -> if (aspect.focalFromImage) "Shape-only: focal length recovered from perspective" else "Shape-only: uses the camera's focal length"
                    else -> "Corners snap to edges in the photo when you let go"
                },
                Datum.type.footnote, c.labelSecondary,
            )
            Spacer(Modifier.height(10.dp))
            PillButton(
                "Add to model", icon = DatumIcons.Sparkles,
                enabled = measured != null || aspect != null,
                onClick = {
                    val estimates = mutableListOf<DimEstimate>()
                    var ratio: RatioConstraint? = null
                    val texAspect: Double
                    if (measured != null) {
                        estimates += DimEstimate(dimW, measured.width, measured.width * measured.relativeSigma, EstimateSource.PHOTO, reference.label)
                        if (shape != ShapeKind.SPHERE) {
                            estimates += DimEstimate(dimH, measured.height, measured.height * measured.relativeSigma, EstimateSource.PHOTO, reference.label)
                        }
                        texAspect = measured.width / measured.height
                    } else {
                        val a = aspect!!
                        ratio = RatioConstraint(dimW, dimH, a.ratio, if (a.focalFromImage) 0.03 else 0.05, "photo")
                        texAspect = a.ratio
                    }
                    if (estimates.isNotEmpty()) app.objectDraft.addEstimates(estimates)
                    ratio?.let { app.objectDraft.addRatio(it) }
                    if (shape == ShapeKind.BOX) {
                        val outW = 640
                        val outH = (outW / texAspect.coerceIn(0.2, 5.0)).roundToInt().coerceIn(64, 1600)
                        val tex = ImageTools.rectify(img.bitmap, objQuad, outW, outH)
                        val file = File(app.library.photosDir, "tex_${UUID.randomUUID()}.jpg")
                        if (ImageTools.saveJpeg(tex, file)) app.objectDraft.setTexture(face.face, file.absolutePath)
                    }
                    app.haptics.success()
                    hud.show(DatumIcons.Sparkles, "Recalibrated")
                    onAdded()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.aboveNavBar())
    }
}
