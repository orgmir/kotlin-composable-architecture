package dev.luisramos.composable.effect

import dev.luisramos.composable.Effect
import dev.luisramos.composable.concatenate
import dev.luisramos.composable.fireAndForget
import dev.luisramos.composable.merge
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Makes the flow cancellable via the passed in id
 *
 * ATTENTION: Make sure to call on the original hot flow, before any mapping happens
 */
public fun <Output> Effect<Output>.cancellable(
    id: Any,
    cancelInFlight: Boolean = false
): Effect<Output> {
    val effect = channelFlow {
        mutex.withReentrantLock {
            lateinit var job: Job
            lateinit var cancellable: Cancellable
            cancellable = Cancellable {
                mutex.withReentrantLock {
                    job.cancel()
                    cancellationCancellables[id]?.remove(cancellable)
                    if (cancellationCancellables[id]?.isEmpty() == true) {
                        cancellationCancellables.remove(id)
                    }
                }
            }

            if (cancellationCancellables[id] == null) {
                cancellationCancellables[id] = mutableSetOf()
            }
            cancellationCancellables[id]?.add(cancellable)

            job = launch {
                this@cancellable
                    .onEach { send(it) }
                    .onCompletion { cancellable.cancel() }
                    .collect()
            }
        }
    }

    return if (cancelInFlight) concatenate(cancelEffect(id), effect) else effect
}

public fun <T> cancelEffect(id: Any): Effect<T> = fireAndForget {
    mutex.withReentrantLock {
        // Create copy so we don't have ConcurrentModificationException
        val map = cancellationCancellables[id]?.toMutableSet()
        map?.forEach { it.cancel() }
    }
}

public fun <T> cancelEffects(vararg ids: Any): Effect<T> =
    merge(*ids.map { cancelEffect<T>(it) }.toTypedArray())

public interface Cancellable {
    public suspend fun cancel()

    public companion object {
        public operator fun invoke(block: suspend () -> Unit): Cancellable = object : Cancellable {
            override suspend fun cancel() {
                block()
            }
        }
    }
}

internal var cancellationCancellables: MutableMap<Any, MutableSet<Cancellable>> = mutableMapOf()
internal val mutex: Mutex = Mutex()

// Taken from
// https://gist.github.com/elizarov/9a48b9709ffd508909d34fab6786acfe
internal suspend fun <T> Mutex.withReentrantLock(block: suspend () -> T): T {
    val key = ReentrantMutexContextKey(this)
    // call block directly when this mutex is already locked in the context
    if (coroutineContext[key] != null) return block()
    // otherwise add it to the context and lock the mutex
    return withContext(ReentrantMutexContextElement(key)) {
        withLock { block() }
    }
}

internal class ReentrantMutexContextElement(
    override val key: ReentrantMutexContextKey
) : CoroutineContext.Element

internal data class ReentrantMutexContextKey(
    val mutex: Mutex
) : CoroutineContext.Key<ReentrantMutexContextElement>
