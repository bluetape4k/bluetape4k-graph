# 이슈 #536 bounded chunk capability 정렬 설계

## 문제

`GraphCapability.CHUNKED_READ`와 `CHUNKED_EXPORT`는 현재 repository interface의
chunk API 존재만으로 계산된다. 동기 기본 구현은 `find*ByLabel`이 반환한 전체
`List`를 먼저 만든 뒤 chunk로 나누므로, 호출자는 chunk 크기를 지정해도 원본
조회가 bounded라고 판단할 수 없다. 반면 TinkerGraph는 Gremlin traversal을
`hasNext`/`next`로 소비해 chunk 크기만큼만 애플리케이션 버퍼에 보관한다.

현재 근거:

- `GraphCapabilities.from`은 repository interface 존재만 보고 두 capability를
  추가한다.
- `GraphVertexRepository`와 `GraphEdgeRepository`의 기본 chunk 구현은 전체
  `List`를 `asGraphExportChunks`로 나눈다.
- TinkerGraph만 동기 `find*ByLabelChunked`를 traversal iterator로 override한다.
- AGE, Neo4j, Memgraph, FalkorDB는 동기 bounded paging/cursor override가 없으므로
  현재 API chunking은 heap bound를 보장하지 않는다.
- GraphML README는 모든 backend가 전체 record list를 materialize하지 않는다고
  설명해 현재 구현보다 강한 보장을 주장한다.

## 목표와 경계

목표는 API chunking과 backend bounded 실행을 capability/constraint에서 분리하고,
모든 지원 backend와 graph-io 문서가 같은 의미를 사용하게 만드는 것이다.

이번 범위에는 새로운 backend paging query, cursor 의존성, 데이터베이스별 SQL/Cypher
최적화, graph-io exporter의 자동 backend 선택을 포함하지 않는다. 이 작업은 현재
구현이 실제로 증명하는 bounded 범위만 공개하고, 네 container backend의 미지원
상태를 명시하는 데 집중한다.

## 대안 비교

### 대안 A — 기존 capability의 constraint 문자열만 변경

`CHUNKED_*`에 `api-chunking-only`를 추가하고 backend별 문자열을 문서화한다.
변경량은 작지만, 호출자가 bounded 보장을 기계적으로 확인할 별도 flag가 없어
문자열 해석에 의존한다.

### 대안 B — bounded capability를 별도 분리한다 (권장)

`BOUNDED_CHUNKED_READ`와 `BOUNDED_CHUNKED_EXPORT`를 추가하고,
`GraphBoundedChunkOperations` marker를 실제 bounded 구현에만 적용한다.
기존 `CHUNKED_*`는 API 호환성을 유지하며 `api-chunking-only` constraint를 갖고,
marker를 가진 구현은 별도 bounded capability와 native traversal constraint를
추가로 광고한다. TinkerGraph가 첫 reference backend가 되고, AGE/Neo4j/Memgraph/
FalkorDB는 bounded flag 없이 명시적인 제한을 유지한다.

### 대안 C — 모든 동기 backend에 paging 구현을 동시에 추가

backend별 `SKIP/LIMIT`, Cypher cursor, AGE SQL, FalkorDB query semantics를
구현하면 강한 보장을 제공할 수 있다. 그러나 backend마다 결과 집합과 순서 보장,
cursor lifecycle이 달라 단일 이슈에서 검증 범위가 과도하게 커지고, 잘못된
paging은 누락·중복을 만들 수 있다. #536에서는 채택하지 않고 후속 backend별
이슈로 분리한다.

## 선택한 설계

1. `GraphCapability`에 `BOUNDED_CHUNKED_READ`와 `BOUNDED_CHUNKED_EXPORT`를
   추가한다. 기존 `CHUNKED_READ`와 `CHUNKED_EXPORT`의 의미는 API가 chunk를
   반환한다는 뜻으로 유지한다.
2. `GraphBoundedChunkOperations` marker와 한국어 KDoc을 graph-core에 추가한다.
   marker는 소스 조회가 전체 label 결과를 먼저 materialize하지 않고 chunk 경계를
   지킨다는 backend 구현자의 명시적 약속이다.
3. `GraphCapabilities.from`은 기존 repository marker로 `CHUNKED_*`를 추가하고,
   `GraphBoundedChunkOperations`가 있을 때만 bounded 두 capability를 추가한다.
   bounded capability constraint는 `native-traversal-bounded`, 기존 capability
   constraint는 `api-chunking-only`를 포함한다.
4. TinkerGraph sync/suspend 구현에 marker를 적용한다. Virtual Thread adapter는
   기존 delegate capability projection을 통해 bounded flag와 constraint를
   보존한다.
5. 공용 conformance fixture와 core capability 테스트는 다음을 증명한다.
   - TinkerGraph는 bounded 두 capability를 보고한다.
   - AGE/Neo4j/Memgraph/FalkorDB는 `CHUNKED_*`만 보고하고 bounded flag를 보고하지
     않는다.
   - 모든 backend의 chunk 결과 크기와 순서는 기존 계약을 유지한다.
   - default list fallback은 API chunking일 뿐 bounded가 아니다.
6. root README, graph-core README, GraphML README의 영어/한국어 문서에서
   `BOUNDED_*` 선택 규칙을 설명하고, backend fallback이 heap bound를 보장하지
   않는다고 명시한다. GraphML의 “never materializes” 단정은 실제 capability를
   확인하는 조건부 문장으로 고친다.

## 실패 모드와 대응

- **잘못된 bounded 광고:** marker가 없는 backend에서 bounded flag가 나오면
  conformance capability equality가 실패하고 해당 backend는 즉시 수정 대상이
  된다.
- **기존 호출자 ABI/동작 변화:** 기존 enum과 repository method는 제거하지 않고,
  새 capability만 추가한다. 기존 chunk 결과의 크기·순서 테스트를 유지한다.
- **문서가 heap bound를 과장:** GraphML/root README에 fallback 조건과 capability
  선택 규칙을 함께 기록하고, 문서 grep 및 diff review에서 단정 문장을 제거한다.
- **decorator capability 손실:** 기존 capability mapping 규칙과 virtual-thread
  projection 테스트를 확장해 bounded flag도 delegate와 동일한지 확인한다.

## 수용 기준

- [ ] `GraphCapability`가 API chunking과 bounded 실행을 별도 flag/constraint로
  표현한다.
- [ ] TinkerGraph sync/suspend/virtual-thread projection이 bounded capability를
  보고하고, AGE·Neo4j·Memgraph·FalkorDB sync conformance는 bounded flag를
  보고하지 않는다.
- [ ] core 기본 fallback과 backend conformance가 chunk 크기, 순서, bounded
  미지원 상태를 assertions로 검증한다.
- [ ] GraphML/root/graph-core 영어·한국어 문서가 fallback의 heap bound 부재를
  명시하고 API 사용 예를 같은 의미로 설명한다.
- [ ] 변경 Kotlin 코드와 테스트가 `bluetape4k-assertions`와 `$bluetape-kotlin-patterns`
  계약을 지키며 compile, detekt, targeted/full affected tests, `git diff --check`
  및 독립 7-Tier review가 통과한다.

## DoD

정확한 feature branch에서 위 수용 기준을 모두 fresh evidence로 확인하고,
P0/P1이 없는 Lore commit을 만든다. PR 생성·push·merge·container backend 실행은
이번 작업 범위에 포함하지 않으며, container 검증은 별도 순차 명령으로 실행해
환경/수명주기 결과를 기록한다.
