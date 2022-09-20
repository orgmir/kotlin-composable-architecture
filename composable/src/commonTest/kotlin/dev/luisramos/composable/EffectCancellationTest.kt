package dev.luisramos.composable

import app.cash.turbine.test
import dev.luisramos.composable.effect.cancelEffect
import dev.luisramos.composable.effect.cancellable
import dev.luisramos.composable.effect.cancellationCancellables
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class EffectCancellationTest {
    object CancelToken

    @BeforeTest
    fun setUp() {
        cancellationCancellables.clear()
    }

    @Test
    fun testCancellation() = runTest {
        val flow = MutableStateFlow(1)

        flow.cancellable(CancelToken)
            .test {
                assertEquals(1, awaitItem())
                flow.value = 2
                assertEquals(2, awaitItem())

                cancelEffect<Unit>(CancelToken)
                    .collect()

                flow.value = 3
                awaitComplete()
            }
    }

    @Test
    fun testCancelInFlight() = runTest {
        val flow = MutableStateFlow(1)

        flow.cancellable(CancelToken, cancelInFlight = true)
            .test {
                assertEquals(1, awaitItem())
                flow.value = 2
                assertEquals(2, awaitItem())

                flow.cancellable(CancelToken, cancelInFlight = true)
                    .test test2@{
                        this@test.awaitComplete()
                        assertEquals(2, awaitItem())
                        flow.value = 3
                        assertEquals(3, awaitItem())
                    }
            }
    }

    @Test
    fun testCancelInFlightAfterMapping() = runTest {
        val flow = flowOf(1)

        flow.cancellable(CancelToken, cancelInFlight = true)
            .map { it + it }
            .test {
                assertEquals(2, awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun testCancellationAfterDelay() = runTest {
        effectOf(1)
            .onEach { delay(100) }
            .cancellable(CancelToken)
            .test {
                advanceTimeBy(50)
                cancelEffect<Unit>(CancelToken)
                    .collect()

                awaitComplete()
            }
    }

    @Test
    fun testCleanupOnCompletion() = runTest {
        effectOf(1)
            .cancellable(1)
            .collect()

        assertTrue(cancellationCancellables.isEmpty())
    }

    @Test
    fun testCleanupOnCancel() = runTest {
        effectOf(1)
            .onEach { delay(100) }
            .cancellable(1)
            .collect()

        cancelEffect<Unit>(1)
            .collect()

        assertTrue(cancellationCancellables.isEmpty())
    }

    @Test
    fun testDoubleCancellation() = runTest {
        val flow = MutableStateFlow(1)

        flow.cancellable(CancelToken)
            .cancellable(CancelToken)
            .test {
                assertEquals(1, awaitItem())
                flow.value = 2
                assertEquals(2, awaitItem())

                cancelEffect<Unit>(CancelToken)
                    .collect()

                awaitComplete()
            }
    }

    @Test
    fun testCompleteBeforeCancellation() = runTest {
        effectOf(1)
            .cancellable(CancelToken)
            .test {
                assertEquals(1, awaitItem())
                awaitComplete()

                cancelEffect<Unit>(CancelToken)
                    .collect()

                assertTrue(cancellationCancellables.isEmpty())
            }
    }

    @Ignore
    @Test
    fun testConcurrentCancels() = runTest {
        val dispatchers = listOf(
            Dispatchers.Default,
            Dispatchers.Unconfined
        )

        val effect = merge(
            *(0 until 1000).map { idx ->
                val id = idx % 10
                merge(
                    effectOf(idx)
                        .onEach { withContext(dispatchers.random()) { delay((1 until 100).random().milliseconds) } }
                        .cancellable(id),
                    effectOf(1)
                        .onEach { withContext(dispatchers.random()) { delay((1 until 100).random().milliseconds) } }
                        .flatMapConcat { cancelEffect<Int>(id) }
                )
            }.toTypedArray()
        )

        effect.collect()

        assertTrue(cancellationCancellables.isEmpty())
    }
}
