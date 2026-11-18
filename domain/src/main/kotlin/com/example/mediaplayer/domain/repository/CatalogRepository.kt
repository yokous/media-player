package com.example.mediaplayer.domain.repository

import com.example.mediaplayer.domain.error.DomainResult
import com.example.mediaplayer.domain.model.Track

interface CatalogRepository {
    suspend fun search(query: String): DomainResult<List<Track>>
}
