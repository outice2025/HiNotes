package com.hiapps.hinotes.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import java.io.File

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

        println("Markdown escapes")
        val escapedItalic = Markdown.render("\\*not italic\\*").text
        check("an escaped marker is literal", escapedItalic == "*not italic*", "'$escapedItalic'")
        // Every character a backslash may protect, on one line; only the backslash needs writing
        // twice. Anything outside this set keeps its backslash, which is what leaves paths alone.
        val punctuation = "\\*\\_\\`\\#\\[\\]\\(\\)\\~\\$\\|\\-\\.\\!\\<\\>\\\\"
        val literal = Markdown.render(punctuation).text
        check("every punctuation escape resolves", literal == "*_`#[]()~$|-.!<>\\", "'$literal'")
        val path = Markdown.render("C:\\Users").text
        check("a backslash before a letter is kept", path == "C:\\Users", "'$path'")
        // A line-leading escape keeps the marker a character instead of opening a block.
        val escapedHeading = Markdown.render("\\# not a heading").text
        check("an escaped hash is not a heading", escapedHeading == "# not a heading", "'$escapedHeading'")
        val escapedBullet = Markdown.render("\\- not a bullet").text
        check("an escaped dash is not a list item", escapedBullet == "- not a bullet", "'$escapedBullet'")
        check(
            "an escaped dash opens no task",
            Markdown.previewBlocks("\\- [ ] not a task").none { it is Markdown.PreviewBlock.Task },
        )
        check(
            "an escaped rule is not a rule",
            Markdown.previewBlocks("\\---").none { it is Markdown.PreviewBlock.Divider },
        )
        val escapedNumber = Markdown.render("1\\. not a list").text
        check("an escaped number is not a list", escapedNumber == "1. not a list", "'$escapedNumber'")

        println("Markdown comments")
        val inlineComment = Markdown.render("a<!-- note -->b").text
        check("a comment is not drawn", inlineComment == "ab", "'$inlineComment'")
        val wholeLine = Markdown.render("a\n<!-- note -->\nb").text
        check("a comment-only line leaves no gap", wholeLine == "a\nb", "'$wholeLine'")
        val spanned = Markdown.render("a\n<!--\nhidden\n-->\nb").text
        check("a comment spanning lines leaves none either", spanned == "a\nb", "'$spanned'")
        check(
            "a comment hides the markup inside it",
            !Markdown.render("<!-- **bold** -->").text.contains("bold"),
        )
        check("a comment-only note has no blocks", Markdown.previewBlocks("<!-- note -->").isEmpty())
        val commented = Markdown.previewBlocks("a\n<!--\nhidden\n-->\nb")
        check(
            "the preview drops comments too",
            commented.size == 1 &&
                (commented[0] as Markdown.PreviewBlock.Prose).text.text == "a\nb",
            "$commented",
        )
        // The line a task reports is what a tap writes back to, so a comment above it must not
        // shorten the note the way a removed line would.
        val afterComment = Markdown.previewBlocks("<!-- note -->\n- [ ] one")
        check(
            "a comment does not shift a task's line",
            afterComment.size == 1 && (afterComment[0] as Markdown.PreviewBlock.Task).line == 1,
            "$afterComment",
        )
        val commentInFence = Markdown.render("```\n<!-- x -->\n```").text
        check("a comment in a fence is code", commentInFence.contains("<!-- x -->"), "'$commentInFence'")

        println("Markdown lists")
        val nested = Markdown.render("- a\n  - b\n    - c\n      - d").text
        check(
            "each level draws its own bullet",
            nested == "•  a\n    ◦  b\n        ▪  c\n            ▪  d",
            "'$nested'",
        )
        val tabbed = Markdown.render("- a\n\t- b").text
        check("a tab is one level", tabbed == "•  a\n    ◦  b", "'$tabbed'")
        check(
            "an ordered marker is kept as written",
            Markdown.render("3. three\n4) four").text == "3. three\n4) four",
            "'${Markdown.render("3. three\n4) four").text}'",
        )
        check(
            "a nested number keeps its own number",
            Markdown.render("1. one\n  1. sub\n2. two").text == "1. one\n    1. sub\n2. two",
            "'${Markdown.render("1. one\n  1. sub\n2. two").text}'",
        )
        check(
            "an ordered item under a bullet stays nested",
            Markdown.render("- a\n  1. b").text == "•  a\n    1. b",
            "'${Markdown.render("- a\n  1. b").text}'",
        )

        // Lists are prose again: a bullet or a number is drawn inside the same run of text as the
        // words of its item, which is what a card and a widget can draw. The preview therefore keeps
        // a run of list lines as one Prose block, and only a task line - a control of its own - still
        // splits the run.
        println("Markdown lists in the preview")

        val listProse = Markdown.previewBlocks("- one\n- two")
        check(
            "a run of list lines is prose",
            listProse.size == 1 && listProse.single() is Markdown.PreviewBlock.Prose,
            "$listProse",
        )
        check(
            "the bullets are the text renderer's",
            (listProse.single() as Markdown.PreviewBlock.Prose).text.text ==
                Markdown.render("- one\n- two").text,
            "'${(listProse.single() as Markdown.PreviewBlock.Prose).text.text}'",
        )
        // The three shapes a list is written in, all of which a block per item used to cut apart: a
        // wrapped continuation line, a blank line between items (a loose list), and a task in the
        // middle. The prose holds the first two; the task is the control it always was.
        val continuation = Markdown.previewBlocks("- a\n  continued\n- b")
        check(
            "a continuation line stays in the list's prose",
            continuation.size == 1 && continuation.single() is Markdown.PreviewBlock.Prose,
            "$continuation",
        )
        val loose = Markdown.previewBlocks("- a\n\n- b")
        check(
            "a blank line between two items keeps one prose run",
            loose.size == 1 && loose.single() is Markdown.PreviewBlock.Prose,
            "$loose",
        )
        val withTask = Markdown.previewBlocks("- a\n- [ ] todo\n- b")
        check(
            "a task line splits the prose and stays a task",
            withTask.size == 3 &&
                withTask[0] is Markdown.PreviewBlock.Prose &&
                withTask[1] is Markdown.PreviewBlock.Task &&
                withTask[2] is Markdown.PreviewBlock.Prose,
            "$withTask",
        )

        println("Markdown tables")
        val table = Markdown.previewBlocks("| Name | Qty |\n|:-----|----:|\n| **apple** | 3 |")
        check(
            "a pipe table is its own block",
            table.size == 1 && table[0] is Markdown.PreviewBlock.Table,
            "$table",
        )
        val grid = table[0] as Markdown.PreviewBlock.Table
        check(
            "the header keeps both cells",
            grid.header.map { it.text } == listOf("Name", "Qty"),
            "${grid.header.map { it.text }}",
        )
        check(
            "the body row keeps both cells",
            grid.rows.single().map { it.text } == listOf("apple", "3"),
            "${grid.rows.single().map { it.text }}",
        )
        check(
            "a cell keeps its inline markup",
            grid.rows.single()[0].spanStyles.any { it.item.fontWeight == FontWeight.Bold },
            "${grid.rows.single()[0].spanStyles}",
        )
        check(
            "the colons carry the alignment",
            grid.alignments == listOf(Markdown.ColumnAlignment.Start, Markdown.ColumnAlignment.End),
            "${grid.alignments}",
        )
        check(
            "a centred column is centred",
            (Markdown.previewBlocks("a | b\n:-:|---")[0] as Markdown.PreviewBlock.Table).alignments ==
                listOf(Markdown.ColumnAlignment.Center, Markdown.ColumnAlignment.Start),
        )
        check(
            "a short row still fills every column",
            (Markdown.previewBlocks("| a | b |\n|---|---|\n| 1 |")[0] as Markdown.PreviewBlock.Table)
                .rows.single().size == 2,
        )
        check(
            "an escaped pipe stays inside its cell",
            (Markdown.previewBlocks("| a | b |\n|---|---|\n| x \\| y | z |")[0]
                as Markdown.PreviewBlock.Table).rows.single().map { it.text } == listOf("x | y", "z"),
        )
        check(
            "strike works inside a cell",
            (Markdown.previewBlocks("| a |\n|---|\n| ~~gone~~ |")[0] as Markdown.PreviewBlock.Table)
                .rows.single()[0].spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough },
        )
        // Malformed tables are the plain text they look like, rather than a grid guessed at.
        check(
            "a header with no delimiter row is prose",
            Markdown.previewBlocks("| a | b |").none { it is Markdown.PreviewBlock.Table },
        )
        check(
            "a delimiter row of the wrong width is not a table",
            Markdown.previewBlocks("| a | b |\n|---|\n| 1 | 2 |")
                .none { it is Markdown.PreviewBlock.Table },
        )
        check(
            "a delimiter row of words is not a table",
            Markdown.previewBlocks("| a |\n| oops |").none { it is Markdown.PreviewBlock.Table },
        )
        check(
            "a table inside a fence stays code",
            Markdown.previewBlocks("```\n| a |\n|---|\n```").none { it is Markdown.PreviewBlock.Table },
        )
        // The text-mode form, for the cards and the widget: monospace rows that line up.
        val textTable = Markdown.render("| Name | Qty |\n|:-----|----:|\n| apple | 3 |").text
        check(
            "text mode lines the columns up",
            textTable == "Name  │ Qty\n──────┼────\napple │   3",
            "'$textTable'",
        )
        check("text mode rules the header off", textTable.contains("─┼─"), "'$textTable'")
        val centred = Markdown.render("| a |\n|:-:|\n| bbb |").text
        check("text mode honours the alignment colons", centred == " a \n───\nbbb", "'$centred'")
        // A Chinese character is two columns wide in a monospace font, so the two-character header
        // sets the column at four and this one-character cell is padded as the two it occupies.
        val wide = Markdown.render("| 名称 | a |\n|---|---|\n| 中 | b |").text.lines()
        check("a full-width glyph takes two columns", wide[2] == "中   │ b", "'${wide[2]}'")

        println("Markdown math")
        val inlineMath = Markdown.render("let \$x\$ be").text
        check("an inline formula loses its dollars", inlineMath == "let x be", "'$inlineMath'")
        val formula = Markdown.render("\$x^2\$")
        check(
            "a formula is monospace and marked for its panel",
            formula.spanStyles.any { it.item.fontFamily == FontFamily.Monospace } &&
                formula.getStringAnnotations(Markdown.MATH_TAG, 0, formula.length)
                    .any { it.item == Markdown.MATH_INLINE },
            "${formula.spanStyles}",
        )
        check(
            "a formula's background is the caller's to round",
            formula.spanStyles.none { it.item.background != Color.Unspecified },
            "a span's background is a rectangle, which is what the rounded panel replaced",
        )
        val oneLine = Markdown.render("\$\$x + y\$\$").text
        check("the one-line block form drops its fences", oneLine == "x + y", "'$oneLine'")
        val block = Markdown.render("before\n\$\$\na = b\n\$\$\nafter").text
        check("a formula block sits on its own line", block == "before\n\na = b\n\nafter", "'$block'")
        val prices = Markdown.render("costs \$5 and \$10").text
        check("a price range is not a formula", prices == "costs \$5 and \$10", "'$prices'")

        println("Markdown strikethrough")
        val struck = Markdown.render("~~gone~~").text
        check("strike still works", struck == "gone", "'$struck'")
        val escapedStrike = Markdown.render("\\~\\~literal\\~\\~ and ~~gone~~").text
        check(
            "strike still works beside an escape",
            escapedStrike == "~~literal~~ and gone",
            "'$escapedStrike'",
        )

        // An escaped delimiter inside a span belongs to the span's text, so the span neither breaks
        // early nor swallows the marker as markup.
        println("Markdown escapes inside a span")
        val boldStar = Markdown.render("**a \\* b**")
        check(
            "an escaped star inside bold is literal",
            boldStar.text == "a * b" &&
                boldStar.spanStyles.any { it.item.fontWeight == FontWeight.Bold },
            "'${boldStar.text}' ${boldStar.spanStyles}",
        )
        val italicStar = Markdown.render("*a \\* b*")
        check(
            "an escaped star inside italics is literal",
            italicStar.text == "a * b" &&
                italicStar.spanStyles.any { it.item.fontStyle == FontStyle.Italic },
            "'${italicStar.text}' ${italicStar.spanStyles}",
        )
        val struckTilde = Markdown.render("~~a \\~ b~~")
        check(
            "an escaped tilde inside strike is literal",
            struckTilde.text == "a ~ b" &&
                struckTilde.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough },
            "'${struckTilde.text}' ${struckTilde.spanStyles}",
        )
        val codeTick = Markdown.render("`a \\` b`")
        check(
            "an escaped backtick inside code is literal",
            codeTick.text == "a ` b" &&
                codeTick.spanStyles.any { it.item.fontFamily == FontFamily.Monospace },
            "'${codeTick.text}' ${codeTick.spanStyles}",
        )
        val mathDollar = Markdown.render("\$a \\\$ b\$")
        check(
            "an escaped dollar inside a formula is literal",
            mathDollar.text == "a \$ b" &&
                mathDollar.getStringAnnotations(Markdown.MATH_TAG, 0, mathDollar.length)
                    .any { it.item == Markdown.MATH_INLINE },
            "'${mathDollar.text}' ${mathDollar.spanStyles}",
        )
        val earlyClose = Markdown.render("**a \\** b**")
        check(
            "an escaped marker does not close a span early",
            earlyClose.text == "a ** b" &&
                earlyClose.spanStyles.any { it.item.fontWeight == FontWeight.Bold },
            "'${earlyClose.text}'",
        )
        val tailEscape = Markdown.render("**a \\***")
        check(
            "an escape at the end of a span stays inside it",
            tailEscape.text == "a *" &&
                tailEscape.spanStyles.any { it.item.fontWeight == FontWeight.Bold },
            "'${tailEscape.text}'",
        )
        val plainInline = Markdown.render("plain **b** tail")
        check(
            "the plain inline path still works",
            plainInline.text == "plain b tail" &&
                plainInline.spanStyles.any { it.item.fontWeight == FontWeight.Bold },
            "'${plainInline.text}'",
        )

        // The reported crash: the caret sitting at the very start of a note whose first character is
        // a line break made the toggle ask for a substring that ran from 1 back to 0.
        val leadingBreak = Markdown.toggleLinePrefix(value("\nabc", start = 0), CHECKBOX)
        check(
            "a caret at the very start of a note gains the prefix",
            leadingBreak.text == CHECKBOX + "\nabc",
            "'${leadingBreak.text}'",
        )
        // Every caret position in the notes that used to break it. The toggle is a button the user
        // presses wherever the caret happens to be, so no position may throw.
        val shapes = listOf("", "\n", "\nabc", "a\n\nb", "\n\n", "a\n", "- one\n\n- two")
        var thrown: String? = null
        shapes.forEach { shape ->
            for (caret in 0..shape.length) {
                runCatching { Markdown.toggleLinePrefix(value(shape, caret), CHECKBOX) }
                    .onFailure { if (thrown == null) thrown = "caret $caret: $it" }
            }
        }
        check("no caret position throws", thrown == null, thrown.orEmpty())

        println("Markdown pictures")

        val picture = Markdown.insertImage(value("hello", start = 5), "hinotes-image://a.png")
        check(
            "a picture lands on a line of its own",
            picture.text == "hello\n![](hinotes-image://a.png)\n",
            "'${picture.text}'",
        )
        check(
            "the caret follows the picture onto the empty line under it",
            picture.selection.start == picture.text.length,
            "${picture.selection}",
        )
        // The break after the picture is what keeps it one: with the caret left on the address, the
        // next keystroke would make that line ordinary text and the preview would show the address
        // instead of the picture, with no way back but editing the Markdown by hand.
        check(
            "what is typed next cannot join the address",
            Markdown.insertImage(value("hello", start = 5), "u")
                .text
                .lineSequence()
                .any { it == "![](u)" },
        )
        val midPicture = Markdown.insertImage(value("ab", start = 1), "u")
        check(
            "a picture in the middle of a line takes one of its own",
            midPicture.text == "a\n![](u)\nb",
            "'${midPicture.text}'",
        )
        val drawnImage = Markdown.render("![alt](hinotes-image://a.png)")
        check(
            "an image is its address where there is no room to draw it",
            drawnImage.text == "hinotes-image://a.png",
            "'${drawnImage.text}'",
        )
        val drawnMarks = drawnImage.getStringAnnotations(Markdown.IMAGE_TAG, 0, drawnImage.length)
        check(
            "an image drawn as its address carries it too",
            drawnMarks.size == 1 && drawnMarks.single().item == "hinotes-image://a.png",
            "$drawnMarks",
        )
        val markedImage = Markdown.render("![alt](hinotes-image://a.png)", imageMark = '*')
        check(
            "an image is reduced to the mark its caller fills in",
            markedImage.text == "*",
            "'${markedImage.text}'",
        )
        val marks = markedImage.getStringAnnotations(Markdown.IMAGE_TAG, 0, markedImage.length)
        check(
            "a reduced image still carries the address it stands for",
            marks.size == 1 && marks.single().item == "hinotes-image://a.png" &&
                marks.single().start == 0 && marks.single().end == 1,
            "$marks",
        )
        // Two pictures in one note: each place carries its own address, so neither can end up drawn
        // as the other - which counting the marks against the addresses could not promise once a
        // line between them was rewritten on the way into the text.
        val twoImages = Markdown.render(
            "![](hinotes-image://a.png)\n![](hinotes-image://b.png)",
            imageMark = '*',
        )
        val twoMarks = twoImages.getStringAnnotations(Markdown.IMAGE_TAG, 0, twoImages.length)
        check(
            "two pictures keep their own addresses",
            twoImages.text == "*\n*" &&
                twoMarks.map { it.item } ==
                listOf("hinotes-image://a.png", "hinotes-image://b.png"),
            "'${twoImages.text}' $twoMarks",
        )
        val imageBlocks = Markdown.previewBlocks("before\n\n![alt](hinotes-image://a.png)\n\nafter")
        check(
            "an image alone on its line is a block of its own",
            imageBlocks.any { it is Markdown.PreviewBlock.Image && it.url == "hinotes-image://a.png" },
            "$imageBlocks",
        )
        val sentenceImage = Markdown.previewBlocks("text ![a](b.png) more")
        check(
            "an image inside a sentence stays prose",
            sentenceImage.none { it is Markdown.PreviewBlock.Image } &&
                sentenceImage.filterIsInstance<Markdown.PreviewBlock.Prose>()
                    .any { it.text.text.contains("b.png") },
            "$sentenceImage",
        )

        println("Markdown links")

        val linked = Markdown.render("see [the docs](https://example.com/x) now")
        check("a link is drawn as its own text", linked.text == "see the docs now", "'${linked.text}'")
        check(
            "a link carries the address a tap opens",
            linked.getLinkAnnotations(0, linked.length).any {
                (it.item as? LinkAnnotation.Url)?.url == "https://example.com/x"
            },
            "${linked.getLinkAnnotations(0, linked.length)}",
        )
        val autolinked = Markdown.render("go to <https://example.com>")
        check(
            "a bare address is a link too",
            autolinked.text == "go to https://example.com",
            "'${autolinked.text}'",
        )
        val linkInCode = Markdown.render("`[a](b)`")
        check(
            "a link inside code stays the characters it is",
            linkInCode.text == "[a](b)",
            "'${linkInCode.text}'",
        )

        println("Markdown code backgrounds")

        val fencedBody = Markdown.render("before\n```\ncode one\ncode two\n```\nafter")
        val codeBlocks = fencedBody.getStringAnnotations(Markdown.CODE_TAG, 0, fencedBody.length)
        check(
            "a fenced block marks every line of its code",
            codeBlocks.size == 2 && codeBlocks.all { it.item == Markdown.CODE_BLOCK },
            "$codeBlocks",
        )
        val codeSpan = Markdown.render("a `x` b")
        val codeSpans = codeSpan.getStringAnnotations(Markdown.CODE_TAG, 0, codeSpan.length)
        check(
            "inline code is marked as a span",
            codeSpans.size == 1 && codeSpans.single().item == Markdown.CODE_INLINE &&
                codeSpan.text.substring(codeSpans.single().start, codeSpans.single().end) == "x",
            "$codeSpans",
        )

        // A heading's size belongs to the surface drawing it, not to the string: a fixed size stops
        // being a heading the moment the reader turns the note size up. The renderer therefore marks
        // the run with its level and every surface applies the same scale to its own body size.
        println("Markdown headings")

        val headings = Markdown.render("# one\n## two\n### three")
        val headingRuns = headings.getStringAnnotations(Markdown.HEADING_TAG, 0, headings.length)
        check(
            "every heading is marked with its level",
            headingRuns.map { it.item } == listOf("1", "2", "3"),
            "$headingRuns",
        )
        check(
            "each mark covers the heading's own words",
            headingRuns.map { headings.text.substring(it.start, it.end) } ==
                listOf("one", "two", "three"),
            "${headingRuns.map { headings.text.substring(it.start, it.end) }}",
        )
        check(
            "a heading carries no fixed size of its own",
            headings.spanStyles.none { it.item.fontSize != TextUnit.Unspecified },
            "a fixed size cannot follow the note's own size setting",
        )
        // The scale table is what every surface reads: the ranks are in order, all above the body,
        // and each grows the line past its own glyph so the heading is not pressed against the lines
        // around it.
        val scales = Markdown.HEADING_SCALES
        check("there is a scale for each heading level", scales.size == 3, "${scales.size}")
        check(
            "the ranks are in order and above the body",
            scales[0].size > scales[1].size && scales[1].size > scales[2].size && scales[2].size > 1f,
            "${scales.map { it.size }}",
        )
        check(
            "each scale grows the line with its glyph",
            scales.all { it.lineHeight > it.size },
            "${scales.map { it.lineHeight to it.size }}",
        )
        // The widget draws the same ranks from the same marks; its spanned text is an Android type
        // this desktop check cannot run, so the wiring is asserted in the source.
        val widgetText = File("app/src/main/java/com/hiapps/hinotes/widget/WidgetText.kt").readText()
        check(
            "the widget scales headings from the same marks",
            widgetText.contains("Markdown.HEADING_TAG") &&
                widgetText.contains("Markdown.HEADING_SCALES") &&
                widgetText.contains("RelativeSizeSpan("),
            "a heading that keeps a page-only size reads wrong on the home screen",
        )

        // A display formula is not Markdown inside: a line in it that looks like a rule, a table or a
        // task is the formula's own characters. The preview keeps the region as one run of prose, so
        // the text renderer can draw it as the formula it is.
        println("Markdown math blocks in the preview")

        val formulaPreview = Markdown.previewBlocks("\$\$\n- a\n\$\$")
        check(
            "a formula's lines are not blocks of the preview",
            formulaPreview.size == 1 && formulaPreview.single() is Markdown.PreviewBlock.Prose,
            "$formulaPreview",
        )
        check(
            "the formula's own lines are kept",
            (formulaPreview.single() as Markdown.PreviewBlock.Prose).text.text.contains("- a"),
            "'${(formulaPreview.single() as Markdown.PreviewBlock.Prose).text.text}'",
        )
        check(
            "a rule written inside a formula is not a divider",
            Markdown.previewBlocks("\$\$\n---\n\$\$").none { it is Markdown.PreviewBlock.Divider },
        )
        check(
            "a table written inside a formula is not a table",
            Markdown.previewBlocks("\$\$\n| a | b |\n|---|---|\n\$\$")
                .none { it is Markdown.PreviewBlock.Table },
        )
        check(
            "a task written inside a formula is not a checkbox",
            Markdown.previewBlocks("\$\$\n- [ ] x\n\$\$").none { it is Markdown.PreviewBlock.Task },
        )
        check(
            "an image written inside a formula is not a picture",
            Markdown.previewBlocks("\$\$\n![](hinotes-image://a.png)\n\$\$")
                .none { it is Markdown.PreviewBlock.Image },
        )

        // The frame and the columns of a table are layout, which this desktop check cannot run; its
        // source is asserted so the grid cannot quietly go back to bare rows.
        println("Markdown table grid")

        val blocksSource =
            File("app/src/main/java/com/hiapps/hinotes/ui/components/MarkdownBlocks.kt").readText()
        check(
            "a table is framed by a rounded border",
            blocksSource.contains("internal fun MarkdownTable(") &&
                blocksSource.contains(".clip(shape)") &&
                blocksSource.contains(".border(1.dp, outline, shape)"),
            "the grid has to be drawn inside a frame",
        )
        check(
            "a rule is drawn between every pair of columns",
            blocksSource.contains("for (column in 1 until columns)") &&
                blocksSource.contains("drawLine("),
            "one rule per row breaks wherever a cell wraps",
        )
        check(
            "the header and the rows keep their horizontal rules",
            blocksSource.contains("HorizontalDivider(color = outline)"),
        )

        // A page that has to scroll to a found match needs two hooks this drawing offers and a card
        // does not: the layout of a prose block, and the row of a table the match is in.
        println("Markdown block hooks")

        val textSource =
            File("app/src/main/java/com/hiapps/hinotes/ui/components/MarkdownText.kt").readText()
        check(
            "a text can report the layout it produced",
            textSource.contains("onTextLayout: ((TextLayoutResult) -> Unit)? = null") &&
                textSource.contains("onTextLayout?.invoke(result)"),
            "a page that has to scroll to a match needs the text's own layout",
        )
        check(
            "a block hands its prose layout and a table's row to the page",
            blocksSource.contains("onTextLayout: ((TextLayoutResult) -> Unit)? = null") &&
                blocksSource.contains("internal data class TableReveal(") &&
                blocksSource.contains("bringIntoViewRequester(target.requester)"),
            "a long block has to be scrolled to the match inside it",
        )
        check(
            "the layout hook reaches the prose block only",
            Regex("onTextLayout = onTextLayout").findAll(blocksSource).count() == 1,
            "a task row or a table row has no text of its own to locate a match in",
        )

        // The renderer's one remaining task path: the widget swaps each character box for a picture,
        // and the renderer reports where every box was written and which line it stands for.
        println("Markdown task marks")

        val marksBody = Markdown.renderMarks("- [ ] one\n- [x] two")
        check("a task line reports its box", marksBody.marks.size == 2, "${marksBody.marks}")
        check(
            "each mark carries the line it stands for",
            marksBody.marks.map { it.line } == listOf(0, 1),
            "${marksBody.marks}",
        )
        check(
            "each mark carries the state the note records",
            marksBody.marks.map { it.checked } == listOf(false, true),
            "${marksBody.marks}",
        )
        check(
            "each mark points at a box character",
            marksBody.marks.all {
                marksBody.text[it.index] == '☐' || marksBody.text[it.index] == '☑'
            },
            "${marksBody.marks}",
        )
        check(
            "a note without tasks reports no marks",
            Markdown.renderMarks("just words").marks.isEmpty(),
        )
        // Comments are removed before the lines are read, but a task below one still stands on its
        // own source line: that line is what a tap writes back to.
        val markAfterComment = Markdown.renderMarks("a\n<!-- note -->\n- [ ] later")
        check(
            "a comment does not shift the line a mark stands for",
            markAfterComment.marks.single().line == 2,
            "${markAfterComment.marks}",
        )

        println("Markdown parser structure")

        val markdownSource =
            File("app/src/main/java/com/hiapps/hinotes/ui/editor/Markdown.kt").readText()
        // The inline-checkbox path is gone (Rules 18): nothing in production filled a place in the
        // text in, and keeping it meant two checkbox models for one drawing.
        check(
            "the abandoned inline-checkbox path is gone",
            !markdownSource.contains("renderBody") &&
                !markdownSource.contains("TaskSlot") &&
                !markdownSource.contains("taskBoxId") &&
                !markdownSource.contains("inlineContent"),
            "the renderer keeps one way to draw a box, not two",
        )
        // The two parsers ask the same questions of a line now. The behaviour checks above prove
        // they agree on formulas; this keeps a copy of the fence test from growing back in one.
        check(
            "both parsers share the fence and block tests",
            Regex("isCodeFence\\(").findAll(markdownSource).count() >= 3 &&
                Regex("opensBlocks\\(").findAll(markdownSource).count() >= 3 &&
                Regex("headingLevel\\(").findAll(markdownSource).count() >= 2,
            "code: ${Regex("isCodeFence\\(").findAll(markdownSource).count()}, " +
                "blocks: ${Regex("opensBlocks\\(").findAll(markdownSource).count()}",
        )

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
