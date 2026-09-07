# WIP - bluetape4k-graph

- 기준일: 2026-09-07 KST
- 최신 안정 버전: `1.0.0`
- 안정 tag commit: `a405300799b36d4d6edb7267ad07ff34d4ad3afe`
- 현재 개발선: `1.1.0-SNAPSHOT`
- 현재 milestone: `1.1.0`

## 현재 상태

`1.0.0` artifact와 GitHub Release 배포를 완료했다. `develop`은 `1.1.0` minor 개발선을 사용한다. 공개 Graph manual은 `1.0.0` tag source로 갱신한다.

`1.1.0` milestone의 #616, #613, #612는 stacked PR #619, #620, #621로
`develop`에 병합했다. 현재 기준 head는
`a76462766edc34f2789a424847266105f375c055`다.

남은 milestone 이슈는 #614, #615, #617, #618, #605, #604 순서의 stacked PR
train으로 진행한다. #614는 GraphML scalar property의 JVM 타입 보존을 구현해
PR #623의 exact-head CI를 통과했다. #615는 Jackson2·3 NDJSON의 codec 이전
줄 길이 상한을 구현해 stacked PR #624에서 exact-head CI를 확인 중이다. 그 위에서
#617이 Ktor `GraphPluginState.close()`의 실패 action 재시도 계약을 고정한다. 각 PR은 로컬
검증과 7-Tier review를 마친 뒤 생성하며, 전체 exact-head CI를 다시 확인한 후
마지막 단계에서 한 번에 병합한다.

## 다음 개발선 규칙

- `gradle.properties`는 `baseVersion=1.1.0`, 빈 `snapshotVersion`을 유지한다.
- SNAPSHOT workflow가 실행할 때만 `-PsnapshotVersion=-SNAPSHOT`을 주입한다.
- 중앙 catalog SHA는 `bluetape4k-dependencies`의 다음 개발선이 병합된 뒤 한 번만 갱신한다.

## 추적

생태계 전체 후속 작업은 [bluetape4k-dependencies #235](https://github.com/bluetape4k/bluetape4k-dependencies/issues/235)에서 추적한다. 신규 기능과 버그는 `1.1.0` milestone에서 관리한다.
