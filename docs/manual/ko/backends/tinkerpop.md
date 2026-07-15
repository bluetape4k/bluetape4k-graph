# TinkerPop과 TinkerGraph

0.5.1 모듈은 TinkerGraph를 프로세스 안에 띄우고 공통 repository 계약을 TinkerPop/Gremlin에 연결한다. 로컬 검증과 알고리즘 테스트는 빠르지만, 원격 서버의 지연·내구성·클러스터·트랜잭션을 재현하지는 않는다.

동기 코드는 [`TinkerGraphOperations.kt`](../../../../graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt), 코루틴 연동은 [`TinkerGraphSuspendOperations.kt`](../../../../graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphSuspendOperations.kt)에서 시작한다. CRUD와 순회는 [`TinkerGraphOperationsTest.kt`](../../../../graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt), commit과 rollback은 [`TinkerGraphTransactionTest.kt`](../../../../graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphTransactionTest.kt)가 검증한다.

단위 테스트, 학습, 첫 모델링에는 좋은 선택이다. 다른 백엔드로 옮길 때는 merge, batch, 스키마, 트랜잭션, 속성 형식, 순회를 그 백엔드에서 다시 실행한다. 메모리 테스트 통과는 도메인 논리 근거이지 운영 준비 근거가 아니다.
