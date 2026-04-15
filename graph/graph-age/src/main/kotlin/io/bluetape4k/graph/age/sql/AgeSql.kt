package io.bluetape4k.graph.age.sql

/**
 * Apache AGE Cypher-over-SQL 쿼리 문자열 빌더.
 *
 * AGE는 PostgreSQL 내에서 다음 형태로 Cypher를 실행합니다:
 * ```sql
 * SELECT * FROM ag_catalog.cypher('graph_name', $$ MATCH (n) RETURN n $$) AS (v agtype)
 * ```
 *
 * ```kotlin
 * // 정점 생성 SQL 생성
 * val sql = AgeSql.createVertex("social", "Person", mapOf("name" to "Alice", "age" to 30))
 *
 * // 간선 조회 SQL 생성
 * val edgeSql = AgeSql.matchEdgesByLabel("social", "KNOWS", mapOf("since" to 2024))
 *
 * // 최단 경로 SQL 생성
 * val pathSql = AgeSql.shortestPath("social", 1L, 2L, edgeLabel = "KNOWS", maxDepth = 5)
 * ```
 */
object AgeSql {

    /**
     * `LOAD 'age'` SQL 문자열을 반환한다. 매 트랜잭션 시작 시 실행해야 한다.
     *
     * ```kotlin
     * AgeSql.loadAge()  // → "LOAD 'age'"
     * ```
     */
    fun loadAge(): String = "LOAD 'age'"

    /**
     * AGE 함수 검색을 위한 `SET search_path` SQL 문자열을 반환한다.
     *
     * ```kotlin
     * AgeSql.setSearchPath()
     * // → """SET search_path = ag_catalog, "$user", public"""
     * ```
     */
    fun setSearchPath(): String = $$"""SET search_path = ag_catalog, "$user", public"""

    /**
     * PostgreSQL에 AGE extension을 설치하는 SQL 문자열을 반환한다. DB 최초 초기화 시 1회만 실행한다.
     *
     * ```kotlin
     * AgeSql.createExtension()  // → "CREATE EXTENSION IF NOT EXISTS age"
     * ```
     */
    fun createExtension(): String = "CREATE EXTENSION IF NOT EXISTS age"

    /**
     * 지정한 이름의 AGE 그래프를 생성하는 SQL 문자열을 반환한다.
     *
     * ```kotlin
     * AgeSql.createGraph("social")  // → "SELECT create_graph('social')"
     * ```
     */
    fun createGraph(graphName: String): String = "SELECT create_graph('$graphName')"

    /**
     * 지정한 AGE 그래프를 삭제하는 SQL 문자열을 반환한다.
     *
     * ```kotlin
     * AgeSql.dropGraph("social")
     * // → "SELECT drop_graph('social', true)"
     * ```
     *
     * @param graphName 삭제할 그래프 이름.
     * @param cascade 의존 객체 포함 삭제 여부 (기본: true).
     */
    fun dropGraph(graphName: String, cascade: Boolean = true): String =
        "SELECT drop_graph('$graphName', $cascade)"

    /**
     * 지정한 이름의 AGE 그래프 존재 여부를 확인하는 COUNT SQL 문자열을 반환한다.
     *
     * ```kotlin
     * AgeSql.graphExists("social")
     * // → "SELECT count(*) FROM ag_catalog.ag_graph WHERE name = 'social'"
     * ```
     */
    fun graphExists(graphName: String): String =
        "SELECT count(*) FROM ag_catalog.ag_graph WHERE name = '$graphName'"

    /**
     * Cypher 쿼리를 AGE SQL로 래핑합니다.
     *
     * ```kotlin
     * AgeSql.cypher(
     *     "social",
     *     "MATCH (n:Person) RETURN n",
     *     listOf("n" to "agtype")
     * )
     * // → "SELECT * FROM ag_catalog.cypher('social', $$ MATCH (n:Person) RETURN n $$) AS (n agtype)"
     * ```
     *
     * @param graphName AGE 그래프 이름.
     * @param cypherQuery 실행할 Cypher 쿼리 문자열.
     * @param columns 결과 컬럼 목록. 예: listOf("v" to "agtype", "e" to "agtype")
     */
    fun cypher(
        graphName: String,
        cypherQuery: String,
        columns: List<Pair<String, String>>,
    ): String {
        val colDef = columns.joinToString(", ") { (name, type) -> "$name $type" }
        return $$"""SELECT * FROM ag_catalog.cypher('$$graphName', $$ $$cypherQuery $$) AS ($$colDef)"""
    }

    /**
     * 정점 생성 Cypher를 AGE SQL로 래핑하여 반환한다.
     *
     * ```kotlin
     * AgeSql.createVertex("social", "Person", mapOf("name" to "Alice"))
     * // → SELECT * FROM ag_catalog.cypher('social', $$ CREATE (v:Person {name: 'Alice'}) RETURN v $$) AS (v agtype)
     * ```
     */
    fun createVertex(graphName: String, label: String, properties: Map<String, Any?>): String {
        val propsStr = AgePropertySerializer.toCypherProps(properties)
        return cypher(
            graphName,
            "CREATE (v:$label $propsStr) RETURN v",
            listOf("v" to "agtype")
        )
    }

    /**
     * 레이블과 필터로 정점을 조회하는 AGE SQL을 반환한다.
     *
     * ```kotlin
     * AgeSql.matchVertices("social", "Person", mapOf("name" to "Alice"))
     * ```
     */
    fun matchVertices(graphName: String, label: String, filter: Map<String, Any?> = emptyMap()): String {
        val filterStr = if (filter.isEmpty()) "" else AgePropertySerializer.toCypherProps(filter)
        return cypher(
            graphName,
            "MATCH (v:$label $filterStr) RETURN v",
            listOf("v" to "agtype")
        )
    }

    /**
     * ID로 단일 정점을 조회하는 AGE SQL을 반환한다.
     *
     * ```kotlin
     * AgeSql.matchVertexById("social", "Person", 42L)
     * ```
     */
    fun matchVertexById(graphName: String, label: String, id: Long): String =
        cypher(
            graphName,
            "MATCH (v:$label) WHERE id(v) = $id RETURN v",
            listOf("v" to "agtype")
        )

    /**
     * 정점 속성을 갱신하는 AGE SQL을 반환한다.
     *
     * ```kotlin
     * AgeSql.updateVertex("social", "Person", 42L, mapOf("age" to 31))
     * ```
     */
    fun updateVertex(graphName: String, label: String, id: Long, properties: Map<String, Any?>): String {
        val sets = properties.entries.joinToString(", ") { (k, v) ->
            "v.$k = ${AgePropertySerializer.toCypherValue(v)}"
        }
        return cypher(
            graphName,
            "MATCH (v:$label) WHERE id(v) = $id SET $sets RETURN v",
            listOf("v" to "agtype")
        )
    }

    /**
     * 정점을 삭제하는 AGE SQL을 반환한다.
     *
     * ```kotlin
     * AgeSql.deleteVertex("social", "Person", 42L)
     * ```
     */
    fun deleteVertex(graphName: String, label: String, id: Long): String =
        cypher(
            graphName,
            "MATCH (v:$label) WHERE id(v) = $id DETACH DELETE v RETURN 1",
            listOf("result" to "agtype")
        )

    /**
     * 레이블별 정점 수를 조회하는 AGE SQL을 반환한다.
     *
     * ```kotlin
     * AgeSql.countVertices("social", "Person")
     * ```
     */
    fun countVertices(graphName: String, label: String): String =
        cypher(
            graphName,
            "MATCH (v:$label) RETURN count(v)",
            listOf("count" to "agtype")
        )

    /**
     * 간선 생성 Cypher를 AGE SQL로 래핑하여 반환한다.
     *
     * ```kotlin
     * AgeSql.createEdge("social", fromId = 1L, toId = 2L, "KNOWS", mapOf("since" to 2024))
     * ```
     */
    fun createEdge(
        graphName: String,
        fromId: Long,
        toId: Long,
        edgeLabel: String,
        properties: Map<String, Any?>,
    ): String {
        val propsStr = AgePropertySerializer.toCypherProps(properties)
        return cypher(
            graphName,
            "MATCH (a), (b) WHERE id(a) = $fromId AND id(b) = $toId CREATE (a)-[e:$edgeLabel $propsStr]->(b) RETURN e",
            listOf("e" to "agtype")
        )
    }

    /**
     * 레이블과 필터로 간선을 조회하는 AGE SQL을 반환한다.
     *
     * ```kotlin
     * AgeSql.matchEdgesByLabel("social", "KNOWS", mapOf("since" to 2024))
     * ```
     */
    fun matchEdgesByLabel(graphName: String, edgeLabel: String, filter: Map<String, Any?> = emptyMap()): String {
        val filterStr = if (filter.isEmpty()) "" else AgePropertySerializer.toCypherProps(filter)
        return cypher(
            graphName,
            "MATCH ()-[e:$edgeLabel $filterStr]->() RETURN e",
            listOf("e" to "agtype")
        )
    }

    /**
     * 간선을 삭제하는 AGE SQL을 반환한다.
     *
     * ```kotlin
     * AgeSql.deleteEdge("social", "KNOWS", 99L)
     * ```
     */
    fun deleteEdge(graphName: String, edgeLabel: String, id: Long): String =
        cypher(
            graphName,
            "MATCH ()-[e:$edgeLabel]->() WHERE id(e) = $id DELETE e RETURN 1",
            listOf("result" to "agtype")
        )

    /**
     * 인접 정점 탐색 SQL을 생성합니다.
     *
     * ```kotlin
     * // 1단계 OUTGOING neighbors
     * AgeSql.neighbors("social", 1L, edgeLabel = "KNOWS", direction = "OUTGOING", depth = 1)
     *
     * // 최대 3홉 양방향
     * AgeSql.neighbors("social", 1L, edgeLabel = null, direction = "BOTH", depth = 3)
     * ```
     *
     * @param graphName AGE 그래프 이름.
     * @param startId 시작 정점의 AGE numeric ID.
     * @param edgeLabel 필터링할 간선 레이블. null이면 모든 간선 포함.
     * @param direction "OUTGOING", "INCOMING", "BOTH" 중 하나.
     * @param depth 최대 탐색 깊이.
     */
    fun neighbors(
        graphName: String,
        startId: Long,
        edgeLabel: String?,
        direction: String,
        depth: Int,
    ): String {
        val depthStr = if (depth == 1) "" else "*1..$depth"
        val edgePart = if (edgeLabel != null) ":$edgeLabel$depthStr" else depthStr.ifEmpty { "" }
        val pattern = when (direction) {
            "OUTGOING" -> "(start)-[$edgePart]->(neighbor)"
            "INCOMING" -> "(start)<-[$edgePart]-(neighbor)"
            else -> "(start)-[$edgePart]-(neighbor)"
        }
        return cypher(
            graphName,
            "MATCH $pattern WHERE id(start) = $startId RETURN DISTINCT neighbor",
            listOf("neighbor" to "agtype")
        )
    }

    /**
     * 최단 경로 탐색 SQL을 생성합니다.
     *
     * AGE는 `shortestPath()` 내장 함수를 지원하지 않으므로
     * 변수 길이 패스 매칭 + `LIMIT 1` 방식으로 구현합니다.
     *
     * ```kotlin
     * AgeSql.shortestPath("social", fromId = 1L, toId = 5L, edgeLabel = "KNOWS", maxDepth = 5)
     * ```
     *
     * @param graphName AGE 그래프 이름.
     * @param fromId 출발 정점 ID.
     * @param toId 도착 정점 ID.
     * @param edgeLabel 필터링할 간선 레이블. null이면 모든 간선 포함.
     * @param maxDepth 최대 탐색 깊이.
     */
    fun shortestPath(
        graphName: String,
        fromId: Long,
        toId: Long,
        edgeLabel: String?,
        maxDepth: Int,
    ): String {
        // AGE는 shortestPath() 내장 함수를 지원하지 않으므로, 변수 길이 패스 + LIMIT 1 로 대체
        val relPattern = if (edgeLabel != null) ":$edgeLabel*1..$maxDepth" else "*1..$maxDepth"
        return cypher(
            graphName,
            "MATCH p = (a)-[$relPattern]-(b) WHERE id(a) = $fromId AND id(b) = $toId RETURN p LIMIT 1",
            listOf("p" to "agtype")
        )
    }

    /**
     * 모든 경로 탐색 SQL을 생성합니다.
     *
     * ```kotlin
     * AgeSql.allPaths("social", fromId = 1L, toId = 5L, edgeLabel = "KNOWS", maxDepth = 3)
     * ```
     *
     * @param graphName AGE 그래프 이름.
     * @param fromId 출발 정점 ID.
     * @param toId 도착 정점 ID.
     * @param edgeLabel 필터링할 간선 레이블. null이면 모든 간선 포함.
     * @param maxDepth 최대 탐색 깊이.
     */
    fun allPaths(
        graphName: String,
        fromId: Long,
        toId: Long,
        edgeLabel: String?,
        maxDepth: Int,
    ): String {
        val relPattern = if (edgeLabel != null) ":$edgeLabel*1..$maxDepth" else "*1..$maxDepth"
        return cypher(
            graphName,
            "MATCH p = (a)-[$relPattern]-(b) WHERE id(a) = $fromId AND id(b) = $toId RETURN p",
            listOf("p" to "agtype")
        )
    }
}
