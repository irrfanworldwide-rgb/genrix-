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

private val Dark = darkColorScheme(
    primary = Color(0xFFC6F432),
    onPrimary = Color(0xFF0B1200),
    primaryContainer = Color(0xFF26330A),
    onPrimaryContainer = Color(0xFFE4FF8F),
    secondary = Color(0xFFB8A1FF),
    tertiary = Color(0xFFFFB454),
    background = Color(0xFF050505),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF050505),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFA0A0A0),
    surfaceContainer = Color(0xFF0D0D0D),
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1F1F1F),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFF4A6300),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6F432),
    onPrimaryContainer = Color(0xFF0B1200),
    secondary = Color(0xFF5B4A99),
    tertiary = Color(0xFFA35F00),
    background = Color(0xFFF4F6EE),
    onBackground = Color(0xFF0B0D08),
    surface = Color(0xFFF4F6EE),
    onSurface = Color(0xFF0B0D08),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF555A4C),
    surfaceContainer = Color(0xFFFFFFFF),
    outline = Color(0xFFD6D9CA),
    outlineVariant = Color(0xFFE6E9DC),
    error = Color(0xFFB3261E),
)

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

private val Corners = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Follows the phone's light or dark setting. */
@Composable
fun GenrixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Type,
        shapes = Corners,
        content = content,
    )
}

// Short names the screens use; they follow light and dark mode.
val Accent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val Background: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val Surface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val Muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Warning: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
