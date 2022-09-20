package dev.luisramos.composable.utils

import platform.Foundation.NSThread

internal actual fun isMainThread(): Boolean = NSThread.isMainThread
