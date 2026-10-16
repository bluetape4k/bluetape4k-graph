package io.bluetape4k.graph.support

import io.bluetape4k.support.requireMatches

internal val SAFE_IDENTIFIER_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

fun String.requireSafeIdentifier(paramName: String): String =
    requireMatches(SAFE_IDENTIFIER_REGEX, paramName) {
        "$paramName must be a valid identifier (letters, digits, underscore; must start with letter or underscore): $this"
    }
