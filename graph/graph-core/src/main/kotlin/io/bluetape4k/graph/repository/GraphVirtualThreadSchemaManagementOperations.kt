package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphIndex
import java.util.concurrent.CompletableFuture

/**
 * Virtual Thread schema management를 위한 optional surface다.
 *
 * schema DDL과 metadata 조회는 [GraphSchemaManagementOperations]의
 * [io.bluetape4k.graph.schema.GraphSchemaManager]를 virtual thread에서 실행한다.
 * schema를 지원하지 않는 backend는 이 surface를 facade capability로 광고하지 않고
 * 명시적인 [UnsupportedOperationException] future를 반환한다.
 */
interface GraphVirtualThreadSchemaManagementOperations {

    fun createIndexAsync(label: String, property: String): CompletableFuture<Unit>

    fun createUniqueConstraintAsync(label: String, property: String): CompletableFuture<Unit>

    fun dropIndexAsync(label: String, property: String): CompletableFuture<Unit>

    fun listIndexesAsync(): CompletableFuture<List<GraphIndex>>

    fun listConstraintsAsync(): CompletableFuture<List<GraphConstraint>>
}
