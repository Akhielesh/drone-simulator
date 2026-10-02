package com.akhielesh.datum.ui.screens.library

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.MeasureKind
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.data.ObjectDraft
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.Tool
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.components.ButtonStyle
import com.akhielesh.datum.ui.components.Chip
import com.akhielesh.datum.ui.components.CircleButton
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.EmptyState
import com.akhielesh.datum.ui.components.IconTile
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.Readout
import com.akhielesh.datum.ui.components.SectionCard
import com.akhielesh.datum.ui.components.TextInput
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.components.pressable
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import java.text.DateFormat
import java.util.Date

fun formatMeasurement(m: Measurement, units: UnitSystem): String {
    m.objectModel?.let { o ->
        val f = { v: Double -> Fmt.length(v, units).toString() }
        return "${f(o.length)} × ${f(o.width)} × ${f(o.height)}"
    }
    return Fmt.format(m.value, m.quantity, units).toString()
}

fun toolFor(kind: MeasureKind): Tool = when (kind) {
    MeasureKind.LEVEL -> Tool.Level
    MeasureKind.SLIDE -> Tool.Slide
    MeasureKind.HEIGHT, MeasureKind.ALTITUDE -> Tool.Height
    MeasureKind.OBJECT -> Tool.Object
    MeasureKind.AR -> Tool.ArMeasure
    MeasureKind.RULER -> Tool.Ruler
    MeasureKind.ANGLE -> Tool.Angle
    MeasureKind.COMPASS -> Tool.Compass
    MeasureKind.LIGHT -> Tool.Light
    MeasureKind.SOUND -> Tool.Sound
    MeasureKind.VIBRATION -> Tool.Vibration
    MeasureKind.STUD -> Tool.Stud
}

private enum class Filter(val label: String, val kinds: Set<MeasureKind>?) {
    All("All", null),
    Objects("Objects", setOf(MeasureKind.OBJECT)),
    Lengths("Lengths", setOf(MeasureKind.SLIDE, MeasureKind.HEIGHT, MeasureKind.AR, MeasureKind.RULER, MeasureKind.ALTITUDE)),
    Angles("Angles", setOf(MeasureKind.LEVEL, MeasureKind.ANGLE, MeasureKind.COMPASS)),
    Environment("Environment", setOf(MeasureKind.LIGHT, MeasureKind.SOUND, MeasureKind.VIBRATION, MeasureKind.STUD)),
}

@Composable
fun LibraryScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val units = LocalSettings.current.units
    val items by app.library.items.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(Filter.All) }
    val shown = items.filter { filter.kinds == null || it.kind in filter.kinds!! }
    Column(Modifier.fillMaxSize()) {
        TopBar("Library", subtitle = "${items.size} saved", onBack = { nav.back() })
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Filter.entries.forEach { f -> Chip(f.label, f == filter, onClick = { filter = f }) }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    DatumIcons.Library,
                    "No measurements",
                    "Use any tool and tap Save — your measurements, objects and calibrations live here.",
                )
            }
            return
        }
        val groups = shown.groupBy { dayLabel(it.createdAt) }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            groups.forEach { (day, list) ->
                item(key = "h$day") {
                    DText(day.uppercase(), Datum.type.sectionLabel, Datum.colors.labelSecondary, Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp))
                }
                items(list, key = { it.id }) { m ->
                    LibraryRow(m, units, Modifier.animateItem()) { nav.push(Route.LibraryDetail(m.id)) }
                    Spacer(Modifier.height(8.dp))
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

private fun dayLabel(t: Long): String = when {
    DateUtils.isToday(t) -> "Today"
    DateUtils.isToday(t + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
}

@Composable
private fun LibraryRow(m: Measurement, units: UnitSystem, modifier: Modifier, onClick: () -> Unit) {
    val c = Datum.colors
    val tool = toolFor(m.kind)
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .card(Shapes.lg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(tool.icon, tool.colors, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            DText(m.title, Datum.type.headline, maxLines = 1)
            DText(
                listOf(m.method, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.createdAt))).filter { it.isNotBlank() }.joinToString(" · "),
                Datum.type.footnote, c.labelSecondary, maxLines = 1,
            )
        }
        DText(formatMeasurement(m, units), Datum.type.subhead.copy(fontFeatureSettings = "tnum"), c.label, maxLines = 1)
    }
}

@Composable
fun LibraryDetailScreen(id: String) {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val hud = LocalHud.current
    val units = LocalSettings.current.units
    val items by app.library.items.collectAsStateWithLifecycle()
    val m = items.firstOrNull { it.id == id }
    val context = LocalContext.current
    val c = Datum.colors
    if (m == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Measurement", onBack = { nav.back() })
            EmptyState(DatumIcons.Info, "Not found", "This measurement was deleted.")
        }
        return
    }
    var title by remember(m.id) { mutableStateOf(m.title) }
    var note by remember(m.id) { mutableStateOf(m.note) }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            m.kind.label,
            subtitle = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(m.createdAt)),
            onBack = {
                if (title != m.title || note != m.note) app.library.update(m.copy(title = title.ifBlank { m.title }, note = note))
                nav.back()
            },
        ) {
            CircleButton(DatumIcons.Share, onClick = {
                val text = buildString {
                    appendLine("${m.title} — ${formatMeasurement(m, units)}")
                    m.details.forEach { d -> appendLine("${d.label}: ${Fmt.format(d.value, d.quantity, units)}") }
                    if (m.note.isNotBlank()) appendLine(m.note)
                    append("Measured with Datum")
                }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"))
            }, size = 38.dp)
            CircleButton(DatumIcons.Trash, onClick = {
                app.library.delete(m.id)
                app.haptics.warning()
                hud.show(DatumIcons.Trash, "Deleted")
                nav.back()
            }, size = 38.dp, tint = c.red)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .animateContentSize(),
        ) {
            Spacer(Modifier.height(12.dp))
            val tool = toolFor(m.kind)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(tool.icon, tool.colors, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    if (m.objectModel == null) {
                        val f = Fmt.format(m.value, m.quantity, units)
                        Readout(f.value, f.unit, style = Datum.type.readoutL)
                    } else {
                        DText(formatMeasurement(m, units), Datum.type.title2)
                    }
                    m.sigma?.let { DText("± " + Fmt.format(it, m.quantity, units), Datum.type.subhead, c.labelSecondary) }
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Name") {
                TextInput(title, { title = it }, Modifier.fillMaxWidth().padding(10.dp), placeholder = "Name")
            }
            if (m.details.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionCard(title = "Details") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        m.details.forEach { d ->
                            val f = Fmt.format(d.value, d.quantity, units)
                            KeyValueRow(d.label, f.value, unit = f.unit)
                        }
                        if (m.method.isNotBlank()) KeyValueRow("Method", m.method)
                    }
                }
            }
            if (m.marks.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionCard(title = "Marks") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        m.marks.forEachIndexed { i, v ->
                            val prev = if (i == 0) 0.0 else m.marks[i - 1]
                            KeyValueRow("Mark ${'A' + i}", Fmt.length(v, units).toString(), hint = "+" + Fmt.length(v - prev, units))
                        }
                    }
                }
            }
            m.objectModel?.let { model ->
                Spacer(Modifier.height(18.dp))
                PillButton(
                    "Open in Designer",
                    icon = DatumIcons.Cube,
                    onClick = {
                        app.objectDraft.load(ObjectDraft.from(model, m.id))
                        nav.push(Route.ObjectDesign)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Notes") {
                TextInput(note, { note = it }, Modifier.fillMaxWidth().padding(10.dp), placeholder = "Add a note")
            }
            Spacer(Modifier.height(10.dp))
            PillButton(
                "Save changes",
                onClick = {
                    app.library.update(m.copy(title = title.ifBlank { m.title }, note = note))
                    hud.show(DatumIcons.Check, "Saved")
                },
                style = ButtonStyle.Tinted,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(60.dp))
        }
    }
}
