package com.hinotes.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Corner radii for components the design specifies explicitly.
 *
 * Material 3's own scale (`MaterialTheme.shapes`: extraSmall 4, small 8, medium 12, large 16,
 * extraLarge 28) covers the standard components, so no custom `Shapes` is installed - the
 * `Shapes` constructor is internal to Material 3, and replacing the scale would fight the
 * component defaults rather than follow them. These tokens are the places the design asks for
 * something more specific than a single M3 step: grouped-list corners and the editor canvas.
 */
object HiNotesCorners {
    /** Outer corner of a grouped list, per the design brief. */
    val GroupOuter = 28.dp

    /** Corner where two neighbours in a group meet. */
    val GroupInner = 8.dp

    /** Gap between neighbouring rows in a group. */
    val GroupGap = 3.dp

    /** Editor canvas container. */
    val Canvas = 28.dp

    /** Extended FAB (M3 medium size: 56dp tall, 16dp radius). */
    val ExtendedFab = 16.dp

    /** Text field corner, per the design brief. */
    val TextField = 16.dp

    /** Cards and image placeholders. */
    val Card = 20.dp

    /** Dialogs. */
    val Dialog = 28.dp

    /** Split button seam between the two segments. */
    val SplitSeam = 2.dp

    /** Inner corner on either side of the split seam. */
    val SplitInner = 8.dp
}
