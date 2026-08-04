package com.fastshare.app.core.util

/** Lightweight domain result wrapper that keeps failures explicit instead of throwing across layers. */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: FastShareError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): FastShareError? = (this as? Failure)?.error
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (FastShareError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

fun <T> T.asSuccess(): Outcome<T> = Outcome.Success(this)

fun FastShareError.asFailure(): Outcome<Nothing> = Outcome.Failure(this)

/** Exhaustive taxonomy of recoverable failures surfaced to the UI layer. */
sealed class FastShareError(open val message: String, open val cause: Throwable? = null) {
    data class NoNetwork(override val message: String = "No local network available") : FastShareError(message)
    data class DiscoveryFailed(override val message: String, override val cause: Throwable? = null) : FastShareError(message, cause)
    data class HandshakeRejected(val reason: String) : FastShareError("Handshake rejected: $reason")
    data class HandshakeFailed(override val message: String, override val cause: Throwable? = null) : FastShareError(message, cause)
    data class ProtocolMismatch(val remoteVersion: Int, val localVersion: Int) :
        FastShareError("Incompatible protocol: remote=$remoteVersion local=$localVersion")
    data class TransferFailed(val transferId: String, override val message: String, override val cause: Throwable? = null) :
        FastShareError(message, cause)
    data class ChecksumMismatch(val fileName: String) : FastShareError("Integrity check failed for $fileName")
    data class StorageUnavailable(override val message: String) : FastShareError(message)
    data class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long) :
        FastShareError("Not enough space: need $requiredBytes bytes, have $availableBytes")
    data class PermissionDenied(val permission: String) : FastShareError("Permission denied: $permission")
    data class Cancelled(override val message: String = "Cancelled") : FastShareError(message)
    data class Unknown(override val message: String, override val cause: Throwable? = null) : FastShareError(message, cause)
}
