package com.hiapps.hinotes.ui

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
 * One page arrives at full width and the page underneath it drifts a quarter of the way out and
 * fades, in both directions: forward the *new* page arrives while the old one drifts away, and
 * back the *old* page returns while the current one drifts away. Popping is therefore the push
 * played backwards - the same distances over the same 300ms, so the page being dismissed moves at
 * the gentle rate in both directions.
 *
 * That symmetry is the point. Popping used to send the current page out at full width while the
 * returning page only hopped a quarter of the way in, so the page the user was looking at was
 * travelling four times faster on the way back than the one they were looking at travelled on the
 * way in - and back read as a snap rather than as the mirror of the push.
 */
private const val TRANSITION_MS = 300

/** Material 3 "standard" easing, the curve the platform's own transitions use. */
private val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun slideSpec(durationMs: Int = TRANSITION_MS) =
    tween<androidx.compose.ui.unit.IntOffset>(durationMs, easing = StandardEasing)

private fun fadeSpec(durationMs: Int = TRANSITION_MS) =
    tween<Float>(durationMs, easing = StandardEasing)

/** How far the covered page drifts while a new one covers it. */
private const val PARALLAX_DIVISOR = 4

/** A pushed page slides in from the right edge and fades in. */
private fun AnimatedContentTransitionScope<*>.pushEnter(): EnterTransition =
    slideInHorizontally(animationSpec = slideSpec(), initialOffsetX = { it }) +
        fadeIn(animationSpec = fadeSpec())

/** The page being covered drifts left a quarter width and dims out. */
private fun AnimatedContentTransitionScope<*>.pushExit(): ExitTransition =
    slideOutHorizontally(animationSpec = slideSpec(), targetOffsetX = { -it / PARALLAX_DIVISOR }) +
        fadeOut(animationSpec = fadeSpec())

/** A popped page returns from the left edge at the same speed a pushed page arrives from the right. */
private fun AnimatedContentTransitionScope<*>.popEnter(): EnterTransition =
    slideInHorizontally(animationSpec = slideSpec(), initialOffsetX = { -it }) +
        fadeIn(animationSpec = fadeSpec())

/** The page being dismissed drifts right a quarter width - the mirror of [pushExit]. */
private fun AnimatedContentTransitionScope<*>.popExit(): ExitTransition =
    slideOutHorizontally(animationSpec = slideSpec(), targetOffsetX = { it / PARALLAX_DIVISOR }) +
        fadeOut(animationSpec = fadeSpec())

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
