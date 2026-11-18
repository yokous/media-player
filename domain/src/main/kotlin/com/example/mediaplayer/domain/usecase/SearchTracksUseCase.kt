package com.example.mediaplayer.domain.usecase

import com.example.mediaplayer.domain.error.DomainResult
import com.example.mediaplayer.domain.model.Track
import com.example.mediaplayer.domain.repository.CatalogRepository
import javax.inject.Inject

class SearchTracksUseCase @Inject constructor(
    private val catalog: CatalogRepository
) {
    suspend operator fun invoke(query: String): DomainResult<List<Track>> = catalog.search(query.trim())
}
