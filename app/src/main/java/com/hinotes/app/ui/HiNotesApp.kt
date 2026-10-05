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
 * Transition timings and easing.
 *
 * These reproduce Android's standard activity/screen transition: the incoming screen slides in
 * over the outgoing one, which stays put rather than being pushed aside. Material 3's shared
 * axis motion for a forward/back pair is a horizontal slide of the full width at 300 ms on the
 * standard easing curve, which is what the platform's own forward navigation does.
 */
private const val TRANSITION_MS = 300

/** Material 3 "standard" easing: `CubicBezierEasing(0.2, 0, 0, 1)`. */
private val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun slide(durationMs: Int = TRANSITION_MS) =
    tween<androidx.compose.ui.unit.IntOffset>(durationMs, easing = StandardEasing)

private fun fade(durationMs: Int = TRANSITION_MS) =
    tween<Float>(durationMs, easing = StandardEasing)

/** A pushed screen slides in from the right edge. */
private fun AnimatedContentTransitionScope<*>.enterFromRight(): EnterTransition =
    slideInHorizontally(animationSpec = slide(), initialOffsetX = { it })

/** The screen being covered stays where it is, exactly as the platform does it. */
private fun AnimatedContentTransitionScope<*>.stayPut(): ExitTransition = ExitTransition.None

/**
 * A popped screen slides back out to the right edge - the exact reverse of [enterFromRight], so
 * pushing and popping are symmetric and take the same time.
 */
private fun AnimatedContentTransitionScope<*>.exitToRight(): ExitTransition =
    slideOutHorizontally(animationSpec = slide(), targetOffsetX = { it })

/** The screen being uncovered is already in place, so it does not animate back in. */
private fun AnimatedContentTransitionScope<*>.alreadyInPlace(): EnterTransition = EnterTransition.None

/**
 * The app's navigation host.
 *
 * Forward navigation and back use one transition pair: the incoming screen slides full-width
 * over the outgoing one on Material 3's standard easing, and the outgoing screen stays put. The
 * in-app back button, the system back button, the back gesture and the predictive back gesture
 * all call [NavHostController.popBackStack] and therefore run this same [popEnter]/[popExit]
 * pair - there is no second animation path that could look different.
 *
 * `android:enableOnBackInvokedCallback` is set to `false` in the manifest so the system does not
 * take the back gesture over and play its own window-close animation on top of this one. That
 * system animation is not customisable, so letting it run is exactly what made the gesture feel
 * different from the back button.
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
        // While a screen slides, the area it has not covered yet is briefly visible. Painting
        // the nav host with the theme's surface colour means that shows the app background
        // rather than the window's black.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        enterTransition = { enterFromRight() },
        exitTransition = { stayPut() },
        popEnterTransition = { alreadyInPlace() },
        popExitTransition = { exitToRight() },
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
