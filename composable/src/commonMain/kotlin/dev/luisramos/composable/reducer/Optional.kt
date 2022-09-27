package dev.luisramos.composable.reducer

import dev.luisramos.composable.CasePath
import dev.luisramos.composable.KeyPath
import dev.luisramos.composable.ReducerProtocol
import dev.luisramos.composable.merge
import kotlinx.coroutines.flow.map

public fun <State, Action, ChildState, ChildAction> ReducerProtocol<State, Action>.optional(
    toChildState: (State) -> ChildState?,
    fromChildState: (State, ChildState) -> State,
    toChildAction: (Action) -> ChildAction?,
    fromChildAction: (ChildAction) -> Action,
    block: () -> ReducerProtocol<ChildState & Any, ChildAction>
): ReducerProtocol<State, Action> {
    val child = block()
    return object : ReducerProtocol<State, Action> {
        override fun reduce(state: State, action: Action): ReducerResult<State, Action> {
            val childAction = toChildAction(action)
                ?: return this@optional.reduce(state, action)

            val childState = toChildState(state) ?: throw IllegalStateException(
                "An optional() received a child action when child state was null."
            )

            val (newChildState, newChildEffect) = child.reduce(childState, childAction)
            val (newState, newEffect) = this@optional.reduce(
                fromChildState(state, newChildState),
                action
            )
            return newState.withEffect(
                merge(
                    newChildEffect.map { fromChildAction(it) },
                    newEffect
                )
            )
        }
    }
}

public fun <State, Action, ChildState, ChildAction> ReducerProtocol<State, Action>.optional(
    state: KeyPath<State, ChildState>,
    action: CasePath<Action, ChildAction>,
    block: () -> ReducerProtocol<ChildState & Any, ChildAction>
): ReducerProtocol<State, Action> = optional(
    toChildState = state::extract,
    fromChildState = state::embed,
    toChildAction = action::extract,
    fromChildAction = action::embed,
    block = block
)