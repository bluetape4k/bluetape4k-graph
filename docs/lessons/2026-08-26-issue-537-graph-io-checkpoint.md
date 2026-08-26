# Issue #537 graph-io checkpoint lesson

## 결정

format importer마다 checkpoint를 임시 구현하지 않고 core의 identity·phase·claim
session을 공통 사용했다. resume는 source identity와 version을 검증하며, importer의
정상 종료·실패·취소 경계에서 checkpoint claim을 명시적으로 release한다.

## 결과와 검증

- CSV, Jackson2/3, GraphML, OkIO의 sync/suspend lifecycle 회귀를 추가했다.
- duplicate/conflict, interrupted phase, external ID mapping, stale claim fencing을
  `bluetape4k.assertions` 기반 테스트로 고정했다.
- graph-io core/format 전체 test와 Detekt가 `BUILD SUCCESSFUL`, `0 failed`로 통과했다.

## 다음 방어선

공유 checkpoint store는 atomic claim/fencing을 제공해야 하며, non-atomic backend에서
exactly-once를 과장하지 않는다. container와 hosted 결과는 stacked PR receipt로 계속
확인한다.
