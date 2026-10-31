package com.hiapps.hinotes.ui

import android.app.Activity
import android.util.Log
import android.view.WindowManager

private const val TAG = "HiNotesPrivacy"

/**
 * Blocks or allows screen capture for this activity's window.
 *
 * `FLAG_SECURE` is a window property rather than a permission, which is what makes it the right
 * tool for the setting: it covers everything the window draws, so a screenshot, a screen recording
 * and a cast all come back blank - and so does the thumbnail the system shows in the recents list,
 * which is the same flag doing its job. It is applied to both of the app's activities, and it can
 * follow the setting while the app is open, because the window is still there to be told.
 *
 * Every change is logged, because this is a window property the user cannot see: when a report says
 * screenshots are still blocked after the switch was turned off, the log says whether the window was
 * told, and when.
 */
fun Activity.applyScreenCapturePolicy(disabled: Boolean) {
    val secure = WindowManager.LayoutParams.FLAG_SECURE
    val wasSecure = window.attributes.flags and secure != 0
    if (disabled) {
        window.addFlags(secure)
    } else {
        window.clearFlags(secure)
    }
    if (disabled != wasSecure) {
        Log.i(TAG, "${javaClass.simpleName}: screen capture ${if (disabled) "blocked" else "allowed"}")
    }
}
