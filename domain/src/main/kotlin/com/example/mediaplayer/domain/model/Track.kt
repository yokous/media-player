package com.example.mediaplayer.domain.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val source: TrackSource
)

sealed interface TrackSource {
    data class Catalog(val streamUrl: String) : TrackSource
    data class Local(val contentUri: String) : TrackSource
}
