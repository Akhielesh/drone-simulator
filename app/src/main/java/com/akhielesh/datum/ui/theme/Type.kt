package com.akhielesh.datum.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.akhielesh.datum.R

/*
 * Inter is the closest open-source relative of Apple's SF Pro. One variable font file provides every
 * weight, and its optical-size axis gives a tighter "Display" cut for the big readouts (like SF
 * Pro Display versus SF Pro Text).
 */
private fun inter(weight: Int, opticalSize: Float) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

val InterText = FontFamily(
    inter(300, 14f), inter(400, 14f), inter(500, 14f), inter(600, 14f), inter(700, 14f),
)

val InterDisplay = FontFamily(
    inter(100, 32f), inter(200, 32f), inter(300, 32f), inter(400, 32f), inter(500, 32f), inter(600, 32f), inter(700, 32f),
)

private const val TABULAR = "tnum"

@Immutable
data class DatumTypography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val sectionLabel: TextStyle,
    /** Giant thin numerals for the primary readout of a tool. */
    val readoutXL: TextStyle,
    val readoutL: TextStyle,
    val readoutM: TextStyle,
    val readoutS: TextStyle,
    val mono: TextStyle,
) {
    companion object {
        private fun text(size: Float, weight: FontWeight, tracking: TextUnit = 0.em, lineHeight: Float = size * 1.28f) = TextStyle(
            fontFamily = InterText,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = lineHeight.sp,
            letterSpacing = tracking,
        )

        private fun display(size: Float, weight: FontWeight, tracking: TextUnit) = TextStyle(
            fontFamily = InterDisplay,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = (size * 1.08f).sp,
            letterSpacing = tracking,
            fontFeatureSettings = TABULAR,
        )

        val Default = DatumTypography(
            largeTitle = text(31f, FontWeight.SemiBold, (-0.022).em, 37f).copy(fontFamily = InterDisplay),
            title1 = text(25f, FontWeight.SemiBold, (-0.02).em, 31f).copy(fontFamily = InterDisplay),
            title2 = text(20f, FontWeight.SemiBold, (-0.017).em, 25f),
            title3 = text(17.5f, FontWeight.SemiBold, (-0.014).em, 23f),
            headline = text(15.5f, FontWeight.SemiBold, (-0.011).em, 21f),
            body = text(15f, FontWeight.Normal, (-0.009).em, 21f),
            callout = text(14f, FontWeight.Normal, (-0.006).em, 19f),
            subhead = text(13f, FontWeight.Medium, (-0.003).em, 18f),
            footnote = text(12f, FontWeight.Normal, 0.em, 16f),
            caption = text(11f, FontWeight.Medium, 0.005.em, 14f),
            sectionLabel = text(11.5f, FontWeight.SemiBold, 0.06.em, 14f),
            readoutXL = display(84f, FontWeight.ExtraLight, (-0.045).em),
            readoutL = display(60f, FontWeight.Light, (-0.035).em),
            readoutM = display(38f, FontWeight.Light, (-0.025).em),
            readoutS = display(24f, FontWeight.Normal, (-0.015).em),
            mono = text(13f, FontWeight.Medium, 0.em).copy(fontFeatureSettings = TABULAR),
        )
    }
}

val LocalTypography = staticCompositionLocalOf { DatumTypography.Default }
