# 2026-08-14 issue #470 Virtual Thread bulk adapter close 전파

## Context

`VirtualThreadGraphBulkAdapter.wrapImporter`와 `wrapExporter`가 동기
delegate를 익명 Virtual Thread adapter로 감싸면서 `AutoCloseable.close()`를
재정의하지 않았다. 따라서 caller가 비동기 wrapper를 닫아도 delegate의 자원
lifecycle이 종료되지 않았다.

## Decision or Finding

- importer/exporter wrapper에 공통 `CloseOnce` guard를 두고 첫 `close()`만
  동기 delegate에 전달한다.
- 반복 `close()`는 no-op으로 고정해 delegate 중복 종료를 막는다.
- adapter는 source/sink를 직접 소유하지 않으며, 소유권과 비동기 작업 중 close
  시점의 의미는 동기 delegate 계약을 따른다고 KDoc에 명시한다.

## Outcome

Virtual Thread bulk adapter의 close가 동기 delegate까지 도달하고, `AtomicBoolean`
guard로 반복 close 호출이 한 번으로 제한된다. 비동기 실행, cancellation,
caller-owned stream 정책은 기존 계약을 유지한다.

## Miss or Surprise

인터페이스의 default `close()`는 컴파일상 정상이라 기존 import/export 테스트가
모두 통과했지만 실제 delegate 종료 누락은 드러나지 않았다. 따라서 close spy를
추가해 구현 전 delegate 호출 0회의 RED를 확인한 뒤 최소 수정했다.

## Verification

- RED: importer/exporter wrapper의 반복 close 회귀 2개가 delegate 호출 0회로
  실패했다.
- GREEN: 동일 targeted suite 12개 통과.
- Module GREEN: `:bluetape4k-graph-io-core:test` 131개 통과.
- Static GREEN: `:bluetape4k-graph-io-core:detekt` 및 `:bluetape4k-graph-io-core:check`
  (Kover 포함) 통과.
- `git diff --check` 통과.
- 외부 backend Testcontainers와 원격 CI는 이 core adapter 변경의 필수 검증
  범위가 아니므로 PR 단계로 남긴다.

## Future Guidance

새로운 Virtual Thread adapter가 `AutoCloseable` delegate를 감쌀 때는
wrapper close 전파와 반복 close 정책을 함께 문서화하고, delegate close spy
회귀를 먼저 작성한다. source/sink를 caller가 소유하는 경로에는 adapter가
스트림을 직접 닫지 않는 계약을 유지한다.
