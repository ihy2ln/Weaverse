package tachiyomi.core.common.util.lang

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Observable<T>.awaitSingle(): T = suspendCancellableCoroutine { continuation ->
    var seen = false
    var value: T? = null
    val subscription = subscribe(
        { next -> if (seen) continuation.resumeWithException(IllegalArgumentException("More than one element")) else { seen = true; value = next } },
        { error -> if (continuation.isActive) continuation.resumeWithException(error) },
        {
            if (!continuation.isActive) return@subscribe
            if (!seen) continuation.resumeWithException(NoSuchElementException("No elements"))
            else @Suppress("UNCHECKED_CAST") continuation.resume(value as T)
        },
    )
    continuation.invokeOnCancellation { subscription.unsubscribe() }
}
