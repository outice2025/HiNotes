package com.hiapps.hinotes.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion scheme used for every transition and state change in the app.
 *
 * Material 3 1.4.0 keeps `MotionScheme.standard()` internal, so the standard scheme is
 * reproduced here from its published design tokens (`StandardMotionTokens`). Spatial springs
 * move things (position, size, shape); effects springs drive non-spatial changes (color, alpha).
 *
 * Damped springs never overshoot, which is what "smooth, no bounce" means in practice.
 */
@Immutable
data class MotionScheme(
    val defaultSpatial: FiniteAnimationSpec<Float>,
    val fastSpatial: FiniteAnimationSpec<Float>,
    val slowSpatial: FiniteAnimationSpec<Float>,
    val defaultEffects: FiniteAnimationSpec<Float>,
    val fastEffects: FiniteAnimationSpec<Float>,
    val slowEffects: FiniteAnimationSpec<Float>,
) {
    companion object {
        /** The Material 3 *standard* motion scheme: damped springs, no overshoot. */
        fun standard(): MotionScheme = MotionScheme(
            defaultSpatial = spring(dampingRatio = 0.9f, stiffness = 700f),
            fastSpatial = spring(dampingRatio = 0.9f, stiffness = 1400f),
            slowSpatial = spring(dampingRatio = 0.9f, stiffness = 300f),
            defaultEffects = spring(dampingRatio = 1f, stiffness = 1600f),
            fastEffects = spring(dampingRatio = 1f, stiffness = 3800f),
            slowEffects = spring(dampingRatio = 1f, stiffness = 800f),
        )

        /** Critically damped spring, handy where a value must settle without any motion feel. */
        val NoBounce: FiniteAnimationSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }
}

val LocalMotionScheme = staticCompositionLocalOf { MotionScheme.standard() }
