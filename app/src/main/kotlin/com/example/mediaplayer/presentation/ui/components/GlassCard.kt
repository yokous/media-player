package com.example.mediaplayer.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.mediaplayer.presentation.ui.theme.AppTheme
import com.example.mediaplayer.presentation.ui.theme.Spacing

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .clip(shape)
            .background(AppTheme.colors.glassFill)
            .border(1.dp, AppTheme.colors.glassStroke, shape)
            .padding(Spacing.space4),
        content = content
    )
}