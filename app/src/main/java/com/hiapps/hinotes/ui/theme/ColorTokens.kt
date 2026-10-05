package com.hiapps.hinotes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The "Light purple" accent palette, straight from the design specification.
 *
 * This and `AccentPalettes.kt` are the only places in the app where literal colour values are
 * allowed to exist. Everything else reads colours through Material 3
 * [androidx.compose.material3.ColorScheme] roles, so any palette - generated, design-specified
 * or wallpaper-derived - can be swapped in wholesale.
 */
internal object ColorTokens {

    /** Pure black, used by the OLED dark variant. */
    val Black = Color(0xFF000000)

    /** Fully opaque scrim black, as specified by Material 3. */
    val Scrim = Color(0xFF000000)

    internal object Light {
        val Primary = Color(0xFF4A4459)
        val OnPrimary = Color(0xFFFFFFFF)
        val PrimaryContainer = Color(0xFFE6E0F0)
        val OnPrimaryContainer = Color(0xFF1A1626)

        val Secondary = Color(0xFF5F5D65)
        val OnSecondary = Color(0xFFFFFFFF)
        val SecondaryContainer = Color(0xFFE6E1E6)
        val OnSecondaryContainer = Color(0xFF1B1B1F)

        // Tertiary is not given as a standalone pair; the container pair is reused so the
        // role stays coherent rather than inventing a hue.
        val Tertiary = Color(0xFF5F5D65)
        val OnTertiary = Color(0xFFFFFFFF)
        val TertiaryContainer = Color(0xFFE9E0EA)
        val OnTertiaryContainer = Color(0xFF1E1A22)

        val Surface = Color(0xFFFCF8FD)
        val SurfaceContainerLowest = Color(0xFFFFFFFF)
        val SurfaceContainerLow = Color(0xFFF5F1F6)
        val SurfaceContainer = Color(0xFFEFEBF0)
        val SurfaceContainerHigh = Color(0xFFE9E5EA)
        val SurfaceContainerHighest = Color(0xFFE4E0E5)

        val OnSurface = Color(0xFF1C1B1F)
        val OnSurfaceVariant = Color(0xFF48454E)
        val Outline = Color(0xFF79747E)
        val OutlineVariant = Color(0xFFCAC4D0)

        val InverseSurface = Color(0xFF313033)
        val InverseOnSurface = Color(0xFFF4EFF4)
        val InversePrimary = Color(0xFFCFC3E0)

        val Error = Color(0xFFB3261E)
        val OnError = Color(0xFFFFFFFF)
        val ErrorContainer = Color(0xFFF9DEDC)
        val OnErrorContainer = Color(0xFF410E0B)
    }

    internal object Dark {
        val Primary = Color(0xFFCAC3DC)
        val OnPrimary = Color(0xFF332D41)
        val PrimaryContainer = Color(0xFF4A4459)
        val OnPrimaryContainer = Color(0xFFE7DFF8)

        val Secondary = Color(0xFFC8C5CD)
        val OnSecondary = Color(0xFF313034)
        val SecondaryContainer = Color(0xFF48464D)
        val OnSecondaryContainer = Color(0xFFE4E1EA)

        val Tertiary = Color(0xFFC8C5CD)
        val OnTertiary = Color(0xFF313034)
        val TertiaryContainer = Color(0xFF524346)
        val OnTertiaryContainer = Color(0xFFF0DEE2)

        val Surface = Color(0xFF141317)
        val SurfaceContainerLowest = Color(0xFF0E0D11)
        val SurfaceContainerLow = Color(0xFF1C1B1F)
        val SurfaceContainer = Color(0xFF201F23)
        val SurfaceContainerHigh = Color(0xFF2B292D)
        val SurfaceContainerHighest = Color(0xFF353438)

        val OnSurface = Color(0xFFE3E2E7)
        val OnSurfaceVariant = Color(0xFFC9C5D1)
        val Outline = Color(0xFF938F9B)
        val OutlineVariant = Color(0xFF484550)

        val InverseSurface = Color(0xFFE3E2E7)
        val InverseOnSurface = Color(0xFF313034)
        val InversePrimary = Color(0xFF615B71)

        val Error = Color(0xFFF2B8B5)
        val OnError = Color(0xFF601410)
        val ErrorContainer = Color(0xFF8C1D18)
        val OnErrorContainer = Color(0xFFF9DEDC)
    }

    /** OLED dark surface ramp, flattened toward true black. */
    internal object Oled {
        val SurfaceContainerLow = Color(0xFF0A0A0C)
        val SurfaceContainer = Color(0xFF111114)
        val SurfaceContainerHigh = Color(0xFF1A1A1E)
        val SurfaceContainerHighest = Color(0xFF232327)
    }
}
