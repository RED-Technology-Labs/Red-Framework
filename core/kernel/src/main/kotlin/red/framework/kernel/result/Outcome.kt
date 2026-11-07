package red.framework.kernel.result

import red.framework.kernel.errors.DomainError

/** Typed outcome for expected domain failures; unexpected infrastructure failures still throw. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: DomainError) : Outcome<Nothing>

    fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}
