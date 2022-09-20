package dev.luisramos.composable.test

import dev.luisramos.composable.ReducerProtocol
import dev.luisramos.composable.reducer.ReducerResult
import dev.luisramos.composable.Store
import dev.luisramos.composable.reducer.withEffect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.fail

public class TestStore<State, LocalState, Action, LocalAction>(
    initialState: State,
    private val reducer: (State, Action) -> ReducerResult<State, Action>,
    private val fromLocalAction: (LocalAction) -> Action,
    private val toLocalState: (State) -> LocalState,
    coroutineContext: CoroutineContext
) {

    private var snapshotState: State = initialState
    private var receivedActions = arrayListOf<Pair<Action, State>>()
    private var longLivingEffects = mutableSetOf<LongLivingEffect>()

    private val store = Store(
        initialState = initialState,
        reducer = { state: State, testAction: TestAction<Action, LocalAction> ->
            val (newState, effects) = when (testAction.origin) {
                is TestAction.Origin.Send ->
                    reducer.invoke(
                        state,
                        fromLocalAction(testAction.origin.localAction)
                    ).also { (newState, _) ->
                        snapshotState = newState
                    }
                is TestAction.Origin.Receive ->
                    reducer.invoke(state, testAction.origin.action)
                        .also { (newState, _) ->
                            receivedActions.add(testAction.origin.action to newState)
                        }
            }
            val effect = LongLivingEffect()
            newState withEffect effects
                .onStart { longLivingEffects.add(effect) }
                .onCompletion { longLivingEffects.remove(effect) }
                .map { TestAction(TestAction.Origin.Receive(it)) }
        },
        coroutineContext = coroutineContext,
        // Tests are usually run with a test scheduler and a main thread is not setup
        mainThreadChecksEnabled = false
    )

    init {
        coroutineContext.job.invokeOnCompletion {
            if (receivedActions.isNotEmpty()) {
                val actions = receivedActions.joinToString("\n") { it.first.toString() }
                val message = """
The store received ${receivedActions.size} unexpected
action${if (receivedActions.size == 1) "" else "s"} after this one:

Unhandled actions: $actions
                """.trimIndent()
                fail(message)
            }
            longLivingEffects.forEach { _ ->
                val message = """
An effect returned for this action is still running. It must complete before the end of
the test.

To fix, inspect any effects the reducer returns for this action and ensure that all of
them complete by the end of the test. There are a few reasons why an effect may not have
completed:

• If an effect uses a scheduler (via "receive", "delay", "debounce", etc.), make
sure that you wait enough time for the scheduler to perform the effect. If you are using
a test scheduler, advance the scheduler so that the effects may complete, or consider
using an immediate scheduler to immediately perform the effect instead.

• If you are returning a long-living effect (timers, notifications, subjects, etc.),
then make sure those effects are torn down.
                """.trimIndent()
                fail(message)
            }
        }
    }

    private data class LongLivingEffect(
        val id: String = generateUUID()
    )

    private data class TestAction<Action, LocalAction>(
        val origin: Origin<Action, LocalAction>
    ) {
        sealed class Origin<Action, LocalAction> {
            data class Send<Action, LocalAction>(val localAction: LocalAction) :
                Origin<Action, LocalAction>()

            data class Receive<Action, LocalAction>(val action: Action) :
                Origin<Action, LocalAction>()
        }
    }

    public fun send(
        action: LocalAction,
        update: (LocalState) -> LocalState = { it }
    ) {
        if (receivedActions.isNotEmpty()) {
            val actions = receivedActions.joinToString("\n") { it.first.toString() }
            val message = """
Must handle ${receivedActions.size} received action${if (receivedActions.size == 1) "" else "s"} before sending an action:

Unhandled actions: 
$actions
            """.trimIndent()
            fail(message)
        }

        val localState = toLocalState(snapshotState)
        store.send(TestAction(TestAction.Origin.Send(action)))
        val expectedState = update(localState)
        expectedStateShouldMatch(
            expected = expectedState,
            actual = toLocalState(snapshotState)
        )
    }

    public fun receive(
        expectedAction: Action,
        update: (LocalState) -> LocalState = { it }
    ) {
        if (receivedActions.isEmpty()) {
            fail("Expected to receive an action, but received none. Did you forgot to advanceTimeUntilIdle()?")
        }

        val (receivedAction, state) = receivedActions.removeFirst()
        assertEquals(expectedAction, receivedAction, "Received unexpected action")

        val localState = toLocalState(snapshotState)
        val expectedState = update(localState)
        expectedStateShouldMatch(
            expected = expectedState,
            actual = toLocalState(state)
        )
        snapshotState = state
    }

    private fun expectedStateShouldMatch(
        expected: LocalState,
        actual: LocalState
    ) {
        assertEquals(expected?.dump(), actual?.dump(), "State change does not match")
    }
}

public fun <State, Action> TestStore(
    initialState: State,
    reducer: (State, Action) -> ReducerResult<State, Action>,
    coroutineContext: CoroutineContext
): TestStore<State, State, Action, Action> =
    TestStore(
        initialState = initialState,
        reducer = reducer,
        fromLocalAction = { it },
        toLocalState = { it },
        coroutineContext = coroutineContext
    )

public fun <State, Action> TestStore(
    initialState: State,
    reducer: ReducerProtocol<State, Action>,
    coroutineContext: CoroutineContext
): TestStore<State, State, Action, Action> =
    TestStore(
        initialState = initialState,
        reducer = reducer::reduce,
        fromLocalAction = { it },
        toLocalState = { it },
        coroutineContext = coroutineContext
    )

/**
 * Revert custom SqlDelight toString() override
 */
private fun String.revertSqlDelightToString() =
    replace("[\n", "(")
        .replace("\n]", ")")
        .replace("\n", ",")

private fun Any.dump(): String {
    val dumpString = toString()
        .revertSqlDelightToString()
    return buildString(dumpString.length) {
        var indent = 0
        fun StringBuilder.line() {
            appendLine()
            repeat(2 * indent) { append(' ') }
        }

        for ((index, char) in dumpString.withIndex()) {
            when (char) {
                ' ' -> continue
                ')' -> {
                    indent--
                    line()
                }
                ']' -> {
                    if (dumpString.getOrNull(index - 1) != '[') {
                        indent--
                        line()
                    }
                }
            }

            if (char == '=') append(' ')
            append(char)
            if (char == '=') append(' ')

            when (char) {
                ',' -> line()
                '(' -> {
                    indent++
                    line()
                }
                '[' -> when (dumpString.getOrNull(index + 1)) {
                    ']' -> {
                        /* don't indent */
                    }
                    else -> {
                        indent++
                        line()
                    }
                }
            }
        }
    }
}

