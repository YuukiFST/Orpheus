package com.yuukifst.orpheus.ui.theme

import androidx.compose.ui.graphics.Color

// OKLCH-derived terminal phosphor palette — green-tinted neutrals, desaturated accent.
// Dark ink: oklch(0.13 0.012 138) | Accent: oklch(0.74 0.17 142) | Frost: oklch(0.91 0.014 138)
val VantaBlack = Color(0xFF0E100D)
val VantaWhite = Color(0xFFE4EAE2)
val VantaAccent = Color(0xFF5BE048)
val VantaGray0 = Color(0xFF141714)
val VantaGray1 = Color(0xFF242823)
val VantaGray2 = Color(0xFF6B7568)
val VantaGray3 = Color(0xFF8A9486)
val VantaGray4 = Color(0xFFB8C0B4)
val VantaHazard = Color(0xFFFF4D4D)
val VantaPhosphorGlow = Color(0x4D5BE048)
val VantaPhosphorDim = Color(0xFF2A4526)
val VantaPhosphorBright = Color(0xFF72FF5C)

// Legacy aliases kept for any downstream references
val OrpheusPurpleDark = VantaBlack
val OrpheusPurplePrimary = VantaAccent
val OrpheusPink = VantaGray2
val OrpheusOrange = VantaGray3
val OrpheusLightPurple = VantaGray4
val OrpheusWhite = VantaWhite
val OrpheusBlack = VantaBlack
val OrpheusSurface = VantaGray0

// Monochrome palette — light: white + black, dark: black + white
val MonoWhite = Color(0xFFFFFFFF)
val MonoBlack = Color(0xFF000000)

// Light mode — oklch(0.96 0.008 138) background, deep forest primary
val LightBackground = Color(0xFFF2F5F0)
val LightSurface = Color(0xFFF8FAF6)
val LightSurfaceVariant = Color(0xFFE6EBE3)
val LightOnSurface = Color(0xFF121412)
val LightOnSurfaceVariant = Color(0xFF4A5248)
val LightPrimary = Color(0xFF2A5C24)
val LightPrimaryContainer = Color(0xFFD4EDCE)
val LightOnPrimaryContainer = Color(0xFF0A1F08)
val LightOutline = Color(0xFF8A9A84)

// Sakura — creative pink (Settings-only)
val SakuraBackground = Color(0xFFFFF5F7)
val SakuraSurface = Color(0xFFFFE8EE)
val SakuraSurfaceHigh = Color(0xFFFFD6E0)
val SakuraOnBackground = Color(0xFF5C2A3A)
val SakuraPrimary = Color(0xFFE85A8C)
val SakuraPrimaryContainer = Color(0xFFFFB7C5)
val SakuraOnPrimary = Color(0xFFFFFFFF)
val SakuraSecondary = Color(0xFFF2A0B8)
val SakuraTertiary = Color(0xFFC45C7A)
val SakuraError = Color(0xFFD64545)
val SakuraOutline = Color(0xFFE8A0B5)
