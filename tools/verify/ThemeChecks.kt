package com.hiapps.hinotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.hiapps.hinotes.widget.widgetSurface
import java.io.File

/**
 * Standalone checks for [nearestPairTo] and for the surface a home-screen widget is painted with,
 * run on a desktop JVM by tools/verify/run.ps1.
 *
 * The home screen paints its delete action in whichever pair of roles in the active scheme is
 * nearest the destructive red the button was specified with. That is arithmetic standing in for a
 * designer's eye, so it is executed here rather than assumed: every light scheme the app ships has
 * to answer with its own error pair, because a scheme that answered with its primary would paint
 * the delete button in the accent colour - the one thing a destructive action must not be.
 *
 * The widget's surface is chosen the same way, against the dial of the clock widget a card is placed
 * beside, and its answers are checked for the same reason: a regenerated palette would repaint every
 * widget on every home screen and nothing would say so.
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

        val light = listOf(
            "BlueLight" to BlueLight,
            "TeaGreenLight" to TeaGreenLight,
            "LightGrayLight" to LightGrayLight,
            "RedLight" to RedLight,
            "LightPurpleLightColorScheme" to LightPurpleLightColorScheme,
            "OrangeLight" to OrangeLight,
            "YellowLight" to YellowLight,
        )
        val dark = listOf(
            "BlueDark" to BlueDark,
            "TeaGreenDark" to TeaGreenDark,
            "LightGrayDark" to LightGrayDark,
            "RedDark" to RedDark,
            "LightPurpleDarkColorScheme" to LightPurpleDarkColorScheme,
            "OrangeDark" to OrangeDark,
            "YellowDark" to YellowDark,
        )

        val reference = ColorTokens.DestructiveReference
        light.forEach { (name, scheme) ->
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

        val declared = declaredSchemes("light")
        check(
            "every light scheme in the sources is checked here",
            declared == light.map { it.first }.toSet(),
            "sources declare $declared",
        )

        widgetSurfaces(light, dark)

        report()
    }

    /**
     * The surface a home-screen widget is painted with, per shipped palette.
     *
     * A card is placed beside the device's clock widget and drawn in the surface nearest that
     * widget's dial, and the app lifts a dark scheme's surfaces before anything is painted with
     * them. Both steps are run here exactly as the widget runs them, because what they come to is
     * what the card looks like: nothing on a home screen would report a palette whose ramp had moved
     * under it.
     */
    private fun widgetSurfaces(
        light: List<Pair<String, ColorScheme>>,
        dark: List<Pair<String, ColorScheme>>,
    ) {
        println("widget surface, per shipped scheme")

        val expectedDark = mapOf(
            "BlueDark" to "surfaceContainerHigh",
            "TeaGreenDark" to "surfaceBright",
            "LightGrayDark" to "surfaceContainerHigh",
            "RedDark" to "surfaceBright",
            "LightPurpleDarkColorScheme" to "surfaceContainerHigh",
            "OrangeDark" to "surfaceBright",
            "YellowDark" to "surfaceBright",
        )
        dark.forEach { (name, scheme) ->
            // A dark card is painted from the ramp the app lifts, not the one the palette declares.
            val role = roleOf(scheme.liftDarkSurfaces(), dark = true)
            check("$name's card is ${expectedDark[name]}", role == expectedDark[name], "$role")
        }
        val declaredDark = declaredSchemes("dark")
        check(
            "every dark scheme in the sources is checked here",
            declaredDark == expectedDark.keys,
            "sources declare $declaredDark",
        )

        val expectedLight = mapOf(
            "BlueLight" to "surfaceContainerLow",
            "TeaGreenLight" to "surfaceBright",
            "LightGrayLight" to "surfaceContainerLow",
            "RedLight" to "surfaceBright",
            "LightPurpleLightColorScheme" to "surfaceContainerLow",
            "OrangeLight" to "surfaceBright",
            "YellowLight" to "surfaceBright",
        )
        light.forEach { (name, scheme) ->
            val role = roleOf(scheme, dark = false)
            check("$name's card is ${expectedLight[name]}", role == expectedLight[name], "$role")
        }
        val declaredLight = declaredSchemes("light")
        check(
            "every light scheme in the sources is checked here",
            declaredLight == expectedLight.keys,
            "sources declare $declaredLight",
        )

        // OLED dark flattens the whole ramp the card is chosen from, so a card on such a panel is
        // drawn in the same near-black family as the app's own surfaces rather than in a slate the
        // flattening left behind.
        val oled = BlueDark.liftDarkSurfaces().toOled()
        check(
            "an OLED panel draws the card in its lightest black",
            widgetSurface(oled, dark = true) == ColorTokens.Oled.SurfaceContainerHighest,
            "${widgetSurface(oled, dark = true)}",
        )
    }

    /** The name of the role [widgetSurface] answered with for [scheme], or null if it is none. */
    private fun roleOf(scheme: ColorScheme, dark: Boolean): String? {
        val chosen = widgetSurface(scheme, dark)
        return ROLES.firstOrNull { it.second(scheme) == chosen }?.first
    }

    /** The surface roles a card may be painted with, by name, in the order the app weighs them. */
    private val ROLES: List<Pair<String, (ColorScheme) -> Color>> = listOf(
        "surface" to { it.surface },
        "surfaceDim" to { it.surfaceDim },
        "surfaceBright" to { it.surfaceBright },
        "surfaceContainerLowest" to { it.surfaceContainerLowest },
        "surfaceContainerLow" to { it.surfaceContainerLow },
        "surfaceContainer" to { it.surfaceContainer },
        "surfaceContainerHigh" to { it.surfaceContainerHigh },
        "surfaceContainerHighest" to { it.surfaceContainerHighest },
        "surfaceVariant" to { it.surfaceVariant },
    )

    /** The schemes the palette sources declare for one mode, read as text. */
    private fun declaredSchemes(kind: String): Set<String> {
        val files = listOf("AccentPalettes.kt", "ColorSchemes.kt")
            .map { File("app/src/main/java/com/hiapps/hinotes/ui/theme/$it") }
        return files.filter { it.isFile }
            .flatMap { file ->
                SCHEME_DECLARATION.findAll(file.readText())
                    .filter { it.groupValues[2] == kind }
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
