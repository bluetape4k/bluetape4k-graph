package io.bluetape4k.graph.repository

import java.util.concurrent.CompletableFuture

/**
 * Virtual Thread에서 하나의 backend transaction block을 실행하는 optional surface다.
 *
 * block 전체가 같은 virtual thread에서 실행되며, backend의 commit/rollback과
 * 예외 전파는 [GraphTransactionalOperations] 계약을 따른다. 이 facade는 borrowed
 * delegate를 닫지 않는다. future 취소와 timeout은 실행 중인 동기 backend 작업을
 * 강제로 종료한다고 보장하지 않으며, 호출자는 backend의 interruption 계약을
 * 별도로 확인해야 한다.
 */
interface GraphVirtualThreadTransactionalOperations {

    fun <T> transactionAsync(block: GraphTransactionScope.() -> T): CompletableFuture<T>
}
