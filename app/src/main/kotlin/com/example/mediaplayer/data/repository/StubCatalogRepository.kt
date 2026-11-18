package com.example.mediaplayer.data.repository

import com.example.mediaplayer.domain.error.DomainResult
import com.example.mediaplayer.domain.model.Track
import com.example.mediaplayer.domain.model.TrackSource
import com.example.mediaplayer.domain.repository.CatalogRepository
import javax.inject.Inject

class StubCatalogRepository @Inject constructor() : CatalogRepository {
    override suspend fun search(query: String): DomainResult<List<Track>> = DomainResult.Success(
        listOf(Track("1", "Demo track", "Demo artist", 180, TrackSource.Catalog("https://example.com/demo.mp3")))
    )
}
