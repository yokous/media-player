package com.example.mediaplayer.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.mediaplayer.presentation.ui.theme.MediaPlayerTheme
import com.example.mediaplayer.presentation.ui.theme.Spacing

@Composable
private fun Gallery() {
    Column(
        modifier = Modifier.padding(Spacing.space5),
        verticalArrangement = Arrangement.spacedBy(Spacing.space3)
    ) {
        AppButton(text = "Играть", onClick = {})
        AppButton(text = "Скачать", onClick = {}, style = AppButtonStyle.Secondary)
        AppButton(text = "Отмена", onClick = {}, style = AppButtonStyle.Text)
        AppButton(text = "Загрузка", onClick = {}, loading = true)
        AppTextField(value = "", onValueChange = {}, hint = "Поиск")
        AppTextField(value = "abc", onValueChange = {}, hint = "Почта", errorText = "Неверный адрес")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
            AppChip(text = "Рок", selected = true, onClick = {})
            AppChip(text = "Поп", selected = false, onClick = {})
        }
        GlassCard { Text("Стеклянная карточка", color = MaterialTheme.colorScheme.onSurface) }
        TrackRow(title = "Demo track", artist = "Demo artist", isCurrent = true, isOffline = true, liked = true)
        TrackRow(title = "Другой трек", artist = "Другой исполнитель", liked = false)
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun GalleryDark() {
    MediaPlayerTheme(darkTheme = true) { Gallery() }
}

@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun GalleryLight() {
    MediaPlayerTheme(darkTheme = false) { Gallery() }
}