package dev.luisramos.composable

import dev.luisramos.composable.reducer.ReducerResult
import dev.luisramos.composable.reducer.withNoEffect

/**
 * Reducer protocol that enables abstracting away business logic in a single reducer
 * or in a combination of several reducers
 *
 * Override [body] if you want to declare several reducers. Check [dev.luisramos.composable.reducer.ReducerBuilder] for
 * helpful builders. Override [reduce] if you want to get straight into it
 */
public interface ReducerProtocol<State, Action> {
    public fun reduce(state: State, action: Action): ReducerResult<State, Action> =
        body.reduce(state, action)

    /**
     * This property should never be called directly. [Store] implementation calls the [reduce]
     * method, this body exists to simplify the creation of a reducer that combines other reducers.
     */
    public val body: ReducerProtocol<State, Action>
        get() = EmptyReducer()
}

/**
 * Reducer that does nothing
 *
 * Useful as a placeholder for APIs that hold reducers
 */
public class EmptyReducer<State, Action> : ReducerProtocol<State, Action> {
    override fun reduce(state: State, action: Action): ReducerResult<State, Action> =
        state.withNoEffect()
}

/**
 * Describes how to extract an action from a parent action. This assumes actions
 * in the for of
 *
 * sealed class Action {
 *  data class Child(val action: ChildAction): Action()
 *
 *  companion object {
 *      val child = CasePath(::Child, Child::action)
 *  }
 * }
 */
public class CasePath<Root, Value>(
    embed: (Value) -> Root,
    extract: (Root) -> Value?
) {
    private val _embed = embed
    private val _extract = extract
    public fun embed(value: Value): Root = _embed(value)
    public fun extract(root: Root): Value? = _extract(root)
}

/**
 * Helper function to help us extract an action from a parent action
 * and embed an action into a parent action
 */
@Suppress("FunctionName")
public inline fun <Root, reified Case : Root, Value> CasePath(
    noinline embed: (Value) -> Root,
    crossinline extract: (Case) -> Value
): CasePath<Root, Value> = CasePath<Root, Value>(
    embed = embed,
    extract = { root -> (root as? Case)?.let { extract(root) } }
)

/**
 * Describes how to extract state from parent state
 * and how to embed state into parent state
 */
public class KeyPath<Root, Value>(
    embed: (Root, Value) -> Root,
    extract: (Root) -> Value
) {
    private val _embed = embed
    private val _extract = extract
    public fun embed(root: Root, value: Value): Root = _embed(root, value)
    public fun extract(root: Root): Value = _extract(root)
}

