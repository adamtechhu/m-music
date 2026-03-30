package com.mmusic.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mmusic.app.data.DarkModeLevel
import com.mmusic.app.data.UiStyle

private val BaseScheme = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF0F1738),
    secondary = Color(0xFF7AD7C4),
    background = Color(0xFF0A0D12),
    surface = Color(0xFF151A22),
    surfaceVariant = Color(0xFF212835),
    primaryContainer = Color(0xFF28324A)
)

private val NeonGridScheme = darkColorScheme(
    primary = Color(0xFF53E6FF),
    onPrimary = Color(0xFF00161C),
    secondary = Color(0xFF7BFFB0),
    background = Color(0xFF05070D),
    surface = Color(0xFF101624),
    surfaceVariant = Color(0xFF172135),
    primaryContainer = Color(0xFF0D2B3D)
)

private val MidnightWaveScheme = darkColorScheme(
    primary = Color(0xFF7AA2FF),
    onPrimary = Color(0xFF04132E),
    secondary = Color(0xFF4FE0C5),
    background = Color(0xFF090B12),
    surface = Color(0xFF151A25),
    surfaceVariant = Color(0xFF20283A),
    primaryContainer = Color(0xFF172542)
)

private val CarbonPulseScheme = darkColorScheme(
    primary = Color(0xFFFF7A59),
    onPrimary = Color(0xFF2A0B00),
    secondary = Color(0xFFFFC857),
    background = Color(0xFF0D0C0C),
    surface = Color(0xFF1C1818),
    surfaceVariant = Color(0xFF2C2525),
    primaryContainer = Color(0xFF3A211A)
)

private val AuroraFlowScheme = darkColorScheme(
    primary = Color(0xFF9EE7FF),
    onPrimary = Color(0xFF062129),
    secondary = Color(0xFF7DFFA6),
    background = Color(0xFF071016),
    surface = Color(0xFF101D24),
    surfaceVariant = Color(0xFF18303B),
    primaryContainer = Color(0xFF12323D)
)

private val ObsidianInkScheme = darkColorScheme(
    primary = Color(0xFFE6E6E6),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFF8BA7B8),
    background = Color(0xFF050505),
    surface = Color(0xFF0E0E10),
    surfaceVariant = Color(0xFF1B1B1F),
    primaryContainer = Color(0xFF202329)
)

private val SolarDriftScheme = darkColorScheme(
    primary = Color(0xFFFFB347),
    onPrimary = Color(0xFF2A1600),
    secondary = Color(0xFFFF7A59),
    background = Color(0xFF120B08),
    surface = Color(0xFF211511),
    surfaceVariant = Color(0xFF35211A),
    primaryContainer = Color(0xFF47291A)
)

private val CrimsonNoirScheme = darkColorScheme(
    primary = Color(0xFFFF6B81),
    onPrimary = Color(0xFF30030E),
    secondary = Color(0xFFFFB86B),
    background = Color(0xFF13070B),
    surface = Color(0xFF251217),
    surfaceVariant = Color(0xFF3A1E25),
    primaryContainer = Color(0xFF4D1F2B)
)

private val OceanGlassScheme = darkColorScheme(
    primary = Color(0xFF67D6FF),
    onPrimary = Color(0xFF04202A),
    secondary = Color(0xFF7EF0E0),
    background = Color(0xFF05131A),
    surface = Color(0xFF0F232C),
    surfaceVariant = Color(0xFF183540),
    primaryContainer = Color(0xFF114150)
)

private val ForestEchoScheme = darkColorScheme(
    primary = Color(0xFF8DDC6F),
    onPrimary = Color(0xFF132A09),
    secondary = Color(0xFFC8F284),
    background = Color(0xFF081108),
    surface = Color(0xFF142116),
    surfaceVariant = Color(0xFF223326),
    primaryContainer = Color(0xFF2B4330)
)

private val SunsetFluxScheme = darkColorScheme(
    primary = Color(0xFFFF8A5B),
    onPrimary = Color(0xFF321005),
    secondary = Color(0xFFFFD166),
    background = Color(0xFF140A07),
    surface = Color(0xFF241511),
    surfaceVariant = Color(0xFF39231D),
    primaryContainer = Color(0xFF4A2B20)
)

@Composable
fun MMusicTheme(
    style: UiStyle,
    darkModeLevel: DarkModeLevel = DarkModeLevel.Standard,
    content: @Composable () -> Unit
) {
    val baseScheme = when (style) {
        UiStyle.Base -> BaseScheme
        UiStyle.NeonGrid -> NeonGridScheme
        UiStyle.MidnightWave -> MidnightWaveScheme
        UiStyle.CarbonPulse -> CarbonPulseScheme
        UiStyle.AuroraFlow -> AuroraFlowScheme
        UiStyle.ObsidianInk -> ObsidianInkScheme
        UiStyle.SolarDrift -> SolarDriftScheme
        UiStyle.CrimsonNoir -> CrimsonNoirScheme
        UiStyle.OceanGlass -> OceanGlassScheme
        UiStyle.ForestEcho -> ForestEchoScheme
        UiStyle.SunsetFlux -> SunsetFluxScheme
    }
    val colorScheme = if (style == UiStyle.Base && darkModeLevel == DarkModeLevel.Extra) {
        baseScheme.copy(
            background = Color(0xFF020202),
            surface = baseScheme.surface.copy(alpha = 0.78f),
            surfaceVariant = baseScheme.surfaceVariant.copy(alpha = 0.85f)
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
