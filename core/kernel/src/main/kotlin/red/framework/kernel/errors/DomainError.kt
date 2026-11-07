package red.framework.kernel.errors

/** Stable, transport-independent error contract. Adapters decide HTTP or job representation. */
sealed interface DomainError {
    val code: String
    val message: String

    data class Validation(override val message: String, val field: String? = null) : DomainError {
        override val code: String = "validation"
    }
    data class NotFound(override val message: String) : DomainError {
        override val code: String = "not_found"
    }
    data class Conflict(override val message: String) : DomainError {
        override val code: String = "conflict"
    }
    data class Forbidden(override val message: String) : DomainError {
        override val code: String = "forbidden"
    }
}
