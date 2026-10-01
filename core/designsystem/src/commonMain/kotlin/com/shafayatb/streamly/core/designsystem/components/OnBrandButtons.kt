package com.shafayatb.streamly.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shafayatb.streamly.core.designsystem.theme.StreamlyPalette
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme

private val PillHeight = 52.dp

/** Solid white pill for the primary action on a [BrandBackground]. */
@Composable
public fun OnBrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    // Brand surfaces look the same in light and dark themes, so the label ignores colorScheme.
    val contentColor = StreamlyPalette.Indigo700
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.height(PillHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = contentColor,
            disabledContainerColor = Color.White.copy(alpha = 0.7f),
            disabledContentColor = contentColor.copy(alpha = 0.7f),
        ),
    ) {
        PillContent(text = text, isLoading = isLoading, indicatorColor = contentColor)
    }
}

/** Outlined white pill for secondary actions on a [BrandBackground]. */
@Composable
public fun OnBrandOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.height(PillHeight),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.7f else 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.6f),
        ),
    ) {
        PillContent(text = text, isLoading = isLoading, indicatorColor = Color.White)
    }
}

/** Underlined text action, for low-emphasis choices such as continuing as a guest. */
@Composable
public fun OnBrandTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = Color.White.copy(alpha = 0.9f),
            disabledContentColor = Color.White.copy(alpha = 0.5f),
        ),
    ) {
        PillContent(
            text = text,
            isLoading = isLoading,
            indicatorColor = Color.White,
            textDecoration = TextDecoration.Underline,
        )
    }
}

// Keeps the label in layout while loading so the button does not change size.
@Composable
private fun PillContent(
    text: String,
    isLoading: Boolean,
    indicatorColor: Color,
    textDecoration: TextDecoration? = null,
) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textDecoration = textDecoration,
            modifier = Modifier.graphicsLayer { alpha = if (isLoading) 0f else 1f },
        )
        if (isLoading) {
            CircularProgressIndicator(
                color = indicatorColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview
@Composable
private fun OnBrandButtonsPreview() {
    StreamlyTheme {
        BrandBackground {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp).width(280.dp),
            ) {
                StreamlyLogo(contentDescription = null)
                Spacer(Modifier.height(24.dp))
                OnBrandButton(text = "Continue with Google", onClick = {}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OnBrandOutlinedButton(text = "Sign in with email", onClick = {}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OnBrandButton(text = "Loading", onClick = {}, isLoading = true, modifier = Modifier.fillMaxWidth())
                OnBrandTextButton(text = "Continue as guest", onClick = {})
            }
        }
    }
}
