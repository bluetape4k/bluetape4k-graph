# #614 GraphML property 타입 7-Tier review

## 범위와 기준

- Issue: [#614](https://github.com/bluetape4k/bluetape4k-graph/issues/614)
- Branch: `fix/issue-614-graphml-property-types`
- Base: `develop` exact head
  `a76462766edc34f2789a424847266105f375c055`
- Scope: GraphML sync/suspend exporter, StAX writer, scalar property 타입 정책,
  round-trip·failure 회귀, EN/KO module 문서
- Review: native `code-reviewer` 독립 lane이 base 대비 전체 변경을 검토했다.

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·API·ABI | PASS | 변경은 GraphML module 내부 writer와 새 internal 타입 추론기로 제한했다. Public constructor와 함수 signature는 바꾸지 않았고 module compile/check가 통과했다. |
| T2 기능·계약 | PASS | Node와 edge별로 `Int`, `Long`, `Float`, `Double`, `Boolean`, `String`을 추론해 `attr.type`에 기록하고 값·타입 round-trip을 검증한다. |
| T3 실패·옵션 | PASS | 동일 scope/key의 mixed non-null JVM 타입은 XML sink를 열기 전에 거부한다. Null은 추론에서 제외하되 `includeEmptyProperties`의 기존 출력 계약을 유지한다. |
| T4 보안·노출 | PASS | Mixed-type 예외는 scope, key, 타입 이름만 포함하고 property 원문을 노출하지 않는다. StAX escaping과 importer 보안 경계는 변경하지 않았다. |
| T5 수명주기·성능 | PASS | 타입 표는 repository chunk를 spool에 기록하기 전에 key 수에 비례해 누적한다. 두 번째 backend 조회, 전체 record 복사, 새 resource ownership은 추가하지 않았다. |
| T6 ecosystem·패턴 | PASS | 기존 `GraphMlAttrType`, `GraphIoRecordSpool`, `GraphExportOptions.includeEmptyProperties`, `bluetape4k-assertions`를 재사용했다. |
| T7 테스트·문서·인계 | PASS/WATCH | Sync/suspend bulk와 internal writer 회귀, module README EN/KO, KDoc, CHANGELOG, lesson을 갱신했다. Hosted exact-head CI와 train merge는 PR 이후 gate다. |

## 독립 리뷰 finding 처리

| 심각도 | Finding | 처리 |
|---|---|---|
| P1 | Null property를 항상 생략해 `includeEmptyProperties=true` 계약을 무시함 | 수정. Flag를 sync/suspend writer session까지 전달하고 `true`/`false` XML 출력 회귀를 추가했다. |
| P2 | 생성 XML의 `attr.type` 선언을 직접 검증하지 않음 | 수정. 지원 타입 전체와 unsupported `string` fallback 선언을 XML에서 확인한다. |
| P2 | Repository chunk 사이 mixed-type 회귀가 없음 | 수정. `exportChunkSize=1`에서 두 번째 chunk 타입 충돌과 sink 미생성을 검증한다. |
| P3 | 수정한 KDoc에 한글·영문 설명이 혼재함 | 수정. Public KDoc의 parameter 설명을 한국어 기술 문체로 정렬했다. |

## 검증 증거

- RED: 지원 타입·mixed 타입·null 정책 회귀 3건 실패를 확인했다.
- `./gradlew :bluetape4k-graph-io-graphml:test --no-daemon --console=plain`:
  66 tests, failures/errors/skipped `0/0/0`.
- `./gradlew :bluetape4k-graph-io-graphml:check --no-daemon --console=plain`:
  test, Detekt, Kover 포함 `BUILD SUCCESSFUL`.
- `git diff --check`: PASS.
- 변경 테스트의 금지 assertion scan: 0건. 신규 exception 회귀는
  `io.bluetape4k.assertions.assertFailsWith`를 사용한다.
- Korean terminology audit: 4개 reader-facing 파일, finding 0건.

## DoD Status

- [x] P0/P1 local finding을 모두 해소했다.
- [x] 지원 scalar 타입의 vertex/edge 값과 JVM 타입을 왕복 검증했다.
- [x] Mixed, null, empty string, unsupported 타입 정책을 테스트와 문서에 고정했다.
- [x] Sync/suspend spool 경로와 `includeEmptyProperties` 계약을 검증했다.
- [ ] PR exact-head hosted CI와 live review/thread read-back은 PR 생성 후 수행한다.

최종 로컬 판정: **PASS/WATCH**. 구현·로컬 검증은 완료했고, hosted evidence와
stacked train merge는 후속 gate로 남긴다.
