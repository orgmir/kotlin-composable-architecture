package dev.luisramos.composable

import dev.luisramos.composable.reducer.ReducerResult
import dev.luisramos.composable.reducer.withNoEffect

/**
 * Reducer protocol that enables abstracting away business logic in a single reducer
 * or in a combination of several reducers
 *
 * Override [body] if you want to declare several reducers. Check [ReducerBuilder] for
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
 * Describes how to embed and extract state from Parent state
 */
public class StatePath<Parent, Child>(
    public val embed: (Parent, Child) -> Parent,
    public val extract: (Parent) -> Child
)

/**
 * Describes how to embed and extract an action from a parent action
 */
public class ActionPath<Parent, Child>(
    public val embed: (parent: Parent, child: Child) -> Parent,
    public val extract: (parent: Parent) -> Child?
)

