package com.hiapps.hinotes.ui.editor

import androidx.compose.ui.text.input.TextFieldValue

/**
 * Standalone checks for the editor's Markdown verbs, run on a desktop JVM.
 *
 * The toolbar's buttons are the app's most tactile controls and the easiest to get subtly wrong:
 * a toggle that removes a prefix which was never there looks like a dead button. These checks
 * drive the real [Markdown] code with the same inputs a phone produces - an empty note, a caret
 * on a blank line, a selection spanning several lines - and assert the resulting text and caret.
 */
object MarkdownChecks {

    private var failures = 0

    private fun check(name: String, condition: Boolean, detail: String = "") {
        if (condition) {
            println("  ok    $name")
        } else {
            failures++
            println("  FAIL  $name ${if (detail.isEmpty()) "" else "-> $detail"}")
        }
    }

    /** A value with the caret at [caret], or a selection between [start] and [end]. */
    private fun value(text: String, start: Int = text.length, end: Int = start) =
        TextFieldValue(text, androidx.compose.ui.text.TextRange(start, end))

    private fun toggle(text: String, prefix: String, start: Int = text.length, end: Int = start):
        TextFieldValue = Markdown.toggleLinePrefix(value(text, start, end), prefix)

    /** The marker the editor's checkbox button inserts. */
    private const val CHECKBOX = "[ ] "

    @JvmStatic
    fun main(args: Array<String>) {
        println("Markdown line prefix")

        // The reported bug: the checkbox button did nothing on a new, empty note, because a blank
        // line counted as "already prefixed".
        val empty = toggle("", CHECKBOX)
        check("empty note gains a checkbox", empty.text == CHECKBOX, "'${empty.text}'")
        check(
            "caret lands after the marker",
            empty.selection.start == CHECKBOX.length,
            "${empty.selection}",
        )

        val emptyBullet = toggle("", "- ")
        check("empty note gains a bullet", emptyBullet.text == "- ", "'${emptyBullet.text}'")

        // Pressing it again removes what it added.
        val twice = Markdown.toggleLinePrefix(empty, CHECKBOX)
        check("second press removes it", twice.text == "", "'${twice.text}'")

        // A caret on a trailing blank line: the block has text above it that is not prefixed.
        val afterText = toggle("hello\n", CHECKBOX, start = 6)
        check(
            "blank trailing line gains a checkbox",
            afterText.text == "hello\n$CHECKBOX",
            "'${afterText.text}'",
        )

        // A caret on a blank line inside a prefixed list adds to that line, not removes from all.
        val checkboxList = "${CHECKBOX}a\n\n${CHECKBOX}b"
        val insideList = toggle(checkboxList, CHECKBOX, start = 6)
        check(
            "blank line inside a list gains a checkbox",
            insideList.text == "${CHECKBOX}a\n$CHECKBOX\n${CHECKBOX}b",
            "'${insideList.text}'",
        )

        // A block where every written line is prefixed toggles off, blank lines left alone.
        val surrounding = toggle("- a\n- b", "- ", start = 0, end = 7)
        check("whole prefixed block toggles off", surrounding.text == "a\nb", "'${surrounding.text}'")

        // Mixed block: the whole thing gains the prefix rather than half-removing it.
        val mixed = toggle("- a\nb", "- ", start = 0, end = 5)
        check("mixed block gains the prefix", mixed.text == "- - a\n- b", "'${mixed.text}'")

        // Multi-line selection, caret preserved at the end of the selection.
        val multi = toggle("one\ntwo\nthree", "- ", start = 0, end = 13)
        check("multi-line selection", multi.text == "- one\n- two\n- three", "'${multi.text}'")
        check("selection end follows the text", multi.selection.max == 19, "${multi.selection}")

        println("Markdown inline")
        val bold = Markdown.toggleInline(value("hello", 0, 5), "**")
        check("bold wraps the selection", bold.text == "**hello**", "'${bold.text}'")
        val unbold = Markdown.toggleInline(bold, "**")
        check("bold unwraps on a second press", unbold.text == "hello", "'${unbold.text}'")
        val boldInside = Markdown.toggleInline(value("**hello**", 2, 7), "**")
        check("bold unwraps from inside", boldInside.text == "hello", "'${boldInside.text}'")

        println("Markdown preview blocks")
        val blocks = Markdown.previewBlocks("intro\n- [ ] one\n- [x] two\nplain")
        check(
            "prose and tasks split",
            blocks.size == 4 &&
                blocks[0] is Markdown.PreviewBlock.Prose &&
                blocks[1] is Markdown.PreviewBlock.Task &&
                blocks[2] is Markdown.PreviewBlock.Task &&
                blocks[3] is Markdown.PreviewBlock.Prose,
            "$blocks",
        )
        val firstTask = blocks[1] as Markdown.PreviewBlock.Task
        check("task text loses the box", firstTask.text.text == "one", "'${firstTask.text.text}'")
        check("task starts unchecked", !firstTask.checked)
        check("task keeps its line number", firstTask.line == 1, "${firstTask.line}")
        val secondTask = blocks[2] as Markdown.PreviewBlock.Task
        check("second task keeps its line number", secondTask.line == 2, "${secondTask.line}")
        check("second task is checked", secondTask.checked)
        val firstProse = (blocks[0] as Markdown.PreviewBlock.Prose).text.text
        check("prose keeps the whole run", firstProse == "intro\n", "'$firstProse'")

        val checkedTask = Markdown.previewBlocks("- [x] done")[0] as Markdown.PreviewBlock.Task
        check("checked task reports checked", checkedTask.checked)
        // The toolbar's bare form is a task line too, so it is clickable in the preview.
        val bare = Markdown.previewBlocks("[ ] bare")[0]
        check("bare form is a task line", bare is Markdown.PreviewBlock.Task, "$bare")
        // Indented task lines keep their indent in the source but not in the rendered text.
        val indented = Markdown.previewBlocks("    - [ ] deep")[0] as Markdown.PreviewBlock.Task
        check("indented task text is clean", indented.text.text == "deep", "'${indented.text.text}'")

        // A box inside a fence is code, and code is not a checkbox.
        val fenced = Markdown.previewBlocks("```\n- [ ] code\n```")
        check(
            "fenced box stays prose",
            fenced.size == 1 && fenced[0] is Markdown.PreviewBlock.Prose,
            "$fenced",
        )
        val fencedText = (fenced[0] as Markdown.PreviewBlock.Prose).text.text
        check(
            "fenced box still shows its brackets",
            fencedText.contains("- [ ] code"),
            "'$fencedText'",
        )

        // A horizontal rule is its own block, so the preview can draw a real line for it instead
        // of a fixed run of dashes that could wrap and leave a stub behind.
        val ruled = Markdown.previewBlocks("above\n\n---\n\nbelow")
        check("a rule becomes a divider", ruled.any { it is Markdown.PreviewBlock.Divider }, "$ruled")
        check(
            "the rule is not also drawn as prose",
            ruled.filterIsInstance<Markdown.PreviewBlock.Prose>()
                .none { it.text.text.contains("─") },
            "$ruled",
        )
        check(
            "two dashes are not a rule",
            Markdown.previewBlocks("a -- b").none { it is Markdown.PreviewBlock.Divider },
        )
        check(
            "every rule form is recognised",
            listOf("---", "----", "***", "___", "  ---  ").all { line ->
                Markdown.previewBlocks(line).any { it is Markdown.PreviewBlock.Divider }
            },
        )
        check(
            "a rule inside a fence stays code",
            Markdown.previewBlocks("```\n---\n```").none { it is Markdown.PreviewBlock.Divider },
        )
        // The text-mode renderer still draws something for a rule - the list cards have no layout
        // to place a divider in - and what it draws has to stay inside a card.
        val ruler = Markdown.render("---").text
        check("text mode draws a short ruler", ruler.length in 6..14, "'$ruler' (${ruler.length})")

        println("Markdown checkbox editing")
        val source = "a\n- [ ] one\n- [x] two\n  * [ ] three"
        check(
            "checking a line ticks it",
            Markdown.setTaskChecked(source, 1, true) == "a\n- [x] one\n- [x] two\n  * [ ] three",
            "'${Markdown.setTaskChecked(source, 1, true)}'",
        )
        check(
            "unchecking a line clears it",
            Markdown.setTaskChecked(source, 2, false) == "a\n- [ ] one\n- [ ] two\n  * [ ] three",
            "'${Markdown.setTaskChecked(source, 2, false)}'",
        )
        check(
            "indent and bullet survive",
            Markdown.setTaskChecked(source, 3, true) == "a\n- [ ] one\n- [x] two\n  * [x] three",
            "'${Markdown.setTaskChecked(source, 3, true)}'",
        )
        check(
            "editing never changes the length",
            Markdown.setTaskChecked(source, 1, true).length == source.length,
        )
        check(
            "a prose line is left alone",
            Markdown.setTaskChecked(source, 0, true) == source,
            "'${Markdown.setTaskChecked(source, 0, true)}'",
        )
        check(
            "a stale line index is ignored",
            Markdown.setTaskChecked(source, 9, true) == source,
            "'${Markdown.setTaskChecked(source, 9, true)}'",
        )
        check(
            "round trip: tick then untick",
            Markdown.setTaskChecked(Markdown.setTaskChecked(source, 1, true), 1, false) == source,
        )
        // The bare form the toolbar writes is editable too.
        check(
            "bare form toggles",
            Markdown.setTaskChecked("[ ] bare", 0, true) == "[x] bare",
            "'${Markdown.setTaskChecked("[ ] bare", 0, true)}'",
        )

        println("Markdown rendering")
        val rendered = Markdown.render("**bold** and *italic*").text
        check("bold markers removed", rendered == "bold and italic", "'$rendered'")
        val list = Markdown.render("- one\n- two").text
        check("bullets drawn", list.startsWith("•"), "'$list'")
        val checkbox = Markdown.render("- [ ] todo\n- [x] done").text
        check("checkboxes drawn", checkbox.contains("☐") && checkbox.contains("☑"), "'$checkbox'")
        // The form the toolbar button writes must render as a checkbox too.
        val bareCheckbox = Markdown.render("[ ] todo\n[x] done").text
        check(
            "bare checkboxes drawn",
            bareCheckbox.contains("☐") && bareCheckbox.contains("☑") &&
                !bareCheckbox.contains("["),
            "'$bareCheckbox'",
        )
        val heading = Markdown.render("# Title").text
        check("heading text kept", heading == "Title", "'$heading'")
        val fence = Markdown.render("```\ncode\n```").text
        check("code fence hidden, body kept", fence.contains("code") && !fence.contains("```"), "'$fence'")

        report()
    }

    private fun report() {
        if (failures == 0) {
            println("markdown checks: all checks passed")
        } else {
            println("markdown checks: $failures check(s) FAILED")
            throw IllegalStateException("$failures markdown check(s) failed")
        }
    }
}
