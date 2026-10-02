package com.akhielesh.datum.ui.screens.objects

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.akhielesh.datum.core.data.ObjectModel
import com.akhielesh.datum.core.data.ShapeKind
import com.akhielesh.datum.core.geometry.Materials
import com.akhielesh.datum.core.geometry.ShapeMath
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.UnitSystem
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object Exporters {
    private const val PT_PER_MM = 72.0 / 25.4

    private fun exportFile(context: Context, name: String): File =
        File(context.cacheDir, "exports/$name").apply { parentFile?.mkdirs() }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun share(context: Context, uri: Uri, mime: String, title: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "object" }

    // --------------------------------------------------------------------------------------------
    // Wavefront OBJ (metres) – opens in Blender, Fusion, slicers, etc.

    fun exportObj(context: Context, m: ObjectModel): Uri {
        val sb = StringBuilder()
        sb.appendLine("# Datum export — ${m.name}")
        sb.appendLine("# ${m.shape} ${"%.4f".format(m.length)} x ${"%.4f".format(m.width)} x ${"%.4f".format(m.height)} m")
        sb.appendLine("o ${safeName(m.name)}")
        when (m.shape) {
            ShapeKind.BOX -> {
                val x = m.length / 2
                val z = m.width / 2
                val h = m.height
                val v = listOf(
                    Triple(-x, 0.0, -z), Triple(x, 0.0, -z), Triple(x, 0.0, z), Triple(-x, 0.0, z),
                    Triple(-x, h, -z), Triple(x, h, -z), Triple(x, h, z), Triple(-x, h, z),
                )
                v.forEach { (a, b, c) -> sb.appendLine("v %.5f %.5f %.5f".format(a, b, c)) }
                listOf("0 0", "1 0", "1 1", "0 1").forEach { sb.appendLine("vt $it") }
                // Counter-clockwise from outside.
                val faces = listOf(listOf(1, 2, 3, 4), listOf(5, 8, 7, 6), listOf(4, 3, 7, 8), listOf(2, 1, 5, 6), listOf(3, 2, 6, 7), listOf(1, 4, 8, 5))
                faces.forEach { f -> sb.appendLine("f " + f.mapIndexed { i, idx -> "$idx/${i + 1}" }.joinToString(" ")) }
            }
            ShapeKind.CYLINDER -> {
                val n = 64
                val r = m.length / 2
                for (ring in 0..1) for (i in 0 until n) {
                    val a = 2 * PI * i / n
                    sb.appendLine("v %.5f %.5f %.5f".format(cos(a) * r, ring * m.height, sin(a) * r))
                }
                sb.appendLine("v 0 0 0")
                sb.appendLine("v 0 %.5f 0".format(m.height))
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    sb.appendLine("f ${i + 1} ${j + 1} ${n + j + 1} ${n + i + 1}")
                    sb.appendLine("f ${2 * n + 1} ${j + 1} ${i + 1}")
                    sb.appendLine("f ${2 * n + 2} ${n + i + 1} ${n + j + 1}")
                }
            }
            ShapeKind.SPHERE -> {
                val r = m.length / 2
                val rings = 18
                val segs = 32
                for (i in 0..rings) for (j in 0 until segs) {
                    val t = PI * i / rings
                    val p = 2 * PI * j / segs
                    sb.appendLine("v %.5f %.5f %.5f".format(r * sin(t) * cos(p), r + r * cos(t), r * sin(t) * sin(p)))
                }
                for (i in 0 until rings) for (j in 0 until segs) {
                    val a = i * segs + j + 1
                    val b = i * segs + (j + 1) % segs + 1
                    sb.appendLine("f $a $b ${b + segs} ${a + segs}")
                }
            }
        }
        val file = exportFile(context, "${safeName(m.name)}.obj")
        file.writeText(sb.toString())
        return uriFor(context, file)
    }

    fun exportPng(context: Context, bitmap: Bitmap, name: String): Uri {
        val file = exportFile(context, "${safeName(name)}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uriFor(context, file)
    }

    // --------------------------------------------------------------------------------------------
    // PDF: blueprint sheet + printable net (box / cylinder) for makers.

    fun exportPdf(context: Context, m: ObjectModel, units: UnitSystem): Uri {
        val doc = PdfDocument()
        blueprintPage(doc, m, units)
        if (m.shape != ShapeKind.SPHERE) netPages(doc, m, units)
        val file = exportFile(context, "${safeName(m.name)}_blueprint.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return uriFor(context, file)
    }

    private val ink = 0xFF1D1D1F.toInt()
    private val blue = 0xFF0A6CFF.toInt()
    private val gray = 0xFF8E8E93.toInt()

    private fun paint(color: Int, size: Float = 9f, bold: Boolean = false, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        if (stroke > 0) {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
    }

    private fun blueprintPage(doc: PdfDocument, m: ObjectModel, units: UnitSystem) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas
        c.drawText("Datum", 40f, 54f, paint(blue, 11f, bold = true))
        c.drawText(m.name, 40f, 80f, paint(ink, 22f, bold = true))
        val dims = when (m.shape) {
            ShapeKind.BOX -> "${Fmt.length(m.length, units)} × ${Fmt.length(m.width, units)} × ${Fmt.length(m.height, units)}"
            ShapeKind.CYLINDER -> "Ø ${Fmt.length(m.length, units)} × ${Fmt.length(m.height, units)}"
            ShapeKind.SPHERE -> "Ø ${Fmt.length(m.length, units)}"
        }
        c.drawText(dims, 40f, 100f, paint(gray, 12f))
        c.drawText(DateFormat.getDateTimeInstance().format(Date()), 555f - paint(gray, 9f).measureText(DateFormat.getDateTimeInstance().format(Date())), 54f, paint(gray, 9f))

        // Three orthographic views at one common scale.
        val l = m.length
        val w = if (m.shape == ShapeKind.BOX) m.width else m.length
        val h = if (m.shape == ShapeKind.SPHERE) m.length else m.height
        val area = RectF(40f, 130f, 555f, 520f)
        val totalW = l + w + 0.25 * max(l, w)
        val totalH = h + w + 0.25 * max(h, w)
        val s = min(area.width() / totalW, area.height() / totalH).toFloat() * 0.85f
        val gap = (0.12 * max(l, w)).toFloat() * s
        val frontX = area.left + 30f
        val frontY = area.top + 30f
        val line = paint(ink, stroke = 1.2f)
        val dimP = paint(blue, stroke = 0.7f)
        val label = paint(blue, 9f, bold = true)
        val caption = paint(gray, 8f)
        fun rectView(x: Float, y: Float, ww: Double, hh: Double, title: String, a: String, b: String, round: Boolean = false) {
            val r = RectF(x, y, x + (ww * s).toFloat(), y + (hh * s).toFloat())
            if (round) c.drawOval(r, line) else c.drawRect(r, line)
            c.drawText(title, x, y - 8f, caption)
            // horizontal dimension below
            val dy = r.bottom + 14f
            c.drawLine(r.left, dy, r.right, dy, dimP)
            c.drawLine(r.left, dy - 4f, r.left, dy + 4f, dimP)
            c.drawLine(r.right, dy - 4f, r.right, dy + 4f, dimP)
            c.drawText(a, r.centerX() - label.measureText(a) / 2, dy + 12f, label)
            // vertical dimension right
            val dx = r.right + 14f
            c.drawLine(dx, r.top, dx, r.bottom, dimP)
            c.drawLine(dx - 4f, r.top, dx + 4f, r.top, dimP)
            c.drawLine(dx - 4f, r.bottom, dx + 4f, r.bottom, dimP)
            c.save()
            c.rotate(-90f, dx + 12f, r.centerY())
            c.drawText(b, dx + 12f - label.measureText(b) / 2, r.centerY(), label)
            c.restore()
        }
        val L = Fmt.length(l, units).toString()
        val W = Fmt.length(w, units).toString()
        val H = Fmt.length(h, units).toString()
        when (m.shape) {
            ShapeKind.BOX -> {
                rectView(frontX, frontY, l, h, "FRONT", L, H)
                rectView(frontX + (l * s).toFloat() + gap + 30f, frontY, w, h, "SIDE", W, H)
                rectView(frontX, frontY + (h * s).toFloat() + gap + 40f, l, w, "TOP", L, W)
            }
            ShapeKind.CYLINDER -> {
                rectView(frontX, frontY, l, h, "SIDE", "Ø $L", H)
                rectView(frontX, frontY + (h * s).toFloat() + gap + 40f, l, l, "TOP", "Ø $L", "Ø $L", round = true)
            }
            ShapeKind.SPHERE -> rectView(frontX, frontY, l, l, "PROFILE", "Ø $L", "Ø $L", round = true)
        }

        // Metrics table.
        val metrics = ShapeMath.metrics(m.shape, m.length, m.width, m.height, m.sigmaL, m.sigmaW, m.sigmaH)
        val material = Materials.byName(m.material)
        val rows = listOf(
            "Volume" to "${Fmt.volume(metrics.volume, units)}  ± ${Fmt.volume(metrics.volumeSigma, units)}",
            "Surface area" to Fmt.area(metrics.surfaceArea, units).toString(),
            "Space diagonal" to Fmt.length(metrics.spaceDiagonal, units).toString(),
            "Footprint" to Fmt.area(metrics.footprint, units).toString(),
            "Weight (${material.name})" to Fmt.mass(ShapeMath.weightKg(material, m.shape, m.length, m.width, m.height), units).toString(),
            "Volumetric weight" to "%.2f kg (÷5000) · %.1f lb (÷139)".format(metrics.dimWeightKg, metrics.dimWeightLb),
            "Uncertainty" to "L ±${Fmt.length(m.sigmaL, units)}  W ±${Fmt.length(m.sigmaW, units)}  H ±${Fmt.length(m.sigmaH, units)}",
            "Sources" to m.estimates.groupBy { it.source.label }.map { (k, v) -> "$k ×${v.size}" }.joinToString(", ").ifEmpty { "—" },
        )
        var y = 560f
        c.drawText("METRICS", 40f, y, paint(gray, 8f, bold = true))
        y += 14f
        val rule = paint(0xFFE5E5EA.toInt(), stroke = 0.6f)
        for ((k, v) in rows) {
            c.drawLine(40f, y + 6f, 555f, y + 6f, rule)
            c.drawText(k, 40f, y, paint(gray, 10f))
            c.drawText(v, 220f, y, paint(ink, 10f, bold = true))
            y += 22f
        }
        c.drawText("Generated by Datum. Sensor-based measurements; verify critical dimensions.", 40f, 810f, paint(gray, 8f))
        doc.finishPage(page)
    }

    /** Printable net. Fits A4 at 1:1 when possible, otherwise scaled with the scale printed. */
    private fun netPages(doc: PdfDocument, m: ObjectModel, units: UnitSystem) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 2).create())
        val c = page.canvas
        val margin = 36f
        val availW = 595f - margin * 2
        val availH = 842f - margin * 2 - 60f
        val cut = paint(ink, stroke = 1.1f)
        val fold = paint(blue, stroke = 0.9f).apply { pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f) }
        val label = paint(gray, 9f)
        val mm = { v: Double -> v * 1000 }
        val title: String
        when (m.shape) {
            ShapeKind.BOX -> {
                val L = mm(m.length)
                val W = mm(m.width)
                val H = mm(m.height)
                val tab = min(15.0, max(6.0, min(L, W) * 0.25))
                val netW = 2 * L + 2 * W + tab
                val netH = H + 2 * W
                val scale = min(1.0, min(availW / (netW * PT_PER_MM), availH / (netH * PT_PER_MM)))
                val k = (PT_PER_MM * scale).toFloat()
                val ox = margin + ((availW - netW * k) / 2).toFloat()
                val oy = margin + 60f
                fun x(v: Double) = ox + (v * k).toFloat()
                fun y(v: Double) = oy + (v * k).toFloat()
                // Outline (cut)
                val p = Path().apply {
                    moveTo(x(W), y(0.0)); lineTo(x(W + L), y(0.0)); lineTo(x(W + L), y(W))
                    lineTo(x(2 * W + 2 * L), y(W)); lineTo(x(2 * W + 2 * L + tab), y(W + tab * 0.6))
                    lineTo(x(2 * W + 2 * L + tab), y(W + H - tab * 0.6)); lineTo(x(2 * W + 2 * L), y(W + H))
                    lineTo(x(W + L), y(W + H)); lineTo(x(W + L), y(2 * W + H)); lineTo(x(W), y(2 * W + H))
                    lineTo(x(W), y(W + H)); lineTo(x(0.0), y(W + H)); lineTo(x(0.0), y(W)); lineTo(x(W), y(W)); close()
                }
                c.drawPath(p, cut)
                // Folds
                c.drawLine(x(W), y(W), x(W + L), y(W), fold)
                c.drawLine(x(W), y(W + H), x(W + L), y(W + H), fold)
                listOf(W, W + L, 2 * W + L, 2 * W + 2 * L).forEach { xv -> c.drawLine(x(xv), y(W), x(xv), y(W + H), fold) }
                fun center(text: String, cx: Double, cy: Double) = c.drawText(text, x(cx) - label.measureText(text) / 2, y(cy), label)
                center("TOP", W + L / 2, W / 2)
                center("FRONT", W + L / 2, W + H / 2)
                center("BOTTOM", W + L / 2, W + H + W / 2)
                center("SIDE", W / 2, W + H / 2)
                center("SIDE", 2 * W + L + W / 2, W + H / 2)
                center("BACK", 2 * W + 1.5 * L, W + H / 2)
                center("GLUE", 2 * W + 2 * L + tab / 2, W + H / 2)
                title = if (scale >= 0.999) "Box template — print at 100 % (actual size)" else "Box template — scale 1:%.1f (too large for A4 at full size)".format(1 / scale)
            }
            ShapeKind.CYLINDER -> {
                val D = mm(m.length)
                val H = mm(m.height)
                val circ = PI * D
                val tab = 10.0
                val netW = circ + tab
                val netH = H + 2 * D + 10
                val scale = min(1.0, min(availW / (netW * PT_PER_MM), availH / (netH * PT_PER_MM)))
                val k = (PT_PER_MM * scale).toFloat()
                val ox = margin + ((availW - netW * k) / 2).toFloat()
                val oy = margin + 60f
                c.drawRect(ox, oy + (D * k).toFloat(), ox + (circ * k).toFloat(), oy + ((D + H) * k).toFloat(), cut)
                c.drawRect(ox + (circ * k).toFloat(), oy + (D * k).toFloat() + 6f, ox + ((circ + tab) * k).toFloat(), oy + ((D + H) * k).toFloat() - 6f, cut)
                val r = (D / 2 * k).toFloat()
                c.drawCircle(ox + (circ / 2 * k).toFloat(), oy + r, r, cut)
                c.drawCircle(ox + (circ / 2 * k).toFloat(), oy + ((D + H) * k).toFloat() + r + 10f, r, cut)
                c.drawText("SIDE  (${Fmt.length(m.length * PI, units)} around)", ox + 6f, oy + ((D + H / 2) * k).toFloat(), label)
                title = if (scale >= 0.999) "Cylinder template — print at 100 % (actual size)" else "Cylinder template — scale 1:%.1f".format(1 / scale)
            }
            ShapeKind.SPHERE -> title = ""
        }
        c.drawText(title, margin, margin + 18f, paint(ink, 13f, bold = true))
        c.drawText("Solid lines: cut · dashed blue: fold", margin, margin + 36f, paint(gray, 9f))
        // 1 cm calibration bar to check print scale.
        val bar = (10 * PT_PER_MM).toFloat()
        c.drawRect(margin, 842f - margin - 8f, margin + bar, 842f - margin - 4f, paint(ink))
        c.drawText("1 cm at 100 %", margin + bar + 6f, 842f - margin - 3f, paint(gray, 8f))
        doc.finishPage(page)
    }

    /** Renders [draw] into a bitmap (used for share images). */
    fun render(width: Int, height: Int, draw: (Canvas) -> Unit): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
}
