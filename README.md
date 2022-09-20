# Kotlin Composable Architecture

Composable Architecture is a library for building multiplatform applications in a consistent and
understandable way. It is a port
of [@pointfreeco The Composable Architecture (TCA)](https://github.com/pointfreeco/swift-composable-architecture)
. It currently supports iOS and Android.

```kotlin
class Counter : ReducerProtocol<Counter.State, Counter.Action> {
    data class State(val counter: Int = 0)

    sealed interface Action {
        object Increment : Action
        object Decrement : Action
    }

    override fun reduce(state: State, action: Action): ReducerResult<State, Action> =
        when (action) {
            Action.Decrement -> state.copy(counter = state.counter - 1).withNoEffect()
            Action.Increment -> state.copy(counter = state.counter + 1).withNoEffect()
        }
}
```

## Download

Currently, only snapshots of the development verion are available in Sonatype's snapshots
repository.

```groovy
repositories {
    maven {
        url 'https://oss.sonatype.org/content/repositories/snapshots/'
    }
}
dependencies {
    testImplementation 'dev.luisramos.composable:composable:0.1.0-SNAPSHOT'
}
```

## Usage

This library follows the same patterns as TCA, so you should be familiar with it before using it.


