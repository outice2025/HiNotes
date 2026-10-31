package com.hiapps.hinotes.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hiapps.hinotes.ui.screens.AboutScreen
import com.hiapps.hinotes.ui.screens.AppearanceSettingsScreen
import com.hiapps.hinotes.ui.screens.BackupScreen
import com.hiapps.hinotes.ui.screens.EditorScreen
import com.hiapps.hinotes.ui.screens.EditorSettingsScreen
import com.hiapps.hinotes.ui.screens.HomeScreen
import com.hiapps.hinotes.ui.screens.SettingsScreen
import com.hiapps.hinotes.ui.screens.UnlockScreen
import com.hiapps.hinotes.ui.screens.WidgetSettingsScreen
import kotlinx.coroutines.delay

/**
 * Route names for the app's screens.
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
    const val SETTINGS_WIDGET = "settings/widget"
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
 * Every way out of a page - the back buttons drawn in the headers, the system's back button and
 * the system's back gesture - ends at one place, [NavHostController.popBackStack] through the
 * guarded `back` below, and therefore runs this same [popEnter]/[popExit] pair: there is no second
 * animation path that could look different.
 *
 * The last two of those used to be left to the host. Back is not always answered by the activity
 * any more: a platform can hand the press to the app's back dispatcher, and Compose Navigation
 * answers that path by seeking the pop with the gesture's own progress and then animating only the
 * fraction of the transition that is left - a return driven by the finger, and visibly quicker than
 * the page arriving. The graph therefore answers back for the pages that have no guard of their
 * own, before the host can start a gesture-driven pop: the press is taken here and answered by the
 * same guarded `back` the headers use. The editor keeps its own guard and its own two presses -
 * closing the find bar, and asking before leaving unsaved work - and the first page keeps the
 * system's back, which is what closes the app from the list. The manifest's
 * `android:enableOnBackInvokedCallback` stays as it is, but the app no longer relies on it for how a
 * return looks: whatever the platform does with the press, it is answered here.
 */
@Composable
fun HiNotesApp(
    viewModel: AppViewModel,
    navController: NavHostController = rememberNavController(),
    startNoteId: String? = null,
    onStartNoteShown: () -> Unit = {},
) {
    /**
     * Leaves the current page.
     *
     * Two presses are refused here, and both of them used to make a return look as if its
     * animation had been skipped. The first is a press that arrives while the page is already on
     * its way out: the back stack no longer holds it, so that pop would take the page *underneath*
     * out of the stack instead, and the animation the user was watching would be replaced by one
     * for a page they never asked to leave. The second is a pop with nothing under it, which has
     * nothing to animate and would leave an empty host; the system's own back still closes the app
     * from the first page, because that is the activity's answer rather than the graph's.
     */
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(TRANSITION_MS.toLong())
            leaving = false
        }
    }
    val back: () -> Unit = {
        if (!leaving && navController.previousBackStackEntry != null) {
            leaving = true
            navController.popBackStack()
        }
    }

    /**
     * Opens [route], and refuses a second call that arrives while the first page is still moving.
     *
     * A card is a large target under a finger that is often still moving, and two taps in quick
     * succession ask for the same page twice. Single-top keeps the second request on the page that
     * is already at the top of the stack, but the request still reaches the navigator, which
     * replaces that page's entry and so rebuilds the screen in the middle of its own arrival - the
     * flash that a quick double tap on a note was reported as. One page is pushed per transition,
     * which is what makes an arrival play once, the way a return already does.
     */
    var pushing by remember { mutableStateOf(false) }
    LaunchedEffect(pushing) {
        if (pushing) {
            delay(TRANSITION_MS.toLong())
            pushing = false
        }
    }
    val push: (String) -> Unit = { route ->
        if (!pushing) {
            pushing = true
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    // A tapped widget asks for one note by name: the editor opens over the home screen, which
    // stays in the back stack underneath, and the request is reported as shown so a recomposition
    // or a rotation does not open it a second time. Tapping the same widget again while its note
    // is already open brings that page forward rather than stacking a second copy of it.
    //
    // The note is looked up before it is opened, because the two halves of a tap are a moment
    // apart: a widget whose note has just been deleted can still be carrying a tap for it, and
    // opening that request would fill the editor with a blank note that was never asked for. A
    // request for a note that is gone opens nothing and is spent like any other.
    LaunchedEffect(startNoteId) {
        val noteId = startNoteId ?: return@LaunchedEffect
        // The card may have written a tick since this list was read (the list is the app's copy,
        // the card writes through its own), so the note is looked at only after a fresh read.
        viewModel.reloadNotes()
        if (viewModel.notesRepository.note(noteId) != null) {
            push(Routes.editor(noteId))
        }
        onStartNoteShown()
    }

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
                // The same guard the cards carry, for the same reason: the button that starts a
                // note is a large target under a finger that is often still moving, and two taps
                // would otherwise leave two editors of the same new note to back out of.
                onCreateNote = {
                    push(Routes.editor(NEW_NOTE_ID))
                },
                // A quick double tap on a card would otherwise navigate twice and push two editors
                // for the same note, so leaving the editor would take two back presses. The editor
                // route is a single destination whatever note it carries, which is what lets
                // single-top keep the second call on the entry already at the top of the stack.
                onOpenNote = { id ->
                    push(Routes.editor(id))
                },
                onOpenSettings = { push(Routes.SETTINGS) },
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
                viewModel = viewModel,
                onBack = back,
                onOpenAppearance = { push(Routes.SETTINGS_APPEARANCE) },
                onOpenEditorSettings = { push(Routes.SETTINGS_EDITOR) },
                onOpenWidgetSettings = { push(Routes.SETTINGS_WIDGET) },
                onOpenBackup = { push(Routes.SETTINGS_BACKUP) },
                onOpenUnlock = { push(Routes.SETTINGS_UNLOCK) },
                onOpenAbout = { push(Routes.ABOUT) },
            )
        }

        composable(Routes.SETTINGS_APPEARANCE) {
            AppearanceSettingsScreen(viewModel = viewModel, onBack = back)
        }

        composable(Routes.SETTINGS_EDITOR) {
            EditorSettingsScreen(viewModel = viewModel, onBack = back)
        }

        composable(Routes.SETTINGS_WIDGET) {
            WidgetSettingsScreen(viewModel = viewModel, onBack = back)
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

    // The graph's own answer to the system's back. The two pages that answer it themselves are
    // left out: the editor already has a guard that has to see the press first (the find bar and
    // the question before unsaved work goes), and the first page keeps the system's back so that a
    // press on the list closes the app. Every other page - the settings screens - is answered here,
    // which keeps its return at the same rate as the push that opened it; see this function's own
    // comment for what the platform would otherwise do with the gesture.
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    BackHandler(enabled = route != null && route != Routes.HOME && route != Routes.EDITOR) {
        back()
    }
}

/** Sentinel used when the FAB opens the editor before a note exists. */
const val NEW_NOTE_ID = "new"
