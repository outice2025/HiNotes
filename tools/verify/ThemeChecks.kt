package com.hiapps.hinotes.ui.theme

import java.io.File

/**
 * Standalone checks for [nearestPairTo], run on a desktop JVM by tools/verify/run.ps1.
 *
 * The home screen paints its delete action in whichever pair of roles in the active scheme is
 * nearest the destructive red the button was specified with. That is arithmetic standing in for a
 * designer's eye, so it is executed here rather than assumed: every light scheme the app ships has
 * to answer with its own error pair, because a scheme that answered with its primary would paint
 * the delete button in the accent colour - the one thing a destructive action must not be.
 *
 * The list below is also what keeps the check complete: the declarations are counted in the sources
 * as well, so a palette added there without being added here fails the check instead of quietly
 * going unverified.
 */
object ThemeChecks {

    private var failures = 0

    private fun check(name: String, condition: Boolean, detail: String = "") {
        if (condition) {
            println("  ok    $name")
        } else {
            failures++
            println("  FAIL  $name ${if (detail.isEmpty()) "" else "-> $detail"}")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("nearest button colour, per shipped light scheme")

        val schemes = listOf(
            "BlueLight" to BlueLight,
            "TeaGreenLight" to TeaGreenLight,
            "LightGrayLight" to LightGrayLight,
            "RedLight" to RedLight,
            "LightPurpleLightColorScheme" to LightPurpleLightColorScheme,
            "OrangeLight" to OrangeLight,
            "YellowLight" to YellowLight,
        )

        val reference = ColorTokens.DestructiveReference
        schemes.forEach { (name, scheme) ->
            val pair = scheme.nearestPairTo(reference)
            val expected = ColorPair(scheme.error, scheme.onError)
            check(
                "$name answers with its own red",
                pair == expected,
                "got ${pair.container} / ${pair.content}, expected ${expected.container}",
            )
        }

        // The design palette's error role is the colour the button was specified from, which is why
        // the answer above is the one the design asked for.
        check(
            "the reference is the design palette's error",
            reference == ColorTokens.Light.Error,
            reference.toString(),
        )

        val declared = declaredLightSchemes()
        check(
            "every light scheme in the sources is checked here",
            declared == schemes.map { it.first }.toSet(),
            "sources declare $declared",
        )

        report()
    }

    /** The light schemes the palette sources declare, read as text. */
    private fun declaredLightSchemes(): Set<String> {
        val files = listOf("AccentPalettes.kt", "ColorSchemes.kt")
            .map { File("app/src/main/java/com/hiapps/hinotes/ui/theme/$it") }
        return files.filter { it.isFile }
            .flatMap { file ->
                SCHEME_DECLARATION.findAll(file.readText())
                    .filter { it.groupValues[2] == "light" }
                    .map { it.groupValues[1] }
            }
            .toSet()
    }

    /** `val Name: ColorScheme = lightColorScheme(` and its dark counterpart. */
    private val SCHEME_DECLARATION = Regex("val (\\w+): ColorScheme = (\\w+)ColorScheme\\(")

    private fun report() {
        if (failures == 0) {
            println("theme checks: all checks passed")
        } else {
            println("theme checks: $failures check(s) FAILED")
            throw IllegalStateException("$failures theme check(s) failed")
        }
    }
}
