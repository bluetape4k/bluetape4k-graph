package io.bluetape4k.graph.age.sql

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import org.junit.jupiter.api.Test

class AgeSqlTest {

    private val graph = "test_graph"

    @Test
    fun `구조적 식별자는 SQL에 보간하기 전에 안전성을 검증한다`() {
        val unsafe = "invalid-label; DROP TABLE users; --"
        val calls: List<() -> String> = listOf(
            { AgeSql.createGraph(unsafe) },
            { AgeSql.dropGraph(unsafe) },
            { AgeSql.graphExists(unsafe) },
            { AgeSql.cypher(unsafe, "RETURN 1", listOf("v" to "agtype")) },
            { AgeSql.createVertex(graph, unsafe, emptyMap()) },
            { AgeSql.createVerticesBatch(graph, unsafe, listOf(AgeSql.BatchVertexRow(0, emptyMap()))) },
            { AgeSql.matchVertices(graph, unsafe) },
            { AgeSql.matchVertexById(graph, unsafe, 1L) },
            { AgeSql.updateVertex(graph, unsafe, 1L, emptyMap()) },
            { AgeSql.deleteVertex(graph, unsafe, 1L) },
            { AgeSql.countVertices(graph, unsafe) },
            { AgeSql.createEdge(graph, 1L, 2L, unsafe, emptyMap()) },
            { AgeSql.createEdgesBatch(graph, unsafe, listOf(AgeSql.BatchEdgeRow(0, 1L, 2L))) },
            { AgeSql.matchEdgesByLabel(graph, unsafe) },
            { AgeSql.matchEdgeBetween(graph, 1L, 2L, unsafe) },
            { AgeSql.updateEdge(graph, unsafe, 1L, emptyMap()) },
            { AgeSql.deleteEdge(graph, unsafe, 1L) },
            { AgeSql.neighbors(graph, 1L, unsafe, "OUTGOING", 1) },
            { AgeSql.shortestPath(graph, 1L, 2L, unsafe, 2) },
            { AgeSql.allPaths(graph, 1L, 2L, unsafe, 2) },
            { AgeSql.matchEdgesByStartId(graph, 1L, unsafe) },
            { AgeSql.matchEdgesByEndId(graph, 2L, unsafe) },
            { AgeSql.degreeCentrality(graph, 1L, unsafe) },
        )

        calls.forEachIndexed { index, call ->
            assertFailsWith<IllegalArgumentException>("unsafe identifier call index=$index") { call() }
        }
    }

    @Test
    fun `cypher 결과 컬럼의 이름과 타입도 안전한 식별자만 허용한다`() {
        assertFailsWith<IllegalArgumentException> {
            AgeSql.cypher(graph, "RETURN 1", listOf("v; DROP TABLE users; --" to "agtype"))
        }
        assertFailsWith<IllegalArgumentException> {
            AgeSql.cypher(graph, "RETURN 1", listOf("v" to "agtype); DROP TABLE users; --"))
        }
        assertFailsWith<IllegalArgumentException> {
            AgeSql.cypher(graph, "RETURN 1", emptyList())
        }
    }

    @Test
    fun `cypher 본문에 dollar quote가 포함되어도 SQL delimiter 경계를 탈출하지 않는다`() {
        val value = "\$\$; DROP TABLE users; --"
        val sql = AgeSql.createVertex(graph, "Person", mapOf("payload" to value))

        sql shouldContain "\$bt4k\$"
        sql shouldContain value
        sql shouldNotContain "cypher('$graph', \$\$"
    }

    @Test
    fun `cypher 본문이 기본 delimiter 태그를 포함하면 다음 태그를 선택한다`() {
        val query = "RETURN '\$bt4k\$' AS first, '\$bt4k_\$' AS second"
        val sql = AgeSql.cypher(graph, query, listOf("v" to "agtype"))

        sql shouldContain "\$bt4k__\$"
        sql shouldContain query
    }

    // ── createGraph / dropGraph / graphExists ─────────────────────────────

    @Test
    fun `createGraph - 그래프 이름을 포함한 SQL을 생성한다`() {
        val sql = AgeSql.createGraph(graph)
        sql shouldContain "create_graph"
        sql shouldContain graph
    }

    @Test
    fun `dropGraph - 그래프 이름과 cascade 여부를 포함한 SQL을 생성한다`() {
        val sql = AgeSql.dropGraph(graph, cascade = true)
        sql shouldContain "drop_graph"
        sql shouldContain graph
        sql shouldContain "true"
    }

    @Test
    fun `dropGraph - cascade false이면 false가 포함된다`() {
        val sql = AgeSql.dropGraph(graph, cascade = false)
        sql shouldContain "false"
    }

    @Test
    fun `graphExists - ag_graph 테이블 조회 SQL을 생성한다`() {
        val sql = AgeSql.graphExists(graph)
        sql shouldContain "ag_catalog.ag_graph"
        sql shouldContain graph
    }

    // ── createVertex ──────────────────────────────────────────────────────

    @Test
    fun `createVertex - properties가 있을 때 CREATE 절과 label을 포함한다`() {
        val sql = AgeSql.createVertex(graph, "Person", mapOf("name" to "Alice", "age" to 30))
        sql shouldContain "CREATE"
        sql shouldContain "Person"
        sql shouldContain "RETURN v"
    }

    @Test
    fun `createVertex - properties가 없을 때도 정상 Cypher를 생성한다`() {
        val sql = AgeSql.createVertex(graph, "Company", emptyMap())
        sql shouldContain "CREATE"
        sql shouldContain "Company"
    }

    @Test
    fun `createVertex - cypher 래퍼 안에 agtype 컬럼 정의가 있다`() {
        val sql = AgeSql.createVertex(graph, "Person", emptyMap())
        sql shouldContain "ag_catalog.cypher"
        sql shouldContain "agtype"
    }

    @Test
    fun `createVerticesBatch - UNWIND row list와 index를 포함한다`() {
        val sql = AgeSql.createVerticesBatch(
            graph,
            "Person",
            listOf(
                AgeSql.BatchVertexRow(0, mapOf("name" to "Alice")),
                AgeSql.BatchVertexRow(1, mapOf("name" to "Bob")),
            ),
        )

        sql shouldContain "UNWIND"
        sql shouldContain "CREATE (v:Person {name: row.p0})"
        sql shouldContain "RETURN row.idx AS idx, v"
        sql shouldContain "ORDER BY idx"
    }

    // ── matchVertices ─────────────────────────────────────────────────────

    @Test
    fun `matchVertices - filter 없을 때 MATCH와 label만 포함한다`() {
        val sql = AgeSql.matchVertices(graph, "Person")
        sql shouldContain "MATCH"
        sql shouldContain "Person"
        sql shouldContain "RETURN v"
    }

    @Test
    fun `matchVertices - filter 있을 때 filter 값을 Cypher에 포함한다`() {
        val sql = AgeSql.matchVertices(graph, "Person", mapOf("name" to "Alice"))
        sql shouldContain "MATCH"
        sql shouldContain "Person"
        sql shouldContain "Alice"
    }

    // ── matchVertexById ───────────────────────────────────────────────────

    @Test
    fun `matchVertexById - WHERE id(v) = N 조건을 포함한다`() {
        val sql = AgeSql.matchVertexById(graph, "Person", 10L)
        sql shouldContain "WHERE id(v) = 10"
        sql shouldContain "RETURN v"
    }

    @Test
    fun `matchVertexById - label을 포함한다`() {
        val sql = AgeSql.matchVertexById(graph, "Employee", 5L)
        sql shouldContain "Employee"
    }

    // ── updateVertex ──────────────────────────────────────────────────────

    @Test
    fun `updateVertex - SET 절이 포함된다`() {
        val sql = AgeSql.updateVertex(graph, "Person", 1L, mapOf("name" to "Bob"))
        sql shouldContain "SET"
        sql shouldContain "v.name"
        sql shouldContain "RETURN v"
    }

    @Test
    fun `updateVertex - WHERE id(v) = N 조건을 포함한다`() {
        val sql = AgeSql.updateVertex(graph, "Person", 7L, mapOf("age" to 25))
        sql shouldContain "WHERE id(v) = 7"
    }

    // ── deleteVertex ──────────────────────────────────────────────────────

    @Test
    fun `deleteVertex - DETACH DELETE를 포함한다`() {
        val sql = AgeSql.deleteVertex(graph, "Person", 3L)
        sql shouldContain "DETACH DELETE v"
    }

    @Test
    fun `deleteVertex - WHERE id(v) = N 조건을 포함한다`() {
        val sql = AgeSql.deleteVertex(graph, "Person", 3L)
        sql shouldContain "WHERE id(v) = 3"
    }

    // ── countVertices ─────────────────────────────────────────────────────

    @Test
    fun `countVertices - count(v) 를 포함한다`() {
        val sql = AgeSql.countVertices(graph, "Person")
        sql shouldContain "count(v)"
    }

    @Test
    fun `countVertices - label을 포함한다`() {
        val sql = AgeSql.countVertices(graph, "Company")
        sql shouldContain "Company"
    }

    // ── createEdge ────────────────────────────────────────────────────────

    @Test
    fun `createEdge - 간선 방향 패턴 a-e-LABEL-방향-b 을 포함한다`() {
        val sql = AgeSql.createEdge(graph, 1L, 2L, "KNOWS", emptyMap())
        sql shouldContain "(a)-[e:KNOWS"
        sql shouldContain "]->(b)"
        sql shouldContain "RETURN e"
    }

    @Test
    fun `createEdge - 시작 및 끝 id 조건을 포함한다`() {
        val sql = AgeSql.createEdge(graph, 10L, 20L, "LIKES", emptyMap())
        sql shouldContain "id(a) = 10"
        sql shouldContain "id(b) = 20"
    }

    @Test
    fun `createEdge - properties가 있을 때 해당 값을 포함한다`() {
        val sql = AgeSql.createEdge(graph, 1L, 2L, "KNOWS", mapOf("since" to 2023))
        sql shouldContain "2023"
    }

    @Test
    fun `createEdgesBatch - endpoint 조회와 edge property row map을 포함한다`() {
        val sql = AgeSql.createEdgesBatch(
            graph,
            "KNOWS",
            listOf(
                AgeSql.BatchEdgeRow(0, 1L, 2L, mapOf("since" to 2024)),
                AgeSql.BatchEdgeRow(1, 2L, 3L, mapOf("since" to 2025)),
            ),
        )

        sql shouldContain "UNWIND"
        sql shouldContain "id(a) = row.fromId"
        sql shouldContain "id(b) = row.toId"
        sql shouldContain "CREATE (a)-[e:KNOWS {since: row.p0}]->(b)"
        sql shouldContain "RETURN row.idx AS idx, e"
    }

    @Test
    fun `matchBatchEdgeEndpoints - create 없이 endpoint index만 반환한다`() {
        val sql = AgeSql.matchBatchEdgeEndpoints(
            graph,
            listOf(AgeSql.BatchEdgeRow(0, 1L, 2L)),
        )

        sql shouldContain "MATCH (a), (b)"
        sql shouldContain "RETURN row.idx AS idx"
        sql shouldNotContain "CREATE"
    }

    // ── matchEdgesByLabel ─────────────────────────────────────────────────

    @Test
    fun `matchEdgesByLabel - edge 패턴과 label을 포함한다`() {
        val sql = AgeSql.matchEdgesByLabel(graph, "KNOWS")
        sql shouldContain "[e:KNOWS"
        sql shouldContain "RETURN e"
    }

    @Test
    fun `matchEdgesByLabel - filter 있을 때 filter 값을 포함한다`() {
        val sql = AgeSql.matchEdgesByLabel(graph, "KNOWS", mapOf("since" to 2020))
        sql shouldContain "2020"
    }

    // ── deleteEdge ────────────────────────────────────────────────────────

    @Test
    fun `deleteEdge - DELETE e를 포함한다`() {
        val sql = AgeSql.deleteEdge(graph, "KNOWS", 99L)
        sql shouldContain "DELETE e"
    }

    @Test
    fun `deleteEdge - WHERE id(e) = N 조건을 포함한다`() {
        val sql = AgeSql.deleteEdge(graph, "KNOWS", 99L)
        sql shouldContain "WHERE id(e) = 99"
    }

    // ── neighbors ─────────────────────────────────────────────────────────

    @Test
    fun `neighbors - OUTGOING 방향이면 start에서 neighbor로 향하는 패턴을 사용한다`() {
        val sql = AgeSql.neighbors(graph, 1L, null, "OUTGOING", 1)
        sql shouldContain "(start)-["
        sql shouldContain "]->(neighbor)"
    }

    @Test
    fun `neighbors - INCOMING 방향이면 neighbor에서 start로 들어오는 패턴을 사용한다`() {
        val sql = AgeSql.neighbors(graph, 1L, null, "INCOMING", 1)
        sql shouldContain "(start)<-["
        sql shouldContain "]-(neighbor)"
    }

    @Test
    fun `neighbors - BOTH 방향이면 양방향 패턴을 사용한다`() {
        val sql = AgeSql.neighbors(graph, 1L, null, "BOTH", 1)
        sql shouldContain "(start)-["
        sql shouldContain "]-(neighbor)"
    }

    @Test
    fun `neighbors - depth가 2 이상이면 가변 길이 패턴을 포함한다`() {
        val sql = AgeSql.neighbors(graph, 1L, null, "OUTGOING", 3)
        sql shouldContain "*1..3"
    }

    @Test
    fun `neighbors - depth가 1이면 가변 길이 패턴을 포함하지 않는다`() {
        val sql = AgeSql.neighbors(graph, 1L, null, "OUTGOING", 1)
        sql shouldNotContain "*1..1"
    }

    @Test
    fun `neighbors - edgeLabel이 있으면 간선 label을 포함한다`() {
        val sql = AgeSql.neighbors(graph, 1L, "KNOWS", "OUTGOING", 1)
        sql shouldContain ":KNOWS"
    }

    // ── shortestPath ──────────────────────────────────────────────────────

    @Test
    fun `shortestPath - LIMIT 1을 포함한다`() {
        val sql = AgeSql.shortestPath(graph, 1L, 5L, null, 5)
        sql shouldContain "LIMIT 1"
    }

    @Test
    fun `shortestPath - 시작 및 끝 id 조건을 포함한다`() {
        val sql = AgeSql.shortestPath(graph, 2L, 8L, null, 4)
        sql shouldContain "id(a) = 2"
        sql shouldContain "id(b) = 8"
    }

    @Test
    fun `shortestPath - edgeLabel이 있으면 간선 label을 포함한다`() {
        val sql = AgeSql.shortestPath(graph, 1L, 3L, "FRIENDS", 3)
        sql shouldContain ":FRIENDS"
    }

    // ── allPaths ──────────────────────────────────────────────────────────

    @Test
    fun `allPaths - LIMIT가 없다`() {
        val sql = AgeSql.allPaths(graph, 1L, 5L, null, 5)
        sql shouldNotContain "LIMIT"
    }

    @Test
    fun `allPaths - 시작 및 끝 id 조건을 포함한다`() {
        val sql = AgeSql.allPaths(graph, 3L, 9L, null, 3)
        sql shouldContain "id(a) = 3"
        sql shouldContain "id(b) = 9"
    }

    @Test
    fun `allPaths - edgeLabel이 있으면 간선 label을 포함한다`() {
        val sql = AgeSql.allPaths(graph, 1L, 4L, "CONNECTS", 2)
        sql shouldContain ":CONNECTS"
    }
}
