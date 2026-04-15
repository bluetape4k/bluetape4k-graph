package io.bluetape4k.graph.age.sql

import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotContain
import org.junit.jupiter.api.Test

class AgeSqlTest {

    private val graph = "test_graph"

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
    fun `createEdge - 방향 패턴 (a)-[e:LABEL]->(b) 을 포함한다`() {
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
    fun `neighbors - OUTGOING 방향이면 (start)-[...]->(neighbor) 패턴을 사용한다`() {
        val sql = AgeSql.neighbors(graph, 1L, null, "OUTGOING", 1)
        sql shouldContain "(start)-["
        sql shouldContain "]->(neighbor)"
    }

    @Test
    fun `neighbors - INCOMING 방향이면 (start)<-[...]-(neighbor) 패턴을 사용한다`() {
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
