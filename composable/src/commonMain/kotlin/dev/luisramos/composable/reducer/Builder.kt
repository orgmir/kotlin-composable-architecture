@file:Suppress("FunctionName")

package dev.luisramos.composable.reducer

import dev.luisramos.composable.Effect
import dev.luisramos.composable.ReducerProtocol
import dev.luisramos.composable.concatenate

@DslMarker
public annotation class ReducerBuilderMarker

/**
 * Builds a reducer by combining all reducers added to it
 */
@ReducerBuilderMarker
public class ReducerBuilder<State, Action> {

    private var reducers = emptyArray<ReducerProtocol<State, Action>>()

    public fun toReducerProtocol(): ReducerProtocol<State, Action> {
        check(reducers.isNotEmpty()) { "ReducerBuilder called without any reducers added. Did you forget a +?" }
        return Reducer { state, action ->
            var effects = emptyArray<Effect<Action>>()
            var currState = state
            reducers.forEach {
                val (newState, effect) = it.reduce(currState, action)
                currState = newState
                effects += effect
            }
            currState.withEffect(concatenate(*effects))
        }
    }

    /**
     * Adds a [ReducerProtocol] to the builder
     */
    public operator fun ReducerProtocol<State, Action>.unaryPlus() {
        reducers += this
    }
}

public inline fun <State, Action> buildReducer(
    crossinline block: ReducerBuilder<State, Action>.() -> Unit
): ReducerProtocol<State, Action> =
    ReducerBuilder<State, Action>().apply(block).toReducerProtocol()

public fun <State, Action> Reducer(
    block: (state: State, action: Action) -> ReducerResult<State, Action>
): ReducerProtocol<State, Action> = reducerOf(block)

public fun <State, Action> reducerOf(
    block: (state: State, action: Action) -> ReducerResult<State, Action>
): ReducerProtocol<State, Action> = object : ReducerProtocol<State, Action> {
    override fun reduce(state: State, action: Action): ReducerResult<State, Action> =
        block(state, action)
}