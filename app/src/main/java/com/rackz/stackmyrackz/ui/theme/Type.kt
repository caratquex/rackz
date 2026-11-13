package com.rackz.stackmyrackz.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rackz.stackmyrackz.R

fun roundness(value: Float): FontVariation.Setting {
    return FontVariation.Setting("ROND", value.coerceIn(0f, 100f))
}

internal fun safeVariableWidth(width: Float): Float {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) width else 100f
}

@OptIn(ExperimentalTextApi::class)
fun googleSansFlex(
    weight: Int = 400,
    width: Float = 100f,
    isRounded: Boolean = true
): FontFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(safeVariableWidth(width)),
            roundness(if (isRounded) 100f else 0f)
        )
    )
)

private val GoogleSansFlexBaseRounded = googleSansFlex(isRounded = true)

val RacksTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum"
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        fontFeatureSettings = "tnum"
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFlexBaseRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

private val DisplayLargeCondensedStyle = TextStyle(
    fontFamily = googleSansFlex(500, 65f, true),
    fontSize = 64.sp,
    lineHeight = 72.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum"
)

private val DisplayMediumCondensedStyle = TextStyle(
    fontFamily = googleSansFlex(700, 75f, true),
    fontSize = 52.sp,
    lineHeight = 60.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum"
)

private val DisplaySmallCondensedStyle = TextStyle(
    fontFamily = googleSansFlex(600, 75f, true),
    fontSize = 44.sp,
    lineHeight = 52.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum"
)

private val DisplayMediumEmphasizedToken = TextStyle(
    fontFamily = googleSansFlex(600, 132f, true),
    fontSize = 52.sp,
    lineHeight = 60.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum"
)

private val TitleLargeCondensedStyle = TextStyle(
    fontFamily = googleSansFlex(700, 85f, true),
    fontSize = 24.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.15.sp
)

private val TitleSmallCondensedStyle = TextStyle(
    fontFamily = googleSansFlex(400, 85f, true),
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp
)

val Typography.displayLargeCondensed: TextStyle
    get() = DisplayLargeCondensedStyle

val Typography.displayMediumCondensed: TextStyle
    get() = DisplayMediumCondensedStyle

val Typography.displaySmallCondensed: TextStyle
    get() = DisplaySmallCondensedStyle

val Typography.displayMediumEmphasizedStyle: TextStyle
    get() = DisplayMediumEmphasizedToken

val Typography.titleLargeCondensed: TextStyle
    get() = TitleLargeCondensedStyle

val Typography.titleSmallCondensed: TextStyle
    get() = TitleSmallCondensedStyle
