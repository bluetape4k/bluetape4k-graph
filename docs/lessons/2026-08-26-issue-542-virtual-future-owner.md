# #542 graph-core Virtual Thread helper owner lesson

## Context

`graph-core`는 이미 `bluetape4k-core`를 사용하면서도
`io.bluetape4k.concurrent.virtualthread` package에
`virtualFutureOfNullable`을 다시 정의하고 있었다. Kotlin source import는 자연스럽게
보이지만 graph-local `CompletableFutureNullableSupportKt` generated class가 추가되어
공식 helper와 owner가 갈라졌다.

## Decision

graph-local source를 삭제하고 공식 `bluetape4k-core` helper를 그대로 사용한다.
새 dependency나 shading은 추가하지 않는다. source-level import는 유지하되 generated
owner를 직접 참조하는 외부 consumer의 재컴파일 경계를 README에 기록한다.

## Outcome

- ownership guard가 local generated owner 부재와 official owner 존재를 검증한다.
- `virtualFutureOfNullable { null }` 결과를 Bluetape assertion으로 확인한다.
- graph-core full 379 tests, compile, Detekt, diff-check를 통과한다.
- upstream split-package는 #563, external generated-owner ABI는 #562로 분리한다.

## Misses and surprises

- `Class.forName` guard는 source 삭제만으로 충분하지 않으므로 clean/re-run output을
  함께 확인해야 한다.
- `inline` helper라도 generated top-level owner를 직접 호출한 precompiled consumer는
  source compatibility와 별개로 재컴파일이 필요하다.
- graph-local duplicate를 제거해도 `bluetape4k-core`와 `bluetape4k-virtualthread-api`
  사이의 upstream split-package는 남는다. package ownership 문제를 한 단계로
  합치지 않는다.

## Verification

```text
TDD RED: local owner가 남아 ClassNotFoundException guard 실패
TDD GREEN: ownership TCK 1개 통과
./gradlew :bluetape4k-graph-core:test --no-daemon --rerun-tasks --console=plain
379 tests passed, BUILD SUCCESSFUL
./gradlew :bluetape4k-graph-core:compileKotlin --no-daemon --rerun-tasks --console=plain
BUILD SUCCESSFUL
./gradlew :bluetape4k-graph-core:detekt --no-daemon --rerun-tasks --console=plain
BUILD SUCCESSFUL
git diff --check
PASS
```

## Future guard

새 Bluetape4k helper를 추가하기 전 catalog와 resolved jar의 generated owner를 검색하고,
source duplicate, codeSource, module-path, precompiled consumer를 구분해 검증한다.
공식 owner migration은 #562, upstream module split은 #563의 acceptance를 따른다.

## Reader-facing note

이 lesson은 #542의 graph-core source ownership 경계를 기록한다. PR merge와 issue close는
전체 stacked train의 마지막 일괄 승인 전까지 보류한다.
