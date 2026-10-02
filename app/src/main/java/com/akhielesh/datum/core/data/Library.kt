package com.akhielesh.datum.core.data

import android.content.Context
import com.akhielesh.datum.core.units.Quantity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Every tool that can save a result. */
@Serializable
enum class MeasureKind(val label: String) {
    LEVEL("Level"), SLIDE("Slide"), HEIGHT("Height"), OBJECT("Object"), AR("AR Measure"),
    RULER("Ruler"), ANGLE("Angle"), ALTITUDE("Altimeter"), COMPASS("Compass"), LIGHT("Light"),
    SOUND("Sound"), VIBRATION("Vibration"), STUD("Stud finder"),
}

@Serializable
data class Detail(val label: String, val value: Double, val quantity: Quantity)

@Serializable
enum class ShapeKind { BOX, CYLINDER, SPHERE }

@Serializable
enum class Dim { L, W, H }

@Serializable
enum class EstimateSource(val label: String) { AR("AR"), MOTION("Motion"), PHOTO("Photo"), MANUAL("Manual") }

/** One independent measurement of one dimension of an object. */
@Serializable
data class DimEstimate(
    val dim: Dim,
    val value: Double,
    val sigma: Double,
    val source: EstimateSource,
    val note: String = "",
)

/** "dimA is ratio × dimB", from a perspective photo without a reference object. */
@Serializable
data class RatioConstraint(val a: Dim, val b: Dim, val ratio: Double, val relSigma: Double, val note: String = "")

@Serializable
enum class Face { FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM }

@Serializable
data class ObjectModel(
    val name: String = "Object",
    val shape: ShapeKind = ShapeKind.BOX,
    val length: Double,
    val width: Double,
    val height: Double,
    val sigmaL: Double = 0.0,
    val sigmaW: Double = 0.0,
    val sigmaH: Double = 0.0,
    val estimates: List<DimEstimate> = emptyList(),
    val ratios: List<RatioConstraint> = emptyList(),
    /** Face → absolute path of a rectified texture photo. */
    val textures: Map<Face, String> = emptyMap(),
    val material: String = "Cardboard",
)

@Serializable
data class Measurement(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val kind: MeasureKind,
    val title: String,
    /** Primary value in SI units. */
    val value: Double,
    val quantity: Quantity,
    val details: List<Detail> = emptyList(),
    val method: String = "",
    val sigma: Double? = null,
    val note: String = "",
    val marks: List<Double> = emptyList(),
    val objectModel: ObjectModel? = null,
    val pinned: Boolean = false,
)

/** Saved measurements, persisted as one small JSON file in app-private storage. */
class LibraryRepository(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "library.json")
    val photosDir: File = File(context.filesDir, "photos").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<Measurement>>(emptyList())
    val items: StateFlow<List<Measurement>> = _items.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching {
                if (file.exists()) json.decodeFromString<List<Measurement>>(file.readText()) else emptyList()
            }.getOrElse { emptyList() }
            _items.value = loaded.sortedByDescending { it.createdAt }
        }
    }

    fun add(m: Measurement) = mutate { listOf(m) + it }

    fun update(m: Measurement) = mutate { list -> list.map { if (it.id == m.id) m else it } }

    fun delete(id: String) = mutate { list ->
        list.firstOrNull { it.id == id }?.objectModel?.textures?.values?.forEach { path ->
            runCatching { File(path).takeIf { f -> f.parentFile == photosDir }?.delete() }
        }
        list.filterNot { it.id == id }
    }

    fun get(id: String): Measurement? = _items.value.firstOrNull { it.id == id }

    private fun mutate(transform: (List<Measurement>) -> List<Measurement>) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val next = transform(_items.value)
                _items.value = next
                val tmp = File(file.parentFile, "library.json.tmp")
                tmp.writeText(json.encodeToString(next))
                tmp.renameTo(file)
            }
        }
    }
}
