# 이슈 #616 - Kover 집계 증거를 fail-closed로 보장

## Context

기존 `aggregate-kover-coverage.py`는 XML 파싱, `INSTRUCTION` counter 조회, 숫자
변환 실패를 `(0, 0)`으로 바꾸었다. 손상된 report가 정상적인 `0.00%` row처럼
보였고, workflow의 `continue-on-error`와 빈 artifact 경로가 partial summary를
숨길 수 있었다.

## Decision or Finding

- `report.xml`과 `reportJvm.xml`을 모두 지원하되, malformed XML, counter 부재·중복,
  잘못된 숫자와 음수 값은 명시적 오류로 기록한다.
- workflow가 기대하는 `coverage-*` artifact manifest를 aggregation 입력으로 전달하고,
  upload/download와 report 존재 여부를 fail-closed로 검증한다.
- Kover generation은 기존 report-only 정책을 유지한다. coverage percentage threshold는
  추가하지 않고 evidence 완전성만 차단 조건으로 둔다.

## Outcome

유효한 report는 계속 module summary에 표시되며, 유효하지 않은 report나 누락된
artifact가 있으면 summary에 원인을 남기고 coverage job이 실패한다. 명시적인
`0/0` instruction counter는 실제 zero-instruction 결과로 보존해 파싱 실패와
구분한다.

## Verification

- `python3 .github/scripts/test_aggregate_kover_coverage.py` — aggregator contract
  fixture 17건 통과.
- `python3 .github/scripts/test_ci_routing_policy.py` — routing contract 6건 통과.
- `python3 -m unittest discover -s .github/scripts -p 'test_*.py'` — 전체 script
  테스트 28건 통과.
- `python3 -m py_compile .github/scripts/aggregate-kover-coverage.py .github/scripts/test_aggregate_kover_coverage.py` — 통과.
- `./gradlew detekt --no-daemon` — BUILD SUCCESSFUL.
- `actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml` — 통과.
- `git diff --check` — 통과.
- Hosted exact-head CI와 Full Nightly는 PR 생성 후 확인한다.

## Future Guidance

새 coverage job이나 matrix shard를 추가할 때 test job의 artifact upload, `needs` 기반
expected manifest, aggregation fixture를 한 변경에서 함께 갱신한다. `continue-on-error`
는 report-only generation에만 남기고, artifact upload/download 및 XML aggregation의
완전성 검증에는 사용하지 않는다.
