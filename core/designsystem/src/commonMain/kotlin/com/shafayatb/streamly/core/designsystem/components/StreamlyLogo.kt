package com.shafayatb.streamly.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** App mark: a frosted rounded tile with a play glyph, sized for brand surfaces. */
@Composable
public fun StreamlyLogo(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(semanticsModifier)
            .size(size)
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(size * 0.3f)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.3f),
        ) {
            val glyph = Path().apply {
                moveTo(this@Canvas.size.width * 0.18f, 0f)
                lineTo(this@Canvas.size.width, this@Canvas.size.height / 2f)
                lineTo(this@Canvas.size.width * 0.18f, this@Canvas.size.height)
                close()
            }
            drawPath(glyph, Color.White)
        }
    }
}
