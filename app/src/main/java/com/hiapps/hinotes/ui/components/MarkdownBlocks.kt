package com.hiapps.hinotes.ui.components

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hiapps.hinotes.data.NoteImages
import com.hiapps.hinotes.ui.editor.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The breath a run of prose is given before the block that follows it when the run ends on a heading.
 *
 * The renderer folds the blank line the author writes under a heading, and inside one run of prose
 * the heading's own line box is what separates it from the next line (about 3.6sp at the default
 * size). A block that starts a new run has no leading of its own to carry that separation - a task
 * row starts at its box, a picture at its frame, a table at its border, a rule at its line - so the
 * run keeps the smallest gap that does not look like a collision. One dp is that gap: enough to keep
 * a heading from sitting flush against the block under it, small enough to leave the spacing the
 * design asks for. It is given as layout padding rather than through the heading's line height so
 * the text layout, and the rectangles a found match is brought into view with, stay exactly as
 * rendered.
 */
private val HeadingBreath = 1.dp

/**
 * A note drawn the way a page is: one block after another.
 *
 * The renderer already splits a note into what a page shows - prose, a task line, a rule, a table, a
 * picture - and this draws each of those as the thing it is, rather than as its characters. A rule
 * is a real line across the note, a picture is the picture, and a task line is a box with its text
 * beside it. The editor's reading mode and a note's card both draw this, which is what keeps the
 * two looking alike.
 *
 * A task's box is a Material 3 checkbox wherever the note is shown: this row is what a card, the
 * editor's reading mode and the widget's own row all draw, so a checklist does not change shape
 * between the places that show the same note. [onToggleTask] is what a caller passes while the note
 * can be written; a caller that cannot write the tick back - the card of a locked note, or a page
 * being read without one - passes nothing, and the same box is drawn in Material 3's disabled
 * colours instead of a different drawing of its own.
 *
 * A page that has found a word in the note needs two things this drawing can give it, and a card
 * does not: where a prose block's text ended up on screen, and which row of a table a match is in.
 * Both are optional hooks - a card passes neither, and its drawing is unchanged.
 *
 * @param blocks what the renderer split the note into.
 * @param onToggleTask what a tap on a task's box writes, or null for a page that cannot be written.
 * @param pictureAspect the tallest a picture may be drawn, as a fraction of its width, or null to
 *   draw it at its own proportions.
 * @param onTextLayout called with the layout of each prose block's text, so a page can find the
 *   rectangle a match is drawn in and scroll to it; null for a card.
 * @param tableReveal the row of the table a page has to scroll into view, or null for a card.
 * @param moreBlocksAfter true when the caller knows another block follows the last one in [blocks],
 *   even though it passes one block at a time: the reading mode draws each block on its own so a
 *   found match can be brought into view, and the breath a heading is given before the next block
 *   has to be given there too.
 */
@Composable
internal fun MarkdownBlocks(
    blocks: List<Markdown.PreviewBlock>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onToggleTask: ((Int, Boolean) -> Unit)? = null,
    pictureAspect: Float? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    tableReveal: TableReveal? = null,
    moreBlocksAfter: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        blocks.forEachIndexed { position, block ->
            // Whether a block follows this one: in this call, or known to the caller that draws the
            // blocks one at a time.
            val followedByBlock = position < blocks.lastIndex || moreBlocksAfter
            when (block) {
                is Markdown.PreviewBlock.Prose -> MarkdownText(
                    text = block.text,
                    style = style,
                    // The blank line under a heading is folded by the renderer, so a run that ends
                    // on one and is followed by a block carries the breath the folded line used to
                    // give it - see [HeadingBreath].
                    modifier = if (followedByBlock && Markdown.endsWithHeading(block.text)) {
                        Modifier.padding(bottom = HeadingBreath)
                    } else {
                        Modifier
                    },
                    onTextLayout = onTextLayout,
                )

                // A rule is a drawn line rather than a run of dashes: it spans the note's own width
                // at any font size, and it cannot wrap the way a fixed number of glyphs could.
                Markdown.PreviewBlock.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                is Markdown.PreviewBlock.Image -> MarkdownPicture(
                    url = block.url,
                    description = block.alt,
                    style = style,
                    maxAspect = pictureAspect,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                is Markdown.PreviewBlock.Table -> MarkdownTable(
                    block = block,
                    style = style,
                    reveal = tableReveal,
                )

                is Markdown.PreviewBlock.Task -> MarkdownTaskRow(
                    checked = block.checked,
                    text = block.text,
                    style = style,
                    onToggle = onToggleTask?.let { write ->
                        { checked: Boolean -> write(block.line, checked) }
                    },
                )
            }
        }
    }
}

/**
 * A picture in a note, drawn where its line is.
 *
 * The picture is decoded at the width it is drawn at rather than at the size it was taken at: a
 * phone's camera is several thousand pixels across, the page a few hundred, and decoding the file
 * whole to draw it small is how a note with a few pictures runs a phone out of memory. It is
 * decoded off the main thread for the same reason - reading and scaling a large file is not
 * something to do in the frame it is first drawn in.
 *
 * A picture that cannot be read - one whose file has gone, or an address this app cannot open - is
 * shown as the address itself, which is what the note holds and what a reader can still act on.
 *
 * @param maxAspect the tallest the picture may be drawn, as a fraction of its width; a card gives
 *   one so a single photograph cannot fill the card on its own, and a page gives none.
 * @param description what the note says the picture shows, read out in place of it.
 */
@Composable
internal fun MarkdownPicture(
    url: String,
    description: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxAspect: Float? = null,
) {
    val context = LocalContext.current
    val maxWidth = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }
    // Three states rather than two, because a picture that is still being read is not one that could
    // not be read: the address is the answer to a picture that is missing, and showing it for the
    // frame a decode takes would flash it over every picture in the note.
    var picture by remember(url) { mutableStateOf<Bitmap?>(null) }
    var missing by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url, maxWidth) {
        val decoded = withContext(Dispatchers.IO) { NoteImages.decode(context, url, maxWidth) }
        if (decoded == null) {
            Log.w("HiNotesEditor", "a picture in the note could not be read: $url")
        }
        picture = decoded
        missing = decoded == null
    }

    val drawn = picture
    when {
        drawn != null -> {
            val ratio = drawn.height.toFloat() / drawn.width
            val cap = maxAspect
            // Cropped to the cap rather than squeezed into it: a photograph that is too tall is
            // shown from its top down to the cap, at its own proportions.
            val capped = cap != null && ratio > cap
            Image(
                bitmap = drawn.asImageBitmap(),
                contentDescription = description.ifBlank { null },
                contentScale = if (capped) ContentScale.Crop else ContentScale.FillWidth,
                modifier = modifier
                    .fillMaxWidth()
                    .then(if (cap != null && capped) Modifier.aspectRatio(1f / cap) else Modifier)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        missing -> Text(
            text = url,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

/**
 * One pipe table in the preview, drawn as a grid.
 *
 * Every column gets an equal share of the note's width: the source says how a cell's text is aligned
 * inside its column, but not how wide any column should be, and on a phone the honest answer is "as
 * wide as everything else". A frame around the whole table and a rule between every pair of columns
 * is what makes a run of cells read as a grid rather than as a paragraph with pipes in it; the rules
 * under the header and between the rows finish it, so a long table is read across as well as down.
 *
 * @param reveal the row a page has to scroll into view, or null when this table is drawn as part of
 *   a card or a page that is not looking for a row (see [TableReveal]).
 */
@Composable
internal fun MarkdownTable(
    block: Markdown.PreviewBlock.Table,
    style: TextStyle,
    reveal: TableReveal? = null,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(TableCorner)
    val columns = block.header.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, outline, shape)
            // The lines between columns belong to the table rather than to one row: drawn per row
            // they would start and stop wherever a cell's text wrapped, which is the one thing a
            // grid must not do. Every column is the same share of the width, so one line per
            // boundary is enough, and drawing them behind the rows lets the horizontal rules cross
            // them at every line.
            .drawBehind {
                if (columns < 2) return@drawBehind
                val step = size.width / columns
                for (column in 1 until columns) {
                    val x = step * column
                    drawLine(
                        color = outline,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            },
    ) {
        TableRowSpan(reveal = reveal, row = 0) {
            MarkdownTableRow(
                cells = block.header,
                alignments = block.alignments,
                style = style,
                header = true,
            )
        }
        HorizontalDivider(color = outline)
        block.rows.forEachIndexed { index, cells ->
            TableRowSpan(reveal = reveal, row = index + 1) {
                MarkdownTableRow(
                    cells = cells,
                    alignments = block.alignments,
                    style = style,
                    header = false,
                )
            }
            if (index != block.rows.lastIndex) {
                HorizontalDivider(color = outline)
            }
        }
    }
}

/** The corner a table's frame is drawn with. */
private val TableCorner = 8.dp

/**
 * Which row of a table a page has to scroll into view, and the requester that scrolls it there.
 *
 * A caller that shows one block on a page passes this with the row its found match is in, and the
 * table hangs the requester on that row's box: a table can be taller than the page, and scrolling
 * to the table's top would leave the row - the thing the reader is being shown - off screen. Row 0
 * is the header row and row n is the n-th body row, which is the numbering a found match in a table
 * already uses.
 */
internal data class TableReveal(
    val row: Int,
    val requester: BringIntoViewRequester,
)

/**
 * A table row's box, carrying the scroll request of [TableReveal] when [row] is the row it names.
 *
 * The request is hung on the row rather than on the table because the row is what has to be brought
 * into view; a table with no request is the row and nothing else, so a card's drawing is unchanged.
 */
@Composable
private fun TableRowSpan(reveal: TableReveal?, row: Int, content: @Composable () -> Unit) {
    val target = reveal?.takeIf { it.row == row }
    if (target == null) {
        content()
    } else {
        Box(Modifier.bringIntoViewRequester(target.requester)) { content() }
    }
}

/** One line of a table: a [MarkdownText] per cell, weighted equally and aligned as the source asked. */
@Composable
private fun MarkdownTableRow(
    cells: List<AnnotatedString>,
    alignments: List<Markdown.ColumnAlignment>,
    style: TextStyle,
    header: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        cells.forEachIndexed { index, cell ->
            MarkdownText(
                text = cell,
                style = if (header) style.copy(fontWeight = FontWeight.SemiBold) else style,
                textAlign = when (alignments.getOrNull(index)) {
                    Markdown.ColumnAlignment.Center -> TextAlign.Center
                    Markdown.ColumnAlignment.End -> TextAlign.End
                    else -> TextAlign.Start
                },
                modifier = Modifier
                    .weight(1f)
                    // Six dp rather than four: on a narrow screen a table of three or more columns
                    // put a cell's first and last characters against the rules between the
                    // columns, so the grid read as one run of text with lines drawn through it. Two
                    // columns keep their look, and the cell still takes its equal share of the
                    // width - only the gap between the writing and the rule grows.
                    .padding(horizontal = 6.dp),
            )
        }
    }
}

