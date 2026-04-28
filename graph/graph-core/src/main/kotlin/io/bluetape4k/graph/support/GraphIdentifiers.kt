package io.bluetape4k.graph.support

internal val SAFE_IDENTIFIER_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

fun String.requireSafeIdentifier(paramName: String): String = apply {
    require(SAFE_IDENTIFIER_REGEX.matches(this)) {
        "$paramName must be a valid identifier (letters, digits, underscore; must start with letter or underscore): $this"
    }
}
