package com.hinotes.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinotes.app.R
import com.hinotes.app.ui.icons.SymbolIcon
import com.hinotes.app.ui.icons.Symbols

/**
 * Shared frame for every settings screen: a back text-icon button and a 28sp title at the top
 * left, then scrollable content.
 *
 * The back button is a 44dp M3 text icon button as the design specifies; it plays the reverse
 * of the push transition because it goes through the same [onBack] pop.
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
        snackbarHost = snackbarHost,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // Top-left: back button, then the screen title.
            Box(Modifier.padding(start = 8.dp, top = 4.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    SymbolIcon(
                        codepoint = Symbols.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        size = 24.dp,
                    )
                }
            }

            Text(
                text = title,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )

            // Generous gap between the page title and the first list, per the design revision.
            Spacer(Modifier.height(SettingsTitleGap))

            Box(Modifier.padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}

/** Vertical space between a settings screen's title and its first group of rows. */
val SettingsTitleGap = 28.dp
