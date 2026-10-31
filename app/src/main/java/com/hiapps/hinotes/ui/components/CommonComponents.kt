package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hiapps.hinotes.data.NoteImages
import com.hiapps.hinotes.ui.editor.Markdown
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols
import com.hiapps.hinotes.ui.theme.HiNotesCorners
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * How much larger a card's title is than the body text beneath it, in the single-column list.
 *
 * The body is `bodyMedium` (14sp), so 1.125 lands the title on 18sp: close enough to the M3 scale
 * to look native, far enough above the body that the two lines never read as one paragraph.
 */
private const val CardTitleSizeStep = 1.125f

/**
 * A note card's title style.
 *
 * A plain `titleMedium` is not enough here. The app-wide font weight moves every role together,
 * so the title and the body underneath it end up at exactly the same weight and differ only by the
 * two points of size the roles carry - which is what "the title does not look like a title" means
 * on screen. The title is therefore one clear step up in size and one step up in weight from the
 * body it heads, and it follows both the app's type scale and its weight setting either way.
 *
 * @param compact the two-column grid, where half the width means the smaller of the two steps.
 */
@Composable
private fun cardTitleStyle(compact: Boolean): TextStyle {
    val base = MaterialTheme.typography.titleMedium
    val step = if (compact) 1f else CardTitleSizeStep
    return base.copy(
        fontSize = base.fontSize * step,
        lineHeight = base.lineHeight * step,
        fontWeight = heavierThan(MaterialTheme.typography.bodyMedium.fontWeight ?: FontWeight.Normal),
    )
}

/**
 * A note card's own metrics.
 *
 * The card's padding is the same on all four sides except at the top, where the title's own
 * margin is added to it, and between the title and the body underneath - the two gaps around the
 * title, which the list was asked to open up a little.
 *
 * The inset is the same in both layouts. The two-column grid used to pull it in to 12dp, which made
 * a run's heading - drawn in the same grid slot, inset to match the titles under it - move 4dp to
 * the right the moment the page was switched to one column, while nothing else about the run had
 * changed. One inset for both means the heading, the titles and the note count above them all keep
 * the one left edge, and the layout switch moves the cards rather than their text.
 */
private val CardPadding = 16.dp
private val CardTitleTopGap = 4.dp
private val CardTitleBottomGap = 8.dp

/**
 * How far a card's content sits from the card's own left edge, in either layout.
 *
 * The title is the first thing inside the card, so this is the distance its first character starts
 * at. The home screen's run headings stand in the same grid slot as the cards they head, so they
 * take this inset as well; card and heading read it from here rather than each writing the number
 * down, which is what keeps a heading and the titles below it on the same line.
 *
 * @param compact the two-column grid, whose cards are half as wide but inset the same way.
 */
internal fun cardSideInset(@Suppress("UNUSED_PARAMETER") compact: Boolean): Dp = CardPadding

/** The next weight up from [weight], so a title never matches the text it heads. */
private fun heavierThan(weight: FontWeight): FontWeight = when (weight) {
    FontWeight.Light, FontWeight.Normal -> FontWeight.Medium
    FontWeight.Medium -> FontWeight.SemiBold
    else -> FontWeight.Bold
}

/**
 * How a [ConfirmDialog]'s labels are coloured.
 *
 * [Destructive] marks an action that removes something: the confirm label takes the error colour
 * and the dismiss label keeps the button's own. [Accent] paints both labels in the palette's
 * accent, for a dialog that is a question rather than a warning - the same colour the palette
 * gives every other button in the app, so it follows the accent and the wallpaper with them.
 */
enum class ConfirmStyle { Destructive, Accent }

/** Standard Material 3 confirmation dialog. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    style: ConfirmStyle = ConfirmStyle.Destructive,
) {
    val accent = style == ConfirmStyle.Accent
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (accent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        },
        dismissButton = {
            // The destructive style leaves the label on the button's own colour; the accent style
            // gives both buttons the palette's colour.
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissLabel,
                    color = if (accent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current
                    },
                )
            }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(HiNotesCorners.GroupOuter),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * The app mark shown on the About screen and the lock screen.
 *
 * Circular, and drawn from the very same `ic_logo_note` vector the launcher icon is generated
 * from, so the in-app identity and the home-screen icon are the same shape.
 *
 * The glyph occupies 40% of the badge's diameter. The mark has been asked for smaller twice now,
 * so the number is stated here rather than buried: the badge itself - its size, its colour and
 * its shape - is untouched, only the artwork inside it shrinks.
 */
@Composable
fun AppLogoPlaceholder(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 95.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.hiapps.hinotes.R.drawable.ic_logo_note,
                ),
                contentDescription = null,
                modifier = Modifier.size(size * 0.40f),
            )
        }
    }
}

/**
 * A note card used on the home screen.
 *
 * Long-press enters multi-select; when a selection is active the card shows a tick and its
 * selected state, and a tap toggles selection instead of opening the note.
 *
 * A card shows the opening of its note and now scrolls, rather than cutting it off: see [CardBody].
 *
 * The card carries no timestamp: a note is recognised by what it says, and the date belongs in
 * the editor's properties sheet, where there is room to show it exactly.
 *
 * The shape is applied to the *modifier* as well as to the card's own background, so the ripple
 * and the long-press highlight are clipped to the rounded corners. Clipping only the background
 * left those overlays as sharp rectangles sitting on the corners of every card.
 *
 * @param content the note body, rendered as Markdown when [markdownEnabled].
 * @param compact a denser layout for the grid arrangement, where cards are half as wide.
 * @param onToggleTask called with the source line of a task in the body and the state it should be
 *   written as; null for a note that cannot be written, whose boxes are then drawn as static marks
 *   rather than as controls nothing may press.
 */
@Composable
fun NoteCard(
    title: String,
    content: String,
    markdownEnabled: Boolean,
    locked: Boolean,
    lockedLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    compact: Boolean = false,
    bodyMaxLines: Int = if (compact) 6 else 3,
    onToggleTask: ((Int, Boolean) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(HiNotesCorners.Card)
    val side = cardSideInset(compact)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(
            Modifier.padding(
                start = side,
                end = side,
                top = side + CardTitleTopGap,
                bottom = side,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    SymbolIcon(
                        codepoint = if (selected) Symbols.Check else Symbols.CheckBoxOutlineBlank,
                        contentDescription = null,
                        size = 20.dp,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = cardTitleStyle(compact),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (locked) {
                    Spacer(Modifier.width(6.dp))
                    SymbolIcon(
                        codepoint = Symbols.Lock,
                        contentDescription = lockedLabel,
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (content.isNotBlank()) {
                Spacer(Modifier.height(CardTitleBottomGap))
                CardBody(
                    content = content,
                    markdownEnabled = markdownEnabled,
                    // Lines are set to the pitch a task's box needs rather than to the body's own:
                    // a box drawn inside the text is as tall as the line it stands in, so a body
                    // set at its usual line height showed a list of tasks as boxes that touched.
                    // The pitch is a length in dp for the same reason - the box it makes room for
                    // is 18dp whatever size the reader has set the text to.
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = with(LocalDensity.current) { TaskBoxPitch.toSp() },
                    ),
                    maxLines = bodyMaxLines,
                    onToggleTask = onToggleTask,
                    scrollable = !selecting,
                )
            }
        }
    }
}

/**
 * The rendered form of a card's note, kept between compositions.
 *
 * A card is drawn from two things built out of the note's Markdown: the blocks the page is drawn
 * from, and one run of text that measures the height the card has always been given (see
 * [CardBody]). Both are made from the note's characters and the theme's link colour alone, so they
 * outlive the card that was drawn from them - and they are the expensive half of drawing it: the
 * blocks parse the whole note and render every run of prose in it, and the ruler renders the whole
 * note a second time.
 *
 * That cost lands on a return. The host disposes a page once the transition that covered it has
 * finished, so coming back to the list rebuilds every visible card from nothing, and all of that
 * parsing is done in the frames the pop transition is drawn in - which is what a return shows as a
 * stutter while opening a page does not, the page being opened having no list under it to rebuild.
 * Holding the renders keyed by what they were made from lets a card that was on screen a moment
 * ago arrive already rendered.
 *
 * Bounded so that a long list does not become a second copy of the store in memory: the renders
 * least recently drawn are dropped, and the bound is a few screens' worth of cards. The map is only
 * touched from composition, which for this app is the frame thread.
 */
private object CardRenderCache {

    /** The most notes whose renders are kept. */
    private const val MAX_ENTRIES = 32

    /**
     * What one render was made from.
     *
     * The colour is part of it because links are painted with it, and the switch because a note
     * with Markdown off is one block of its own characters rather than parsed blocks.
     */
    private data class Key(val content: String, val markdown: Boolean, val linkColor: Color)

    /** One note as a card draws it: the page's blocks, and the text the card is measured by. */
    class Rendered(val blocks: List<Markdown.PreviewBlock>, val ruler: AnnotatedString)

    // Access-ordered, so the first entry is the render that has gone longest without being drawn.
    private val entries = LinkedHashMap<Key, Rendered>(MAX_ENTRIES, 0.75f, true)

    /** The render for [content], built the first time that note is drawn. */
    fun render(content: String, markdown: Boolean, linkColor: Color): Rendered {
        val key = Key(content, markdown, linkColor)
        entries[key]?.let { return it }
        val blocks = if (markdown && content.isNotBlank()) {
            Markdown.previewBlocks(content, linkColor)
        } else {
            // Without Markdown the body is plain text, so the page is one block of its characters.
            listOf(Markdown.PreviewBlock.Prose(AnnotatedString(content)))
        }
        // The ruler: the same note as one run of text, cut to the number of lines a card shows. The
        // lines that hold nothing but a picture are left out - their addresses are not what the card
        // is measured by, the picture's own box is.
        val ruler = if (markdown) {
            Markdown.render(
                source = Markdown.withoutPictureLines(content),
                linkColor = linkColor,
            )
        } else {
            AnnotatedString(Markdown.withoutPictureLines(content))
        }
        val rendered = Rendered(blocks, ruler)
        entries[key] = rendered
        if (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        return rendered
    }
}

/**
 * The note's body inside a card: as much of it as the card is allowed to show, scrolled in place.
 *
 * A card's height is still decided by the number of lines of writing it shows ([maxLines]), and
 * that rule does not change: what changes is that the body is drawn as a page - a rule across the
 * card, a picture where the note holds one - and that the rest of the note is reachable rather than
 * cut off. A picture is the one thing that does add to the height: a card whose note holds one grows
 * by the height that picture is drawn at, so the note's first picture is seen rather than scrolled
 * through a slit. That height is capped (see [CardPictureAspect]), and pictures below the first
 * scroll inside the card like the rest of the note.
 *
 * Two children are laid out and only one is drawn. The first is a ruler: the note's own writing,
 * without the lines that hold nothing but a picture, rendered as text and cut to [maxLines]. It is
 * the measurement the card has always been given, so a note of writing keeps the height its content
 * earned. The second is the page itself, every block of it, measured against that height - which is
 * what gives it something to scroll inside. The ruler draws nothing (it skips its own drawing and is
 * hidden from accessibility), so nothing is double-drawn.
 *
 * Scrolling is offered only while the card is not part of a selection: a long press makes the whole
 * card one target that ticks the note, and a body that still slid under the finger would swallow the
 * drag that is meant to move the page instead.
 */
@Composable
private fun CardBody(
    content: String,
    markdownEnabled: Boolean,
    style: TextStyle,
    maxLines: Int,
    onToggleTask: ((Int, Boolean) -> Unit)?,
    scrollable: Boolean,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    // Built once per note rather than once per visit to this card: a page that has just been
    // returned to recomposes every visible card, and this is the work that used to be done in the
    // pop transition's first frames. See [CardRenderCache].
    val rendered = remember(content, markdownEnabled, linkColor) {
        CardRenderCache.render(content, markdownEnabled, linkColor)
    }
    val blocks = rendered.blocks
    val ruler = rendered.ruler

    // The first picture in the note, and how tall it is drawn: the card has to know before it lays
    // anything out, so the picture is decoded here rather than only where it is drawn.
    val firstPicture = remember(blocks) {
        blocks.filterIsInstance<Markdown.PreviewBlock.Image>().firstOrNull()
    }
    val decodeWidth = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }
    var pictureAspect by remember(firstPicture?.url) { mutableStateOf(0f) }
    LaunchedEffect(firstPicture?.url, decodeWidth) {
        val url = firstPicture?.url
        pictureAspect = if (url == null) {
            0f
        } else {
            withContext(Dispatchers.IO) { NoteImages.decode(context, url, decodeWidth) }
                ?.let { bitmap -> bitmap.height.toFloat() / bitmap.width }
                ?: 0f
        }
    }

    val scroll = rememberScrollState()
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Box(
                Modifier
                    .fillMaxWidth()
                    // Measured, never drawn and never read out: it is the card's ruler.
                    .drawWithContent { }
                    .clearAndSetSemantics { },
            ) {
                MarkdownText(
                    text = ruler,
                    style = style,
                    maxLines = maxLines,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(state = scroll, enabled = scrollable),
            ) {
                MarkdownBlocks(
                    blocks = blocks,
                    style = style,
                    onToggleTask = onToggleTask,
                    pictureAspect = CardPictureAspect,
                )
            }
        },
    ) { measurables, constraints ->
        val rulerPlaceable = measurables[0].measure(constraints)
        val width = constraints.maxWidth
        val pictureHeight = (width * pictureAspect.coerceAtMost(CardPictureAspect)).roundToInt()
        val height = rulerPlaceable.height + pictureHeight
        val body = measurables[1].measure(constraints.copy(minHeight = 0, maxHeight = height))
        layout(width, height) {
            rulerPlaceable.place(0, 0)
            body.place(0, 0)
        }
    }
}

/**
 * The tallest a picture is drawn in a card, as a fraction of the card's width.
 *
 * A photograph taken the usual way is 4:3 or taller, and drawn at the card's full width it would be
 * taller than the card itself - one picture would push the note's writing out of sight. 5:8 is a
 * little taller than a 16:10 frame: wide pictures keep their own proportions, and tall ones are
 * shown from the top down to that height.
 */
private const val CardPictureAspect = 0.625f
