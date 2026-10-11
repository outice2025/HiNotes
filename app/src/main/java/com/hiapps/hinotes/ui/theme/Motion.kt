package com.hiapps.hinotes.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The motion the app's controls are animated with.
 *
 * Material 3 1.4.0 keeps `MotionScheme.standard()` internal, so the one token the app uses is
 * reproduced here from its published design tokens (`StandardMotionTokens`): the effects spring
 * drives non-spatial changes - a colour, an alpha - in the controls that animate on state.
 *
 * Damped springs never overshoot, which is what "smooth, no bounce" means in practice.
 */
@Immutable
data class MotionScheme(
    val fastEffects: FiniteAnimationSpec<Float>,
) {
    companion object {
        /** The standard scheme's fast effects spring: damped, so nothing bounces. */
        fun standard(): MotionScheme = MotionScheme(
            fastEffects = spring(dampingRatio = 1f, stiffness = 3800f),
        )
    }
}

val LocalMotionScheme = staticCompositionLocalOf { MotionScheme.standard() }
