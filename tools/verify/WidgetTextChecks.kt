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
    private fun pieces(body: CharSequence, taskLines: List<Int> = emptyList()): List<String> =
        WidgetChunks.chunks(body, taskLines).map { it.text.toString() }

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
        val rendered = pieces(Markdown.render("intro\n\n- [ ] one\n- [x] two\n").text)
        check("a rendered checklist is one chunk", rendered.size == 1, "$rendered")
        check("the rendered chunk ends on the last task", rendered.single().endsWith("two"), "'${rendered.single()}'")
        val withBreak = Markdown.render("intro\n\nlast\n\n").text
        val renderedTail = pieces(withBreak)
        check("a rendered body ending in a break is one chunk", renderedTail.size == 1, "$renderedTail")
        check(
            "a rendered body ending in a break loses it",
            renderedTail.single() == withBreak.trimEnd().toString(),
            "'${renderedTail.single()}'",
        )

        println("Widget task items")

        val note = "intro\n\n- [ ] one\n- [x] two\n"
        val taskLines = WidgetChunks.taskLines(note)
        check("the renderer's own parse names the task lines", taskLines == listOf(2, 3), "$taskLines")

        val checklist = WidgetChunks.chunks(Markdown.render(note).text, taskLines)
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

        // A body whose boxes cannot be matched up with the note's own task lines is drawn as prose:
        // a tick written back to the wrong line is worse than a box that has to be ticked in the editor.
        val unmatched = WidgetChunks.chunks(Markdown.render(note).text, emptyList())
        check("an unmatched body stays prose", unmatched.size == 1, "$unmatched")
        check(
            "and keeps its boxes as characters",
            unmatched.single().text.contains('\u2610') && unmatched.single().text.contains('\u2611'),
            "${unmatched.single().text}",
        )

        println("Widget interaction")

        val service = source("app/src/main/java/com/hiapps/hinotes/widget/HiNotesWidgetService.kt")
        val provider = source("app/src/main/java/com/hiapps/hinotes/widget/HiNotesWidgetProvider.kt")
        check("a task's box answers a tap of its own", service.contains("setOnClickResponse(R.id.widget_box"))
        check("the tap is asked for by broadcast rather than by opening the app", service.contains("ACTION_TICK_TASK"))
        check("it carries the state the tap means", service.contains("EXTRA_TASK_CHECKED, !chunk.checked"))
        check("the card writes the tick back to the line", provider.contains("Markdown.setTaskChecked("))
        check("the gap between two boxes is a task line's own", service.contains("TASK_GAP_DP"))
        check(
            "two boxes cannot share one pending intent",
            service.contains("data = Uri.parse(\"hinotes://tick/"),
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
}
