package io.bluetape4k.graph.age

import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.graph.schema.UnsupportedGraphSchemaManager

/**
 * Apache AGE용 스키마 관리자.
 *
 * AGE는 Neo4j/Memgraph와 같은 portable schema DDL을 제공하지 않는다. PostgreSQL label table
 * expression index는 AGE image와 agtype 연산자에 강하게 결합되므로, 검증되지 않은 DDL을 성공으로
 * 가장하지 않고 명시적으로 실패시킨다.
 */
class AgeGraphSchemaManager: GraphSchemaManager by UnsupportedGraphSchemaManager(
    backendName = "Apache AGE",
    reason = "Apache AGE schema indexes require PostgreSQL-side agtype expression indexes that are not yet portable.",
)
