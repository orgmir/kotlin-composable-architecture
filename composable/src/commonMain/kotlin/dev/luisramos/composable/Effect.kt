package dev.luisramos.composable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Effect is a Flow, the type alias allows for us decent semantics at call site
 * keeping all the flow super powers
 */
public typealias Effect<T> = Flow<T>

public fun <Output> effectOf(vararg elements: Output): Effect<Output> = flowOf(*elements)

public fun <Output> effectOf(value: Output): Effect<Output> = flowOf(value)

public fun <Output> effect(block: suspend FlowCollector<Output>.() -> Unit): Effect<Output> =
    flow(block)

public fun <Output> emptyEffect(): Effect<Output> = emptyFlow()

public fun <Output> noEffect(): Effect<Output> = emptyEffect()

public fun <Output> concatenate(vararg effects: Effect<Output>): Effect<Output> {
    if (effects.isEmpty()) return noEffect()
    return flowOf(*effects).flattenConcat()
}

public fun <Output> merge(vararg effects: Effect<Output>): Effect<Output> {
    if (effects.isEmpty()) return noEffect()
    return flowOf(*effects).flattenMerge()
}

/**
 * Does some computation and emits an empty flow.
 */
public fun <Output> fireAndForget(block: suspend () -> Unit): Effect<Output> = effect { block() }

/**
 * Allows us to fire an effect and cast it to Effect type
 */
public fun <Output, NewOutput> Flow<Output>.fireAndForget(): Effect<NewOutput> =
    flatMapConcat { emptyFlow() }
