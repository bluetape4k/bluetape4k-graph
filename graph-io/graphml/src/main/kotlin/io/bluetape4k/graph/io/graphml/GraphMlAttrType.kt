package io.bluetape4k.graph.io.graphml

/** GraphML `attr.type` 열거형. 파싱 시 값을 적절한 JVM 타입으로 변환한다. */
enum class GraphMlAttrType(val xmlName: String) {
    STRING("string"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    DOUBLE("double"),
    BOOLEAN("boolean");

    fun coerce(value: String): Any = when (this) {
        STRING -> value
        INT -> value.toIntOrNull() ?: value
        LONG -> value.toLongOrNull() ?: value
        FLOAT -> value.toFloatOrNull() ?: value
        DOUBLE -> value.toDoubleOrNull() ?: value
        BOOLEAN -> value.toBooleanStrictOrNull() ?: value
    }

    companion object {
        fun fromXml(name: String): GraphMlAttrType =
            entries.firstOrNull { it.xmlName == name } ?: STRING
    }
}
