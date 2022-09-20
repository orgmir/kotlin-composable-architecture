package dev.luisramos.composable

import dev.luisramos.composable.effect.debounce
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class EffectDebounceTest {

    @Test
    fun testDebounce() = runTest {
        val values = arrayListOf<Int>()

        suspend fun runDebouncedEffect(value: Int) {
            launch {
                effectOf(value)
                    .debounce("cancel token", 1.seconds)
                    .onEach { values += it }
                    .collect()
            }
        }

        runDebouncedEffect(1)

        assertEquals(arrayListOf(), values)

        advanceTimeBy(500)
        assertEquals(arrayListOf(), values)

        runDebouncedEffect(2)

        advanceTimeBy(500)
        assertEquals(arrayListOf(), values)

        runDebouncedEffect(3)

        advanceTimeBy(500)
        assertEquals(arrayListOf(), values)

        advanceTimeBy(501)
        assertEquals(arrayListOf(3), values)

        advanceUntilIdle()
        assertEquals(arrayListOf(3), values)
    }

    @Test
    fun testDebounceLazy() = runTest {
        val values = arrayListOf<Int>()
        var effectRuns = 0

        suspend fun runDebounceEffect(value: Int) {
            launch {
                effect<Int> {
                    effectRuns += 1
                    emit(value)
                }
                    .debounce("cancel token", 1.seconds)
                    .onEach { values += it }
                    .collect()
            }
        }

        runDebounceEffect(1)

        assertEquals(arrayListOf(), values)
        assertEquals(0, effectRuns)

        advanceTimeBy(500)

        assertEquals(arrayListOf(), values)
        assertEquals(0, effectRuns)

        advanceTimeBy(501)

        assertEquals(arrayListOf(1), values)
        assertEquals(1, effectRuns)
    }
}
