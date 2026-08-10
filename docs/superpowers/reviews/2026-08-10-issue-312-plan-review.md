# Issue #312 계획 리뷰

검토 대상은 다음 현재 문서 snapshot이다.

- 설계: `4f7212762ea7016b317d4efa0199dd3d655b35b8f0613c85320c4a3e60120dc7`
- 계획: `33a483af151f4692937351a37b1e1c6f6320b83886b5cf0796c21350fdcea99f`

실제 backend adapter, URI/file dereference, staging I/O, Testcontainers는 이
이슈의 범위가 아니며 후속 adapter 이슈로 남긴다.

## 관점별 판정

| 관점 | P0 | P1 | P2 | 판정 | 핵심 근거 |
|---|---:|---:|---:|---|---|
| Developer/API | 0 | 0 | 0 | PASS | Kotlin 2.4/JDK 25/Gradle 9.7 기준, `ReentrantLock/Condition`, R/V 분리, Serializable 모델, `CONTRACT_VIOLATION`, nullable merge/AtomicBoolean 경계가 고정되었다. |
| Security | 0 | 0 | 2 | PASS | 고정 비민감 `native-bulk-load` label, source/toString 비노출, fixed-code redaction, URI default deny와 allowlist cardinality/aggregate bounds를 확인했다. 후속 adapter의 URI canonicalization 세부(IP literal/default port)는 후속 범위다. |
| Performance | 0 | 0 | 0 | PASS | progress callback 상한과 interval 경계, overflow-safe deadline, 단일 observer in-flight/circuit, pending timeout 1회 retry를 확인했다. 실제 backend benchmark는 범위 밖이다. |
| Stability | 0 | 0 | 0 | PASS | validation rollback 단일 owner/pending completion, deferred cleanup owner, listener/load deadline, completion 이후 `CLOSED` publish를 확인했다. expired observer는 실제 worker completion callback까지 in-flight를 유지한다. |
| Operator/Ops | 0 | 0 | 0 | PASS | close timeout diagnostic pending 보존, expired async dispatch, single-inflight, retry CAS 순서, global diagnostic correlation을 확인했다. 최신 수정으로 timeout worker 종료 전 새 observer worker를 만들지 않는다. |
| User/Caller | 0 | 0 | 0 | PASS | public request/report/progress 계약, listener 원본 primary 보존, bounded caller 복귀, unsupported fixed error, README 경계가 계획에 반영되었다. 기존 portable importer API는 변경하지 않는다. |

## 수정 이력

1. `Any.wait/notifyAll` 예시를 `ReentrantLock/Condition`으로 바꾸고 현재
   toolchain을 Kotlin 2.4/JDK 25/Gradle 9.7로 고정했다.
2. raw `R`와 validated `V`를 분리하고, validation 이후 execution에는 typed
   validated handle과 동일 cancellation token만 전달하도록 했다.
3. request/capabilities를 받는 중앙 report factory와 progress/report
   postcondition을 추가하고, SPI 계약 위반은 `CONTRACT_VIOLATION`으로 분리했다.
4. provisional validation rollback과 loader/source deferred cleanup을 단일
   deadline-bound owner call로 만들고, 실제 completion 전 `CLOSED`를 금지했다.
5. diagnostic observer에 secret-free fixed fields와 load/close correlation을
   적용했다. expired deadline의 observer는 caller를 막지 않는 비동기 경로로
   보존하되, timeout 시 실제 worker completion callback까지 in-flight를 유지한다.
   pending `CANCELLED/TIMEOUT` 이벤트는 CAS로 한 번만 retry한다.

## 최종 gate

- P0: 0
- P1: 0
- P2: 2 (후속 adapter의 URI canonicalization 세부; 이번 core SPI에서
  dereference하지 않으므로 비차단)
- P3: 0
- 설계/계획 판정: **PASS — implementation-ready**

구현 시작 후에는 계획의 RED 테스트를 먼저 실행하고, targeted nativebulk
테스트·`compileKotlin`·전체 `graph-io-core` 테스트·`git diff --check`를
순차 검증한다.
