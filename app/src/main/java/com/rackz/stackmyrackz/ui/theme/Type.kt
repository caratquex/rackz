package com.rackz.stackmyrackz.ui.theme

import androidx.compose.material3.Typography

private val DefaultTypography = Typography()

val RacksTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(
        fontFeatureSettings = "tnum"
    ),
    displayMedium = DefaultTypography.displayMedium.copy(
        fontFeatureSettings = "tnum"
    ),
    displaySmall = DefaultTypography.displaySmall.copy(
        fontFeatureSettings = "tnum"
    ),
    headlineLarge = DefaultTypography.headlineLarge.copy(
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = DefaultTypography.headlineMedium.copy(
        fontFeatureSettings = "tnum"
    )
)
