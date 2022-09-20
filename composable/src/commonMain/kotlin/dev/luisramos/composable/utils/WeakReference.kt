package dev.luisramos.composable.utils

internal expect class WeakReference<T : Any>(value: T) {
    val value: T?
}
