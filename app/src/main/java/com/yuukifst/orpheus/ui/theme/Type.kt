package com.yuukifst.orpheus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yuukifst.orpheus.R

val JetBrainsMonoNerd = FontFamily(
    Font(R.font.jetbrains_mono_nerd_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_nerd_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_nerd_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_nerd_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_nerd_extrabold, FontWeight.ExtraBold),
)

val MontserratFamily = JetBrainsMonoNerd
val RoundedSans = JetBrainsMonoNerd

private const val TabularNums = "tnum"
private const val LabelTrackingSp = 0.8f

val ExpTitleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 52.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    displayMedium = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 44.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    titleMedium = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.25).sp,
        lineHeight = 32.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = TabularNums
    ),
    bodySmall = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = TabularNums
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        letterSpacing = LabelTrackingSp.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoNerd,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = LabelTrackingSp.sp
    )
)
