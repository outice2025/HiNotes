package com.hiapps.hinotes

import android.app.Application
import android.content.res.Configuration
import com.hiapps.hinotes.widget.HiNotesWidgetProvider

/**
 * The process, and the one event the home-screen widget cannot place on its own.
 *
 * A widget is drawn by the launcher from a RemoteViews rather than by the app's composition, so its
 * colours cannot come from a `values-night` resource: they are resolved in code every time a card is
 * drawn (see `widgetStyle`), and a card only gets new ones when the app sends a new drawing. A
 * light/dark switch is a configuration change, so the drawing is asked for below, from the process
 * that is alive while the app sits in the background and the setting flips.
 *
 * A screen that is alive hears the same change and asks again (see `MainActivity`); the requests
 * coalesce into one drawing (see `HiNotesWidgetProvider.refresh`), which is deliberate: with the
 * activity declaring `configChanges` there is no guarantee which of the two the platform calls.
 *
 * The boundary of what this can hear: a process that does not exist hears nothing. A phone switched
 * while the app has no process at all is repaired by the drawing in [onCreate] the next time a
 * process starts for any reason - the launcher binding the widget's collection service, a tap on a
 * card, or the app being opened.
 */
class HiNotesApplication : Application() {

    /**
     * A process that started after the setting changed draws what it finds.
     *
     * The launcher's first update after a reboot, or after the app was killed, can be older than
     * the appearance the app holds; one drawing here repaints whatever the cards hold now. It does
     * nothing when no widget is placed, and it coalesces with a drawing that is already on its way.
     */
    override fun onCreate() {
        super.onCreate()
        HiNotesWidgetProvider.refreshAll(this)
    }

    /**
     * The device's configuration changed - a light/dark switch is one, and so is a font-size or
     * density change.
     *
     * The cards are drawn from the setting in force at the moment of the drawing, so asking for one
     * is right for any of these changes, and a burst of them is coalesced by the provider's own
     * request window. Reading the setting here rather than comparing configurations is what keeps a
     * switch from being missed when this callback arrives before the process resources have caught
     * up with it.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        HiNotesWidgetProvider.refreshAll(this)
    }
}
