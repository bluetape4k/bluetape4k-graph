# WIP - bluetape4k-graph

- 기준일: 2026-09-07 KST
- 최신 안정 버전: `1.0.0`
- 안정 tag commit: `a405300799b36d4d6edb7267ad07ff34d4ad3afe`
- 현재 개발선: `1.1.0-SNAPSHOT`
- 현재 milestone: `1.1.0`

## 현재 상태

### #641 후속 #632

AGE 자동 설정이 임의 이름의 DataSource를 받고, AGE 전용 Exposed Database를 명시적으로 주입하도록 수정한다. 이슈별 브랜치에서 PR을 준비하며, 전체 PR 검증 후 머지를 모아서 진행한다.
### #641 후속 #631

FalkorDB traversal의 edgeLabel을 query 실행 전에 검증하여 sync/suspend의 식별자 우회 경로를 차단한다. 이슈별 브랜치에서 PR을 준비하며, 전체 PR 검증 후 머지를 모아서 진행한다.
### #641 후속 #633

CSV None 모드에서 vertex/edge property를 header, spool 및 출력 값에서 제외한다. 이슈별 브랜치에서 PR을 준비하며, 전체 PR 검증 후 머지를 모아서 진행한다.

`1.0.0` artifact와 GitHub Release 배포를 완료했다. `develop`은 `1.1.0` minor 개발선을 사용한다. 공개 Graph manual은 `1.0.0` tag source로 갱신한다.

`1.1.0` milestone의 #616, #613, #612는 stacked PR #619, #620, #621로
`develop`에 병합했다. 현재 기준 head는
`a76462766edc34f2789a424847266105f375c055`다.

남은 milestone 이슈는 #614, #615, #617, #605, #618, #604 순서의 stacked PR
train으로 진행한다. #614는 GraphML scalar property의 JVM 타입 보존을 구현해
PR #623의 exact-head CI를 통과했다. #615는 Jackson2·3 NDJSON의 codec 이전
줄 길이 상한을 구현해 stacked PR #624의 exact-head CI를 통과했다. 그 위에서
#617이 Ktor `GraphPluginState.close()`의 실패 action 재시도와 동시 close pass 병합
계약을 고정하고 stacked PR #625를 생성했다. #605는 최신 중앙 catalog를 고정하고
TinkerPop의 취약 transitive dependency, root build-tool metadata, graph BOM과 공개
POM의 security floor를 정렬한다. Dependency submission은 선택한 exact ref의 named
project/build/test configuration을 제출하고, 실제 graph와 중복되는 임시
`detachedConfiguration*` metadata만 제외하도록 수정했다. 그 위에서 #618은
GraphPlugin 소유 종료 동작을 공통 Ktor `ApplicationResourceRegistry`의 하나의 bounded
group으로 연결하고, caller-owned resource와 실패 action 재시도 경계는 기존 state에
유지한다. 마지막 #604는 `develop`을 canonical/default/release/SNAPSHOT branch로
확정하고, `main`의 독점 이력을 tree 변경 없는 merge commit으로 보존했다. 동결
head, parent, tree-equivalence, ancestry와 workflow 대상 branch는 전용 fail-closed
gate가 검사하며 stacked PR #628을 생성했다. 각 PR은 로컬
검증과 7-Tier review를 마친 뒤 생성하며, 전체 exact-head CI를 다시 확인한 후
마지막 단계에서 한 번에 병합한다.

PR #628 exact-head CI의 retry evidence에서 CSV streaming reader의 validation과
owned source close가 경쟁해 primary failure 순서가 뒤집히는 회귀를 발견했다.
후속 #629는 record 변환을 source 소유권 경계 안으로 이동하고 결정적 회귀 테스트를
추가하며, #628 위의 train 마지막 PR로 검증한다. 따라서 기존 PR의 완료 판정과
Full Nightly는 #629 exact head가 첫 시도에 통과할 때까지 보류한다.

## 다음 개발선 규칙

- `gradle.properties`는 `baseVersion=1.1.0`, 빈 `snapshotVersion`을 유지한다.
- SNAPSHOT workflow가 실행할 때만 `-PsnapshotVersion=-SNAPSHOT`을 주입한다.
- 중앙 catalog SHA는 검증된 `bluetape4k-dependencies` commit
  `55b5269bddd2bd041d5f282abcd0238dc242c171`을 사용한다.

## 추적

생태계 전체 후속 작업은 [bluetape4k-dependencies #235](https://github.com/bluetape4k/bluetape4k-dependencies/issues/235)에서 추적한다. 신규 기능과 버그는 `1.1.0` milestone에서 관리한다.
