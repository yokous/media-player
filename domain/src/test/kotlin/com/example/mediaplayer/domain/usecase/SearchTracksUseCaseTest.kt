package com.example.mediaplayer.domain.usecase

import com.example.mediaplayer.domain.error.DomainResult
import com.example.mediaplayer.domain.model.Track
import com.example.mediaplayer.domain.repository.CatalogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTracksUseCaseTest {
    private class Recording : CatalogRepository {
        var lastQuery: String? = null

        override suspend fun search(query: String): DomainResult<List<Track>> {
            lastQuery = query
            return DomainResult.Success(emptyList())
        }
    }

    @Test
    fun `query is trimmed before it reaches the repository`() = runTest {
        val repo = Recording()
        SearchTracksUseCase(repo)("  rock  ")
        assertEquals("rock", repo.lastQuery)
    }
}
