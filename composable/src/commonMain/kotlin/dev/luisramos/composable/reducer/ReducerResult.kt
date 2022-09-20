package dev.luisramos.composable.reducer

import dev.luisramos.composable.Effect
import dev.luisramos.composable.effect
import dev.luisramos.composable.effectOf
import dev.luisramos.composable.emptyEffect
import kotlinx.coroutines.flow.FlowCollector

public data class ReducerResult<out State, Action>(
    val state: State,
    val effect: Effect<Action>
)

public fun <State, Action> State.withEffect(block: suspend FlowCollector<Action>.() -> Unit): ReducerResult<State, Action> =
    ReducerResult(this, effect(block))

public fun <State, Action> State.withEffectOf(vararg elements: Action): ReducerResult<State, Action> =
    ReducerResult(this, effectOf(*elements))

public fun <State, Action> State.withEffectOf(action: Action): ReducerResult<State, Action> =
    ReducerResult(this, effectOf(action))

public fun <State, Action> State.withNoEffect(): ReducerResult<State, Action> =
    ReducerResult(this, emptyEffect())

public infix fun <State, Action> State.withEffect(effect: Effect<Action>): ReducerResult<State, Action> =
    ReducerResult(this, effect)