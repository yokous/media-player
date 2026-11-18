package com.example.mediaplayer.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mediaplayer.presentation.ui.theme.AppTheme
import com.example.mediaplayer.presentation.ui.theme.capsuleShape

enum class AppButtonStyle { Primary, Secondary, Text }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
    val sized = modifier.heightIn(min = 52.dp)
    val active = enabled && !loading

    when (style) {
        AppButtonStyle.Primary -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = active,
            shape = capsuleShape,
            content = content
        )
        AppButtonStyle.Secondary -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = active,
            shape = capsuleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.glassFill,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, AppTheme.colors.glassStroke),
            content = content
        )
        AppButtonStyle.Text -> TextButton(
            onClick = onClick,
            modifier = sized,
            enabled = active,
            shape = capsuleShape,
            content = content
        )
    }
}