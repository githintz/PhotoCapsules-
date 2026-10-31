package com.photocapsules.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.photocapsules.core.astro.LightType

/** Colour code shared by all screens (matches the light/body semantics of the planner). */
object Palette {
    val Background = Color(0xFF121214)
    val Surface = Color(0xFF1E1E22)
    val SurfaceHigh = Color(0xFF2A2A30)
    val Divider = Color(0xFF34343C)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFA0A0A8)
    val Accent = Color(0xFFFFC93C)
    val Sun = Color(0xFFFFC93C)
    val Sunrise = Color(0xFFFFE066)
    val Sunset = Color(0xFFFF8A2B)
    val Moon = Color(0xFF7FC8FF)
    val Moonrise = Color(0xFF9FDBFF)
    val Moonset = Color(0xFF3F6FD8)
    val MilkyWay = Color(0xFFE8E8F0)
    val Red = Color(0xFFFF4D4D)
    val Green = Color(0xFF59D98E)

    val Day = Color(0xFF8FD3FF)
    val Golden = Color(0xFFFFB347)
    val Blue = Color(0xFF3A5BD9)
    val Nautical = Color(0xFF2B3A8C)
    val Astronomical = Color(0xFF1C2357)
    val Night = Color(0xFF0B0E24)

    fun light(t: LightType): Color = when (t) {
        LightType.DAY -> Day
        LightType.GOLDEN_HOUR -> Golden
        LightType.BLUE_HOUR -> Blue
        LightType.NAUTICAL_TWILIGHT -> Nautical
        LightType.ASTRONOMICAL_TWILIGHT -> Astronomical
        LightType.NIGHT -> Night
    }
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.Black,
    secondary = Palette.Moon,
    onSecondary = Color.Black,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    outline = Palette.Divider,
)

@Composable
fun PhotoCapsulesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            bodyMedium = TextStyle(fontSize = 14.sp),
            labelSmall = TextStyle(fontSize = 11.sp),
        ),
        content = content,
    )
}
