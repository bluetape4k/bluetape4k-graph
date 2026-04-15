package io.bluetape4k.graph.age.sql

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging

/**
 * Apache AGE의 agtype 결과를 파싱하여 Graph 모델로 변환합니다.
 *
 * ```kotlin
 * val vertex = AgeTypeParser.parseVertex(rs.getString("v"))
 * val edge   = AgeTypeParser.parseEdge(rs.getString("e"))
 * val path   = AgeTypeParser.parsePath(rs.getString("p"))
 * ```
 */
object AgeTypeParser : KLogging() {

    /**
     * AGE vertex agtype 문자열을 [GraphVertex]로 파싱한다.
     *
     * ```kotlin
     * val vertex = AgeTypeParser.parseVertex(rs.getString("v"))
     * println(vertex.label)               // "Person"
     * println(vertex.properties["name"])  // "Alice"
     * ```
     *
     * @param agtypeStr AGE가 반환하는 vertex agtype 문자열. 예: `{"id": 1, "label": "Person", "properties": {"name": "Alice"}}::vertex`
     * @return 파싱된 [GraphVertex].
     */
    fun parseVertex(agtype: String): GraphVertex {
        val json = agtype.removeSuffix("::vertex").trim()
        val map = parseJsonObject(json)
        val id = GraphElementId.of((map["id"] as Number).toLong())
        val label = map["label"] as String
        @Suppress("UNCHECKED_CAST")
        val properties = (map["properties"] as? Map<String, Any?>) ?: emptyMap()
        return GraphVertex(id, label, properties)
    }

    /**
     * AGE edge agtype 문자열을 [GraphEdge]로 파싱한다.
     *
     * ```kotlin
     * val edge = AgeTypeParser.parseEdge(rs.getString("e"))
     * println(edge.label)  // "KNOWS"
     * ```
     *
     * @param agtypeStr AGE가 반환하는 edge agtype 문자열.
     * @return 파싱된 [GraphEdge].
     */
    fun parseEdge(agtype: String): GraphEdge {
        val json = agtype.removeSuffix("::edge").trim()
        val map = parseJsonObject(json)
        val id = GraphElementId.of((map["id"] as Number).toLong())
        val label = map["label"] as String
        val startId = GraphElementId.of((map["start_id"] as Number).toLong())
        val endId = GraphElementId.of((map["end_id"] as Number).toLong())
        @Suppress("UNCHECKED_CAST")
        val properties = (map["properties"] as? Map<String, Any?>) ?: emptyMap()
        return GraphEdge(id, label, startId, endId, properties)
    }

    /**
     * AGE path agtype 문자열을 [GraphPath]로 파싱한다.
     *
     * path는 vertex와 edge가 교대로 나열된 배열 형태로 표현된다.
     *
     * ```kotlin
     * val path = AgeTypeParser.parsePath(rs.getString("p"))
     * println(path.length)    // 경로 단계 수
     * println(path.vertices)  // 경로 내 정점 목록
     * ```
     *
     * @param agtypeStr AGE가 반환하는 path agtype 문자열.
     * @return 파싱된 [GraphPath].
     */
    fun parsePath(agtype: String): GraphPath {
        val json = agtype.removeSuffix("::path").trim()
        // path는 [{...}::vertex, {...}::edge, ...] 형태의 agtype 배열
        val content = json.trim().removePrefix("[").removeSuffix("]").trim()
        val elements = parseAgtypeElements(content)
        val steps = elements.mapNotNull { element ->
            when {
                element.endsWith("::vertex") -> PathStep.VertexStep(parseVertex(element))
                element.endsWith("::edge") -> PathStep.EdgeStep(parseEdge(element))
                else -> null.also { log.warn("Unknown agtype element: $element") }
            }
        }
        return GraphPath(steps)
    }

    /**
     * agtype 배열 내용에서 각 요소를 `{...}::type` 형태의 문자열로 추출.
     * `parseJsonArray`는 `::type` suffix를 제거하므로 path 파싱에는 사용 불가.
     */
    private fun parseAgtypeElements(content: String): List<String> {
        val elements = mutableListOf<String>()
        var i = 0
        while (i < content.length) {
            while (i < content.length && (content[i] == ',' || content[i] == ' ')) i++
            if (i >= content.length) break
            if (content[i] == '{') {
                val end = findClosing(content, i, '{', '}')
                val jsonPart = content.substring(i, end + 1)
                var j = end + 1
                while (j < content.length && content[j] == ' ') j++
                if (j + 1 < content.length && content[j] == ':' && content[j + 1] == ':') {
                    var typeEnd = j + 2
                    while (typeEnd < content.length && content[typeEnd] != ',' && content[typeEnd] != ' ') typeEnd++
                    elements.add("$jsonPart${content.substring(j, typeEnd)}")
                    i = typeEnd
                } else {
                    elements.add(jsonPart)
                    i = end + 1
                }
            } else {
                i++
            }
        }
        return elements.toList()
    }

    /**
     * [agtypeStr]이 AGE vertex 타입 문자열인지 판별한다.
     *
     * ```kotlin
     * AgeTypeParser.isVertex("""{"id": 1, "label": "Person", ...}::vertex""")  // true
     * AgeTypeParser.isVertex("""{"id": 2, ...}::edge""")                       // false
     * ```
     */
    fun isVertex(agtype: String): Boolean = agtype.trimEnd().endsWith("::vertex")

    /**
     * [agtypeStr]이 AGE edge 타입 문자열인지 판별한다.
     *
     * ```kotlin
     * AgeTypeParser.isEdge("""{"id": 99, "label": "KNOWS", ...}::edge""")  // true
     * ```
     */
    fun isEdge(agtype: String): Boolean = agtype.trimEnd().endsWith("::edge")

    /**
     * [agtypeStr]이 AGE path 타입 문자열인지 판별한다.
     *
     * ```kotlin
     * AgeTypeParser.isPath("""[{...}::vertex, {...}::edge, {...}::vertex]""")  // true
     * ```
     */
    fun isPath(agtype: String): Boolean = agtype.trimEnd().endsWith("::path")

    /**
     * 단순 JSON 객체 파싱 (Jackson/Gson 의존성 없이 AGE agtype 처리용).
     * 복잡한 중첩 구조는 지원하지 않으므로, 필요 시 Jackson 도입 고려.
     *
     * ```kotlin
     * AgeTypeParser.parseJsonObject("""{"name": "Alice", "age": 30}""")
     * // → mapOf("name" to "Alice", "age" to 30)
     * ```
     *
     */
    fun parseJsonObject(json: String): Map<String, Any?> {
        val content = json.trim().removePrefix("{").removeSuffix("}").trim()
        if (content.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Any?>()
        var i = 0
        while (i < content.length) {
            // key 파싱
            if (content[i] == '"') {
                val keyEnd = content.indexOf('"', i + 1)
                val key = content.substring(i + 1, keyEnd)
                i = keyEnd + 1
                // ':' 건너뜀
                while (i < content.length && (content[i] == ':' || content[i] == ' ')) i++
                // value 파싱
                val (value, nextIndex) = parseValue(content, i)
                result[key] = value
                i = nextIndex
                while (i < content.length && (content[i] == ',' || content[i] == ' ')) i++
            } else {
                i++
            }
        }
        return result.toMap()
    }

    private fun parseValue(content: String, start: Int): Pair<Any?, Int> {
        return when {
            content[start] == '"'              -> {
                val end = content.indexOf('"', start + 1)
                content.substring(start + 1, end) to end + 1
            }
            content[start] == '{'              -> {
                val end = findClosing(content, start, '{', '}')
                parseJsonObject(content.substring(start, end + 1)) to end + 1
            }
            content[start] == '['              -> {
                val end = findClosing(content, start, '[', ']')
                parseJsonArray(content.substring(start, end + 1)) to end + 1
            }
            content.startsWith("null", start)  -> null to start + 4
            content.startsWith("true", start)  -> true to start + 4
            content.startsWith("false", start) -> false to start + 5
            else                               -> {
                val commaPos = content.indexOf(',', start)
                val bracePos = content.indexOf('}', start)
                val end = when {
                    commaPos < 0 && bracePos < 0 -> content.length
                    commaPos < 0 -> bracePos
                    bracePos < 0 -> commaPos
                    else -> minOf(commaPos, bracePos)
                }
                val numStr = content.substring(start, end).trim()
                val num = numStr.toLongOrNull() ?: numStr.toDoubleOrNull() ?: numStr
                num to end
            }
        }
    }

    /**
     * AGE JSON 배열 문자열을 List로 파싱한다.
     *
     * ```kotlin
     * AgeTypeParser.parseJsonArray("""[1, "str", null, true]""")
     * // → listOf(1, "str", null, true)
     * ```
     *
     * @param jsonArrayStr `[1, "str", null, true]` 형태의 JSON 배열 문자열.
     * @return 파싱된 불변 [List].
     */
    fun parseJsonArray(json: String): List<Any?> {
        val content = json.trim().removePrefix("[").removeSuffix("]").trim()
        if (content.isEmpty()) return emptyList()
        val result = mutableListOf<Any?>()
        var i = 0
        while (i < content.length) {
            val (value, nextIndex) = parseValue(content, i)
            result.add(value)
            i = nextIndex
            while (i < content.length && (content[i] == ',' || content[i] == ' ')) i++
        }
        return result.toList()
    }

    private fun findClosing(content: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        for (i in start until content.length) {
            when (content[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return content.length - 1
    }
}
