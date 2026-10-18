# PR #656 인라인 대체 리뷰

## 범위와 리뷰 경로

기준은 `develop`의 `667a8c93bee7136269a70b26aa91d33567ad7d91`이며, PR의 기존
head `c73190626b19919d1ca1ebfebd73540871c23a0d`와 이번 재개 작업의 로컬 변경을
검토했다. 전체 변경은 graph-core, graph-io, 데이터베이스 백엔드, Ktor·Spring Boot,
예제, 벤치마크와 문서를 포함하는 전 저장소 리팩터다. `.omc/self-improve`의 생성
상태·계획 파일 삭제도 diff 범위에 포함했다.

독립 리뷰 lane `independent_review_r4`는 사용량 제한(usage limit)으로 초기 실행과
지연 재시도가 모두 판정을 내지 못했다. 제한된 liveness probe 뒤 lane을 중단하고 실패를
workflow receipt에 기록했다. 이 문서는 workspace workflow의 inline fallback이며 독립
리뷰 attestation이 아니다.

고위험 검토는 public JVM API와 binary descriptor, graph-io record framing·stream
소유권·예외 보존, AGE/FalkorDB/Neo4j 실행 경계와 민감 로그, TinkerPop 자원 정리,
백엔드별 테스트 연결을 중심으로 수행했다. repository 전체 compile/build와 관련
module test 증거도 대조했다.

## 발견 사항과 조치

| 심각도 | 위치 | 판단과 조치 |
| --- | --- | --- |
| P1 | `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphPath.kt:179`; `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphAlgorithmRepository.kt:84`; `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/MissingWeightPolicy.kt:97` | 기존 `List` factory, `BfsDfsOptions` BFS/DFS API, `MissingWeightException` 상위 타입과 2-argument 생성자 descriptor를 복원했다. sync·suspend·virtual-thread 경로에 호환 오버로드를 두고 기존 develop 산출물과 확인한 descriptor 손실이 없음을 검증했다. |
| P1 | `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/extension/Jackson2OkioExtensions.kt:1`; `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/extension/Jackson3OkioExtensions.kt:1` | 두 확장 파일이 합성 facade `JacksonOkioExtensionsKt`를 제공하도록 JVM file facade를 복원했다. 테스트가 기존 10개 overload를 확인한다. |
| P2 | `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/algo/internal/TreeTraverser.kt:51`; AGE/FalkorDB/Neo4j query 실행 코드 | traversal vertex properties와 query parameter가 trace/debug 로그에 직렬화될 수 있던 로그를 제거하거나 구조 정보만 남겼다. |
| P2 | `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoRecordSpool.kt:158,330` | replay input 등록·제거·스냅샷·close가 서로 다른 동기화 방식으로 경쟁할 수 있었다. 하나의 lock으로 registry 접근을 보호하고, 동시 close 중에도 복사한 입력을 모두 닫는다. |
| P2 | `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoRecordSpool.kt:393,399` | 필수 문자열의 빈 값은 허용하고, nullable 문자열 sentinel `-1`과 최대 길이만 허용한다. 잘못된 음수 길이는 배열 할당 오류 대신 입력 검증 오류로 거부한다. |
| P2 | `graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/sql/AgeTypeParser.kt:43,281`; `graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperations.kt:573` | AGE ID 필드는 정수 `Long` 범위를 검증하고 label/type을 확인한다. suspend algorithm Flow는 수집 시점에 실행되며 blocking 조회는 `Dispatchers.IO`에서 수행한다. |
| P2 | `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/vt/VirtualThreadOptionalAdapters.kt:143` | chunk 수집 중 예외가 나더라도 close를 실행하고, close 실패는 기존 primary exception의 suppressed 항목으로 보존한다. |
| P2 | `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphImportReport.kt:43`; `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/VirtualThreadCsvImportErrorTest.kt:130` | 음수 검증에서 오류가 난 실제 필드 이름을 보고하도록 정정했다. CSV virtual-thread elapsed 양수 검증은 나노초 정밀도를 사용한다. |

현재 발견한 P0/P1은 수정됐고 미해결 P0/P1은 없다.

## 7-Tier 판정

| Tier | 판정 | 근거 |
| --- | --- | --- |
| 계약·범위 | PASS | public API 호환 경로, graph-io 입력 경계, backend 실행 경계와 전체 PR 범위를 확인했다. 생성 상태·계획 파일 삭제도 diff에 포함했다. |
| API·ABI | PASS / WATCH | 기존 호출 및 선택한 JVM descriptor를 보존했다. 전체 공개 jar descriptor 전수 비교는 남아 있다. |
| 구현·자원 | PASS | replay input 동기화, stream 종료, primary/suppressed 예외 관계와 AGE Flow 수집 시점 실행을 검토했다. |
| 입력·보안 | PASS | AGE 숫자 필드와 spool record 길이를 검증하고 vertex property/query parameter 로깅을 제거했다. |
| 테스트 | PASS / WATCH | 변경 영향 module 테스트가 통과했다. 기존 disabled 6건과 정확한 PR HEAD의 hosted test는 별도 항목으로 남겼다. |
| 문서·사용성 | PASS | EN/KO README, WIP, CHANGELOG, lesson과 이 리뷰 기록의 용어·상태 주장을 확인했다. |
| 빌드·운영 | PASS / WATCH | 전체 build가 통과했다. 신규 커밋의 exact-head CI와 merge gate는 push 뒤 확인해야 한다. |

## 검증 증거

- `./gradlew :bluetape4k-graph-core:test :bluetape4k-graph-core:detekt :bluetape4k-graph-io-core:test :bluetape4k-graph-io-core:detekt --console=plain` — `BUILD SUCCESSFUL`; graph-core 432개(실패 0, 오류 0, skip 6), graph-io-core 191개(실패 0, 오류 0, skip 0).
- `./gradlew build -x test --max-workers=1 --console=plain` — 저장소 전체 `BUILD SUCCESSFUL`; 예제, 벤치마크, Ktor, Spring Boot를 포함해 161 task 완료 또는 up-to-date.
- graph-io CSV, Jackson2/3, GraphML, Okio, AGE, FalkorDB, Memgraph, Neo4j, TinkerPop 대상 테스트와 Detekt는 앞선 직렬 검증에서 통과했다. graph database 테스트는 Colima/Testcontainers provider 확인 후 모듈별로 실행했다.
- `javap -public -s`를 사용한 선택 ABI 비교는 정확한 develop base 산출물과 수행했고, core API, graph-io report/options, `JacksonOkioExtensionsKt` facade에서 기존 descriptor 손실이 없었다. 전체 공개 jar descriptor 전수 비교는 하지 않았다.
- `git diff --check` 통과.

graph-core에서 skip된 여섯 테스트는 `BfsDfsRunnerTest`의 기존 `@Disabled("use GraphVisitorTest")` 케이스다. 실패로 분류하지 않으며, 해당 여섯 케이스의 별도 실행은 이 검증에서 하지 않았다.

## 잔여 위험과 게이트

- 독립 리뷰어 판정은 확보하지 못했다. 본 문서는 fallback이며 독립 attestation으로 사용할 수 없다.
- 위 ABI 확인은 선택한 공개 descriptor에 한정되고 전체 jar 전수 비교는 미실행이다.
- 새 커밋의 GitHub exact-head CI는 push 전이라 아직 실행되지 않았다. 이전 head의 실패 check는 현재 수정 결과의 증거가 아니다.
- merge와 auto-merge는 수행하지 않는다. merge는 exact-head CI, 리뷰/thread read-back, 별도 신선 승인 후에만 진행한다.

## 최종 판정

Inline verdict: PASS; P0=0, P1=0.

확인된 P1/P2 발견 사항은 조치했고, 위 검증 한계와 exact-head CI 대기를 별도로 기록한다.
