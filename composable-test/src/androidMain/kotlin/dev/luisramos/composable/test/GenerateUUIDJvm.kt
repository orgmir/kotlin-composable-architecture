package dev.luisramos.composable.test

import java.util.UUID

internal actual fun generateUUID(): String = UUID.randomUUID().toString()