package com.hiapps.hinotes.ui.screens

import java.io.File
import kotlin.system.exitProcess

/**
 * Standalone checks for the three things this round went wrong on, run by tools/verify/run.ps1.
 *
 * Each of them failed on a real phone while passing every build-time check there is, which is why
 * they are executed here instead of reasoned about:
 *
 * - the document pick, whose request code one Android 16 device refused outright;
 * - the back transition, whose dismissed page has to travel far enough and stay opaque, or it
 *   leaves an afterimage;
 * - the opening frame, which was drawn before the stored settings had been read and so flashed the
 *   wrong theme.
 *
 * The activity and screen halves are read from source rather than reflected on: `android.jar`'s
 * stubs throw from every method body, so what is being checked is that the code still says what it
 * must, and that no earlier mistake has crept back.
 */
object DocumentPickChecks {

    private var failures = 0

    private fun check(name: String, condition: Boolean, detail: String = "") {
        if (condition) {
            println("  ok    $name")
        } else {
            failures++
            println("  FAIL  $name ${if (detail.isEmpty()) "" else "-> $detail"}")
        }
    }

    /** A source file with its comment lines dropped, so prose about a mistake is not the mistake. */
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
        picks()
        transitions()
        startup()
        readme()

        if (failures == 0) {
            println("document pick checks: all checks passed")
        } else {
            println("document pick checks: $failures failed")
            exitProcess(1)
        }
    }

    /**
     * The import's file picker.
     *
     * What the platform enforces: `Activity.startActivityForResult` rejects any request code wider
     * than the low 16 bits, which is exactly what it did to the code the activity-result registry
     * handed out. The number this app chooses itself has to fit, and both halves of the handover -
     * the constant the picker is started with, the constant the activity compares against - have to
     * be the same one.
     */
    private fun picks() {
        println("document pick request code")

        check(
            "request code fits in lower 16 bits",
            REQUEST_OPEN_DOCUMENT in 0..0xFFFF,
            "0x${REQUEST_OPEN_DOCUMENT.toString(16)}",
        )
        check("request code is positive", REQUEST_OPEN_DOCUMENT > 0, "$REQUEST_OPEN_DOCUMENT")

        val activity = code("app/src/main/java/com/hiapps/hinotes/MainActivity.kt")
        val screen = code("app/src/main/java/com/hiapps/hinotes/ui/screens/BackupScreen.kt")

        check(
            "the activity checks the code the picker was started with",
            activity.contains("requestCode != REQUEST_OPEN_DOCUMENT"),
        )
        check("the activity accepts only a successful result", activity.contains("Activity.RESULT_OK"))
        check("the activity overrides onActivityResult", activity.contains("override fun onActivityResult("))
        check(
            "the activity hands the URI to the relay the screen reads",
            activity.contains("PickedDocumentRelay.publish("),
        )

        check(
            "the picker no longer goes through the activity-result registry",
            !screen.contains("rememberLauncherForActivityResult"),
            "the registry's request code is the one that was refused",
        )
        check(
            "the picker is started with the shared request code",
            screen.contains("startActivityForResult(openDocumentIntent(), REQUEST_OPEN_DOCUMENT)"),
        )
        check("a returned file is consumed from the relay", screen.contains("PickedDocumentRelay.consume()"))
        check(
            "the confirmation names the file",
            screen.contains("stringResource(R.string.backup_import_confirm_message, chosen.name)"),
        )
        // The question is asked about a file that came back, not about one that was never picked.
        check("no row raises a confirmation on its own", !screen.contains("onClick = { pendingImport ="))
        check("the confirmation is driven by the picked file", screen.contains("val chosen = pickedFile"))
    }

    /**
     * The back transition.
     *
     * The dismissed page has to travel far enough to leave the screen and has to stay opaque while
     * it does: left at a quarter width it was cut off mid-screen, and faded out while it travelled
     * it turned translucent over the page underneath, which is the afterimage that has now been
     * reported twice. So the full-width offset is required, and any fade at all is refused.
     */
    private fun transitions() {
        println("screen transitions")

        val app = code("app/src/main/java/com/hiapps/hinotes/ui/HiNotesApp.kt")

        check("the dismissed page travels the full width", app.contains("targetOffsetX = { it })"))
        check(
            "the page underneath drifts in by the parallax",
            app.contains("initialOffsetX = { -it / PARALLAX_DIVISOR }"),
        )
        check("the push still covers from the right edge", app.contains("initialOffsetX = { it }"))
        check("nothing fades", !app.contains("fadeIn") && !app.contains("fadeOut"))
        check("both directions run on the same clock", app.contains("TRANSITION_MS = 300"))
    }

    /**
     * The opening frame.
     *
     * The theme is derived from the settings flow, which emitted its stored values a moment after
     * the first composition - so that composition was drawn with the defaults, and on a phone whose
     * system is light while the app is set to dark, the app visibly opened light and then flipped.
     * The stored settings are now read before anything is published.
     */
    private fun startup() {
        println("first frame")

        val model = code("app/src/main/java/com/hiapps/hinotes/ui/AppViewModel.kt")
        val store = code("app/src/main/java/com/hiapps/hinotes/data/Preferences.kt")

        check("the settings are read before the state is published", model.contains("initialSettings"))
        check("the read blocks until it has an answer", store.contains("runBlocking { current() }"))
        check(
            "the theme still comes from the settings flow",
            model.contains("val settings: StateFlow<HiNotesSettings>"),
        )
    }

    /**
     * Every heading link in the README.
     *
     * GitHub does not render the heading itself with an id. It renders `<h2>` bare and follows it
     * with `<a id="user-content-<slug>">`, which is also the id its own permalink anchors point at
     * - checked against the live page, where `id="user-content-english"` exists and `id="english"`
     * does not. A link written against the bare heading text therefore names an element that is
     * not in the document, which is what the language switch did. Each fragment here has to
     * resolve to the prefixed id the platform actually emits.
     */
    private fun readme() {
        println("README anchors")

        val file = File("README.md")
        check("README.md is readable", file.isFile)
        if (!file.isFile) return
        val text = file.readText()

        val renderedIds = HashSet<String>()
        for (line in text.lines()) {
            val heading = Regex("^#{1,6}\\s+(.+?)\\s*$").find(line)?.groupValues?.get(1) ?: continue
            val slug = heading.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s-]"), "")
                .trim()
                .replace(Regex("\\s+"), "-")
            renderedIds.add("user-content-$slug")
        }
        // An explicit anchor in the file renders under its own id, so it counts as well.
        for (match in Regex("<a\\s+id=\"([^\"]+)\"").findAll(text)) {
            renderedIds.add(match.groupValues[1])
        }
        check(
            "both language headings are reachable",
            renderedIds.contains("user-content-english") &&
                renderedIds.contains("user-content-简体中文"),
        )

        val broken = ArrayList<String>()
        for (match in Regex("href=\"#([^\"]+)\"").findAll(text)) {
            val target = match.groupValues[1]
            if (!renderedIds.contains(target)) broken.add("#$target")
        }
        check(
            "every heading link resolves to an id the page renders",
            broken.isEmpty(),
            broken.joinToString(", "),
        )
    }
}
