package dev.luisramos.composable.utils

public interface Closeable {
    public fun close()

    public companion object {
        public operator fun invoke(block: () -> Unit): Closeable = object : Closeable {
            override fun close() {
                block()
            }
        }
    }
}
