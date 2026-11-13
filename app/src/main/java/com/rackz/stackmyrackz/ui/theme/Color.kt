package com.rackz.stackmyrackz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

fun combineColors(color1: Color, color2: Color, angle: Float = 0.5f): Color =
    lerp(color1, color2, angle)

val colorButton: Color
    @Composable @ReadOnlyComposable
    get() = combineColors(
        combineColors(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.surfaceVariant,
            0.76f
        ),
        MaterialTheme.colorScheme.surface,
        0.68f
    )

val colorOnButton: Color
    @Composable @ReadOnlyComposable
    get() = combineColors(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onSurface,
        0.56f
    )

val colorEditor: Color
    @Composable @ReadOnlyComposable
    get() = combineColors(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surface,
        0.56f
    )

val primaryLight = Color(0xFF516526)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFD4EC9E)
val onPrimaryContainerLight = Color(0xFF3A4D10)
val secondaryLight = Color(0xFF5A6147)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFDEE6C5)
val onSecondaryContainerLight = Color(0xFF424A31)
val tertiaryLight = Color(0xFF656015)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFEDE68C)
val onTertiaryContainerLight = Color(0xFF4D4800)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF93000A)
val backgroundLight = Color(0xFFFAFAEE)
val onBackgroundLight = Color(0xFF1A1C15)
val surfaceLight = Color(0xFFFAFAEE)
val onSurfaceLight = Color(0xFF1A1C15)
val surfaceVariantLight = Color(0xFFE2E4D4)
val onSurfaceVariantLight = Color(0xFF45483C)
val outlineLight = Color(0xFF76786B)
val outlineVariantLight = Color(0xFFC6C8B9)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2F3129)
val inverseOnSurfaceLight = Color(0xFFF1F1E5)
val inversePrimaryLight = Color(0xFFB8CF84)
val surfaceDimLight = Color(0xFFDADBCF)
val surfaceBrightLight = Color(0xFFFAFAEE)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF4F4E8)
val surfaceContainerLight = Color(0xFFEEEFE3)
val surfaceContainerHighLight = Color(0xFFE9E9DD)
val surfaceContainerHighestLight = Color(0xFFE3E3D7)

val primaryDark = Color(0xFFB8CF84)
val onPrimaryDark = Color(0xFF253500)
val primaryContainerDark = Color(0xFF3A4D10)
val onPrimaryContainerDark = Color(0xFFD4EC9E)
val secondaryDark = Color(0xFFC2CAAA)
val onSecondaryDark = Color(0xFF2C331D)
val secondaryContainerDark = Color(0xFF424A31)
val onSecondaryContainerDark = Color(0xFFDEE6C5)
val tertiaryDark = Color(0xFFD0C973)
val onTertiaryDark = Color(0xFF353200)
val tertiaryContainerDark = Color(0xFF4D4800)
val onTertiaryContainerDark = Color(0xFFEDE68C)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF12140D)
val onBackgroundDark = Color(0xFFE3E3D7)
val surfaceDark = Color(0xFF12140D)
val onSurfaceDark = Color(0xFFE3E3D7)
val surfaceVariantDark = Color(0xFF45483C)
val onSurfaceVariantDark = Color(0xFFC6C8B9)
val outlineDark = Color(0xFF909284)
val outlineVariantDark = Color(0xFF45483C)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFE3E3D7)
val inverseOnSurfaceDark = Color(0xFF2F3129)
val inversePrimaryDark = Color(0xFF516526)
val surfaceDimDark = Color(0xFF12140D)
val surfaceBrightDark = Color(0xFF383A32)
val surfaceContainerLowestDark = Color(0xFF0D0F08)
val surfaceContainerLowDark = Color(0xFF1A1C15)
val surfaceContainerDark = Color(0xFF1E2019)
val surfaceContainerHighDark = Color(0xFF292B23)
val surfaceContainerHighestDark = Color(0xFF34362E)
