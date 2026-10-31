package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiapps.hinotes.R
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols

/**
 * Shared frame for every settings screen: a back button, and a page title that travels into the bar.
 *
 * The title starts under the back button, at the size the settings pages have always given it, and
 * as the page scrolls it rises and fades while the same name fades in beside the button - so the
 * heading ends up in the bar rather than leaving the page without a name. The bar's own colour comes
 * in with it, because a list that scrolls under a clear title is a list read through the title.
 *
 * The title in the bar is a *row* with the back button, not a position worked out from the scroll:
 * a title placed by arithmetic is only as straight as the arithmetic, and this one is centred on the
 * button by the layout engine on every device, at every font size.
 *
 * The whole page reaches past the navigation bar: the window's background behind the gesture bar is
 * this page's own surface, and a row cannot draw over the title as it goes past, because it scrolls
 * in the space under it.
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // How far the page reaches past the navigation bar, so its last row can be scrolled clear of
    // the gesture handle. The bar itself is transparent and the window draws behind it, which is
    // what the home screen already does: a page that reserved the bar's height instead showed a
    // flat band under its own content, which reads as a bar of a different colour.
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scrollState = rememberScrollState()
    val surface = MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = surface,
        modifier = modifier,
        // The message sits above the navigation bar rather than in it. The page itself reaches past
        // that bar, but a message is a thing to read and to tap, and one drawn inside the gesture
        // area is one the system's own bar is drawn over.
        snackbarHost = {
            Box(Modifier.navigationBarsPadding()) { snackbarHost() }
        },
        // Only the top and the sides keep a system inset. The page is painted to the bottom of the
        // window, so what shows through the navigation bar is this page's own surface.
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Everything the screen has to say scrolls here, in the space under the title. The
            // title's own room is padding inside the scroller rather than a row above it, so the
            // heading can travel over the list instead of pushing it down as it moves.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(top = ExpandedHeader, bottom = 32.dp + navigationBarInset)
                    .padding(horizontal = 16.dp),
            ) {
                content()
            }

            Heading(title = title, scrollState = scrollState)

            TopBar(
                title = title,
                scrollState = scrollState,
                color = surface,
                onBack = onBack,
            )
        }
    }
}

/** The button the title starts under: 44dp, inset 4dp from the top of the page's frame. */
private val BackButtonTop = 4.dp
private val BackButtonSize = 44.dp

/** Height of the bar the title ends up in: the back button and the little margin above it. */
private val BarHeight = BackButtonTop + BackButtonSize

/** Where the title rests before the page is scrolled, and how big it is there. */
private val TitleStartTop = BarHeight + 12.dp
private val TitleStartSize = 28.sp

/** The size the title takes once it is in the bar, and the line it is set on there. */
private val TitleEndSize = 22.sp
private val TitleEndLine = 28.sp

/** The one line height the large title is laid out in, so it cannot jump as it fades. */
private val TitleStartLine = 36.sp

/** The gap under the title, before the first row. */
private val TitleBottomGap = 36.dp

/** Room the scrolling content leaves for the expanded title. */
private val ExpandedHeader = TitleStartTop + 36.dp + TitleBottomGap

/**
 * How far the page scrolls before its heading has gone and the bar holds the name instead.
 *
 * Short enough that the heading is out of the way before the first row reaches the bar - which it
 * does once the title's own room and the bar's have been scrolled past - and long enough that the
 * change is read as the heading rising rather than as a flicker.
 */
private val TitleTravel = 44.dp

/**
 * The bar at the top of the page: the way back, and the page's name beside it.
 *
 * The name is drawn from the first pixel of scrolling but stays clear until the heading has begun to
 * go, and the bar's own colour follows behind it - at rest the bar is invisible, with nothing behind
 * it but the title's own gap.
 */
@Composable
private fun TopBar(
    title: String,
    scrollState: ScrollState,
    color: Color,
    onBack: () -> Unit,
) {
    val progress = scrollProgress(scrollState)
    val barAlpha = (progress * 1.4f - 0.4f).coerceIn(0f, 1f)
    val nameAlpha = (progress * 2f - 1f).coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .background(color = color.copy(alpha = barAlpha))
            // The button's own margin above it belongs to the row, so the button and the name are
            // centred in the same 44dp and cannot sit a couple of dp apart.
            .padding(top = BackButtonTop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(BackButtonSize),
            shape = CircleShape,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            SymbolIcon(
                codepoint = Symbols.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                size = 24.dp,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            fontSize = TitleEndSize,
            lineHeight = TitleEndLine,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
                .alpha(nameAlpha),
        )
    }
}

/**
 * The page's title before it has travelled: it scrolls up with the list and fades out.
 *
 * It is drawn under the bar in the drawing order, so it passes behind the bar's own colour rather
 * than over it, and it is gone before the first row is under the bar.
 */
@Composable
private fun Heading(title: String, scrollState: ScrollState) {
    val progress = scrollProgress(scrollState)
    if (progress >= 1f) return
    val density = LocalDensity.current
    val travel = with(density) { TitleTravel.toPx().toInt() }
    val scrolled = scrollState.value.coerceAtMost(travel)
    Text(
        text = title,
        fontSize = TitleStartSize,
        lineHeight = TitleStartLine,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .offset(y = TitleStartTop - with(density) { scrolled.toDp() })
            .alpha(1f - progress),
    )
}

/**
 * How much of the way to the bar the page has scrolled, from 0 to 1.
 *
 * Read in the composition of the two pieces that need it, and nowhere else: the page's content does
 * not depend on the scroll position, so it is not rebuilt while the page moves.
 */
@Composable
private fun scrollProgress(scrollState: ScrollState): Float {
    val density = LocalDensity.current
    val travel = with(density) { TitleTravel.toPx() }
    return (scrollState.value / travel).coerceIn(0f, 1f)
}
