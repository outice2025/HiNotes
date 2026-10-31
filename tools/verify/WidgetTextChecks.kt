package com.hiapps.hinotes.widget

import com.hiapps.hinotes.ui.editor.Markdown
import java.io.File

/**
 * Standalone checks for the items a widget's list is built from, run by tools/verify/run.ps1.
 *
 * The reported defect was the blank line at the end of a chunk: a note whose last line is empty is
 * rendered with the newline that line ends on, so cutting the body up handed the list an item ending
 * in a break, which a `TextView` draws as an empty line and the user reads as an item taller than the
 * text in it. The cut is pure Kotlin over the body's characters, so it is executed here rather than
 * reasoned about, against the same shapes a phone produces: a note that ends without a break, one
 * that ends with several, one that ends in spaces, one long enough to be cut by line count and one
 * with no break in it at all.
 *
 * The bodies here are plain text, which is what a note without Markdown is drawn from and what the
 * cut itself is given: the cut sees characters and knows nothing about where they came from, so the
 * two ends a line can leave on - the newline of a last source line and the whitespace around it - are
 * all it can be told apart by. A checklist is the other shape it now has to handle: a task line is an
 * item of its own, with its box taken out of the text and drawn beside it.
 */
object WidgetTextChecks {

    private var failures = 0

    private fun check(name: String, condition: Boolean, detail: String = "") {
        if (condition) {
            println("  ok    $name")
        } else {
            failures++
            println("  FAIL  $name ${if (detail.isEmpty()) "" else "-> $detail"}")
        }
    }

    /** One source line of a long note, so a body's chunk count is the arithmetic of its length. */
    private fun line(index: Int): String = "line $index"

    /** The text of each item, which is what most of these checks are about. */
    private fun pieces(
        body: CharSequence,
        marks: List<Markdown.TaskMark> = emptyList(),
    ): List<String> = WidgetChunks.chunks(body, marks).map { it.text.toString() }

    @JvmStatic
    fun main(args: Array<String>) {
        println("Widget body chunks")

        // The reported defect, in its smallest form: one line, and the break the note ends on.
        val oneLine = pieces("only line\n")
        check("a body ending in one break is one chunk", oneLine.size == 1, "$oneLine")
        check("that chunk keeps the text", oneLine.single() == "only line", "$oneLine")

        val manyBreaks = pieces("only line\n\n\n")
        check("trailing blank lines leave no chunk behind", manyBreaks.size == 1, "$manyBreaks")
        check("nor an empty line inside the chunk", manyBreaks.single() == "only line", "$manyBreaks")

        val trailingSpaces = pieces("only line   \n \n")
        check("trailing whitespace is cut off with the breaks", trailingSpaces.single() == "only line", "$trailingSpaces")

        // A note that ends at its last written character is what the cut has to leave alone: the fix
        // has only trailing whitespace to work on, and this body has none.
        val plain = pieces("first\nsecond")
        check("a body with no trailing break is unchanged", plain.single() == "first\nsecond", "$plain")

        val blank = WidgetChunks.chunks("\n\n \n")
        check("a body of blank lines has no chunks", blank.isEmpty(), "$blank")
        check("an empty body has no chunks", WidgetChunks.chunks("").isEmpty())

        // Ten source lines to a chunk, and a body of twenty-five does not divide evenly into tens, so
        // this ends on a partly filled chunk and a break wrongly kept at the end of a full one would
        // turn the line before it into a blank one.
        val long = (0 until 25).joinToString("\n") { line(it) }
        val longChunks = pieces(long)
        check("a long body is cut into chunks", longChunks.size == 3, "${longChunks.size}")
        check(
            "no chunk in it ends on a break",
            longChunks.none { it.endsWith("\n") },
            "${longChunks.map { it.takeLast(2) }}",
        )
        check(
            "no chunk in it ends on a space either",
            longChunks.none { it.endsWith(" ") },
            "${longChunks.map { it.takeLast(2) }}",
        )
        // The cut is meant to lose nothing but the whitespace it strips, so the pieces put back
        // together are the body itself.
        check("the chunks hold the whole body", longChunks.joinToString("\n") == long, "${longChunks.size}")
        check("no chunk of it is blank", longChunks.all { it.isNotBlank() }, "$longChunks")

        // The three shapes a note can end on, all of which have to come out the same way.
        val endings = listOf("tail", "tail\n", "tail\n\n")
        val endingsTails = endings.map { body ->
            val items = pieces(body)
            items.lastOrNull().orEmpty() to items.size
        }
        check("every ending leaves the same last chunk", endingsTails.all { it.first == "tail" && it.second == 1 }, "$endingsTails")
        check("every chunk ends on a character rather than a break", endings.all { body -> pieces(body).none { it.endsWith("\n") } })

        // A note may hold no break at all, and one long paragraph would otherwise be a single item
        // taller than the card: the cut falls on a character count as well as on a line count.
        val oneChunk = pieces("x".repeat(601))
        check("a line with no break is cut by length", oneChunk.size == 2, "${oneChunk.size}")

        // The body the widget actually renders, to keep the check honest about where its text comes
        // from: a note whose last line is empty is exactly the report, and a note that ends on a
        // filled line must not lose it.
        val renderedChecklist = pieces(Markdown.render("intro\n\n- [ ] one\n- [x] two\n").text)
        check("a rendered checklist is one chunk", renderedChecklist.size == 1, "$renderedChecklist")
        check(
            "the rendered chunk ends on the last task",
            renderedChecklist.single().endsWith("two"),
            "'${renderedChecklist.single()}'",
        )
        val withBreak = Markdown.render("intro\n\nlast\n\n").text
        val renderedTail = pieces(withBreak)
        check("a rendered body ending in a break is one chunk", renderedTail.size == 1, "$renderedTail")
        check(
            "a rendered body ending in a break loses it",
            renderedTail.single() == withBreak.trimEnd().toString(),
            "'${renderedTail.single()}'",
        )

        println("Widget task items")

        // The renderer is the only thing that decides what a task line is, and the widget splits its
        // body at the boxes the renderer itself drew: the two readings that used to disagree - one
        // drawing boxes, the other naming the lines they belonged to - are now one reading.
        val note = "intro\n\n- [ ] one\n- [x] two\n"
        val rendered = Markdown.renderMarks(note)
        check(
            "the renderer reports the task lines it drew",
            rendered.marks.map { it.line } == listOf(2, 3),
            "${rendered.marks}",
        )
        check(
            "each mark points at the box that was written there",
            rendered.marks.all { rendered.text[it.index] == '\u2610' || rendered.text[it.index] == '\u2611' },
            "${rendered.marks.map { rendered.text[it.index] }}",
        )

        val checklist = WidgetChunks.chunks(rendered.text, rendered.marks)
        check("a checklist is split around its tasks", checklist.size == 3, "$checklist")
        check(
            "each task is an item of its own",
            checklist.filterIsInstance<WidgetChunk.Task>().size == 2,
            "$checklist",
        )
        check(
            "the box is taken out of the item's text",
            checklist.map { it.text.toString() } == listOf("intro", "one", "two"),
            "${checklist.map { it.text }}",
        )
        check(
            "each item carries the line it writes back to",
            checklist.filterIsInstance<WidgetChunk.Task>().map { it.line } == listOf(2, 3),
            "$checklist",
        )
        check(
            "each item carries the state the note records",
            checklist.filterIsInstance<WidgetChunk.Task>().map { it.checked } == listOf(false, true),
            "$checklist",
        )

        // Every form the checkbox button has written over the versions of this app, and the forms a
        // note may have been pasted with, are tasks to both readings: a line that gains a box in the
        // preview must gain a control on a widget, and one that does not must stay a character.
        val taskSyntax = listOf(
            "- [ ] a",
            "- [x] b",
            "* [ ] c",
            "+ [x] d",
            "[ ] e",
            "[x]f",
            "  - [X] g",
        )
        for (line in taskSyntax) {
            val body = Markdown.renderMarks(line)
            val preview = Markdown.previewBlocks(line).filterIsInstance<Markdown.PreviewBlock.Task>().size
            check(
                "both readings call it a task: '$line'",
                body.marks.size == 1 && preview == 1,
                "marks=${body.marks.size} preview=$preview",
            )
        }
        val notATask = listOf("- plain item", "1. numbered", "# [ ] heading text", "[important] words")
        for (line in notATask) {
            val body = Markdown.renderMarks(line)
            val preview = Markdown.previewBlocks(line).filterIsInstance<Markdown.PreviewBlock.Task>().size
            check("neither reading calls it a task: '$line'", body.marks.isEmpty() && preview == 0, "$line")
        }

        // A note pasted out of another system carries the other system's line breaks. A carriage
        // return is invisible, but it is a character the task pattern does not match, so a checklist
        // that arrived that way used to be drawn as a run of characters - the home screen showed it
        // as one paragraph of ☑/☐.
        val windowsNote = "intro\r\n\r\n- [ ] one\r\n- [x] two\r\n"
        val windowsBody = Markdown.renderMarks(windowsNote)
        val windowsChunks = WidgetChunks.chunks(windowsBody.text, windowsBody.marks)
        check(
            "a note written with Windows breaks still has its tasks",
            windowsBody.marks.map { it.line } == listOf(2, 3),
            "${windowsBody.marks}",
        )
        check(
            "and they are items on the card",
            windowsChunks.filterIsInstance<WidgetChunk.Task>().map { it.line } == listOf(2, 3),
            "$windowsChunks",
        )
        check(
            "the preview reads those lines the same way",
            Markdown.previewBlocks(windowsNote).filterIsInstance<Markdown.PreviewBlock.Task>().size == 2,
            "${Markdown.previewBlocks(windowsNote)}",
        )
        check(
            "ticking one writes the note back with plain breaks",
            Markdown.setTaskChecked(windowsNote, 3, false) == "intro\n\n- [ ] one\n- [ ] two\n",
            "'${Markdown.setTaskChecked(windowsNote, 3, false)}'",
        )

        // A body whose boxes the renderer did not draw - no marks to split at - is drawn as prose:
        // a tick written back to the wrong line is worse than a box that has to be ticked in the editor.
        val unmatched = WidgetChunks.chunks(rendered.text, emptyList())
        check("an unmatched body stays prose", unmatched.size == 1, "$unmatched")
        check(
            "and keeps its boxes as characters",
            unmatched.single().text.contains('\u2610') && unmatched.single().text.contains('\u2611'),
            "${unmatched.single().text}",
        )

        println("Widget interaction")

        val service = source("app/src/main/java/com/hiapps/hinotes/widget/HiNotesWidgetService.kt")
        val provider = source("app/src/main/java/com/hiapps/hinotes/widget/HiNotesWidgetProvider.kt")
        val tap = source("app/src/main/java/com/hiapps/hinotes/widget/WidgetTapActivity.kt")
        val content = source("app/src/main/java/com/hiapps/hinotes/widget/WidgetContent.kt")
        val card = source("app/src/main/res/layout/widget_card.xml")
        // The body's items are built by one drawing class, shared by the eager API 31+ path and the
        // legacy scrolling collection, so the item contract is asserted against that file.
        val itemDrawing = source("app/src/main/java/com/hiapps/hinotes/widget/WidgetItems.kt")
        // A view inside a collection item cannot carry a click response every launcher honours, so
        // the box fills the card's own template in instead: the tap that reaches the app is the
        // tick, and the line's text fills nothing in and opens the note as it always did.
        check(
            "a task's box is the row that ticks rather than opens",
            itemDrawing.contains("setOnClickFillInIntent(R.id.widget_box"),
        )
        check(
            "the box fills in the line it stands for",
            itemDrawing.contains("HiNotesWidgetProvider.EXTRA_TASK_LINE, chunk.line"),
        )
        check("it carries the state the tap means", itemDrawing.contains("EXTRA_TASK_CHECKED, !chunk.checked"))
        check(
            "the template a row is filled into is the tap handler",
            provider.contains("setPendingIntentTemplate(android.R.id.list, tapNote(") &&
                provider.contains("Intent(app, WidgetTapActivity::class.java)"),
            "a row cannot open a page or write a note by itself",
        )
        check(
            "the template may be filled in",
            provider.contains("PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE"),
            "an immutable pending intent refuses the fill the rows deliver",
        )
        check(
            "one card's tap cannot be mistaken for another's",
            provider.contains("hinotes://tap/"),
        )
        // The write is handed to the card's receiver rather than started by the activity that reads
        // the tap: the activity finishes at once, and a receiver holds the process alive with
        // goAsync() until the write has landed. A write started from the activity can be reaped
        // with the process, which the user reads as a box that does nothing.
        check(
            "the tap is answered without the app coming forward",
            tap.contains("HiNotesWidgetProvider.ACTION_TICK_TASK") &&
                tap.contains("sendBroadcast(") &&
                tap.contains("finish()") &&
                !tap.contains("HiNotesWidgetProvider.tick("),
            "a tick is not a visit to the app, and not a write from a finished activity",
        )
        check(
            "a locked note's boxes offer no tick",
            itemDrawing.contains("if (!readOnly)"),
            "a note the app will not write to must not offer a write",
        )
        check("the card writes the tick back to the line", provider.contains("Markdown.setTaskChecked("))
        check("the gap between two boxes is a task line's own", itemDrawing.contains("TASK_GAP_DP"))

        // The note starts at the card's top-left rather than floating in the middle of it, and a card
        // with nothing to show offers the picker that gives it something - or the app, when the app
        // holds no note to pick at all.
        check(
            "a card's content starts at its top-left corner",
            card.contains("android:gravity=\"top\""),
            "a short note sat in the middle of the surface",
        )
        check(
            "a note that has gone is a note to choose again",
            content.contains("class Missing(val notesAvailable: Boolean)") &&
                provider.contains("if (content.notesAvailable) pickNote(app, widgetId) else openApp(app, widgetId)"),
            "an empty card that opens nothing leaves a widget with no way back",
        )
        check(
            "the picker is the one the launcher opens",
            provider.contains("Intent(app, WidgetConfigActivity::class.java)") &&
                provider.contains("putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)"),
        )
        check(
            "a locked app's card takes no tap",
            content.contains("data object Locked") && provider.contains("else -> null"),
            "a card is not a second way round the password",
        )

        // A bitmap span does not survive the parcel to the launcher - the home screen showed "OBJ"
        // where a picture should have been - so a picture is an item with a view of its own.
        println("Widget pictures")

        val mark = '\uFFFC'
        val withPicture = "before\n$mark\nafter"
        val pictureChunks = WidgetChunks.chunks(
            withPicture,
            emptyList(),
            mapOf(7 to "hinotes-image://a.png"),
        )
        check(
            "a picture is an item of its own",
            pictureChunks.size == 3 &&
                pictureChunks[1] is WidgetChunk.Picture &&
                (pictureChunks[1] as WidgetChunk.Picture).url == "hinotes-image://a.png",
            "$pictureChunks",
        )
        check(
            "the writing around a picture is kept",
            pictureChunks[0].text.toString() == "before" &&
                pictureChunks[2].text.toString() == "after",
            "${pictureChunks.map { it.text }}",
        )
        check(
            "the card draws a picture in a view rather than in its text",
            itemDrawing.contains("setImageViewBitmap(R.id.widget_picture") &&
                source("app/src/main/res/layout/widget_item_regular.xml")
                    .contains("@+id/widget_picture"),
            "a bitmap span is dropped on the way to the launcher",
        )
        check(
            "a picture that cannot be read leaves its address",
            itemDrawing.contains("chunk.url"),
            "an empty item says nothing about what the note held",
        )
        // The band a picture is cut for is full width, and the picture is cropped rather than
        // squeezed: a picture scaled down on both axes to fit a band is what the home screen showed
        // as a mosaic, and the pixels the card draws it at are the pixels it was decoded at.
        check(
            "a picture keeps the card's own width",
            itemDrawing.contains("NoteImages.decode(context, url,") &&
                itemDrawing.contains("targetWidth(") &&
                itemDrawing.contains("Bitmap.createBitmap(bitmap, 0, top, bitmap.width,"),
            "a picture squeezed to a band loses the width it is drawn at",
        )
        check(
            "a picture hands the launcher no more than a binder can carry",
            itemDrawing.contains("EAGER_PICTURE_BYTES") &&
                itemDrawing.contains("COLLECTION_PICTURE_BYTES") &&
                itemDrawing.contains("Bitmap.Config.RGB_565"),
            "an over-sized bitmap does not arrive at all",
        )
        // The box beside a task stands on the item's first line, the way the composed task row's does.
        // The item layout pins the picture to the row's top instead of centring it, and the picture is
        // taller than the square - the square sits in the middle of a band one line of the body
        // occupies - so an item that wraps onto several lines still shows its box beside its first
        // line rather than in the middle of the paragraph.
        val item = source("app/src/main/res/layout/widget_item_regular.xml")
        check(
            "a task's box stands on the item's first line",
            item.contains("android:layout_gravity=\"top\"") &&
                item.contains("android:layout_height=\"wrap_content\"") &&
                item.contains("android:layout_width=\"20dp\"") &&
                item.contains("android:layout_marginEnd=\"8dp\"") &&
                !item.contains("android:layout_marginTop="),
            "a box centred on a wrapped item sits in the middle of the paragraph",
        )
        val boxDrawing = source("app/src/main/java/com/hiapps/hinotes/widget/WidgetBoxes.kt")
        val bodyText = source("app/src/main/java/com/hiapps/hinotes/widget/WidgetText.kt")
        check(
            "the box picture is taller than the square and centres it in the line's band",
            boxDrawing.contains("canvas.translate(0f, (band - size) / 2f)") &&
                boxDrawing.contains("bandPx.coerceAtLeast(sizePx)"),
            "a square picture pinned to the row's top would draw its box above the first line",
        )
        check(
            "the band is one line of the body plus the task line's own gap",
            bodyText.contains("fun lineHeightPx(context: Context, sizeSp: Float): Int") &&
                bodyText.contains("LINE_SPACING_MULTIPLIER = 1.4f") &&
                bodyText.contains("LINE_SPACING_EXTRA_DP = 4f") &&
                itemDrawing.contains("WidgetText.lineHeightPx(context, bodySizeSp) + taskGapPx * 2"),
            "the picture's band and the text's line have to be the same height",
        )
        check(
            "the drawing reports what it drew",
            service.contains("items, ") && service.contains("tasks") &&
                itemDrawing.contains("chunk -> chunk is WidgetChunk.Task"),
            "a card that shows the wrong thing has to be readable in a log",
        )

        // One item is one RemoteViews: whatever the previous item left on a recycled frame has to be
        // cleared before this one is drawn, or a picture or a box from another line stays visible
        // and a tap on it answers with the previous line's fill-in.
        println("Widget item drawing")

        val reset = blockOf(itemDrawing, "private fun reset(")
        check(
            "every item starts from the state that shows nothing",
            reset.contains("setViewVisibility(R.id.widget_chunk, View.GONE)") &&
                reset.contains("setViewVisibility(R.id.widget_box, View.GONE)") &&
                reset.contains("setViewVisibility(R.id.widget_picture, View.GONE)") &&
                reset.contains("setViewPadding(R.id.widget_chunk, 0, 0, 0, 0)"),
            "a frame recycled from another item keeps the view state it was drawn with",
        )
        val drawItem = blockOf(itemDrawing, "private fun draw(")
        check(
            "every item is reset and made tappable before its kind is drawn",
            ordered(drawItem, "reset(views)", "when (chunk)") &&
                drawItem.contains("setOnClickFillInIntent(R.id.widget_item, Intent())"),
            "a reset after the branch would erase what the branch drew",
        )
        check(
            "a text item shows its own text",
            blockOf(itemDrawing, "private fun drawText(").let { textItem ->
                textItem.contains("setViewVisibility(R.id.widget_chunk, View.VISIBLE)") &&
                    textItem.contains("setTextViewText(R.id.widget_chunk, text)") &&
                    textItem.contains("setTextViewTextSize(R.id.widget_chunk,") &&
                    textItem.contains("setTextColor(R.id.widget_chunk,")
            },
            "a recycled frame whose text view is hidden would show no text at all",
        )
        check(
            "a task item shows its box beside its text",
            blockOf(itemDrawing, "private fun drawTask(").let { taskItem ->
                taskItem.contains("setViewVisibility(R.id.widget_box, View.VISIBLE)") &&
                    taskItem.contains("setImageViewBitmap(") &&
                    taskItem.contains("setViewPadding(R.id.widget_chunk, 0, gap, 0, gap)") &&
                    taskItem.contains("if (!readOnly)")
            },
            "a task without its box is a line of text with nothing to tick",
        )
        check(
            "a picture item shows the picture and no text",
            blockOf(itemDrawing, "private fun drawPicture(").contains("setViewVisibility(R.id.widget_picture, View.VISIBLE)") &&
                blockOf(itemDrawing, "private fun drawPicture(").contains("setImageViewBitmap(R.id.widget_picture"),
        )
        check(
            "a picture that cannot be read is drawn as its address, not as a hole",
            blockOf(itemDrawing, "private fun drawPicture(").contains("drawText(views, chunk.url)"),
        )
        check(
            "an item that shows nothing hides every view",
            blockOf(itemDrawing, "fun emptyItem(", braceOnly = true).let { empty ->
                empty.contains("setViewVisibility(R.id.widget_chunk, View.GONE)") &&
                    empty.contains("setViewVisibility(R.id.widget_box, View.GONE)") &&
                    empty.contains("setViewVisibility(R.id.widget_picture, View.GONE)")
            },
            "an empty item left a recycled frame's picture or box in place",
        )
        // The platform forwards a throw from a factory to the app's uncaught handler: one bad
        // picture would otherwise take the whole app down while the launcher waits.
        val itemAt = blockOf(itemDrawing, "private fun itemAt(")
        check(
            "an item whose drawing fails is replaced rather than thrown",
            itemAt.contains("runCatching { draw(views, chunk) }") &&
                ordered(itemAt, "drawFallback(", "return Item("),
            "one bad picture must cost one line of text, not the app",
        )
        val fallback = blockOf(itemDrawing, "private fun drawFallback(")
        check(
            "the fallback item is a plain text item that can be tapped",
            fallback.contains("reset(views)") &&
                fallback.contains("setOnClickFillInIntent(R.id.widget_item, Intent())") &&
                fallback.contains("drawText(views, text)"),
            "a fallback that cannot be tapped is a row that does nothing",
        )
        check(
            "the band a picture is cut in is never smaller than one pixel",
            blockOf(itemDrawing, "private fun targetWidth(").contains("coerceIn(1, size.width)") &&
                itemDrawing.substringAfter("private fun targetHeight(").take(220)
                    .contains("coerceIn(1, size.height)") &&
                blockOf(itemDrawing, "private fun picture(").contains("if (size.width <= 0 || size.height <= 0) return null") &&
                blockOf(itemDrawing, "private fun bandBitmap(").contains("if (bitmap.width <= 0 || bitmap.height <= 0 || width <= 0 || height <= 0) return null"),
            "a zero-sized band is a Bitmap.createBitmap that throws",
        )
        check(
            "a picture is cut to its band before it is scaled",
            ordered(
                blockOf(itemDrawing, "private fun bandBitmap("),
                "Bitmap.createBitmap(bitmap, 0, top, bitmap.width,",
                "Bitmap.createScaledBitmap(",
            ),
            "a whole picture scaled to a band is the mosaic the card showed",
        )
        check(
            "an eager list stops before the update that carries it gets too big",
            blockOf(itemDrawing, "fun views(").contains("EAGER_LIST_BYTES") &&
                itemDrawing.contains("ITEM_OVERHEAD_BYTES"),
        )
        // The two collection paths share one drawing but not one parcel: an eagerly sent list pays
        // for every picture in the same transaction as the card, so it is cut smaller than an item
        // that is its own binder call.
        check(
            "each collection path draws its pictures to its own budget",
            blockOf(itemDrawing, "fun list(").contains("EAGER_PICTURE_BYTES") &&
                blockOf(itemDrawing, "fun collection(").contains("COLLECTION_PICTURE_BYTES"),
        )
        check(
            "a list that cannot be built answers with nothing rather than an exception",
            blockOf(itemDrawing, "fun list(").contains("runCatching") &&
                blockOf(itemDrawing, "fun list(").contains("emptyList()") &&
                blockOf(itemDrawing, "fun list(").contains("Log.w"),
            "the platform takes a throw from an update path to the uncaught handler",
        )

        // Below Android 12 the body is the deprecated collection service, and the platform hands a
        // throw from any of its factory methods straight to the process's uncaught-exception
        // handler: every one of them has to answer rather than throw.
        println("Widget collection factory")

        val getViewAt = blockOf(service, "override fun getViewAt(")
        check(
            "a factory item is the shared drawing's item",
            getViewAt.contains("drawing?.viewAt(position)") &&
                getViewAt.contains("WidgetItems.emptyItem(context, layout)"),
            "a second item builder is a second item contract to keep aligned",
        )
        check(
            "a factory item that cannot be drawn answers with an empty one rather than throwing",
            getViewAt.contains("catch (failure: Throwable)") &&
                ordered(getViewAt, "catch (failure: Throwable)", "WidgetItems.emptyItem(") &&
                getViewAt.contains("Log.w"),
            "the platform takes a factory throw to the uncaught handler",
        )
        val dataChanged = blockOf(service, "override fun onDataSetChanged(")
        check(
            "a factory read that fails is logged and leaves an empty body",
            dataChanged.contains("runCatching") &&
                ordered(dataChanged, "runCatching", "WidgetItems.collection(") &&
                dataChanged.contains("drawing = null"),
            "a read that throws here takes the app down while the launcher waits",
        )
        check(
            "the factory reads its body through the shared drawing",
            dataChanged.contains("WidgetItems.collection(") &&
                dataChanged.contains("WidgetContent.note("),
        )
        // A factory can serve every item layout over its life - the type weight is a setting - and
        // the host maps each distinct layout id it meets to one of the declared view types, refusing
        // an item once that mapping runs past the count. The declared count therefore has to cover
        // every layout the body can be drawn in, even though one drawing uses only one of them.
        val itemLayouts = listOf("light", "regular", "medium", "semibold")
            .map { source("app/src/main/res/layout/widget_item_$it.xml") }
        val bodyLayouts = blockOf(
            source("app/src/main/java/com/hiapps/hinotes/widget/WidgetText.kt"),
            "fun bodyLayout(",
            braceOnly = true,
        )
        val declaredViewTypes = Regex("override fun getViewTypeCount\\(\\): Int = (\\d+)")
            .find(service)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
        check(
            "the factory declares a view type for every item layout it can draw",
            declaredViewTypes == itemLayouts.size &&
                itemLayouts.all { it.contains("@+id/widget_item") } &&
                bodyLayouts.contains("R.layout.widget_item_light") &&
                bodyLayouts.contains("R.layout.widget_item_regular") &&
                bodyLayouts.contains("R.layout.widget_item_medium") &&
                bodyLayouts.contains("R.layout.widget_item_semibold") &&
                !service.contains("override fun getItemViewType("),
            "declared $declaredViewTypes view type(s) for ${itemLayouts.size} item layout(s)",
        )
        check(
            "the factory's count, ids and stability come from the drawing",
            service.contains("override fun getCount(): Int = drawing?.itemCount ?: 0") &&
                service.contains("override fun getItemId(position: Int): Long = position.toLong()") &&
                service.contains("override fun hasStableIds(): Boolean = false"),
        )
        check(
            "the factory's drawing says what it drew",
            dataChanged.contains("items, ") && dataChanged.contains("tasks, "),
            "a card that shows the wrong thing has to be readable in a log",
        )

        // The card itself: one drawing has to reach the launcher as one card and one list of
        // items, and the next drawing has to know exactly which card and note it belongs to. A
        // drawing that updates the card without telling the list leaves the previous note's body
        // under the new title - which reads as a widget that shows the wrong note.
        println("Widget drawing pipeline")

        val draw = blockOf(provider, "private suspend fun draw(")
        check(
            "a drawing that fails is logged rather than thrown",
            draw.contains("runCatching") && draw.contains(".onFailure"),
            "this runs on a scope nobody awaits, so a throw takes the app down",
        )
        // Android 16 converts a legacy RemoteViewsService collection eagerly inside updateAppWidget,
        // and notifyAppWidgetViewDataChanged may quietly do nothing there; the supported shape from
        // API 31 is RemoteCollectionItems carried by the update itself, so one changed card reaches
        // the launcher as one updateAppWidget and no notification at all.
        check(
            "a change reaches the launcher as the card update itself on Android 12+",
            provider.contains("RemoteViews.RemoteCollectionItems.Builder()") &&
                provider.contains("views.setRemoteAdapter(android.R.id.list, collection.build())") &&
                ordered(
                    blockOf(provider, "private fun card("),
                    "Build.VERSION.SDK_INT >= Build.VERSION_CODES.",
                    "eagerBody(",
                ),
            "the deprecated service collection is converted by the platform on 16",
        )
        val eagerBody = blockOf(provider, "private fun eagerBody(")
        check(
            "the eager body is the note's items built in this process",
            eagerBody.contains("WidgetItems.list(app, widgetId, note, settings)") &&
                eagerBody.contains("if (items.isEmpty()) return false"),
            "a body that cannot be built is a card with a title and nothing under it",
        )
        check(
            "the eager body is declared for the platform version it uses",
            provider.contains("import androidx.annotation.RequiresApi") &&
                provider.contains("@RequiresApi(Build.VERSION_CODES.") ,
            "an unguarded API 31 collection call is a crash below 31",
        )
        val legacyBody = blockOf(provider, "private fun legacyBody(")
        check(
            "the collection service is the body below the eager path",
            legacyBody.contains("setRemoteAdapter(android.R.id.list, adapter(app, widgetId))") &&
                ordered(blockOf(provider, "private fun card("), "Build.VERSION.SDK_INT >= Build.VERSION_CODES.", "legacyBody("),
            "the service-backed collection is deprecated from API 35",
        )
        check(
            "a body that cannot be built eagerly falls back to the service",
            ordered(
                blockOf(provider, "private fun card("),
                "runCatching { eagerBody(",
                "legacyBody(views, app, widgetId)",
            ),
            "a failed eager build must not cost the card its body",
        )
        // The two branches have to be complements: whichever version draws the body through the
        // service also has to be told that the collection has changed, or that version keeps the
        // list it read on the last notification while the card around it is redrawn.
        val eagerGuard = Regex("Build\\.VERSION\\.SDK_INT >= Build\\.VERSION_CODES\\.([A-Z0-9_]+)")
            .find(blockOf(provider, "private fun card("))
            ?.groupValues
            ?.get(1)
        check(
            "the list is notified on exactly the versions whose body is the service's",
            eagerGuard != null &&
                provider.contains("@RequiresApi(Build.VERSION_CODES.$eagerGuard)") &&
                draw.contains("Build.VERSION.SDK_INT < Build.VERSION_CODES.$eagerGuard"),
            "the eager branch and the notification guard disagree around API $eagerGuard",
        )
        // The notification only exists for the legacy path and only for a card that has a list: a
        // card with nothing bound has no collection to refresh, and notifying one is the call the
        // conversion layer logs "possibly notifying updates for nonexistent view Id" about.
        val notifyUses = Regex("notifyAppWidgetViewDataChanged\\(").findAll(provider).count()
        check(
            "the list is notified only after the card update, and only for a card that has a list",
            notifyUses == 1 &&
                ordered(draw, "updateAppWidget(", "notifyAppWidgetViewDataChanged(") &&
                ordered(draw, "content is WidgetContent.Card.Shown", "notifyAppWidgetViewDataChanged("),
            "found $notifyUses notification(s): one legacy-only call was expected",
        )
        check(
            "a card with nothing to show says so by leaving its list out",
            blockOf(provider, "private fun card(").contains("if (body)") &&
                blockOf(provider, "private fun card(").contains("setViewVisibility(android.R.id.list, View.GONE)"),
            "an empty or locked card must not keep a scrolling body either",
        )

        val adapterIntent = blockOf(provider, "private fun adapter(", braceOnly = true)
        check(
            "each widget asks for its own list items",
            adapterIntent.contains("putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)") &&
                adapterIntent.contains("Uri.parse(toUri(Intent.URI_INTENT_SCHEME))"),
            "without an address every widget shares the first intent's extras",
        )

        // Several triggers land on one card within a frame or two - the host's update, the picker,
        // a save - and a drawing that is already waiting is replaced rather than repeated: both
        // would read the same stored note.
        val refresh = blockOf(provider, "internal fun refresh(")
        check(
            "a burst of triggers on one card is drawn once",
            refresh.contains("pending.remove(widgetId)?.cancel()") &&
                refresh.contains("scope.launch") &&
                ordered(refresh, "delay(COALESCE_MS)", "draw(app, widgetId, readSettings(app))"),
            "several triggers rebuilt and re-sent one card's list several times",
        )
        check(
            "every redraw request ends in the same per-widget drawing",
            blockOf(provider, "fun refreshAll(").contains(".forEach { refresh(context, it) }") &&
                blockOf(provider, "fun refreshNotes(").contains(".forEach { refresh(context, it) }") &&
                provider.contains("private val pending = HashMap<Int, Job>()"),
            "a second path that draws directly would not coalesce with the first",
        )

        val cardView = blockOf(provider, "private fun card(")
        check(
            "a card with nothing to show is emptied rather than left as it was",
            cardView.contains("titleVisibility(settings, bound = false)") &&
                cardView.contains("setViewVisibility(android.R.id.list, View.GONE)"),
            "a card whose note has gone must not keep its title or its list",
        )
        check(
            "a note that has gone offers the picker, and an app with no notes opens itself",
            cardView.contains("Card.Missing") &&
                cardView.contains("pickNote(app, widgetId)") &&
                cardView.contains("openApp(app, widgetId)"),
            "an empty card that opens nothing leaves a widget with no way back",
        )
        check(
            "a card showing a note binds the body, the row template and its own tap",
            cardView.contains("setPendingIntentTemplate(android.R.id.list, tapNote(") &&
                cardView.contains("setOnClickPendingIntent(R.id.widget_card, openNote(app, note.id, widgetId))"),
            "a note's card without a body or a tap shows a title over nothing",
        )
        // The template every row fills in has to be mutable, and each of the pending intents a
        // card can fire has to have an address of its own: two intents that differ only in their
        // extras are one pending intent to the platform, and the second would overwrite the first.
        check(
            "a row's tap template may be filled in",
            blockOf(provider, "private fun tapNote(").contains("PendingIntent.FLAG_MUTABLE") &&
                provider.contains("setPendingIntentTemplate(android.R.id.list, tapNote("),
            "an immutable template refuses the fill-in the rows deliver",
        )
        val intentAddresses = listOf("hinotes://note/", "hinotes://tap/", "hinotes://home/", "hinotes://pick/")
        check(
            "every pending intent a card can fire has an address of its own",
            intentAddresses.all { provider.contains(it) } &&
                provider.contains("Uri.parse(toUri(Intent.URI_INTENT_SCHEME))"),
            "missing one of $intentAddresses",
        )

        check(
            "the host's first request draws every widget it names",
            blockOf(provider, "override fun onUpdate(").contains("drawNow(context, ids)"),
            "the host's own update must be answered before the broadcast ends",
        )
        // The host's request is a broadcast: once the receiver returns, the process is empty and can
        // be reclaimed before an eager body has been built. goAsync keeps it alive until every card
        // of the request has been drawn, and every outcome finishes it.
        val drawNow = blockOf(provider, "private fun drawNow(")
        check(
            "a drawing the host asked for is finished before the broadcast ends",
            drawNow.contains("goAsync()") &&
                ordered(drawNow, "goAsync()", "ids.forEach { draw(") &&
                ordered(drawNow, "ids.forEach { draw(", "pending.finish()") &&
                drawNow.contains("catch (failure: Throwable)"),
            "a card whose drawing outlives its broadcast can be left on the placeholder layout",
        )
        check(
            "a measured or resized card is drawn again",
            blockOf(provider, "override fun onAppWidgetOptionsChanged(")
                .contains("drawNow(context, intArrayOf(appWidgetId))"),
            "a widget may be left in the layout's own colours when its first drawing is missed",
        )
        check(
            "a widget the host has removed leaves nothing pointing at it",
            blockOf(provider, "override fun onDeleted(").contains("WidgetNotes.unbind("),
        )
        val onReceive = blockOf(provider, "override fun onReceive(")
        check(
            "the widget's receiver no longer waits for a broadcast it can never be sent",
            !onReceive.contains("ACTION_CONFIGURATION_CHANGED"),
            "ACTION_CONFIGURATION_CHANGED is not delivered to a manifest-declared receiver",
        )
        // A tick is written by the receiver the tapped box broadcast to, not by the activity that
        // read the tap: goAsync() keeps the process alive until the write and its drawing are done,
        // which a fire-and-forget coroutine from a finished activity cannot promise.
        val tickTask = blockOf(provider, "private fun tickTask(")
        check(
            "a tapped box's tick is answered by the receiver, which holds the process for it",
            onReceive.contains("ACTION_TICK_TASK") &&
                onReceive.contains("tickTask(context, intent)") &&
                tickTask.contains("goAsync()") &&
                ordered(tickTask, "goAsync()", "tick(context.applicationContext, widgetId, line, checked)") &&
                ordered(tickTask, "tick(context.applicationContext, widgetId, line, checked)", "pending.finish()"),
            "a write that outlives the receiver can be reaped before it lands",
        )
        val tickWrite = blockOf(provider, "internal suspend fun tick(")
        check(
            "a tick of a locked note writes nothing",
            tickWrite.contains("if (note.isLocked)") &&
                ordered(tickWrite, "if (note.isLocked)", "Markdown.setTaskChecked("),
            "a locked note's task must not be changed from the home screen",
        )
        check(
            "a tick replaces a drawing that was already waiting",
            ordered(tickWrite, "cancelPending(widgetId)", "draw(app, widgetId, readSettings(app))"),
            "a waiting drawing read the note before the tick and would paint the pre-tick state",
        )

        // The other half of the contract: an app-side change that a card shows must ask for a
        // drawing of exactly the cards that show it, after the change is stored rather than
        // before it. A redraw launched before the write reads the note as it was.
        println("Widget redraws from the app")

        val appModel = source("app/src/main/java/com/hiapps/hinotes/ui/AppViewModel.kt")
        val saveNote = blockOf(appModel, "suspend fun saveNote(")
        check(
            "a save is stored before the cards that show the note are redrawn",
            ordered(saveNote, "notesRepository.saveNote(note)", "HiNotesWidgetProvider.refreshNote(appContext, note.id)"),
            "a card drawn before the write shows the note as it was",
        )
        check(
            "a save redraws the cards showing that note, not every card",
            saveNote.isNotEmpty() && !saveNote.contains("refreshAll"),
            "every keystroke redrawing every widget is work no card asked for",
        )
        val deleteNote = blockOf(appModel, "suspend fun deleteNote(")
        check(
            "a deleted note is stored as deleted before its cards are redrawn",
            ordered(deleteNote, "notesRepository.deleteNote(id)", "HiNotesWidgetProvider.refreshNote(appContext, id)"),
            "a card redrawn before the delete still finds the note",
        )
        val setLocked = blockOf(appModel, "suspend fun setNoteLocked(")
        check(
            "locking a note redraws the cards showing it as read-only",
            ordered(setLocked, "notesRepository.setLocked(id, locked)", "HiNotesWidgetProvider.refreshNote(appContext, id)"),
            "a locked note's card keeps offering a tick the app will refuse",
        )
        val tickEditor = blockOf(appModel, "suspend fun setTaskChecked(")
        check(
            "a tick is written before the card that ticked it is redrawn",
            ordered(tickEditor, "notesRepository.replaceContent(id, updated)", "HiNotesWidgetProvider.refreshNote(appContext, id)"),
            "a card redrawn before the write shows the box in its old state",
        )
        check(
            "a tick of a line that is no longer a task writes nothing",
            ordered(tickEditor, "if (updated == note.content) return", "notesRepository.replaceContent("),
            "a stale tap must not rewrite a line that has since become something else",
        )
        val commitDelete = blockOf(appModel, "suspend fun commitStagedDelete(")
        check(
            "a committed deletion redraws every card it touches, once",
            ordered(commitDelete, "notesRepository.deleteNote(it)", "HiNotesWidgetProvider.refreshNotes(appContext, ids)") &&
                !commitDelete.contains("refreshAll"),
            "one redraw per deleted note would draw one card several times",
        )
        val replaceAll = blockOf(appModel, "suspend fun replaceAllNotes(")
        check(
            "an import that replaced every note redraws every card",
            ordered(replaceAll, "notesRepository.replaceAll(notes)", "HiNotesWidgetProvider.refreshAll(appContext)"),
            "a restored backup left the cards showing notes that have been replaced",
        )
        val pinNote = blockOf(appModel, "suspend fun setNotePinned(")
        check(
            "a pin is not something a card shows, so it redraws none",
            pinNote.isNotEmpty() && !pinNote.contains("HiNotesWidgetProvider"),
            "a redraw per pin is work a card cannot show",
        )
        val applySettings = blockOf(
            appModel,
            "suspend fun applySettings(block: suspend SettingsRepository.() -> Unit)",
        )
        check(
            "a settings write redraws the cards only when it changed something they draw with",
            applySettings.contains("if (widgetSettings(before) != widgetSettings(settingsRepository.current()))") &&
                ordered(applySettings, "widgetSettings(before)", "HiNotesWidgetProvider.refreshAll(appContext)"),
            "a slider step that changes nothing a card draws must cost no drawing",
        )
        val widgetSettings = blockOf(appModel, "private fun widgetSettings(")
        // Every setting a card draws with: its scheme, whether the note is rendered, its own type,
        // whether the title is shown, and the language of the card's own labels. A setting added
        // to a card without being listed here is a card that keeps an appearance the user replaced.
        val widgetSettingsKeys = listOf(
            "dynamicColor", "accentPalette", "darkMode", "oledDark", "markdownEnabled",
            "widgetFontScale", "widgetFontWeight", "widgetShowTitle", "language",
        )
        val unlistedSettings = widgetSettingsKeys.filterNot { widgetSettings.contains("settings.$it") }
        check(
            "every setting a card draws with is compared before the cards are redrawn",
            widgetSettings.isNotEmpty() && unlistedSettings.isEmpty(),
            "missing from widgetSettings(): $unlistedSettings",
        )
        val widgetRepos = source("app/src/main/java/com/hiapps/hinotes/widget/WidgetRepositories.kt")
        check(
            "a widget reads the settings store rather than keeping a snapshot of it",
            widgetRepos.contains("SettingsRepository") && !widgetRepos.contains("HiNotesSettings"),
            "a snapshot kept here would paint every later card from an old appearance",
        )
        // The repository kept for widget drawing is process-wide and its list is only current for
        // writes made through that same instance; the app writes through its own instance, so the
        // card has to be decided on a fresh read of the storage both writers share. Reading the
        // list would paint the note as it was at the last home-screen tick until process restart.
        val cardRead = blockOf(content, "suspend fun card(")
        check(
            "a widget decides its card on a fresh read of storage",
            ordered(cardRead, "notes.refresh()", "notes.note("),
            "the repository's list is only filled by the widget's own writes",
        )
        // The other direction of the same split: the widget writes through its own repository
        // instance, so the app's copy can be behind what is on disk when the app comes forward.
        // It re-reads once on the way in, or a note opened from a ticked card shows the old text
        // and the next save puts that text back over the tick.
        val mainSource = source("app/src/main/java/com/hiapps/hinotes/MainActivity.kt")
        check(
            "returning to the app re-reads the notes a widget may have written",
            blockOf(appModel, "suspend fun reloadNotes(").contains("refreshNotes()") &&
                blockOf(mainSource, "override fun onResume(").contains("viewModel.reloadNotes()"),
            "a stale app copy can save the pre-tick text back over the tick",
        )

        // Every way a change can arrive, from the launcher's picker to a tap on a row, has to
        // reach the same drawing code with the right note and the right widget.
        println("Widget entry points")

        val main = source("app/src/main/java/com/hiapps/hinotes/MainActivity.kt")
        check(
            "opening the app draws every card again",
            blockOf(main, "override fun onCreate(").contains("HiNotesWidgetProvider.refreshAll(this)"),
            "a setting changed while the app was not running would otherwise stay stale",
        )
        // A light/dark switch is a configuration change. The process hears it while the app is in
        // the background, and the activity hears it too: it declares `uiMode` in `configChanges`, so
        // a switch does not restart it and no other callback there would notice. Both requests are
        // coalesced into one drawing by the provider's own request window.
        val application = source("app/src/main/java/com/hiapps/hinotes/HiNotesApplication.kt")
        val manifest = source("app/src/main/AndroidManifest.xml")
        check(
            "the process hears a light/dark switch while the app is in the background",
            manifest.contains("android:name=\".HiNotesApplication\"") &&
                !manifest.contains("android.intent.action.CONFIGURATION_CHANGED") &&
                blockOf(application, "override fun onConfigurationChanged(")
                    .contains("HiNotesWidgetProvider.refreshAll(this)"),
            "a switch made with no screen brought forward would leave the cards in the old colours",
        )
        check(
            "the screen hears the switch it declares configChanges for",
            blockOf(main, "override fun onConfigurationChanged(")
                .contains("HiNotesWidgetProvider.refreshAll(this)"),
            "with `uiMode` handled by the activity, no other callback there sees a switch",
        )
        check(
            "a process that starts after a change repaints the cards it finds",
            blockOf(application, "override fun onCreate(")
                .contains("HiNotesWidgetProvider.refreshAll(this)"),
            "a card that changed while the process was dead would stay stale until a setting changed",
        )
        check(
            "coming forward to the app repaints the cards it holds",
            blockOf(main, "override fun onResume(")
                .contains("HiNotesWidgetProvider.refreshAll(this)"),
            "a card tapped while its process was gone would otherwise stay in the old colours",
        )
        check(
            "a tap on a card publishes the note it asked for, once",
            blockOf(main, "override fun onCreate(").contains("pendingNoteId.value = intent?.getStringExtra(EXTRA_NOTE_ID)") &&
                blockOf(main, "override fun onNewIntent(").contains("pendingNoteId.value = intent.getStringExtra(EXTRA_NOTE_ID)"),
            "a rotation must not open the note a second time",
        )
        check(
            "a card names the note it wants by an extra of the app's own",
            main.contains("const val EXTRA_NOTE_ID") && tap.contains("MainActivity.EXTRA_NOTE_ID"),
        )
        val shell = source("app/src/main/java/com/hiapps/hinotes/ui/HiNotesApp.kt")
        val startNote = blockOf(shell, "LaunchedEffect(startNoteId)")
        check(
            "a note a card named is opened, and the request is spent",
            startNote.contains("viewModel.notesRepository.note(noteId)") &&
                startNote.contains("push(Routes.editor(noteId))") &&
                startNote.contains("onStartNoteShown()"),
            "a tap on a card whose note was deleted must not open a blank note",
        )
        // The card may have written a tick since the app's list was read (the card writes through
        // its own repository instance), so the lookup is made only after a fresh read - otherwise
        // the editor opens on the pre-tick text and the next save can write it back.
        check(
            "a note a card named is looked up after a fresh read",
            ordered(startNote, "viewModel.reloadNotes()", "viewModel.notesRepository.note(noteId)"),
            "the note opened from a just-ticked card must show the tick the card wrote",
        )
        val config = source("app/src/main/java/com/hiapps/hinotes/widget/WidgetConfigActivity.kt")
        val choose = blockOf(config, "private fun choose(")
        check(
            "the picker binds the note before it answers the host",
            ordered(choose, "WidgetNotes.bind(this, widgetId, noteId)", "setResult("),
            "the host may draw the widget the moment it has the answer",
        )
        check(
            "the picker draws the card itself rather than trusting the host to",
            ordered(choose, "setResult(", "HiNotesWidgetProvider.refresh(this, widgetId)") &&
                ordered(choose, "HiNotesWidgetProvider.refresh(this, widgetId)", "finish()"),
            "a launcher that never asks for a first drawing leaves a new widget blank",
        )
        check(
            "a row's tap asks for the tick through the receiver and leaves",
            tap.contains("HiNotesWidgetProvider.ACTION_TICK_TASK") &&
                tap.contains("sendBroadcast(") &&
                !tap.contains("HiNotesWidgetProvider.tick(") &&
                !tap.contains("setContentView") &&
                tap.contains("finish()"),
            "a tick is not a visit to the app, and not a write from a finished activity",
        )
        check(
            "a row's tap opens the note the card showed",
            tap.contains("putExtra(MainActivity.EXTRA_NOTE_ID, noteId)") &&
                tap.contains("startActivity(open)"),
        )
        val home = source("app/src/main/java/com/hiapps/hinotes/ui/screens/HomeScreen.kt")
        check(
            "ticking a box on a card goes through the one write that redraws the widget",
            home.contains("viewModel.setTaskChecked(note.id, line, checked)") &&
                home.contains("onToggleTask = if (note.isLocked || selecting)"),
            "a tick written around the view model leaves the widget showing the old box",
        )
        val editor = source("app/src/main/java/com/hiapps/hinotes/ui/screens/EditorScreen.kt")
        check(
            "every way the editor stores a note goes through the save that redraws the card",
            blockOf(editor, "suspend fun persist(").contains("viewModel.saveNote("),
            "a second write path would leave the card on the last text it saw",
        )
        check(
            "leaving the editor writes the draft before the page is left",
            ordered(blockOf(editor, "fun leave("), "persist()", "onBack()"),
            "a save-as-you-type app writes on the way out, not after the page has gone",
        )
        val backup = source("app/src/main/java/com/hiapps/hinotes/ui/screens/BackupScreen.kt")
        check(
            "restoring the defaults is a settings change a card can see",
            backup.contains("viewModel.applySettings { restoreDefaults() }"),
            "a restored default must not stay behind a card painted in the replaced scheme",
        )
        check(
            "importing settings is a settings change a card can see",
            backup.contains("viewModel.applySettings { replaceAll(decoded) }"),
        )
        check(
            "importing notes redraws every card after the write",
            backup.contains("viewModel.replaceAllNotes(notes)"),
            "an import can have replaced every note at once",
        )
        val unlock = source("app/src/main/java/com/hiapps/hinotes/ui/screens/UnlockScreen.kt")
        check(
            "setting a password blanks the cards after the store holds it",
            ordered(unlock, "viewModel.lockStore.setPassword(newPassword)", "viewModel.refreshWidgets()"),
            "a card drawn before the password is set shows a note it should hide",
        )
        check(
            "removing the password lets the cards show their notes again",
            ordered(unlock, "viewModel.lockStore.clear()", "viewModel.refreshWidgets()"),
            "the cards would stay blank until something else happened to draw them",
        )

        if (failures == 0) {
            println("widget text checks: all checks passed")
        } else {
            println("widget text checks: $failures check(s) FAILED")
            throw IllegalStateException("$failures widget text check(s) failed")
        }
    }

    /** One source file of the app, as the checks read the code rather than run it. */
    private fun source(path: String): String = File(path).readText()

    /**
     * The text of [source] that starts at [marker] and runs to the bracket that closes the first
     * one after it.
     *
     * A source check about "the drawing" has to read one function as one unit: a statement found
     * anywhere else in the file is not evidence about that function, and a check written against
     * the whole file would pass while the function does the opposite. Strings and comments are
     * stepped over, so a bracket written inside one does not end the block.
     *
     * [braceOnly] reads a function whose body is a braced builder - a chain of calls that itself
     * contains parentheses before the brace the body begins with.
     */
    private fun blockOf(source: String, marker: String, braceOnly: Boolean = false): String {
        val start = source.indexOf(marker)
        if (start < 0) return ""
        var i = start + marker.length
        while (i < source.length && source[i] != '{' && (braceOnly || source[i] != '(')) i++
        if (i == source.length) return ""
        val open = source[i]
        val close = if (open == '{') '}' else ')'
        var depth = 0
        var j = i
        while (j < source.length) {
            val c = source[j]
            if (c == '"') {
                j = skipString(source, j)
                continue
            }
            if (c == '\'') {
                j = skipChar(source, j)
                continue
            }
            if (c == '/' && j + 1 < source.length && source[j + 1] == '/') {
                val end = source.indexOf('\n', j)
                j = if (end < 0) source.length else end + 1
                continue
            }
            if (c == '/' && j + 1 < source.length && source[j + 1] == '*') {
                // Kotlin's block comments nest, so the matching close is counted rather than found.
                var nesting = 0
                var k = j
                while (k < source.length) {
                    if (k + 1 < source.length && source[k] == '/' && source[k + 1] == '*') {
                        nesting++
                        k += 2
                        continue
                    }
                    if (k + 1 < source.length && source[k] == '*' && source[k + 1] == '/') {
                        nesting--
                        k += 2
                        if (nesting == 0) break
                        continue
                    }
                    k++
                }
                j = k
                continue
            }
            if (c == open) {
                depth++
            } else if (c == close) {
                depth--
                if (depth == 0) return source.substring(start, j + 1)
            }
            j++
        }
        return source.substring(start)
    }

    /** The index just past the string literal that opens at [start], raw or escaped. */
    private fun skipString(source: String, start: Int): Int {
        val raw = start + 2 < source.length && source[start + 1] == '"' && source[start + 2] == '"'
        var i = if (raw) start + 3 else start + 1
        while (i < source.length) {
            val c = source[i]
            if (!raw && c == '\\') {
                i += 2
                continue
            }
            if (c == '"') {
                if (!raw) return i + 1
                if (i + 2 < source.length && source[i + 1] == '"' && source[i + 2] == '"') return i + 3
            }
            i++
        }
        return source.length
    }

    /** The index just past the character literal that opens at [start]. */
    private fun skipChar(source: String, start: Int): Int {
        var i = start + 1
        while (i < source.length) {
            if (source[i] == '\\') {
                i += 2
                continue
            }
            if (source[i] == '\'') return i + 1
            i++
        }
        return source.length
    }

    /** True when [first] and [second] are in [section] and in that order. */
    private fun ordered(section: String, first: String, second: String): Boolean {
        val at = section.indexOf(first)
        if (at < 0) return false
        return section.indexOf(second, at + first.length) >= 0
    }
}
