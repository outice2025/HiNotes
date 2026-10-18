package com.hiapps.hinotes.ui.screens

import java.io.File
import kotlin.system.exitProcess

/**
 * Standalone checks for the editor's own controls and for the picture picker, run by
 * tools/verify/run.ps1.
 *
 * Each of these is a fault that reached a phone after a build that passed every compile-time check
 * there is, and each is a fact about the *source* rather than about a value: the composables need a
 * device to lay anything out, and `android.jar`'s stubs throw from every method body so an intent
 * cannot even be built here. What is checked is that the code still says what it must - every way
 * into the editor opens one of them, a heading starts where the cards' titles do, the picture picker
 * keeps its fallbacks, and the formatting toolbar is the one the design asks for: the design's own
 * size, drawn for the whole of the edit mode, with the room it needs kept free for the writing area.
 */
object EditorChecks {

    private var failures = 0

    private fun check(name: String, condition: Boolean, detail: String = "") {
        if (condition) {
            println("  ok    $name")
        } else {
            failures++
            println("  FAIL  $name ${if (detail.isEmpty()) "" else "-> $detail"}")
        }
    }

    /**
     * A source file with its comment lines dropped.
     *
     * Dropping them is what keeps prose *about* a mistake from counting as the mistake: a comment
     * that explains why a picker needs a fallback names the fallback.
     */
    private fun code(path: String): String {
        val file = File(path)
        check("$path is readable", file.isFile)
        if (!file.isFile) return ""
        val kept = ArrayList<String>()
        for (line in file.readLines()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                continue
            }
            kept.add(line)
        }
        return kept.joinToString("\n")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        pictures()
        navigation()
        headings()
        toolbar()
        findBar()
        codeBackgrounds()
        taskSlots()
        windowFrame()
        cardCheckboxes()
        deleteUndo()
        selectionControls()

        if (failures == 0) {
            println("editor checks: all checks passed")
        } else {
            println("editor checks: $failures failed")
            exitProcess(1)
        }
    }

    /**
     * The way into the editor.
     *
     * A quick double tap on a card asked for the same page twice, and single-top alone was not
     * enough to keep the arrival still: the repeated request replaces the page's entry, which
     * rebuilds the screen as it is sliding in, and a slow single tap does not flash. So every way
     * forward goes through one guard that refuses a second push while the first transition runs,
     * and the guard is the only place that navigates.
     */
    private fun navigation() {
        println("opening a note")

        val app = code("app/src/main/java/com/hiapps/hinotes/ui/HiNotesApp.kt")

        check(
            "one guard opens every page",
            app.contains("val push: (String) -> Unit ="),
        )
        check(
            "the guard refuses a second push while a page is moving",
            app.contains("if (!pushing) {"),
            "two taps a moment apart must not ask for the same page twice",
        )
        check(
            "a card opens its note through the guard",
            app.contains("push(Routes.editor(id))"),
        )
        check(
            "the button that starts a note goes through it too",
            app.contains("push(Routes.editor(NEW_NOTE_ID))"),
        )
        check(
            "the guard is the only way forward",
            Regex("navController\\.navigate\\(").findAll(app).count() == 1,
            "a second call site would push without the guard",
        )
        check(
            "the page it pushes is single-top",
            app.contains("launchSingleTop = true"),
        )
    }

    /**
     * The headings the home screen puts above its two runs of notes.
     *
     * A heading shares the grid's own slot, while a card gives its title an inset of its own, so the
     * heading's first character stood to the left of every title under it until it was given the
     * same inset - the one the card itself uses, published once so the two cannot drift apart.
     */
    private fun headings() {
        println("home headings")

        val home = code("app/src/main/java/com/hiapps/hinotes/ui/screens/HomeScreen.kt")
        val card = code("app/src/main/java/com/hiapps/hinotes/ui/components/CommonComponents.kt")

        check("the card's own inset is published", card.contains("internal fun cardSideInset("))
        check(
            "a heading starts where a card's title starts",
            home.contains("padding(start = cardSideInset(compact))"),
        )
        check(
            "the heading follows the layout the cards are drawn in",
            home.contains("compact = gridLayout"),
        )
    }

    /**
     * The picker the editor's picture button opens.
     *
     * On the phone this was reported on the button did nothing at all, twice for two different
     * reasons: the first drawing asked the platform's photo picker by name, which one device answers
     * for only through its document picker; the second replaced the pick with an activity-result
     * registry launcher, whose launch the activity refuses before any picker is asked at all - its
     * request codes are wider than the 16 bits `FragmentActivity` accepts. The three pickers are
     * therefore tried in turn, from the one that shows the user the most to the one that has answered
     * since the beginning, and every one of them is started with a request code this app chooses.
     */
    private fun pictures() {
        println("picture picker")

        val pick = code("app/src/main/java/com/hiapps/hinotes/ui/screens/ImagePick.kt")
        val editor = code("app/src/main/java/com/hiapps/hinotes/ui/screens/EditorScreen.kt")

        val photoPicker = pick.indexOf("MediaStore.ACTION_PICK_IMAGES")
        val documentPicker = pick.indexOf("Intent.ACTION_OPEN_DOCUMENT")
        val contentPicker = pick.indexOf("Intent.ACTION_GET_CONTENT")

        // The order is the fix: the platform's photo picker started and drew nothing on the phone
        // this was reported on, and being asked first it took the tap with it.
        check(
            "the document picker is asked first",
            documentPicker >= 0 && documentPicker < contentPicker,
            "it is the one picker every device answers with a window",
        )
        check(
            "the gallery's own picker is the second try",
            contentPicker > documentPicker && contentPicker < photoPicker,
        )
        check(
            "the platform's photo picker is the last try",
            photoPicker > contentPicker,
            "it was asked first once, and the tap did nothing on that device",
        )
        check(
            "every picker asks for a picture",
            Regex("setType\\(\"image/\\*\"\\)").findAll(pick).count() >= 3,
            "one per picker",
        )
        check(
            "each picker is tried in turn",
            editor.contains("for (intent in pickImageIntents())"),
            "a single intent is what left the button doing nothing",
        )
        check(
            "the pick is started with a request code this app chooses",
            editor.contains("activity.startActivityForResult(intent, REQUEST_PICK_IMAGE)"),
            "the registry's own codes are wider than 16 bits",
        )
        check(
            "the registry's launcher is not used for the pick",
            !editor.contains("rememberLauncherForActivityResult("),
            "a FragmentActivity refused its launch, so no picker ever opened",
        )
        check(
            "every attempt to open a picker is logged",
            editor.contains("asking for a picture with ") &&
                editor.contains("no installed app answered"),
            "a device that answers for none of them cannot be diagnosed from the outside",
        )
        check(
            "every picker answer is logged too",
            code("app/src/main/java/com/hiapps/hinotes/MainActivity.kt")
                .contains("picker answer: request="),
            "a pick that never comes back and one that comes back empty look the same on screen",
        )
        check(
            "a pick that cannot be started says so",
            editor.contains("R.string.editor_image_failed"),
        )
        check(
            "a picker's answer is read from its data or its clip item",
            pick.contains("clipData"),
            "one photo picker was seen answering with a clip item and no data",
        )
        check(
            "a picker that answers without a picture is reported as a failure",
            code("app/src/main/java/com/hiapps/hinotes/MainActivity.kt")
                .contains("PickedImageRelay.refuse()") &&
                editor.contains("PickedImage.Refused"),
            "a tap that silently does nothing is the fault this guards",
        )
        // The writing area is the note's Markdown source: a picture imported there is only a line of
        // text until the page is drawn as the reader sees it, which is where pictures are drawn.
        check(
            "a picture that lands turns the page to the rendered note",
            editor.contains("previewMode = true") && editor.contains("picture copied to "),
            "an import that draws nothing looks exactly like an import that failed",
        )
        // The relay's value is the effect's own key, so the work it starts must not live in that
        // effect: clearing the relay recomposes the key and cancels whatever was suspended inside.
        // That is what silently threw away every imported picture until it was moved out.
        val relayEffect = editor
            .substringAfter("LaunchedEffect(pickedImage) {")
            .substringBefore("// ----")
        check(
            "the import is started on the screen's scope, not inside the effect that clears the relay",
            relayEffect.contains("PickedImageRelay.consume()") &&
                relayEffect.substringBefore("scope.launch {").isNotEmpty() &&
                !relayEffect.substringBefore("scope.launch {").contains("importPicture(") &&
                relayEffect.substringAfter("scope.launch {").contains("importPicture(answer.uri)"),
            "the effect's key is the value it clears, so suspending in it cancels the copy",
        )
        check(
            "a copy that comes back empty is a failure, not a picture",
            code("app/src/main/java/com/hiapps/hinotes/data/NoteImages.kt")
                .contains("target.length() > 0"),
            "a provider answers an empty stream for a picture that has gone",
        )
    }

    /**
     * The formatting toolbar.
     *
     * The bar is the design's own control at the design's own size: a row of 48dp buttons that is
     * drawn whenever the note is being written, with the writing area keeping a bar's worth of room
     * free at its lower edge so the text never runs into it. Two faults are behind the checks: the
     * room used to be held for the whole of the edit mode even when the bar was not on screen, and
     * the bar was briefly sized to whatever canvas it found itself on, which made the same control
     * a different size on different phones.
     */
    private fun toolbar() {
        println("formatting toolbar")

        val editor = code("app/src/main/java/com/hiapps/hinotes/ui/screens/EditorScreen.kt")

        check(
            "the bar is the design system's own size",
            editor.contains("size = ExpressiveButtonSize.Compact"),
        )
        check(
            "the same size is kept on every screen",
            !editor.contains("toolbarSize(") && !editor.contains("BoxWithConstraints("),
            "the bar is not resized to the canvas it is drawn on",
        )
        check(
            "the bar is drawn for the whole of the edit mode",
            editor.contains("if (!showingPreview) {"),
            "it is an editing control, so the preview and a locked note do not get one",
        )
        check(
            "the bar keeps its distance from the canvas' lower edge",
            editor.contains(".padding(ToolbarInset)"),
        )
        check(
            "the picture button is the last control on the bar",
            editor.indexOf("Symbols.AddPhotoAlternate") > editor.indexOf("Symbols.List"),
        )
        check(
            "the room the bar needs is the writing area's bottom inset",
            editor.contains("bottom = BodyBottomInset"),
        )

        // The header's two actions were one split control, and the half that was the menu could only
        // be found by pressing the half that was not. They are two buttons now, one of which is not
        // drawn at all while the note saves itself.
        val components = code("app/src/main/java/com/hiapps/hinotes/ui/components/ExpressiveComponents.kt")
        check(
            "save and the menu are two separate buttons",
            editor.contains("EditorActionButtons(") && !editor.contains("SplitButton("),
            "a split control hides one action behind the other",
        )
        check(
            "the menu has a button of its own",
            components.contains("Symbols.MoreVert"),
        )
        check(
            "the save button is left out while the note saves itself",
            editor.contains("showSave = !settings.autoSave"),
            "with auto-save on, leaving the note is the save",
        )
        check(
            "both are drawn like the find button beside them",
            components.contains("modifier = Modifier.size(EditorActionSize)") &&
                components.contains("shape = RoundedCornerShape(percent = 50)") &&
                components.contains("contentColor = MaterialTheme.colorScheme.onSurfaceVariant"),
            "the header keeps one language of buttons",
        )
    }

    /**
     * The rounded background behind a run of code.
     *
     * The panel is the height of the code's own face, measured from the line's baseline, and not the
     * height of the line: a line is taller than the text on it, and the code hangs from a baseline
     * part-way down, so a panel drawn from the line's edges sat above the code and let its
     * descenders cross the lower edge. A fenced block's panel therefore runs from the first code
     * line's baseline to the last one's, and an inline run's box from its own line's baseline. A
     * fenced block's panel also reaches past the code on both sides, and whitespace at either end of
     * a line's share of a run is left out of its box.
     */
    private fun codeBackgrounds() {
        println("code backgrounds")

        val text = code("app/src/main/java/com/hiapps/hinotes/ui/components/MarkdownText.kt")

        check(
            "the code's own face decides how tall a background is",
            text.contains("Typeface.MONOSPACE") && text.contains("fontMetrics"),
            "the panel is the height of the face the code is set in",
        )
        check(
            "the background is placed from the line's baseline",
            text.contains("getLineBaseline(firstLine)") && text.contains("getLineBaseline(line)"),
            "the line's own top and bottom are taller than the text between them",
        )
        check(
            "a background is never drawn from the line's box",
            !text.contains("getLineTop(") && !text.contains("getLineBottom("),
            "a panel from the line's edges is what let the code cross its lower edge",
        )
        check(
            "a run inside a heading is the size of the heading",
            text.contains("codeFontSize(run.start"),
        )
        check(
            "a fenced block's panel reaches past the code it holds",
            text.contains("Offset(-padding, top)") && text.contains("size.width + padding * 2"),
        )
        check(
            "whitespace at a line's end is left out of a box",
            text.contains("text[from].isWhitespace()") && text.contains("text[to - 1].isWhitespace()"),
        )
    }

    /**
     * The slot a task's box takes inside a line of a card's text.
     *
     * A box drawn inside a line is as tall as the line it stands in, and the box Material 3 draws is
     * 18dp: a body set at its usual line height showed a card's checklist as a column of boxes that
     * touched. What the card can change is the line's pitch, so the pitch is what is checked - and
     * that it is a length in dp, because a pitch measured in the text's units would grow with the
     * reader's text size without ever changing the gap.
     */
    private fun taskSlots() {
        println("task rows")

        val row = code("app/src/main/java/com/hiapps/hinotes/ui/components/TaskRow.kt")
        val card = code("app/src/main/java/com/hiapps/hinotes/ui/components/CommonComponents.kt")
        val pitch = Regex("TaskBoxPitch = (\\d+)\\.dp").find(row)?.groupValues?.get(1)?.toInt()
        val box = Regex("TaskBoxTarget = (\\d+)\\.dp").find(row)?.groupValues?.get(1)?.toInt()

        check("the pitch a task line needs is declared", pitch != null, "TaskBoxPitch")
        check("the box's own size is declared", box != null, "TaskBoxTarget")
        if (pitch != null && box != null) {
            check(
                "a card's lines are as far apart as one task row is tall",
                pitch == box,
                "a ${pitch}dp pitch for a ${box}dp row",
            )
        }
        check(
            "a card sets its body to that pitch",
            card.contains("lineHeight = with(LocalDensity.current) { TaskBoxPitch.toSp() }"),
            "the line height is what spaces the boxes, not the slot's own height",
        )
    }

    /**
     * Where the find bar sits.
     *
     * A search box drawn over the page hid the matches it had just found, so the bar takes over the
     * note's title box instead: the same footprint, and the title - which the search does not look
     * through - is what goes under it. It takes the typing as soon as it opens, because a keystroke
     * that landed in the covered title field would change something nobody can see.
     */
    private fun findBar() {
        println("find bar")

        val editor = code("app/src/main/java/com/hiapps/hinotes/ui/screens/EditorScreen.kt")

        check("the find bar is a piece of its own", editor.contains("private fun FindBar("))
        check(
            "it takes over the title box",
            editor.contains("shape = TitleShape") && editor.contains(".height(TITLE_HEIGHT)"),
            "the same footprint as the field it covers",
        )
        check(
            "it is drawn over the title rather than over the page",
            !editor.contains("Find-in-note bar, shown above the toolbar"),
            "a bar over the page hides the matches it just found",
        )
        check(
            "it takes the typing when it opens",
            editor.contains("focusRequester.requestFocus()"),
        )
        check(
            "the taps that land on it do not reach the field it covers",
            editor.contains("awaitFirstDown().consume()"),
        )
    }

    /**
     * The window's own furniture: the bars over the page and under it.
     *
     * Two reports sit behind these checks. The status bar's icons stayed white on the app's own white
     * background whenever the app was in light mode while the phone was in dark: the edge-to-edge
     * helper decides that colour from the *system's* setting, and the app's theme is its own. And a
     * settings page reserved the navigation bar's height, which left a flat band of the window under
     * its content - the home screen paints behind that bar, and a settings page has to as well.
     */
    private fun windowFrame() {
        println("window furniture")

        val activity = code("app/src/main/java/com/hiapps/hinotes/MainActivity.kt")
        val settings = code("app/src/main/java/com/hiapps/hinotes/ui/components/SettingsScaffold.kt")
        val home = code("app/src/main/java/com/hiapps/hinotes/ui/screens/HomeScreen.kt")

        check(
            "the bars' icons follow the app's theme rather than the system's",
            activity.contains("val appDark = viewModel.settings.value.darkMode.isDark(systemDark())") &&
                activity.contains("SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { appDark }"),
            "a light app on a dark phone drew white icons on its own white bar",
        )
        check(
            "the theme reaches the bars again when it changes",
            activity.contains("LaunchedEffect(darkTheme)") &&
                activity.contains("statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)"),
        )
        check(
            "a settings page paints behind the navigation bar",
            settings.contains("contentWindowInsets = WindowInsets.systemBars.only(") &&
                settings.contains("WindowInsetsSides.Top + WindowInsetsSides.Horizontal"),
            "reserving the bar's height is what showed as a band of another colour",
        )
        check(
            "and keeps its last row clear of the gesture handle",
            settings.contains("padding(bottom = 32.dp + navigationBarInset)"),
        )
        check(
            "that is the frame the home screen uses",
            home.contains("contentWindowInsets = WindowInsets.systemBars.only("),
        )
    }

    /**
     * A card's own checkboxes.
     *
     * A card drew its task boxes as pictures, so tapping one opened the note instead of ticking the
     * item: the boxes are controls now, tied to the line of the note they were drawn on, and a locked
     * note keeps them inert because the app will not write to one.
     */
    private fun cardCheckboxes() {
        println("card checkboxes")

        val preview = code("app/src/main/java/com/hiapps/hinotes/ui/components/MarkdownPreview.kt")
        val card = code("app/src/main/java/com/hiapps/hinotes/ui/components/CommonComponents.kt")
        val rows = code("app/src/main/java/com/hiapps/hinotes/ui/components/TaskRow.kt")
        val home = code("app/src/main/java/com/hiapps/hinotes/ui/screens/HomeScreen.kt")
        val viewModel = code("app/src/main/java/com/hiapps/hinotes/ui/AppViewModel.kt")

        check(
            "a card renders its body with the places its boxes fit in",
            preview.contains("Markdown.renderBody(content, linkColor)"),
            "one id shared by every box could not say which line was tapped",
        )
        check(
            "the card passes a tap on a box out to its caller",
            preview.contains("onToggleTask: ((Int, Boolean) -> Unit)? = null") &&
                preview.contains("onToggleTask?.invoke(slot.line, !slot.checked)"),
        )
        check("a card hands the callback to the body it draws", card.contains("onToggleTask = onToggleTask"))
        check(
            "the box itself carries the tap",
            rows.contains("onCheckedChange = onClick?.let { toggle -> { _: Boolean -> toggle() } }"),
            "a tap anywhere but the box would tick the wrong line",
        )
        check(
            "the home screen writes the tick back to the note",
            home.contains("viewModel.setTaskChecked(note.id, line, checked)"),
        )
        check(
            "a locked note's boxes cannot be tapped",
            home.contains("onToggleTask = if (note.isLocked || selecting) {") &&
                home.contains("null"),
            "the app will not write to a note it cannot open, and a selection is not a tick",
        )
        check(
            "the tick is written to the line the box stood on",
            viewModel.contains("suspend fun setTaskChecked(id: String, line: Int, checked: Boolean)") &&
                viewModel.contains("Markdown.setTaskChecked(note.content, line, checked)"),
        )
    }

    /**
     * Deleting a note can be undone for a moment.
     *
     * The rows leave the list at once, which is what the tap asked for, and the notes themselves
     * wait out the window in the view model rather than in the screen: a user who navigates away
     * while the offer is on screen still gets the deletion they asked for. A note can be deleted
     * from the editor as well, and the offer belongs to the list either way - it is the screen the
     * note comes back to.
     */
    private fun deleteUndo() {
        println("note deletion")

        val home = code("app/src/main/java/com/hiapps/hinotes/ui/screens/HomeScreen.kt")
        val editor = code("app/src/main/java/com/hiapps/hinotes/ui/screens/EditorScreen.kt")
        val viewModel = code("app/src/main/java/com/hiapps/hinotes/ui/AppViewModel.kt")

        check("the rows go before the notes do", viewModel.contains("suspend fun stageDelete("))
        check(
            "the notes are only hidden while the window is open",
            viewModel.contains("notesRepository.visibleNotes().filterNot { it.id in held }"),
            "a delete that cannot be undone is not what the window promises",
        )
        check(
            "the window closes itself",
            viewModel.contains("delay(DeleteUndoMs)") &&
                viewModel.contains("viewModelScope.launch {"),
            "a screen that goes away must not leave the notes hidden forever",
        )
        check("undo brings them back", viewModel.contains("fun undoStagedDelete()"))
        check(
            "the window is one number",
            viewModel.contains("const val DeleteUndoMs = 3_000L"),
        )
        // The offer is made by the list, from the flow of what is held, so it reaches a deletion made
        // in the editor as well as one made on the list itself.
        check(
            "the list offers back a note deleted from anywhere",
            home.contains("val stagedDelete by viewModel.stagedDelete") &&
                home.contains("viewModel.undoStagedDelete()"),
            "a deletion made in the editor had no way back before",
        )
        check(
            "the offer is a snackbar with undo on it",
            home.contains("actionLabel = context.getString(R.string.editor_undo)"),
        )
        // The dialog asked a question that had already been answered - by the long press, by the
        // delete button, by opening the note's menu - and the window after the deletion is the
        // better guard, because it can be taken back.
        check(
            "no dialog stands between the delete button and the deletion",
            home.contains("onClick = { deleteSelection() }") &&
                !home.contains("confirmDelete"),
            "the undo window is what replaces the question",
        )
        check(
            "the editor deletes without a question too",
            editor.contains("viewModel.stageDelete(listOf(stored))") &&
                !editor.contains("confirmDeleteNote"),
            "the third answer to the same question was the dialog",
        )
        check(
            "the offer cannot outlive the window",
            home.contains("snackbarHostState.currentSnackbarData?.dismiss()"),
        )
    }

    /**
     * The controls a long press brings: pinning the selection, and the fade between the two modes.
     *
     * Pinning a selection used to write one note at a time, and each write re-read and re-sorted the
     * whole list - which the page showed as a flash of the heading and of the cards moving while the
     * button was still under the finger. And the two groups of controls were laid out one after the
     * other, so the new buttons were pushed in beside the old ones instead of fading where they were.
     */
    private fun selectionControls() {
        println("selection controls")

        val home = code("app/src/main/java/com/hiapps/hinotes/ui/screens/HomeScreen.kt")
        val model = code("app/src/main/java/com/hiapps/hinotes/ui/AppViewModel.kt")
        val data = code("app/src/main/java/com/hiapps/hinotes/data/NotesRepository.kt")
        val store = code("app/src/main/java/com/hiapps/hinotes/data/NotesDatabase.kt")

        check(
            "a selection is pinned in one write",
            home.contains("viewModel.pinNotes(ids, pinned)") &&
                model.contains("suspend fun pinNotes(ids: Collection<String>, pinned: Boolean)"),
            "a write per note re-flowed the page once per note",
        )
        check(
            "the write is one statement, not one per note",
            store.contains("fun setPinned(ids: Collection<String>, pinned: Boolean)") &&
                store.contains(" IN ("),
        )
        check(
            "the repository publishes one new list",
            data.contains("suspend fun setPinned(ids: Collection<String>, pinned: Boolean)"),
        )
        check(
            "the button beside the field is the selection's pin",
            home.contains("selectionPinned -> Symbols.KeepOff") &&
                home.contains("else -> Symbols.Keep"),
        )
        check(
            "the two groups of controls fade in one place, over each other",
            home.contains("Box(contentAlignment = Alignment.CenterEnd)") &&
                home.contains("visible = !selecting") &&
                home.contains("visible = selecting"),
            "laid out side by side, the new buttons were pushed in from the left",
        )
    }
}
