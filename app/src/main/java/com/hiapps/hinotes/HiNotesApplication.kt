package com.hiapps.hinotes

import android.app.Application
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.hiapps.hinotes.widget.HiNotesWidgetProvider

private const val TAG = "HiNotesWidget"

/**
 * The process, and the events the home-screen widget cannot place on its own.
 *
 * A widget is drawn by the launcher from a RemoteViews rather than by the app's composition, so its
 * colours cannot come from a `values-night` resource: they are resolved in code every time a card is
 * drawn (see `widgetStyle`), and a card only gets new ones when the app sends a new drawing. A
 * light/dark switch is a configuration change, so the drawing is asked for below, from the process
 * that is alive while the app sits in the background and the setting flips.
 *
 * A wallpaper change is the other change a card follows, and it is not a configuration change: the
 * dynamic colour scheme is derived from the wallpaper, and the platform announces a new one through
 * a callback rather than through a configuration change. [listenForWallpaperSignals] starts that
 * listening when the process starts: from API 27 the platform's own colour callback
 * ([WallpaperManager.addOnColorsChangedListener]) is the signal, and the versions before it only
 * have the [Intent.ACTION_WALLPAPER_CHANGED] broadcast, so a runtime receiver is registered there
 * instead. Either way the listening lasts as long as the process does.
 *
 * A screen that is alive hears a configuration change too and asks again (see `MainActivity`); the
 * requests coalesce into one drawing (see `HiNotesWidgetProvider.refresh`), which is deliberate:
 * with the activity declaring `configChanges` there is no guarantee which of the two the platform
 * calls. The wallpaper signals ask for the same coalesced drawing, so a burst of announcements
 * costs one drawing rather than one per announcement.
 *
 * The boundary of what this can hear: a process that does not exist hears nothing, and neither a
 * platform callback nor a broadcast can be delivered to it. A wallpaper or theme switch made while
 * the app has no process at all is repaired by the drawing in [onCreate] the next time a process
 * starts for any reason - the launcher binding the widget's collection service, a tap on a card, or
 * the app being opened. Nothing polls for it, wakes the process, or asks the platform for a
 * background run: a colour change is not worth a foreground service, and a process the platform has
 * not started is not one an app may start for a repaint.
 */
class HiNotesApplication : Application() {

    /**
     * The API 27+ wallpaper-colour listener, or null before it is registered.
     *
     * Held in a field because it has to outlive the call that registers it: the platform is handed
     * the listener once and announces colour changes to it for as long as the app's process lives,
     * so the object must not be collectable in between. Nothing unregisters it - a process-wide
     * listener ends with the process itself.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private var wallpaperColorsListener: WallpaperManager.OnColorsChangedListener? = null

    /**
     * The broadcast receiver the versions before API 27 hear a wallpaper change from, or null
     * before it is registered.
     *
     * Held in a field for the same reason as [wallpaperColorsListener], and it doubles as the guard
     * that keeps the registration from being repeated: [onCreate] runs once per process, and a
     * second call to the registration method is a no-op while this is set.
     */
    private var wallpaperReceiver: BroadcastReceiver? = null

    /**
     * A process that started after the setting changed draws what it finds.
     *
     * The launcher's first update after a reboot, or after the app was killed, can be older than
     * the appearance the app holds; one drawing here repaints whatever the cards hold now. It does
     * nothing when no widget is placed, and it coalesces with a drawing that is already on its way.
     * One of the things it repairs is a wallpaper change that happened while this process did not
     * exist - neither of the signals below can reach a process that was not running.
     */
    override fun onCreate() {
        super.onCreate()
        listenForWallpaperSignals()
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

    /**
     * Starts the wallpaper listening this process can have on its platform version.
     *
     * From API 27 the platform itself announces a change of the wallpaper's colours to registered
     * listeners, and that callback is the signal the dynamic colour scheme follows. The versions
     * before it have no such callback and only the wallpaper-changed broadcast, so the runtime
     * receiver is registered there instead. Neither of the two can be heard by a process that is
     * not running; the drawing in [onCreate] is the repair for that case.
     */
    private fun listenForWallpaperSignals() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            listenForWallpaperColors()
        } else {
            listenForWallpaperChanges()
        }
    }

    /**
     * Draws the cards again when the wallpaper's colours change, from API 27.
     *
     * The listener is called on the main thread's looper, which is the one this request is cheap
     * on: it only hands the work to the provider's own scope, where it is coalesced with any other
     * drawing already on its way. The callback can arrive more often than the wallpaper changes - a
     * lock-screen colour update is one too - and that coalescing is what keeps a burst from being
     * one drawing per announcement.
     *
     * The timing is the platform's: where a build updates its colour resources a moment after the
     * callback, this drawing reads the colours in force when it runs, and the next drawing from any
     * other trigger - opening the app, a save, the next process start - picks up the rest. No delay
     * or timer is added to chase that; the callback is taken as the moment the platform says the
     * colours changed.
     *
     * Nothing unregisters the listener: it belongs to the process, and [onCreate] runs once per
     * process, so the registration itself happens once.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun listenForWallpaperColors() {
        if (wallpaperColorsListener != null) return
        // A best-effort signal must not cost the process its start: a platform that refuses the
        // registration leaves the drawing in onCreate as the only repair, exactly as it is on a
        // build that never announces a wallpaper change.
        runCatching {
            val manager = getSystemService(WallpaperManager::class.java) ?: return@runCatching
            val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
                // The cards follow the scheme the new wallpaper derives; the provider coalesces
                // this request with any drawing already on its way.
                HiNotesWidgetProvider.refreshAll(this)
            }
            manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            wallpaperColorsListener = listener
        }.onFailure { failure ->
            Log.w(TAG, "could not listen for wallpaper colours", failure)
        }
    }

    /**
     * Draws the cards again when the wallpaper changes, on the versions before API 27.
     *
     * The change is announced by a system broadcast there rather than by a configuration change, so
     * a runtime receiver is registered instead of a manifest entry: the app is only interested
     * while its own process is alive, and a manifest receiver would be one more reason for the
     * platform to start a process this app does no background work in. Nothing unregisters it,
     * because an application-scoped receiver lives exactly as long as the process it was registered
     * in.
     *
     * It is registered as not exported: the action is a system broadcast, so the platform is the
     * only sender this app would take it from, and no other app has any business delivering it
     * here. On API 33 and later a dynamically registered receiver has to state whether other apps
     * may reach it, and "not exported" is what the receiver's purpose actually is. The action
     * itself is deprecated from Android 16, whose wallpaper announcements are the colour callback
     * above; it is kept here for the versions that have nothing else, and this path is not taken on
     * a version that has the callback.
     *
     * The broadcast is best effort in the versions that still send it, and a process that is not
     * running cannot receive it at all; the drawing in [onCreate] is the repair for both.
     */
    @Suppress("DEPRECATION")
    private fun listenForWallpaperChanges() {
        if (wallpaperReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // The cards follow the scheme the new wallpaper derives. The provider's own window
                // coalesces this with any drawing already on its way.
                HiNotesWidgetProvider.refreshAll(context)
            }
        }
        // A best-effort signal must not cost the process its start: a build that refuses the
        // registration leaves the receiver unregistered and the fallback drawing as the only path,
        // exactly as it is on a build that never sends the broadcast. The call below can throw on
        // the compatibility path for versions before 26, where it needs a signature permission the
        // library's own manifest declares; a build that failed to merge that permission would
        // otherwise crash every app start for a colour refresh.
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onSuccess {
            wallpaperReceiver = receiver
        }.onFailure { failure ->
            Log.w(TAG, "could not listen for wallpaper changes", failure)
        }
    }
}
