package dev.luisramos.composable.test

import platform.Foundation.NSUUID

internal actual fun generateUUID() = NSUUID().UUIDString