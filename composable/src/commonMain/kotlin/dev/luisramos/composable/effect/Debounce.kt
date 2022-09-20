package dev.luisramos.composable.effect

import dev.luisramos.composable.Effect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration

public fun <Output> Effect<Output>.debounce(id: Any, dueTime: Duration): Effect<Output> =
    flowOf(Unit)
        .onEach { delay(dueTime) }
        .flatMapConcat { this@debounce }
        .cancellable(id, cancelInFlight = true)
