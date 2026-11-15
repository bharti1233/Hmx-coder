package com.hmx.harness.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * HMXHARNESS Material 3 palettes.
 *
 * These are the fallback schemes used when wallpaper dynamic color is unavailable or
 * disabled (pre-Android 12 devices, or dynamicColor off). The seed is HMX electric
 * purple (#7C3AED): a deep violet primary with lavender containers so accents read as
 * branding without washing the surfaces.
 *
 * The tertiary ramp stays a neutral slate; primary carries the brand accent, so a
 * second saturated ramp would only add noise to chips and badges.
 */

internal val LightColors = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF25005A),
    secondary = Color(0xFF5B4B7D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7DDFF),
    onSecondaryContainer = Color(0xFF1B1033),
    tertiary = Color(0xFF4A5560),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9DFE6),
    onTertiaryContainer = Color(0xFF141B22),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAFAFC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF75777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4FA),
    surfaceContainer = Color(0xFFEDEEF4),
    surfaceContainerHigh = Color(0xFFE7E9EF),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Color(0xFFC9A6FF),
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFC9A6FF),
    onPrimary = Color(0xFF360076),
    primaryContainer = Color(0xFF5B21B6),
    onPrimaryContainer = Color(0xFFEDE0FF),
    secondary = Color(0xFFCBBEDD),
    onSecondary = Color(0xFF33294A),
    secondaryContainer = Color(0xFF4A3F63),
    onSecondaryContainer = Color(0xFFE7DDFF),
    tertiary = Color(0xFFB9C2CD),
    onTertiary = Color(0xFF232B33),
    tertiaryContainer = Color(0xFF39424C),
    onTertiaryContainer = Color(0xFFD8DEE6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C21),
    surfaceContainer = Color(0xFF1D2025),
    surfaceContainerHigh = Color(0xFF272A30),
    surfaceContainerHighest = Color(0xFF32353B),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    inversePrimary = Color(0xFF7C3AED),
    scrim = Color(0xFF000000),
)

/**
 * AMOLED surfaces: the dark scheme with every background role at (or near)
 * true black, where OLED pixels are fully off. Accent and content roles come
 * from the base scheme untouched, so this works over both the static palette
 * and a wallpaper-derived dynamic one.
 */
internal fun ColorScheme.amoledSurfaces(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF1B1B1F),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF101014),
    surfaceContainerHigh = Color(0xFF16161A),
    surfaceContainerHighest = Color(0xFF1D1D22),
    outlineVariant = Color(0xFF2C2C31),
)

/**
 * The only hardcoded hues in the app: status colors.
 *
 * M3 has no success/warning roles, and status must read the same regardless of the
 * (dynamic, wallpaper-derived) accent, a green "done" and a red "failed" are
 * universal. Everything else in the UI uses theme roles only.
 */
class StatusColors(val success: Color, val warning: Color)

internal val StatusLight = StatusColors(
    success = Color(0xFF157A3E),
    warning = Color(0xFF9A6A00),
)

internal val StatusDark = StatusColors(
    success = Color(0xFF83D69F),
    warning = Color(0xFFF0C97A),
)

val LocalStatusColors = staticCompositionLocalOf { StatusLight }
