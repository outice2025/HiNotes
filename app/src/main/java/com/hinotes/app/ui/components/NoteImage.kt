package com.hinotes.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinotes.app.R
import com.hinotes.app.data.ImageStore

/**
 * Renders an image a note references.
 *
 * Decoding happens off the main thread (inside [ImageStore.load]) and is keyed on the reference,
 * so scrolling a long note neither blocks nor re-decodes. While loading, and if the file is
 * missing, a `surfaceContainerHighest` placeholder stands in - matching the design's image
 * treatment, which is a 20dp-rounded box that keeps its aspect ratio and crops from the centre.
 */
@Composable
fun NoteImage(
    reference: String,
    alt: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(reference) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(reference) { mutableStateOf(false) }

    LaunchedEffect(reference) {
        val loaded = ImageStore.load(context, reference)
        if (loaded == null) failed = true else bitmap = loaded
    }

    val shape = RoundedCornerShape(20.dp)
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = alt.ifBlank { null },
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 480.dp),
            contentScale = ContentScale.Crop,
        )
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        failed && alt.isNotBlank() -> alt
                        failed -> stringResource(R.string.editor_image_unavailable)
                        else -> "…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
