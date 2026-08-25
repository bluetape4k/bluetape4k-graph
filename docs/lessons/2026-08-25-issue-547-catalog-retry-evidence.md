# #547 catalog ownership·retry-only CI evidence 교훈

## 결정

`gradle/libs.versions.toml`은 repository leaf가 소유하는 alias만 보유하고,
bluetape4k BOM과 ecosystem version은 `settings.gradle.kts`의 immutable `bt4k`
catalog가 소유하도록 경계를 고정했다. 미사용 local alias를 남겨 두면 같은
dependency family의 두 source가 drift할 수 있으므로 accessor 검색과 Gradle
help resolution을 함께 실행한다.

`examples.yml` build와 `ci.yml` core-test의 bounded retry는 공통 helper로
실행한다. 모든 시도와 첫 실패를 artifact로 보존하고, 첫 시도 성공과 retry 후
성공을 `success`/`success_after_retry`로 구분한다. 마지막 retry가 green이어도
flaky signal을 정상 green으로 축약하지 않는다. 명령이 성공해도 attempt log,
summary, output을 저장하지 못하면 증거가 없는 green이므로 helper 자체가
fail-closed로 종료하고 artifact upload도 missing evidence를 오류로 처리한다.

## 범위 경계

이번 이슈의 근거가 가리킨 두 workflow step만 helper로 정렬했다. 나머지 기존
retry loop는 동작 변경 없이 남겨 두었으며, 같은 evidence 계약을 적용하는 후속
이슈에서 별도 검토한다. 범위를 넓혀 한 PR에서 모든 CI 정책을 재작성하면 각
workflow의 실패 semantics와 review surface가 섞이기 때문이다.

## 다음 변경에 대한 지침

- retry step을 추가하거나 바꿀 때는 `attempt-N.log`, `first-failure.log`,
  `summary.env`, step output, always-upload artifact를 함께 제공한다.
- command와 evidence writer(`tee`, `cp`, summary/output redirect)의 실패를
  독립적으로 검사한다. 명령 결과만으로 green을 만들지 않는다.
- retry로 통과한 hosted run은 DoD에서 `success_after_retry`로 기록하고,
  원인 미분류 상태에서 release/merge gate를 자동으로 열지 않는다. 상태
  집계가 green이어도 최종 stacked train 승인 전 exact head와 첫 실패
  artifact를 사람이 확인하기 전에는 merge-ready로 분류하지 않는다.
- central catalog alias를 삭제·이동할 때는 accessor 검색과 최소 Gradle
  resolution을 같은 변경에서 실행하고, catalog owner를 governance 문서에
  남긴다.
