package com.hiapps.hinotes.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hiapps.hinotes.ui.screens.AboutScreen
import com.hiapps.hinotes.ui.screens.AppearanceSettingsScreen
import com.hiapps.hinotes.ui.screens.BackupScreen
import com.hiapps.hinotes.ui.screens.EditorScreen
import com.hiapps.hinotes.ui.screens.EditorSettingsScreen
import com.hiapps.hinotes.ui.screens.HomeScreen
import com.hiapps.hinotes.ui.screens.SettingsScreen
import com.hiapps.hinotes.ui.screens.UnlockScreen

/**
 * Route names for the app's eight screens.
 *
 * The editor carries the note id, because the home screen and the editor are the only places a
 * note identity matters; every settings screen is a fixed destination.
 */
object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{noteId}"
    const val SETTINGS = "settings"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_EDITOR = "settings/editor"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_UNLOCK = "settings/unlock"
    const val ABOUT = "about"

    fun editor(noteId: String) = "editor/$noteId"
}

/**
 * Screen transition, matching the way Android's own Settings app moves between pages.
 *
 * One page arrives at full width and the page underneath it drifts a quarter of the way out, in
 * both directions: forward the *new* page arrives while the old one drifts away, and back the
 * *old* page returns while the current one drifts away. Popping is therefore the push played
 * backwards - the same distances over the same 300ms - so the page being dismissed moves at the
 * gentle rate in both directions, and neither direction snaps.
 *
 * Pushing: the arriving page sweeps the full width over the page drifting away, and nothing fades.
 * Two full-screen opaque pages cross-fading are both half transparent at the same moment, which is
 * exactly what an afterimage is, so forward there is no fade at all.
 *
 * Popping: the page being dismissed sweeps the full width to the right at the same speed the page
 * being returned to drifts its quarter width back underneath it. The dismissed page stays opaque
 * the whole way, so it cannot blend with anything, and it is completely gone when the motion ends.
 * The two designs before this one both failed that last point: left at a quarter width it was cut
 * off mid-screen, and faded out while it travelled it turned translucent over the page underneath,
 * which is the ghost. A full-width opaque slide has neither problem, and it is the push played
 * exactly backwards, so the two directions still mirror each other.
 */
private const val TRANSITION_MS = 300

/** Material 3 "standard" easing, the curve the platform's own transitions use. */
private val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun slideSpec(durationMs: Int = TRANSITION_MS) =
    tween<androidx.compose.ui.unit.IntOffset>(durationMs, easing = StandardEasing)

/** How far the covered page drifts while a new one covers it. */
private const val PARALLAX_DIVISOR = 4

/** A pushed page slides in from the right edge. */
private fun AnimatedContentTransitionScope<*>.pushEnter(): EnterTransition =
    slideInHorizontally(animationSpec = slideSpec(), initialOffsetX = { it })

/** The page being covered drifts left a quarter width; the page above it hides the rest. */
private fun AnimatedContentTransitionScope<*>.pushExit(): ExitTransition =
    slideOutHorizontally(animationSpec = slideSpec(), targetOffsetX = { -it / PARALLAX_DIVISOR })

/** The page being returned to drifts back in from the left, mirroring [pushExit]. */
private fun AnimatedContentTransitionScope<*>.popEnter(): EnterTransition =
    slideInHorizontally(animationSpec = slideSpec(), initialOffsetX = { -it / PARALLAX_DIVISOR })

/** The dismissed page slides the whole way off to the right, opaque until it is gone. */
private fun AnimatedContentTransitionScope<*>.popExit(): ExitTransition =
    slideOutHorizontally(animationSpec = slideSpec(), targetOffsetX = { it })

/**
 * The app's navigation host.
 *
 * The back button, the system back button and the back gesture all call
 * [NavHostController.popBackStack], so all three run this same [popEnter]/[popExit] pair -
 * there is no second animation path that could look different.
 *
 * `android:enableOnBackInvokedCallback` is `false` in the manifest so the system does not take
 * the back gesture over and play its own window-close animation instead.
 */
@Composable
fun HiNotesApp(
    viewModel: AppViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // While a page moves, the area it has not covered yet is briefly visible. Painting the
        // nav host with the theme's surface colour means that shows the app background rather
        // than the window's black.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        enterTransition = { pushEnter() },
        exitTransition = { pushExit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onCreateNote = { navController.navigate(Routes.editor(NEW_NOTE_ID)) },
                onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.EDITOR) { entry ->
            val noteId = entry.arguments?.getString("noteId").orEmpty()
            EditorScreen(
                viewModel = viewModel,
                noteId = noteId,
                onBack = back,
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                onOpenEditorSettings = { navController.navigate(Routes.SETTINGS_EDITOR) },
                onOpenBackup = { navController.navigate(Routes.SETTINGS_BACKUP) },
                onOpenUnlock = { navController.navigate(Routes.SETTINGS_UNLOCK) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }

        composable(Routes.SETTINGS_APPEARANCE) {
            AppearanceSettingsScreen(viewModel = viewModel, onBack = back)
        }

        composable(Routes.SETTINGS_EDITOR) {
            EditorSettingsScreen(viewModel = viewModel, onBack = back)
        }

        composable(Routes.SETTINGS_BACKUP) {
            BackupScreen(viewModel = viewModel, onBack = back)
        }

        composable(Routes.SETTINGS_UNLOCK) {
            UnlockScreen(viewModel = viewModel, onBack = back)
        }

        composable(Routes.ABOUT) {
            AboutScreen(viewModel = viewModel, onBack = back)
        }
    }
}

/** Sentinel used when the FAB opens the editor before a note exists. */
const val NEW_NOTE_ID = "new"
