package dev.luisramos.composable

import dev.luisramos.composable.reducer.OptionalScope
import dev.luisramos.composable.reducer.Reducer
import dev.luisramos.composable.reducer.ReducerResult
import dev.luisramos.composable.reducer.Scope
import dev.luisramos.composable.reducer.buildReducer
import dev.luisramos.composable.reducer.withEffect
import dev.luisramos.composable.reducer.withNoEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StoreTest {

    private class Counter : ReducerProtocol<Counter.State, Counter.Action> {
        data class State(
            val counter: Int
        )

        sealed class Action {
            object Increment : Action()
            object Decrement : Action()
        }

        override fun reduce(state: State, action: Action): ReducerResult<State, Action> =
            when (action) {
                Action.Decrement ->
                    state.copy(counter = state.counter - 1)
                        .withNoEffect()
                Action.Increment ->
                    state.copy(counter = state.counter + 1)
                        .withNoEffect()
            }
    }

    @Test
    fun storeShouldSendActions() = runTest {
        val store = Store(
            initialState = Counter.State(0),
            reducer = Counter(),
            coroutineContext = coroutineContext,
            mainThreadChecksEnabled = false
        )

        store.send(Counter.Action.Increment)

        assertEquals(1, store.state.value.counter)
    }

    @Test
    fun effectsShouldBeTriggered() = runTest {
        val delayedReducer = Reducer { state: Counter.State, action: Counter.Action ->
            when (action) {
                Counter.Action.Increment ->
                    state.copy(counter = state.counter + 1)
                        .withEffect {
                            delay(100)
                            emit(Counter.Action.Decrement)
                        }
                Counter.Action.Decrement ->
                    state.copy(counter = state.counter - 1)
                        .withNoEffect()
            }
        }

        val store = Store(Counter.State(0), delayedReducer::reduce, coroutineContext, false)

        store.send(Counter.Action.Increment)
        assertEquals(1, store.state.value.counter)

        // test if side-effect is run
        advanceUntilIdle()
        assertEquals(0, store.state.value.counter)
    }

    @Test
    fun higherOrderFunctionsCanAccessPreviousStateAfterMutation() = runTest {
        fun <State, Action> peek(
            reducer: (State, Action) -> ReducerResult<State, Action>,
        ): (State, Action) -> ReducerResult<State, Action> = { state, action ->
            val (newState, effect) = reducer.invoke(state, action)

            assertNotEquals(state, newState)

            newState withEffect effect
        }

        val store = Store(Counter.State(0), peek(Counter()::reduce), coroutineContext, false)

        store.send(Counter.Action.Increment)

        assertEquals(1, store.state.value.counter)
    }

    private class AppFeature : ReducerProtocol<AppFeature.State, AppFeature.Action> {
        var isTest: Boolean = true

        data class State(
            val counter: Counter.State,
            val optionalCounter: Counter.State? = null,
            val localCounter: Int = 0
        ) {
            companion object {
                val counter = KeyPath(
                    embed = { parent, child -> parent.copy(counter = child) },
                    extract = State::counter
                )

                val optionalCounter = KeyPath(
                    embed = { parent, child -> parent.copy(optionalCounter = child) },
                    extract = State::optionalCounter
                )
            }
        }

        sealed class Action {
            data class Counter(val action: StoreTest.Counter.Action) : Action()

            object SetOptionalState : Action()

            companion object {
                val counter =
                    CasePath<Action, Counter, StoreTest.Counter.Action>(::Counter, Counter::action)
            }
        }

        override val body: ReducerProtocol<State, Action> = buildReducer {
            +Scope(State.counter, Action.counter) {
                Counter()
            }
            +OptionalScope(State.optionalCounter, Action.counter) {
                Counter()
            }
            +Reducer<State, Action> { state, action ->
                when (action) {
                    is Action.Counter -> state.withNoEffect()
                    is Action.SetOptionalState -> state.copy(optionalCounter = Counter.State(0))
                        .withNoEffect()
                }
            }
        }
    }

    @Test
    fun optionalScopeShouldNotReceiveActionsUntilItIsNonOptional() = runTest {
        val store = Store(AppFeature.State(Counter.State(0)), AppFeature(), coroutineContext, false)

        store.send(AppFeature.Action.Counter(Counter.Action.Increment))

        assertEquals(1, store.state.value.counter.counter)

        store.send(AppFeature.Action.SetOptionalState)
        store.send(AppFeature.Action.Counter(Counter.Action.Decrement))

        assertEquals(0, store.state.value.counter.counter)
        assertEquals(-1, store.state.value.optionalCounter?.counter)
    }

    @Test
    fun scopeShouldTransformReducerToLocalState() = runTest {
        val store = Store(AppFeature.State(Counter.State(0)), AppFeature(), coroutineContext, false)

        store.send(AppFeature.Action.Counter(Counter.Action.Increment))

        assertEquals(1, store.state.value.counter.counter)
    }

    @Test
    fun scopeShouldPropagateStateChangesFromParentToChild() = runTest {
        val store = Store(AppFeature.State(Counter.State(0)), AppFeature(), coroutineContext, false)
        val childStore = store.scope<Counter.State, Counter.Action>(
            toLocalState = { it.counter },
            fromLocalAction = { AppFeature.Action.Counter(it) }
        )

        store.send(AppFeature.Action.Counter(Counter.Action.Increment))

        assertEquals(1, store.state.value.counter.counter)

        // Child store launches a coroutine, we need to process it
        // before doing any assertions
        advanceUntilIdle()
        assertEquals(1, childStore.state.value.counter)
    }

    @Test
    fun scopeShouldPropagateStateChangesFromChildToParent() = runTest {
        val store = Store(AppFeature.State(Counter.State(0)), AppFeature(), coroutineContext, false)
        val childStore = store.scope<Counter.State, Counter.Action>(
            toLocalState = { it.counter },
            fromLocalAction = { AppFeature.Action.Counter(it) }
        )

        childStore.send(Counter.Action.Decrement)

        assertEquals(-1, store.state.value.counter.counter)
        assertEquals(-1, childStore.state.value.counter)
    }

    enum class Action {
        Tap, Next1, Next2, End
    }

    @Test
    fun testSynchronousEffectsSentAfterCollecting() = runTest {
        val values = arrayListOf<Int>()
        val counterReducer = Reducer { _: Unit, action: Action ->
            when (action) {
                Action.Tap -> Unit withEffect concatenate(
                    effectOf(Action.Next1),
                    effectOf(Action.Next2),
                    fireAndForget { values += 1 }
                )
                Action.Next1 -> Unit withEffect concatenate(
                    effectOf(Action.End),
                    fireAndForget { values += 2 }
                )
                Action.Next2 -> Unit withEffect fireAndForget { values += 3 }
                Action.End -> Unit withEffect fireAndForget { values += 4 }
            }
        }

        val store = Store(
            initialState = Unit,
            reducer = counterReducer,
            coroutineContext = coroutineContext,
            mainThreadChecksEnabled = false
        )

        store.send(Action.Tap)

        advanceUntilIdle()
        assertEquals(arrayListOf(1, 2, 3, 4), values)
    }

    enum class CountStopAction {
        Incr, Stop
    }

    @Test
    fun testLotsOfSynchronousActions() = runTest {
        val reducer = Reducer { state: Int, action: CountStopAction ->
            when (action) {
                CountStopAction.Incr -> (state + 1) withEffect
                        effectOf(if (state >= 99999) CountStopAction.Stop else CountStopAction.Incr)
                CountStopAction.Stop -> state.withNoEffect()
            }
        }

        val store = Store(0, reducer, coroutineContext, false)
        store.send(CountStopAction.Incr)
        advanceUntilIdle()
        assertEquals(100000, store.state.value)
    }

    @Test
    fun testScopingWorksFine() = runTest {
        val store = Store(Counter.State(0), Counter(), coroutineContext, false)
        // scope 100 times
        var childStore = store
        repeat(100) { childStore = childStore.scope { it } }
        childStore.send(Counter.Action.Increment)
        assertEquals(1, store.state.value.counter)
    }
}
