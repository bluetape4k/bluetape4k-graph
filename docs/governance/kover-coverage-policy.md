# Kover Coverage 정책

## 현재 상태

`bluetape4k-graph`는 production module의 Kover report를 aggregate하고,
benchmark/example module은 coverage aggregation에서 제외한다.

## 정책

상태: report-only transition.

Graph database backend는 외부 runtime이 필요하고 pure graph-io module과 coverage
profile이 다르다. Coverage gate는 module-level이어야 한다.

## Threshold 계획

- Kover는 build gate가 아니라 trend signal로 다룬다.
- Nightly XML report와 기존 coverage artifact upload를 사용해 coverage regression을
  식별한다.
- module에 coverage repair가 필요하면 집중 이슈를 연다. 기본 enforcement mechanism으로
  failing threshold를 도입하지 않는다.
- benchmark와 example module은 production coverage gate 밖에 둔다.

## CI/Nightly 계약

Nightly는 coverage artifact를 업로드하고 trend visibility를 유지한다. 향후 이슈가
명시적으로 해당 gate를 다시 도입하기 전까지 CI와 Nightly는 고정 coverage percentage
미달만으로 실패해서는 안 된다.
