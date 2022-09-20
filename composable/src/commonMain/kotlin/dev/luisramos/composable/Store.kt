package dev.luisramos.composable

import dev.luisramos.composable.reducer.ReducerResult
import dev.luisramos.composable.reducer.withNoEffect
import dev.luisramos.composable.utils.Closeable
import dev.luisramos.composable.utils.WeakReference
import dev.luisramos.composable.utils.isMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.coroutines.CoroutineContext

public class Store<State, Action>(
    initialState: State,
    private val reducer: (State, Action) -> ReducerResult<State, Action>,
    coroutineContext: CoroutineContext = Dispatchers.Main,
    private val mainThreadChecksEnabled: Boolean = true
) {
    private val job = Job()
    private val coroutineScope = CoroutineScope(coroutineContext + job)
    private var bufferedActions = arrayListOf<Action>()
    private var scope: Scope? = null
    private var isSending = false
    private val _state = MutableStateFlow(initialState)
    public val state: StateFlow<State> = _state.asStateFlow()

    /**
     *  Useful in K/N a context, so we get access to the correct type
     */
    public val stateValue: State = _state.value

    init {
        threadCheck(ThreadCheckStatus.Init)
    }

    /**
     * This method allows to access the state flow in K/N land, without
     * losing types. For example, a Store<CounterState, CounterAction>
     * will have the stateNative.onEach be of type (CounterState) -> Void
     *
     * Without this, onEach would be of type (AnyObject) -> Void, and we
     * would need to type cast.
     *
     * Some Swift glue is needed to take this closeable
     */
    public fun stateNative(
        onEach: (State) -> Unit,
        onCompletion: (cause: Throwable?) -> Unit
    ): Closeable {
        val job = Job()
        var cause: Throwable? = null
        state
            .distinctUntilChanged { old, new -> old == new }
            .onEach { onEach(it) }
            .catch { cause = it }
            .onCompletion { onCompletion(cause) }
            .launchIn(coroutineScope + job)
        return Closeable { job.cancel() }
    }

    public fun send(action: Action, originatingAction: Action? = null) {
        threadCheck(ThreadCheckStatus.Send(action, originatingAction))

        bufferedActions.add(action)
        if (isSending) return

        isSending = true
        var currentState = state.value
        try {
            while (bufferedActions.isNotEmpty()) {
                val bufferedAction = bufferedActions.removeFirst()
                val (newState, effect) = reducer(currentState, bufferedAction)
                currentState = newState

                coroutineScope.launch {
                    effect
                        .onEach { effectAction -> send(effectAction, originatingAction = action) }
                        .onCompletion {
                            threadCheck(ThreadCheckStatus.EffectCompletion(action))
                        }
                        .collect()
                }
            }
        } finally {
            isSending = false
            _state.update { currentState }
        }
    }

    public fun <LocalState, LocalAction> scope(
        toLocalState: (State) -> LocalState,
        fromLocalAction: (LocalAction) -> Action
    ): Store<LocalState, LocalAction> {
        threadCheck(ThreadCheckStatus.Scope)
        return (scope ?: ScopeImpl.createScope(this))
            .rescope(this, toChildState = toLocalState, fromChildAction = fromLocalAction)
    }

    public fun <LocalState> scope(
        toLocalState: (State) -> LocalState,
    ): Store<LocalState, Action> = scope(toLocalState = toLocalState, fromLocalAction = { it })

    private sealed class ThreadCheckStatus<out Action> {
        data class EffectCompletion<Action>(val action: Action) : ThreadCheckStatus<Action>()
        object Init : ThreadCheckStatus<Nothing>()
        object Scope : ThreadCheckStatus<Nothing>()
        data class Send<Action>(val action: Action, val originatingAction: Action?) :
            ThreadCheckStatus<Action>()
    }

    private fun threadCheck(status: ThreadCheckStatus<Action>) {
        if (!mainThreadChecksEnabled) {
            return
        }
        check(isMainThread()) {
            when (status) {
                is ThreadCheckStatus.EffectCompletion -> """
An effect completed on a non-main thread.

Effect returned from:
${status.action}

Make sure to use ".receive(on:)" on any effects that execute on background threads to
receive their output on the main thread, or create your store via "Store.unchecked" to
opt out of the main thread checker.

The "Store" class is not thread-safe, and so all interactions with an instance of
"Store" (including all of its scopes and derived view stores) must be done on the same
thread.
                """.trimIndent()
                ThreadCheckStatus.Init -> """
A store initialized on a non-main thread.

The "Store" class is not thread-safe, and so all interactions with an instance of
"Store" (including all of its scopes and derived view stores) must be done on the same
thread.
                """.trimIndent()
                ThreadCheckStatus.Scope -> """
"Store.scope" was called on a non-main thread. …

Make sure to use "Store.scope" on the main thread, or create your store via
"Store.unchecked" to opt out of the main thread checker.

The "Store" class is not thread-safe, and so all interactions with an instance of
"Store" (including all of its scopes and derived view stores) must be done on the same
thread.
                """.trimIndent()
                is ThreadCheckStatus.Send -> """
"ViewStore.send" was called on a non-main thread with: $status

Make sure that "ViewStore.send" is always called on the main thread.

The "Store" class is not thread-safe, and so all interactions with an instance of
"Store" (including all of its scopes and derived view stores) must be done on the same
thread.
                """.trimIndent()
            }
        }
    }

    private interface Scope {
        fun <State, Action, ChildState, ChildAction> rescope(
            store: Store<State, Action>,
            toChildState: (State) -> ChildState,
            fromChildAction: (ChildAction) -> Action
        ): Store<ChildState, ChildAction>
    }

    @Suppress("UNCHECKED_CAST")
    private class ScopeImpl<RootState, RootAction>(
        private val root: Store<RootState, RootAction>,
        private val toChildState: Any,
        private val fromChildAction: Any
    ) : Scope {

        companion object {
            fun <State, Action, RootState, RootAction> createScope(
                root: Store<RootState, RootAction>,
                toChildState: (RootState) -> State,
                fromChildAction: (Action) -> RootAction
            ) = ScopeImpl(root, toChildState, fromChildAction)

            fun <RootState, RootAction> createScope(
                root: Store<RootState, RootAction>
            ) = createScope<RootState, RootAction, RootState, RootAction>(root, { it }, { it })
        }

        override fun <State, Action, ChildState, ChildAction> rescope(
            store: Store<State, Action>,
            toChildState: (State) -> ChildState,
            fromChildAction: (ChildAction) -> Action
        ): Store<ChildState, ChildAction> {
            val toState = this.toChildState as (RootState) -> State
            val fromAction = this.fromChildAction as (Action) -> RootAction

            var isSending = false
            val childStore = Store(
                initialState = toChildState(store.state.value),
                reducer = { _: ChildState, childAction: ChildAction ->
                    try {
                        isSending = true
                        root.send(fromAction(fromChildAction(childAction)))
                        val childState = toChildState(store.state.value)
                        childState.withNoEffect()
                    } finally {
                        isSending = false
                    }
                },
                coroutineContext = store.coroutineScope.coroutineContext,
                mainThreadChecksEnabled = store.mainThreadChecksEnabled
            )
            val childStoreRef = WeakReference(childStore)
            childStore.coroutineScope.launch {
                store.state
                    .collect { newState ->
                        if (isSending) return@collect
                        childStoreRef.value?._state?.update { toChildState(newState) }
                    }
            }
            childStore.scope = createScope<ChildState, ChildAction, RootState, RootAction>(
                root = root,
                toChildState = { toChildState(toState(it)) },
                fromChildAction = { fromAction(fromChildAction(it)) }
            )
            return childStore
        }
    }
}

/**
 * Binding to create a store using a [ReducerProtocol]
 */
@Suppress("FunctionName")
public fun <State, Action> Store(
    initialState: State,
    reducer: ReducerProtocol<State, Action>,
    coroutineContext: CoroutineContext = Dispatchers.Main,
    mainThreadChecksEnabled: Boolean = true
): Store<State, Action> = Store(
    initialState = initialState,
    reducer = reducer::reduce,
    coroutineContext = coroutineContext,
    mainThreadChecksEnabled = mainThreadChecksEnabled
)
