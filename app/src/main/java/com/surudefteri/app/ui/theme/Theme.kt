package com.surudefteri.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Açık tema: güneş altında, merada okunaklı olsun. Çayır yeşili + buğday sarısı + krem.
val Meadow = Color(0xFF2F6B3E)
val MeadowDark = Color(0xFF1E4A2A)
val MeadowSoft = Color(0xFFDCEBDD)
val Wheat = Color(0xFFF2B544)
val WheatSoft = Color(0xFFFBEBC6)
val Cream = Color(0xFFF6F1E6)
val Paper = Color(0xFFFFFCF6)
val Soil = Color(0xFF3B3226)
val SoilDim = Color(0xFF7A6E5E)
val Line = Color(0xFFE6DCC8)
val Alert = Color(0xFFC0392B)

private val scheme = lightColorScheme(
    primary = Meadow,
    onPrimary = Color.White,
    primaryContainer = MeadowSoft,
    onPrimaryContainer = MeadowDark,
    secondary = Wheat,
    onSecondary = Soil,
    secondaryContainer = WheatSoft,
    onSecondaryContainer = Soil,
    background = Cream,
    onBackground = Soil,
    surface = Cream,
    onSurface = Soil,
    surfaceVariant = Paper,
    onSurfaceVariant = SoilDim,
    surfaceContainer = Paper,
    surfaceContainerHigh = Color(0xFFF3ECDD),
    surfaceContainerHighest = Line,
    outline = Line,
    outlineVariant = Line,
    error = Alert,
)

private val base = Typography()
private val typography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun SuruTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
