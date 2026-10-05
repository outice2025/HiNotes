package com.hinotes.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.hinotes.app.ui.screens.AboutScreen
import com.hinotes.app.ui.screens.AppearanceSettingsScreen
import com.hinotes.app.ui.screens.BackupScreen
import com.hinotes.app.ui.screens.EditorScreen
import com.hinotes.app.ui.screens.EditorSettingsScreen
import com.hinotes.app.ui.screens.HomeScreen
import com.hinotes.app.ui.screens.SettingsScreen
import com.hinotes.app.ui.screens.UnlockScreen

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
 * Transition timings.
 *
 * Forward and back use one shared spec, so pushing a screen and popping it take exactly the
 * same time and ease - a user cannot tell whether a pop came from the toolbar's back button,
 * the system back button, or the predictive back gesture, because all three run through the
 * same `NavHostController.popBackStack()` and therefore the same transition.
 *
 * The design asks for motion that does not bounce, so these are plain tweens on Material 3's
 * standard easing rather than the spatial springs used inside components. In-app state changes
 * still use the standard motion scheme through
 * [com.hinotes.app.ui.theme.LocalMotionScheme].
 */
private const val TRANSITION_MS = 300

/** Material 3 "standard" easing: `CubicBezierEasing(0.2, 0, 0, 1)`. */
private val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun slide(durationMs: Int = TRANSITION_MS) =
    tween<androidx.compose.ui.unit.IntOffset>(durationMs, easing = StandardEasing)

private fun fade(durationMs: Int = TRANSITION_MS) =
    tween<Float>(durationMs, easing = StandardEasing)

/** Pushes the new screen in from the right edge. */
private fun AnimatedContentTransitionScope<*>.enterFromRight(): EnterTransition =
    slideInHorizontally(animationSpec = slide(), initialOffsetX = { it }) + fadeIn(fade())

/** Slides the covered screen slightly left while a new screen covers it. */
private fun AnimatedContentTransitionScope<*>.exitToLeft(): ExitTransition =
    slideOutHorizontally(animationSpec = slide(), targetOffsetX = { -it / 4 }) + fadeOut(fade())

/**
 * Brings a covered screen back from the left while the top screen leaves to the right.
 *
 * This is exactly [enterFromRight]'s mirror image, and [popExit] is exactly [exitToLeft]'s, so a
 * pop is a faithful reverse of the push that preceded it.
 */
private fun AnimatedContentTransitionScope<*>.popEnterFromLeft(): EnterTransition =
    slideInHorizontally(animationSpec = slide(), initialOffsetX = { -it / 4 }) + fadeIn(fade())

/** Slides the leaving screen out to the right, matching the push's enter. */
private fun AnimatedContentTransitionScope<*>.popExitToRight(): ExitTransition =
    slideOutHorizontally(animationSpec = slide(), targetOffsetX = { it }) + fadeOut(fade())

/**
 * The app's navigation host.
 *
 * Every forward navigation slides in from the right; every pop plays the exact reverse. Because
 * the predictive back gesture, the system back button and the in-app back buttons all call
 * [NavHostController.popBackStack], they all inherit the identical `popEnter`/`popExit`
 * transition - there is no second animation path to drift out of sync.
 *
 * `android:enableOnBackInvokedCallback` is set in the manifest so Android 13+ hands the back
 * gesture to the app instead of playing its own exit animation.
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
        // Both screens slide during a transition, so whatever sits behind them is briefly
        // visible at the edges. Painting the nav host with the theme's surface colour means
        // that gap shows the app background instead of the window's black.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        enterTransition = { enterFromRight() },
        exitTransition = { exitToLeft() },
        popEnterTransition = { popEnterFromLeft() },
        popExitTransition = { popExitToRight() },
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
