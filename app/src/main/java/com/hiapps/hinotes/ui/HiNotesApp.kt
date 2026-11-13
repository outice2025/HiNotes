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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** The one page transition. A page always moves on this clock, in either direction. */
private const val TRANSITION_MS = 300

/**
 * Room left after a transition before its guard lets go.
 *
 * The guard is armed by the transition itself (see `armGuard`), so the slide may still need this
 * room for its first frame; without it the flag could drop on the movement's last frame, and a
 * return that waited for the movement would catch its tail.
 */
private const val SETTLE_MS = 32

/**
 * How long a movement may stay unannounced before its guard lets go anyway.
 *
 * A transition that never reaches its lambdas would otherwise leave the page guard held for good;
 * this is longer than any arrival that does compose, so it only ever answers a movement that never
 * started.
 */
private const val GUARD_FALLBACK_MS = 2_000L

/** How far the page underneath drifts while the page above it covers it. */
private const val PARALLAX_DIVISOR = 4

/** Material 3's standard easing, the curve the platform's own page changes use. */
private val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The single animation spec all four transitions share.
 *
 * One spec is what keeps a return from ever looking quicker than the push it mirrors: a page that
 * moved on a different curve or a different length would visibly outrun the one it is paired with.
 */
private val SlideSpec = tween<IntOffset>(durationMillis = TRANSITION_MS, easing = StandardEasing)

/**
 * The page transition itself, which is a slide in both directions and nothing else.
 *
 * A moving page is always fully opaque, so two pages can never show through each other: there is no
 * fade to make one out of two halves, and no point in the motion where the screen changes by
 * itself. Forward, the arriving page sweeps in from the right edge at full width while the page
 * underneath drifts a quarter width to the left, mostly hidden under it; back is that same motion
 * played in reverse, the page underneath drifting back in from the left while the page on top
 * slides the full width off to the right.
 */

/** A pushed page arrives from the right edge, covering the page underneath. */
private fun AnimatedContentTransitionScope<*>.pushEnter(): EnterTransition =
    slideInHorizontally(animationSpec = SlideSpec, initialOffsetX = { it })

/** The page being covered drifts a quarter width left; the arriving page hides the rest. */
private fun AnimatedContentTransitionScope<*>.pushExit(): ExitTransition =
    slideOutHorizontally(animationSpec = SlideSpec, targetOffsetX = { -it / PARALLAX_DIVISOR })

/** The page underneath drifts back in from the left, mirroring [pushExit]. */
private fun AnimatedContentTransitionScope<*>.popEnter(): EnterTransition =
    slideInHorizontally(animationSpec = SlideSpec, initialOffsetX = { -it / PARALLAX_DIVISOR })

/** The dismissed page slides the full width off to the right, opaque until it is gone. */
private fun AnimatedContentTransitionScope<*>.popExit(): ExitTransition =
    slideOutHorizontally(animationSpec = SlideSpec, targetOffsetX = { it })

/**
 * The app's navigation host.
 *
 * Every way out of a page - the back buttons drawn in the headers, the system's back button and the
 * system's back gesture, and the editor's own way out - ends at one place, [NavHostController.popBackStack]
 * through the guarded `back` below, so all of them play this same [popEnter]/[popExit] pair: there
 * is no second animation path that could look different.
 *
 * The system's back is taken here rather than left to the host, because a predictive back gesture
 * would otherwise seek the pop with the finger's own progress and then animate only the fraction of
 * the transition that is left, which makes a return visibly quicker than the push it mirrors. The
 * editor keeps its own handler and its own presses - closing the find bar, and asking before
 * unsaved work goes - and the first page keeps the system's back, which is what closes the app from
 * the list.
 */
@Composable
fun HiNotesApp(
    viewModel: AppViewModel,
    navController: NavHostController = rememberNavController(),
    startNoteId: String? = null,
    onStartNoteShown: () -> Unit = {},
) {
    // The page change is announced here before the slide that follows it is drawn: this state
    // holds the entry that has just become current. Both guards below are armed from this moment,
    // because a page that is slow to compose can take longer than a frame to appear, and a clock
    // started at the tap would run out while its arrival is still on screen.
    val currentEntry by navController.currentBackStackEntryAsState()

    /** Set while a page is leaving: the pop that is playing must not be asked to play again. */
    var popping by remember { mutableStateOf(false) }

    /** Set while a page is arriving: the push that is playing must not be asked to play again. */
    var pushing by remember { mutableStateOf(false) }

    /** A return that came in while a page was arriving, played once that page has landed. */
    var pendingBack by remember { mutableStateOf(false) }

    val guardScope = rememberCoroutineScope()
    val guardJob = remember { arrayOfNulls<Job>(1) }

    /**
     * Arms the guard for the movement that is starting.
     *
     * The clock starts when the transition itself starts, not when the tap or the page change
     * happens: a page that is slow to compose can take longer than a transition to appear, and a
     * clock begun at the page change ran out while the arrival was still on screen. A return then
     * started the opposite slide on top of a push that was still playing, and the two transitions
     * drawn over each other read as no animation at all. Every transition lambda calls this, so
     * whichever way a page moves, its guard covers exactly that movement.
     */
    val armGuard = {
        guardJob[0]?.cancel()
        guardJob[0] = guardScope.launch {
            delay(TRANSITION_MS.toLong() + SETTLE_MS)
            pushing = false
            popping = false
        }
    }

    // A movement that never announces itself must not hold the guard for good.
    LaunchedEffect(pushing, popping) {
        if (pushing || popping) {
            delay(GUARD_FALLBACK_MS)
            pushing = false
            popping = false
        }
    }

    /**
     * Leaves the current page.
     *
     * A press that arrives while the page is already on its way out is refused: the entry is no
     * longer in the back stack, so that pop would take the page *underneath* out instead, and the
     * animation the user is watching would be replaced by one for a page they never asked to
     * leave. A press that arrives while the current page is still arriving is kept for after it:
     * popping then would start the opposite slide on top of the push that is still playing, and
     * the two transitions drawn over each other read as no animation at all. A pop with nothing
     * under it has nothing to animate and would leave an empty host; the system's own back still
     * closes the app from the first page.
     */
    val back: () -> Unit = {
        when {
            // Already leaving: the pop the user is watching is that request.
            popping -> Unit
            // Still arriving: play the return once the arrival has finished.
            pushing -> pendingBack = true
            // Nothing underneath: the system's back closes the app from the first page.
            navController.previousBackStackEntry != null -> {
                popping = true
                navController.popBackStack()
            }
            else -> Unit
        }
    }

    // A return that waited for the page to arrive is played now, through the same guarded pop as
    // every other way out, so it is the normal return animation and not a second slide laid over
    // the push that is already running.
    LaunchedEffect(pendingBack, pushing) {
        if (pendingBack && !pushing) {
            pendingBack = false
            back()
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
     * which is what makes an arrival play once.
     */
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
        // Every movement announces itself here, which is where its guard starts and ends - not
        // when the page change reaches the composition, because the arrival can be slower than
        // the guard.
        enterTransition = {
            armGuard()
            pushEnter()
        },
        exitTransition = {
            armGuard()
            pushExit()
        },
        popEnterTransition = {
            armGuard()
            popEnter()
        },
        popExitTransition = {
            armGuard()
            popExit()
        },
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

    // The graph's own answer to the system's back. [popping] keeps this handler enabled for the
    // whole of a return: as soon as the pop starts, the current entry is the page underneath, and
    // on the way back to the list that page is the home screen, whose back belongs to the system -
    // a second press in the last frames would otherwise close the app over an unfinished
    // animation. While the flag is set, such a press is swallowed by the guard in `back` instead.
    // The editor answers its own press while it is on screen, and the first page keeps the
    // system's back, which is what closes the app from the list.
    val route = currentEntry?.destination?.route
    BackHandler(enabled = popping || (route != null && route != Routes.HOME && route != Routes.EDITOR)) {
        back()
    }
}

/** Sentinel used when the FAB opens the editor before a note exists. */
const val NEW_NOTE_ID = "new"
