@file:Suppress("FunctionName")

package dev.luisramos.composable.reducer

import dev.luisramos.composable.CasePath
import dev.luisramos.composable.KeyPath
import dev.luisramos.composable.ReducerProtocol
import kotlinx.coroutines.flow.map

public fun <State, Action, ChildState, ChildAction> Scope(
    toChildState: (State) -> ChildState,
    fromChildState: (State, ChildState) -> State,
    toChildAction: (Action) -> ChildAction?,
    fromChildAction: (ChildAction) -> Action,
    block: () -> ReducerProtocol<ChildState, ChildAction>
): ReducerProtocol<State, Action> {
    val child = block()
    return Reducer { state, action ->
        val childAction = toChildAction(action) ?: return@Reducer state.withNoEffect()
        val childState = toChildState(state)
        val (newChildState, newChildEffect) = child.reduce(childState, childAction)
        fromChildState(state, newChildState).withEffect(
            newChildEffect.map { fromChildAction(it) }
        )
    }
}

public fun <State, Action, ChildState, ChildAction> Scope(
    state: KeyPath<State, ChildState>,
    action: CasePath<Action, ChildAction>,
    block: () -> ReducerProtocol<ChildState, ChildAction>
): ReducerProtocol<State, Action> = Scope(
    toChildState = state::extract,
    fromChildState = state::embed,
    toChildAction = action::extract,
    fromChildAction = action::embed,
    block = block
)

public fun <State, Action, ChildState, ChildAction> OptionalScope(
    toChildState: (State) -> ChildState?,
    fromChildState: (State, ChildState) -> State,
    toChildAction: (Action) -> ChildAction?,
    fromChildAction: (ChildAction) -> Action,
    block: () -> ReducerProtocol<ChildState & Any, ChildAction>
): ReducerProtocol<State, Action> {
    val child = block()
    return Reducer { state, action ->
        val childAction = toChildAction(action) ?: return@Reducer state.withNoEffect()
        val childState = toChildState(state) ?: return@Reducer state.withNoEffect()
        val (newChildState, newChildEffect) = child.reduce(childState, childAction)
        fromChildState(state, newChildState).withEffect(
            newChildEffect.map { fromChildAction(it) }
        )
    }
}

public fun <State, Action, ChildState, ChildAction> OptionalScope(
    state: KeyPath<State, ChildState>,
    action: CasePath<Action, ChildAction>,
    block: () -> ReducerProtocol<ChildState & Any, ChildAction>
): ReducerProtocol<State, Action> = OptionalScope(
    toChildState = state::extract,
    fromChildState = state::embed,
    toChildAction = action::extract,
    fromChildAction = action::embed,
    block = block
)