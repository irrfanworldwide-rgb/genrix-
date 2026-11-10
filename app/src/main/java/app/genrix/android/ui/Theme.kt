package app.genrix.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The fixed brand palette (dark first). Screens use the semantic names further down instead. */
object GenrixColors {
    val Background = Color(0xFF070A12)
    val BackgroundElevated = Color(0xFF0B1120)
    val Card = Color(0xFF101827)
    val CardElevated = Color(0xFF151F31)
    val Cyan = Color(0xFF64D9FF)
    val Violet = Color(0xFF8B7CFF)
    val Glow = Color(0xFF3B82F6)
    val TextPrimary = Color(0xFFF2F6FF)
    val TextSecondary = Color(0xFF9BAAC2)
    val TextMuted = Color(0xFF66758D)
    val Success = Color(0xFF55E6A5)
    val Warning = Color(0xFFFFC66D)
    val Error = Color(0xFFFF6B7A)
}

/**
 * The two typefaces, in one place. They are the system sans and monospace families so the app
 * works offline and reads Hindi, English and Hinglish everywhere. To use Manrope and
 * JetBrains Mono, add the font files under res/font and point these two values at them.
 */
object GenrixFonts {
    val Ui: FontFamily = FontFamily.SansSerif
    val Mono: FontFamily = FontFamily.Monospace
}

private val Dark = darkColorScheme(
    primary = GenrixColors.Cyan,
    onPrimary = Color(0xFF04222B),
    primaryContainer = Color(0xFF0E3A4A),
    onPrimaryContainer = Color(0xFFBDEFFF),
    secondary = GenrixColors.Violet,
    onSecondary = Color(0xFF100B33),
    tertiary = GenrixColors.Warning,
    background = GenrixColors.Background,
    onBackground = GenrixColors.TextPrimary,
    surface = GenrixColors.Background,
    onSurface = GenrixColors.TextPrimary,
    surfaceVariant = GenrixColors.Card,
    onSurfaceVariant = GenrixColors.TextSecondary,
    surfaceContainer = GenrixColors.BackgroundElevated,
    surfaceContainerHigh = GenrixColors.CardElevated,
    outline = Color(0xFF2A3650),
    outlineVariant = Color(0xFF1C2638),
    error = GenrixColors.Error,
)

private val Light = lightColorScheme(
    primary = Color(0xFF006B8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6EEFF),
    onPrimaryContainer = Color(0xFF001F2B),
    secondary = Color(0xFF5B4FD6),
    onSecondary = Color.White,
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF3F6FC),
    onBackground = Color(0xFF0B1220),
    surface = Color(0xFFF3F6FC),
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF4A5A73),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE9EFFA),
    outline = Color(0xFFC9D3E6),
    outlineVariant = Color(0xFFE1E8F4),
    error = Color(0xFFB3261E),
)

// Type scale: Display = the assistant's state, Headline = screen titles, Title = cards and
// settings groups, Body = descriptions, Label = status and compact metadata.
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = GenrixFonts.Mono, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp),
    headlineSmall = TextStyle(fontFamily = GenrixFonts.Ui, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = GenrixFonts.Ui, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = GenrixFonts.Ui, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = GenrixFonts.Mono, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
    bodyLarge = TextStyle(fontFamily = GenrixFonts.Ui, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = GenrixFonts.Ui, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = GenrixFonts.Ui, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = GenrixFonts.Ui, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = GenrixFonts.Mono, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

private val Corners = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Follows the phone's light or dark setting; dark is the signature look. */
@Composable
fun GenrixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Type,
        shapes = Corners,
        content = content,
    )
}

// Semantic colour names the screens use; they follow light and dark mode.
val Accent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val AccentAlt: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
val Background: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val Surface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val Raised: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val Muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Warning: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val MutedDim: Color @Composable get() = if (isSystemInDarkTheme()) GenrixColors.TextMuted else Color(0xFF7A879C)
val Positive: Color @Composable get() = if (isSystemInDarkTheme()) GenrixColors.Success else Color(0xFF0E8F5B)
