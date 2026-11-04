// Generated: the same recipe in every app of the suite (Synchrotron, Mémento, Ecoutify, Météorite, Échoppe).
package app.echoppe.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Fixed brand colors (never the wallpaper's), from the app accent #D9542B (the lanterns' vermilion):
 * Material's tonal-spot scheme for surfaces and containers, the accent at full strength for `primary`.
 */
internal val SuiteLightColors = lightColorScheme(
    primary = Color(0xFFAC340B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD1),
    onPrimaryContainer = Color(0xFF723522),
    inversePrimary = Color(0xFFFFB59F),
    secondary = Color(0xFF77574E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF5D4037),
    tertiary = Color(0xFF6B5D2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E1A7),
    onTertiaryContainer = Color(0xFF524619),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED7),
    onSurfaceVariant = Color(0xFF53433F),
    surfaceTint = Color(0xFFAC340B),
    inverseSurface = Color(0xFF392E2B),
    inverseOnSurface = Color(0xFFFFEDE8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceContainer = Color(0xFFFCEAE5),
    surfaceContainerHigh = Color(0xFFF7E4E0),
    surfaceContainerHighest = Color(0xFFF1DFDA),
    surfaceContainerLow = Color(0xFFFFF1ED),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE8D6D2),
)

internal val SuiteDarkColors = darkColorScheme(
    primary = Color(0xFFFFB59F),
    onPrimary = Color(0xFF561F0F),
    primaryContainer = Color(0xFF723522),
    onPrimaryContainer = Color(0xFFFFDBD1),
    inversePrimary = Color(0xFFAC340B),
    secondary = Color(0xFFE7BDB2),
    onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFFD8C58D),
    onTertiary = Color(0xFF3B2F05),
    tertiaryContainer = Color(0xFF524619),
    onTertiaryContainer = Color(0xFFF5E1A7),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF1DFDA),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    surfaceTint = Color(0xFFFFB59F),
    inverseSurface = Color(0xFFF1DFDA),
    inverseOnSurface = Color(0xFF392E2B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08C87),
    outlineVariant = Color(0xFF53433F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF423734),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D322F),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceDim = Color(0xFF1A110F),
)

/** Rounded shapes shared by the whole suite. */
internal val SuiteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
