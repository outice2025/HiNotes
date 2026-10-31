package com.hiapps.hinotes

import java.io.File
import kotlin.system.exitProcess

/**
 * Standalone checks for the app shell around the widget, run by tools/verify/run.ps1.
 *
 * A card is painted once from whatever the app last sent it, so the pieces that decide *when* it is
 * painted again are as much a part of the widget as the provider: a tap outside one of the pickers
 * has to close it, the settings pages have to keep the heading spacing their reference measured, and
 * a wallpaper change has to reach a redraw without the app keeping a process awake for it. The
 * checks also pin the version the test package was cut at, so a run cannot quietly report on a build
 * other than the one in testers' hands.
 *
 * Each check is a fact about the source, or about one plain value read out of it: the composables
 * need a device to lay anything out, and `android.jar`'s stubs throw from every method body, so an
 * intent or a registered listener cannot even be built here. What is asserted is that the code still
 * says what a phone passed.
 */
object AppShellChecks {

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
     * Dropping them is what keeps prose *about* a mistake from counting as the mistake: the manifest
     * comment on a missing receiver names the broadcast, and a KDoc line below a heading names the
     * background work the app deliberately does not do.
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

    /**
     * An XML file with its comments removed.
     *
     * Same reason as [code]: the manifest explains why an entry is absent, and that explanation
     * names the entry it is about.
     */
    private fun xml(path: String): String {
        val file = File(path)
        check("$path is readable", file.isFile)
        if (!file.isFile) return ""
        return file.readText().replace(Regex("<!--[\\s\\S]*?-->"), "")
    }

    /** The text between two markers, or empty when either is missing. */
    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        if (from < 0) return ""
        val to = source.indexOf(end, from + start.length)
        return if (to < 0) source.substring(from) else source.substring(from, to)
    }

    /** The text from the first [marker] on, or empty when it is missing. */
    private fun after(source: String, marker: String): String {
        val at = source.indexOf(marker)
        return if (at < 0) "" else source.substring(at)
    }

    private const val SOURCE = "app/src/main/java/com/hiapps/hinotes"
    private const val MANIFEST = "app/src/main/AndroidManifest.xml"

    @JvmStatic
    fun main(args: Array<String>) {
        buildFacts()
        pickerDialog()
        settingsHeadingSpacing()
        wallpaperSignals()
        lifecycleTriggers()
        report()
    }

    /**
     * What the test package is.
     *
     * The checks that follow were adjusted for this release, so a run has to say which build it was
     * run against. The channel is part of the fact: the package published from this tree is the
     * release build, and its diagnostics have been removed before it was built.
     */
    private fun buildFacts() {
        println("Build facts")

        val gradle = code("app/build.gradle.kts")
        check(
            "the test package is version 1.1.1",
            gradle.contains("val appVersionName = \"1.1.1\""),
            "the checks below were revised for this release",
        )
        check(
            "the test package is versionCode 22",
            gradle.contains("val appVersionCode = 22"),
            "a package with the same code cannot replace the one testers installed",
        )
        check(
            "the package is the release build",
            gradle.contains("val appBuildChannel = \"Release\""),
            "the channel is what the About screen promises and what a published build says",
        )
        check(
            "the values are the ones the build stamps",
            gradle.contains("versionCode = appVersionCode") &&
                gradle.contains("versionName = appVersionName") &&
                gradle.contains("buildConfigField(\"String\", \"BUILD_CHANNEL\""),
            "a literal beside the value would let a build ship a version no check named",
        )
    }

    /**
     * The frame both single-choice pickers share.
     *
     * The reference draws the card with the screen's own margins, and the dialog's own outside-click
     * handling measures the composition's bounds, so a content that fills the window defeats it: the
     * empty area around the card is still "inside". The tap is handled by the frame instead, and a
     * tap on the card itself is swallowed before that handler sees it.
     */
    private fun pickerDialog() {
        println("Picker dialog dismissal")

        val picker =
            code("$SOURCE/ui/components/PickerDialog.kt")
        check(
            "the outside tap closes with the current handler",
            picker.contains("val dismiss by rememberUpdatedState(onDismiss)") &&
                picker.contains("import androidx.compose.runtime.rememberUpdatedState") &&
                picker.contains("import androidx.compose.runtime.getValue"),
            "a remembered lambda would close with the first composition's state",
        )

        // The handler has to belong to the full-window box, and to sit before the card's margins in
        // the chain: the margins are part of the area a tap outside the card lands in.
        val windowFill = picker.indexOf(".fillMaxSize()")
        val outsideTap = picker.indexOf("detectTapGestures { dismiss() }")
        val cardMargin = picker.indexOf(".padding(horizontal = PickerSideMargin")
        check(
            "the area around the card closes the picker",
            outsideTap >= 0 && windowFill in 0 until outsideTap && outsideTap < cardMargin,
            "the tap target has to cover the whole window, margins included",
        )

        // The card consumes the first down so the handler around it never sees a tap that landed on
        // the card: rows and the cancel button are hit first and still work.
        val cardHeight = picker.indexOf(".heightIn(max = maxHeight)")
        val cardConsume = picker.indexOf("awaitEachGesture { awaitFirstDown().consume() }")
        val cardShape = picker.indexOf("shape = RoundedCornerShape(")
        check(
            "a tap on the card is not a dismissal",
            cardConsume >= 0 && cardHeight in 0 until cardConsume && cardConsume < cardShape,
            "the card has to swallow the down before the handler around it runs",
        )
        check(
            "the back key still closes the picker",
            picker.contains("onDismissRequest = onDismiss"),
            "a picker that traps back cannot be left with a gesture alone",
        )
        check(
            "cancel still closes the picker",
            picker.contains("TextButton(onClick = onDismiss)"),
        )

        val accent = code("$SOURCE/ui/components/AccentColorDialog.kt")
        val language = code("$SOURCE/ui/components/LanguageDialog.kt")
        check(
            "both pickers go through the shared frame",
            accent.contains("PickerDialog(") && accent.contains("onDismiss = onDismiss") &&
                language.contains("PickerDialog(") && language.contains("onDismiss = onDismiss"),
            "a picker outside the frame would have neither the outside tap nor the card's swallow",
        )
    }

    /**
     * The room a settings page keeps around its own heading.
     *
     * The title starts under the bar's height plus 20dp, the first row starts 44dp under the heading,
     * and the content's top room is computed from both so enlarging either moves the first row down
     * with the title instead of leaving the heading to overlap it.
     */
    private fun settingsHeadingSpacing() {
        println("Settings heading spacing")

        val scaffold = code("$SOURCE/ui/components/SettingsScaffold.kt")
        check(
            "the bar's height is still its button and its margin",
            scaffold.contains("private val BarHeight = BackButtonTop + BackButtonSize"),
            "the heading is placed from this height, so it cannot be a literal",
        )
        check(
            "the heading starts 20dp under the bar",
            scaffold.contains("private val TitleStartTop = BarHeight + 20.dp"),
            "the breathing room above a settings page's own name",
        )
        check(
            "the first row starts 44dp under the heading",
            scaffold.contains("private val TitleBottomGap = 44.dp"),
            "the first row has to read as the list beginning, not the heading's next line",
        )
        check(
            "the content's top room is computed from both gaps",
            scaffold.contains("private val ExpandedHeader = TitleStartTop + 36.dp + TitleBottomGap") &&
                scaffold.contains("top = ExpandedHeader") &&
                !scaffold.contains("148.dp"),
            "a written-out height would stop following the heading it is made of",
        )
        check(
            "the bar's own title size and travel are unchanged",
            scaffold.contains("private val TitleEndSize = 22.sp") &&
                scaffold.contains("private val TitleTravel = 44.dp"),
            "moving the heading must not move the bar it becomes",
        )
    }

    /**
     * How a wallpaper change reaches the cards.
     *
     * The widget's colours are resolved in code every time a card is drawn, so a new wallpaper's
     * dynamic scheme only reaches a card when the app sends a new drawing. A wallpaper change is not
     * a configuration change: from API 27 the platform announces the new colours to a listener, and
     * the versions before it only send the wallpaper-changed broadcast, so a runtime receiver is
     * registered there. Neither can reach a process that is not running - a drawing on the next
     * process start is the repair for that - and neither adds a permission, a manifest entry or any
     * scheduled work: the app does no background work, so a reboot or a wallpaper change made while
     * the app has no process leaves the cards as last drawn until something starts the process.
     */
    private fun wallpaperSignals() {
        println("Wallpaper colour signals")

        val app = code("$SOURCE/HiNotesApplication.kt")
        val onCreate = between(app, "override fun onCreate()", "override fun onConfigurationChanged(")
        check(
            "a process start listens for the wallpaper",
            onCreate.contains("listenForWallpaperSignals()"),
            "the process that draws a card is the only one that can hear a colour change",
        )
        check(
            "a process start draws the cards it finds",
            onCreate.contains("HiNotesWidgetProvider.refreshAll(this)"),
            "this is the repair for a change made while no process existed",
        )

        val dispatch =
            between(app, "private fun listenForWallpaperSignals()", "private fun listenForWallpaperColors()")
        check(
            "the listener follows the platform's own signal from API 27",
            dispatch.contains("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {") &&
                dispatch.contains("listenForWallpaperColors()") &&
                dispatch.contains("else {") &&
                dispatch.contains("listenForWallpaperChanges()"),
            "the colour callback exists from API 27; earlier versions only have the broadcast",
        )

        val colors =
            between(app, "private fun listenForWallpaperColors()", "private fun listenForWallpaperChanges()")
        check(
            "the API 27 signal is the platform's colour callback",
            colors.contains("WallpaperManager.OnColorsChangedListener { _, _ ->") &&
                colors.contains("addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))"),
            "the callback is delivered on the main looper, where the redraw request is cheap",
        )
        check(
            "a wallpaper colour change asks for a redraw",
            colors.contains("HiNotesWidgetProvider.refreshAll(this)"),
            "a scheme that changes without a drawing is a card in the old colours",
        )
        check(
            "the colour listener is held for the process's life",
            app.contains(
                "private var wallpaperColorsListener: WallpaperManager.OnColorsChangedListener? = null"
            ) && colors.contains("wallpaperColorsListener = listener"),
            "a listener the platform holds but the app does not would be collectable",
        )
        check(
            "the colour listener is registered once",
            colors.contains("if (wallpaperColorsListener != null) return"),
            "onCreate runs once per process, and a second registration would draw twice",
        )
        check(
            "a refused colour registration does not crash the start",
            colors.contains("runCatching {") &&
                colors.contains("onFailure") &&
                colors.contains("Log.w(TAG, \"could not listen for wallpaper colours\""),
            "a best-effort signal must not cost the process its start",
        )

        val changes = after(app, "private fun listenForWallpaperChanges()")
        check(
            "the old versions hear the wallpaper changed broadcast",
            changes.contains("object : BroadcastReceiver()") &&
                changes.contains("IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)"),
            "before API 27 the broadcast is the only wallpaper signal there is",
        )
        check(
            "the old versions register the receiver at runtime",
            changes.contains("ContextCompat.registerReceiver(") &&
                changes.contains("ContextCompat.RECEIVER_NOT_EXPORTED"),
            "a manifest receiver would be one more reason to start a process the app does no work in",
        )
        check(
            "a wallpaper broadcast asks for a redraw",
            changes.contains("HiNotesWidgetProvider.refreshAll(context)"),
        )
        check(
            "the broadcast receiver is held and registered once",
            app.contains("private var wallpaperReceiver: BroadcastReceiver? = null") &&
                changes.contains("if (wallpaperReceiver != null) return") &&
                changes.contains("wallpaperReceiver = receiver"),
            "the field is both the process-lifetime reference and the guard",
        )
        check(
            "a refused broadcast registration does not crash the start",
            changes.contains("runCatching {") &&
                changes.contains("Log.w(TAG, \"could not listen for wallpaper changes\""),
        )
        // The annotation sits above the declaration, so the slice after the signature cannot hold
        // it: the file itself is what says the deprecated action was accepted on purpose.
        val suppression = app.lastIndexOf("@Suppress(\"DEPRECATION\")")
        val changesSignature = app.indexOf("private fun listenForWallpaperChanges()")
        check(
            "the deprecated broadcast is explicitly accepted",
            suppression >= 0 && changesSignature > suppression &&
                app.substring(suppression + "@Suppress(\"DEPRECATION\")".length, changesSignature).isBlank(),
            "the action is deprecated from Android 16, whose signal is the colour callback",
        )

        val configuration =
            between(app, "override fun onConfigurationChanged(", "private fun listenForWallpaperSignals()")
        check(
            "the process hears a configuration change too",
            configuration.contains("super.onConfigurationChanged(newConfig)") &&
                configuration.contains("HiNotesWidgetProvider.refreshAll(this)"),
            "a light/dark switch is a configuration change, not a wallpaper signal",
        )

        // The new signals add no reason for the platform to keep or start the process.
        val manifest = xml(MANIFEST)
        check(
            "the new signals ask for no permission",
            Regex("<uses-permission").findAll(manifest).count() == 2 &&
                manifest.contains("android.permission.USE_BIOMETRIC") &&
                manifest.contains("android.permission.USE_FINGERPRINT"),
            "a wallpaper repaint justifies no new permission",
        )
        check(
            "the new signals add no manifest receiver",
            Regex("<receiver").findAll(manifest).count() == 1 &&
                manifest.contains(".widget.HiNotesWidgetProvider"),
            "the app widget provider is still the only one the manifest declares",
        )
        check(
            "the new signals add no service",
            Regex("<service").findAll(manifest).count() == 1 &&
                manifest.contains(".widget.HiNotesWidgetService"),
            "the widget's collection service is still the only one the manifest declares",
        )
        check(
            "the app gains no background work",
            listOf(
                "AlarmManager",
                "WorkManager",
                "JobScheduler",
                "ScheduledExecutor",
                "postDelayed",
                "Timer(",
            ).none { app.contains(it) },
            "a colour refresh is not worth work the platform keeps the process awake for",
        )
        check(
            "the manifest declares no boot or foreground-service entry",
            !manifest.contains("BOOT_COMPLETED") &&
                !manifest.contains("FOREGROUND_SERVICE") &&
                !manifest.contains("foregroundServiceType"),
            "no process exists after a reboot, so the cards keep their last drawing until one starts",
        )
    }

    /**
     * The app's own lifecycle triggers, which repaint cards after the process or the screen missed a
     * change.
     */
    private fun lifecycleTriggers() {
        println("The app's own lifecycle triggers")

        val main = code("$SOURCE/MainActivity.kt")
        val onCreate = between(main, "override fun onCreate(", "setContent {")
        check(
            "opening the app draws the cards once",
            onCreate.contains("HiNotesWidgetProvider.refreshAll(this)"),
            "a change made while the app was not running is repaired here",
        )

        val onResume = between(main, "override fun onResume(", "override fun onConfigurationChanged(")
        check(
            "coming back to the app reads the notes again",
            onResume.contains("viewModel.reloadNotes()"),
            "a widget writes through its own repository while the app is away",
        )
        check(
            "coming back to the app redraws the cards",
            onResume.contains("HiNotesWidgetProvider.refreshAll(this)"),
            "this is also the way back after the process that heard a switch was gone",
        )

        val onConfigurationChanged =
            between(main, "override fun onConfigurationChanged(", "private fun systemDark")
        check(
            "a configuration change in a live screen redraws the cards",
            onConfigurationChanged.contains("super.onConfigurationChanged(newConfig)") &&
                onConfigurationChanged.contains("HiNotesWidgetProvider.refreshAll(this)"),
            "the screen declares uiMode, so no other callback here notices the switch",
        )
    }

    private fun report() {
        if (failures == 0) {
            println("app shell checks: all checks passed")
        } else {
            println("app shell checks: $failures check(s) FAILED")
            exitProcess(1)
        }
    }
}
