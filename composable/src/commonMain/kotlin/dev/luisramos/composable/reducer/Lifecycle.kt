package dev.luisramos.composable.reducer

import dev.luisramos.composable.Effect
import dev.luisramos.composable.ReducerProtocol
import dev.luisramos.composable.fireAndForget
import dev.luisramos.composable.noEffect
import kotlinx.coroutines.flow.map

public interface LifecycleReducer<WrappedState, WrappedAction> :
    ReducerProtocol<WrappedState?, LifecycleReducer.Action<WrappedAction>> {
    public sealed class Action<out Wrapped> {
        public object OnAppear : Action<Nothing>()
        public object OnDisappear : Action<Nothing>()
        public data class Wrapped<Wrapped>(val action: Wrapped) : Action<Wrapped>()
    }
}

internal class LifecycleReducerIml<WrappedState, WrappedAction>(
    private val wrapped: ReducerProtocol<WrappedState & Any, WrappedAction>,
    private val onAppear: () -> Effect<WrappedAction>,
    private val onDisappear: () -> Effect<Unit>
) : LifecycleReducer<WrappedState, WrappedAction> {

    override val body: ReducerProtocol<WrappedState?, LifecycleReducer.Action<WrappedAction>> =
        Reducer<WrappedState?, LifecycleReducer.Action<WrappedAction>> { state, action ->
            when (action) {
                LifecycleReducer.Action.OnAppear ->
                    state withEffect onAppear().map { LifecycleReducer.Action.Wrapped(it) }

                LifecycleReducer.Action.OnDisappear ->
                    state withEffect onDisappear().fireAndForget()

                is LifecycleReducer.Action.Wrapped<WrappedAction> ->
                    state.withNoEffect()
            }
        }.optional(
            toChildState = { it },
            fromChildState = { _, child -> child },
            toChildAction = { (it as? LifecycleReducer.Action.Wrapped<WrappedAction>)?.action },
            fromChildAction = { _, child -> LifecycleReducer.Action.Wrapped(child) }
        ) {
            wrapped
        }
}

public fun <State, Action> ReducerProtocol<State & Any, Action>.lifecycle(
    onAppear: Effect<Action>,
    onDisappear: Effect<Unit> = noEffect()
): LifecycleReducer<State, Action> =
    LifecycleReducerIml(wrapped = this, onAppear = { onAppear }, onDisappear = { onDisappear })

public fun <State, Action> ReducerProtocol<State & Any, Action>.lifecycle(
    onAppear: () -> Effect<Action>,
    onDisappear: () -> Effect<Unit> = { noEffect() }
): LifecycleReducer<State, Action> =
    LifecycleReducerIml(wrapped = this, onAppear = onAppear, onDisappear = onDisappear)
