package com.hiapps.hinotes.ui.theme

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

    /** Home action buttons (create note, delete): 56dp tall with a 20dp radius. */
    val ExtendedFab = 20.dp

    /** Editor header controls: 44dp tall with a 16dp radius. */
    val SplitControl = 16.dp

    /** Cards and image placeholders. */
    val Card = 20.dp

    /** Dialogs. */
    val Dialog = 28.dp

    /** Seam between the two segments of a split button. */
    val SplitSeam = 2.dp
}
