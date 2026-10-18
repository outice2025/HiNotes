package com.hiapps.hinotes.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * How the picture the user picked gets from the activity back to the editor.
 *
 * The same handover the backup screen's file picker uses, and for the same reason: the pick is
 * started with `Activity.startActivityForResult` and a request code this app chooses, because the
 * activity-result registry's own code is refused outright on this app's activity - see
 * [PickedDocumentRelay] for the refusal itself and what it looks like.
 *
 * What comes back is reported as one of three things rather than as a nullable address. A picker
 * that answers without an address is a real answer - the platform's own photo picker has been seen
 * handing its result over in a clip item rather than in the intent's data - and it has to reach the
 * editor as a failure the user can see, because the alternative is a tap that silently does
 * nothing, which is exactly how the picture button was first reported.
 */
internal object PickedImageRelay {
    /** What the picker answered, or null when there is nothing waiting. */
    val answer: MutableState<PickedImage?> = mutableStateOf(null)

    /** Called by the activity when a pick returns a picture. */
    fun publish(picked: Uri) {
        answer.value = PickedImage.Imported(picked)
    }

    /** Called by the activity when a picker answered without a picture. */
    fun refuse() {
        answer.value = PickedImage.Refused
    }

    /** Called by the editor once it has taken the value, so it is not imported twice. */
    fun consume() {
        answer.value = null
    }
}

/** What a picture pick came back with. */
internal sealed interface PickedImage {
    /** The address of the picture the picker returned. */
    data class Imported(val uri: Uri) : PickedImage

    /** A picker answered, and there was no picture in what it answered with. */
    data object Refused : PickedImage
}

/**
 * The picture an activity result carries, or null when it carries none.
 *
 * A picker may hand its result over as the intent's data, which is what every form of the pick
 * documents, or as a clip item - the platform's own photo picker has been seen returning nothing in
 * the data on one device - so both are read before the result is called empty.
 */
internal fun pickedImage(data: Intent?): Uri? =
    data?.data ?: data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

/**
 * The intents that ask the user for a picture, in the order they are tried.
 *
 * The document picker comes first, and the order is the whole point of this list. On the phone this
 * was reported on, `ACTION_PICK_IMAGES` started without throwing and then drew nothing at all: the
 * tap looked dead, and because a start that does not throw was taken as success, the pickers behind
 * it were never asked. `ACTION_OPEN_DOCUMENT` is the storage-access framework's own picker - it is
 * answered by the system's file UI rather than by whichever gallery app happens to be installed, it
 * can browse the pictures in a folder, and it has answered on every version this app supports - so
 * it is asked first and the tap always has something to show for itself.
 *
 * The two behind it are the fallbacks: `ACTION_GET_CONTENT`, which is the gallery's own picker where
 * a gallery has claimed it, and from Android 13 the platform's photo picker, which shows the gallery
 * without giving the app access to anything else in it.
 *
 * The type is set on each of them, and the read grant is asked for by name on the two that take
 * one: a few providers only hand a URI over readable when the request carries it.
 */
internal fun pickImageIntents(): List<Intent> = buildList {
    add(
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
    add(
        Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"))
    }
}

/** The request code the picture picker's result arrives under; see [REQUEST_OPEN_DOCUMENT]. */
internal const val REQUEST_PICK_IMAGE = 0x1002
