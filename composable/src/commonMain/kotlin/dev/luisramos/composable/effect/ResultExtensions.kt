package dev.luisramos.composable.effect

import dev.luisramos.composable.Effect
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

public fun <T> Effect<Result<T>>.onEachSuccess(block: suspend (T) -> Unit): Effect<Result<T>> =
    map {
        val value = it.getOrNull()
        if (value != null) {
            block(value)
        }
        it
    }

public fun <T, R> Effect<Result<T>>.mapOnSuccess(transform: (value: T) -> R): Effect<Result<R>> =
    map { it.map(transform) }

public fun <T, R> Effect<Result<T>>.flatMapOnSuccess(transform: (value: T) -> Effect<Result<R>>): Effect<Result<R>> =
    flatMapConcat { result ->
        val value = result.getOrElse { return@flatMapConcat flowOf(Result.failure(it)) }
        transform(value)
    }
