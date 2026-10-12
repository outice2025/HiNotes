package com.hiapps.hinotes.ui

import android.app.Activity
import android.view.WindowManager

/**
 * Blocks or allows screen capture for this activity's window.
 *
 * `FLAG_SECURE` is a window property rather than a permission, which is what makes it the right
 * tool for the setting: it covers everything the window draws, so a screenshot, a screen recording
 * and a cast all come back blank - and so does the thumbnail the system shows in the recents list,
 * which is the same flag doing its job. It is applied to both of the app's activities, and it can
 * follow the setting while the app is open, because the window is still there to be told.
 */
fun Activity.applyScreenCapturePolicy(disabled: Boolean) {
    if (disabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
