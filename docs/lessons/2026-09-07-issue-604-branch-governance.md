# 장기 분기 branch를 tree 변경 없이 정렬하는 방법

## 문제

`main`과 `develop`은 공통 merge base 이후 각각 10개와 772개의 독점 commit을
가졌다. 오래된 `main`을 일반 merge하면 이미 검증된 `develop` tree에 과거
FalkorDB, dependency, WIP, README 상태가 다시 들어올 수 있고, `main`을 강제로
옮기면 보존·보호 원칙을 위반한다.

두 branch의 목적도 이미 달랐다. GitHub default branch, 최근 stable release와
SNAPSHOT source는 `develop`이었고, `main`에는 현재 개발선보다 오래된 이력만
남아 있었다.

## 결정

`develop`을 canonical/default/release/SNAPSHOT branch로 명시하고, `main`은
정확한 head에서 동결하는 legacy-history anchor로 바꿨다. `main`의 독점 commit은
내용을 가져오지 않는 `ours` merge의 second parent로 연결했다.

이 방식에서 중요한 검증은 두 branch의 현재 tree가 같은지가 아니다. 동결
`main`의 과거 tree와 발전한 `develop` tree는 계속 달라도 된다. 검증할 불변식은
다음과 같다.

- 정렬 commit의 second parent가 동결 `main` head다.
- 정렬 commit tree가 first-parent tree와 정확히 같다.
- 정렬 commit과 동결 `main`이 현재 canonical head의 ancestor다.
- 동결 `main` ref는 정책에 기록한 SHA에서 움직이지 않는다.

## 구현

`config/branch-governance.json`은 branch 역할, 동결 SHA와 정렬 commit을 한곳에
고정한다. Python stdlib 검증기는 실제 Git object를 조회해 parent, tree와 ancestry를
검사하며, 임시 저장소 회귀 테스트는 정상 정렬과 legacy 이동, content merge,
ancestry 누락, 잘못된 workflow 대상 branch를 검증한다.

활성 CI·Examples·Testcontainers workflow는 `main`을 canonical trigger에서
제거했다. Stacked PR의 임시 semantic base는 review와 선행 변경 공유를 위한 것이며,
최종 merge 전에 `develop`으로 retarget하고 exact-head checks를 다시 실행한다.

## 배운 점

- `git cherry`에서 patch-equivalent가 없다는 사실만으로 오래된 변경을 모두
  재적용해야 하는 것은 아니다. change/revert 쌍과 현재 tree의 후속 구현을 함께
  분류해야 한다.
- history 보존과 content 통합은 별도 결정이다. `ours` merge도 parent와 tree를
  exact SHA로 검증하지 않으면 안전하다고 주장할 수 없다.
- workflow 문자열 정렬만으로는 branch drift를 막지 못한다. 동결 ref와 ancestry를
  schedule/`develop` push에서 다시 조회하는 동적 gate가 필요하다.
- Branch protection은 repository 파일과 다른 외부 상태다. PR merge 뒤 live rule을
  다시 읽고 적용·재조회해야 완료된다.

## 검증

- branch governance unit test 7개 통과
- CI routing policy unit test 8개 통과
- 실제 stacked head의 동결 SHA, merge parent, tree-equivalence, ancestry 검증 통과
- 변경된 workflow 5개 `actionlint` 통과
- `git diff --check` 통과

## 추적

- Issue: [#604](https://github.com/bluetape4k/bluetape4k-graph/issues/604)
- 정렬 commit: `4d9d8d962a7bd47c1dd39eaee02d9825814724b4`
