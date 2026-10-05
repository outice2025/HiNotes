package com.hiapps.hinotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// The Mono fallback scheme, used on devices without wallpaper-based dynamic color
// (Android 11 and below) or when the user turns dynamic color off.
//
// Every value is taken verbatim from the design specification; the UI only ever reads
// these through Material 3 color roles, never as literal colors.

internal val MonoLightColorScheme: ColorScheme = lightColorScheme(
    primary = ColorTokens.Light.Primary,
    onPrimary = ColorTokens.Light.OnPrimary,
    primaryContainer = ColorTokens.Light.PrimaryContainer,
    onPrimaryContainer = ColorTokens.Light.OnPrimaryContainer,
    inversePrimary = ColorTokens.Light.InversePrimary,

    secondary = ColorTokens.Light.Secondary,
    onSecondary = ColorTokens.Light.OnSecondary,
    secondaryContainer = ColorTokens.Light.SecondaryContainer,
    onSecondaryContainer = ColorTokens.Light.OnSecondaryContainer,

    tertiary = ColorTokens.Light.Tertiary,
    onTertiary = ColorTokens.Light.OnTertiary,
    tertiaryContainer = ColorTokens.Light.TertiaryContainer,
    onTertiaryContainer = ColorTokens.Light.OnTertiaryContainer,

    background = ColorTokens.Light.Surface,
    onBackground = ColorTokens.Light.OnSurface,

    surface = ColorTokens.Light.Surface,
    onSurface = ColorTokens.Light.OnSurface,
    surfaceVariant = ColorTokens.Light.SurfaceContainerHighest,
    onSurfaceVariant = ColorTokens.Light.OnSurfaceVariant,
    surfaceContainerLowest = ColorTokens.Light.SurfaceContainerLowest,
    surfaceContainerLow = ColorTokens.Light.SurfaceContainerLow,
    surfaceContainer = ColorTokens.Light.SurfaceContainer,
    surfaceContainerHigh = ColorTokens.Light.SurfaceContainerHigh,
    surfaceContainerHighest = ColorTokens.Light.SurfaceContainerHighest,

    surfaceTint = ColorTokens.Light.Primary,
    inverseSurface = ColorTokens.Light.InverseSurface,
    inverseOnSurface = ColorTokens.Light.InverseOnSurface,

    outline = ColorTokens.Light.Outline,
    outlineVariant = ColorTokens.Light.OutlineVariant,
    scrim = ColorTokens.Scrim,

    error = ColorTokens.Light.Error,
    onError = ColorTokens.Light.OnError,
    errorContainer = ColorTokens.Light.ErrorContainer,
    onErrorContainer = ColorTokens.Light.OnErrorContainer,
)

internal val MonoDarkColorScheme: ColorScheme = darkColorScheme(
    primary = ColorTokens.Dark.Primary,
    onPrimary = ColorTokens.Dark.OnPrimary,
    primaryContainer = ColorTokens.Dark.PrimaryContainer,
    onPrimaryContainer = ColorTokens.Dark.OnPrimaryContainer,
    inversePrimary = ColorTokens.Dark.InversePrimary,

    secondary = ColorTokens.Dark.Secondary,
    onSecondary = ColorTokens.Dark.OnSecondary,
    secondaryContainer = ColorTokens.Dark.SecondaryContainer,
    onSecondaryContainer = ColorTokens.Dark.OnSecondaryContainer,

    tertiary = ColorTokens.Dark.Tertiary,
    onTertiary = ColorTokens.Dark.OnTertiary,
    tertiaryContainer = ColorTokens.Dark.TertiaryContainer,
    onTertiaryContainer = ColorTokens.Dark.OnTertiaryContainer,

    background = ColorTokens.Dark.Surface,
    onBackground = ColorTokens.Dark.OnSurface,

    surface = ColorTokens.Dark.Surface,
    onSurface = ColorTokens.Dark.OnSurface,
    surfaceVariant = ColorTokens.Dark.SurfaceContainerHighest,
    onSurfaceVariant = ColorTokens.Dark.OnSurfaceVariant,
    surfaceContainerLowest = ColorTokens.Dark.SurfaceContainerLowest,
    surfaceContainerLow = ColorTokens.Dark.SurfaceContainerLow,
    surfaceContainer = ColorTokens.Dark.SurfaceContainer,
    surfaceContainerHigh = ColorTokens.Dark.SurfaceContainerHigh,
    surfaceContainerHighest = ColorTokens.Dark.SurfaceContainerHighest,

    surfaceTint = ColorTokens.Dark.Primary,
    inverseSurface = ColorTokens.Dark.InverseSurface,
    inverseOnSurface = ColorTokens.Dark.InverseOnSurface,

    outline = ColorTokens.Dark.Outline,
    outlineVariant = ColorTokens.Dark.OutlineVariant,
    scrim = ColorTokens.Scrim,

    error = ColorTokens.Dark.Error,
    onError = ColorTokens.Dark.OnError,
    errorContainer = ColorTokens.Dark.ErrorContainer,
    onErrorContainer = ColorTokens.Dark.OnErrorContainer,
)

/**
 * OLED dark: the same dark scheme with every surface role flattened to true black so an
 * OLED panel draws nothing. Content colors are untouched, which keeps contrast against black.
 */
internal val MonoOledDarkColorScheme: ColorScheme = MonoDarkColorScheme.copy(
    surface = ColorTokens.Black,
    background = ColorTokens.Black,
    surfaceContainerLowest = ColorTokens.Black,
    surfaceContainerLow = ColorTokens.Oled.SurfaceContainerLow,
    surfaceContainer = ColorTokens.Oled.SurfaceContainer,
    surfaceContainerHigh = ColorTokens.Oled.SurfaceContainerHigh,
    surfaceContainerHighest = ColorTokens.Oled.SurfaceContainerHighest,
    surfaceVariant = ColorTokens.Oled.SurfaceContainerHighest,
)
