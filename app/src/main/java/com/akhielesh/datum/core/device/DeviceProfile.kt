package com.akhielesh.datum.core.device

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Physical facts about the phone that measuring depends on: its body size (the phone itself is a
 * known-length ruler, and a lever arm for corner touching) and its true screen pixel density.
 */
data class DeviceProfile(
    val marketingName: String,
    val family: Family,
    /** Body size in millimetres, portrait: height (long edge) × width × depth. */
    val heightMm: Double,
    val widthMm: Double,
    val depthMm: Double,
    val screenDiagonalIn: Double,
    val nativeShortPx: Int,
    val nativeLongPx: Int,
    val hasUwb: Boolean,
    /** True when the dimensions come from the built-in catalogue rather than an estimate. */
    val catalogued: Boolean,
    val approximate: Boolean = false,
) {
    enum class Family { GALAXY_S, GALAXY_S_FE, GALAXY_Z, OTHER }

    val isTunedGalaxy: Boolean get() = catalogued && family != Family.OTHER

    /** Native panel density, from the panel's resolution and diagonal. */
    val nativePpi: Double get() = hypot(nativeShortPx.toDouble(), nativeLongPx.toDouble()) / screenDiagonalIn
}

object DeviceCatalog {
    private data class Entry(
        val prefix: String,
        val name: String,
        val family: DeviceProfile.Family,
        val h: Double, val w: Double, val d: Double,
        val diag: Double, val shortPx: Int, val longPx: Int,
        val uwb: Boolean,
        val approximate: Boolean = false,
    )

    private val S = DeviceProfile.Family.GALAXY_S
    private val FE = DeviceProfile.Family.GALAXY_S_FE
    private val Z = DeviceProfile.Family.GALAXY_Z

    // Galaxy S22 and newer — the devices this app is tuned for. Model codes are matched by prefix
    // so every regional variant (SM-S908B, SM-S908U1, SM-S9080, …) resolves to its family.
    private val entries = listOf(
        Entry("SM-S901", "Galaxy S22", S, 146.0, 70.6, 7.6, 6.1, 1080, 2340, uwb = false),
        Entry("SM-S906", "Galaxy S22+", S, 157.4, 75.8, 7.6, 6.6, 1080, 2340, uwb = true),
        Entry("SM-S908", "Galaxy S22 Ultra", S, 163.3, 77.9, 8.9, 6.8, 1440, 3088, uwb = true),
        Entry("SM-S911", "Galaxy S23", S, 146.3, 70.9, 7.6, 6.1, 1080, 2340, uwb = false),
        Entry("SM-S916", "Galaxy S23+", S, 157.8, 76.2, 7.6, 6.6, 1080, 2340, uwb = true),
        Entry("SM-S918", "Galaxy S23 Ultra", S, 163.4, 78.1, 8.9, 6.8, 1440, 3088, uwb = true),
        Entry("SM-S711", "Galaxy S23 FE", FE, 158.0, 76.5, 8.2, 6.4, 1080, 2340, uwb = false),
        Entry("SM-S921", "Galaxy S24", S, 147.0, 70.6, 7.6, 6.2, 1080, 2340, uwb = false),
        Entry("SM-S926", "Galaxy S24+", S, 158.5, 75.9, 7.7, 6.7, 1440, 3120, uwb = true),
        Entry("SM-S928", "Galaxy S24 Ultra", S, 162.3, 79.0, 8.6, 6.8, 1440, 3120, uwb = true),
        Entry("SM-S721", "Galaxy S24 FE", FE, 162.0, 77.3, 8.0, 6.7, 1080, 2340, uwb = false),
        Entry("SM-S931", "Galaxy S25", S, 146.9, 70.5, 7.2, 6.2, 1080, 2340, uwb = false),
        Entry("SM-S936", "Galaxy S25+", S, 158.4, 75.8, 7.3, 6.7, 1440, 3120, uwb = true),
        Entry("SM-S938", "Galaxy S25 Ultra", S, 162.8, 77.6, 8.2, 6.9, 1440, 3120, uwb = true),
        Entry("SM-S937", "Galaxy S25 Edge", S, 158.2, 75.6, 5.8, 6.7, 1440, 3120, uwb = false),
        Entry("SM-S731", "Galaxy S25 FE", FE, 161.3, 76.6, 7.4, 6.7, 1080, 2340, uwb = false),
        Entry("SM-S942", "Galaxy S26", S, 149.6, 71.7, 7.2, 6.3, 1080, 2340, uwb = false, approximate = true),
        Entry("SM-S947", "Galaxy S26+", S, 158.4, 75.8, 7.3, 6.7, 1440, 3120, uwb = true, approximate = true),
        Entry("SM-S948", "Galaxy S26 Ultra", S, 163.6, 78.1, 7.9, 6.9, 1440, 3120, uwb = true, approximate = true),
        // Foldables: dimensions are for the way the phone is used to measure (Fold folded, Flip open).
        Entry("SM-F936", "Galaxy Z Fold4", Z, 155.1, 67.1, 15.8, 6.2, 904, 2316, uwb = true),
        Entry("SM-F946", "Galaxy Z Fold5", Z, 154.9, 67.1, 13.4, 6.2, 904, 2316, uwb = true),
        Entry("SM-F956", "Galaxy Z Fold6", Z, 153.5, 68.1, 12.1, 6.3, 968, 2376, uwb = true),
        Entry("SM-F966", "Galaxy Z Fold7", Z, 158.4, 72.8, 8.9, 6.5, 1080, 2520, uwb = true, approximate = true),
        Entry("SM-F721", "Galaxy Z Flip4", Z, 165.2, 71.9, 6.9, 6.7, 1080, 2640, uwb = false),
        Entry("SM-F731", "Galaxy Z Flip5", Z, 165.1, 71.9, 6.9, 6.7, 1080, 2640, uwb = false),
        Entry("SM-F741", "Galaxy Z Flip6", Z, 165.1, 71.9, 6.9, 6.7, 1080, 2640, uwb = false),
        Entry("SM-F766", "Galaxy Z Flip7", Z, 166.7, 75.2, 6.5, 6.9, 1080, 2520, uwb = false, approximate = true),
    )

    fun detect(context: Context): DeviceProfile {
        val model = Build.MODEL.uppercase()
        entries.firstOrNull { model.startsWith(it.prefix) }?.let { e ->
            return DeviceProfile(
                marketingName = e.name, family = e.family,
                heightMm = e.h, widthMm = e.w, depthMm = e.d,
                screenDiagonalIn = e.diag, nativeShortPx = e.shortPx, nativeLongPx = e.longPx,
                hasUwb = e.uwb, catalogued = true, approximate = e.approximate,
            )
        }
        return estimate(context)
    }

    /** Any other Android phone: derive body size from the panel's physical size plus typical bezels. */
    private fun estimate(context: Context): DeviceProfile {
        val dm = realMetrics(context)
        val shortPx = min(dm.widthPixels, dm.heightPixels)
        val longPx = max(dm.widthPixels, dm.heightPixels)
        val xdpi = if (dm.xdpi > 100) dm.xdpi.toDouble() else dm.densityDpi.toDouble()
        val ydpi = if (dm.ydpi > 100) dm.ydpi.toDouble() else dm.densityDpi.toDouble()
        val shortMm = shortPx / xdpi * 25.4
        val longMm = longPx / ydpi * 25.4
        val diag = hypot(shortMm, longMm) / 25.4
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val name = if (Build.MODEL.startsWith(manufacturer, ignoreCase = true)) Build.MODEL else "$manufacturer ${Build.MODEL}"
        return DeviceProfile(
            marketingName = name,
            family = DeviceProfile.Family.OTHER,
            heightMm = longMm + 9.0,
            widthMm = shortMm + 4.0,
            depthMm = 8.5,
            screenDiagonalIn = diag,
            nativeShortPx = shortPx,
            nativeLongPx = longPx,
            hasUwb = false,
            catalogued = false,
            approximate = true,
        )
    }

    @Suppress("DEPRECATION")
    fun realMetrics(context: Context): DisplayMetrics {
        val dm = DisplayMetrics()
        val wm = context.getSystemService(WindowManager::class.java)
        wm.defaultDisplay.getRealMetrics(dm)
        return dm
    }

    /**
     * True physical pixels-per-inch of the panel at its *current* resolution setting. Samsung lets
     * users pick FHD+/WQHD+, and `xdpi` is not always updated, so the catalogue wins when known.
     */
    fun physicalPpi(context: Context, profile: DeviceProfile): Double {
        val dm = realMetrics(context)
        val shortPx = min(dm.widthPixels, dm.heightPixels).toDouble()
        if (profile.catalogued) {
            return profile.nativePpi * shortPx / profile.nativeShortPx
        }
        val reported = (dm.xdpi + dm.ydpi) / 2.0
        return if (reported > 100) reported else dm.densityDpi.toDouble()
    }
}
