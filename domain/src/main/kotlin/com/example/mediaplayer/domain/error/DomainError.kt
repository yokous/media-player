package com.example.mediaplayer.domain.error

sealed interface DomainError {
    data object NoInternet : DomainError
    data object Timeout : DomainError
    data class Server(val code: Int) : DomainError
    data object Unauthorized : DomainError
    data object NotFound : DomainError
    data object RateLimited : DomainError
    data object Unknown : DomainError
}
