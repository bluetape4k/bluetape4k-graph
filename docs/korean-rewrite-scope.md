# 한국어 문서 및 주석 재작성 범위

이 문서는 #400 Epic의 작업 경계를 고정한다. 이후 하위 이슈는 이 문서를
기준으로 범위를 선택하고, 각 PR은 merge 없이 PR 생성까지만 진행한다.

## 목표

- 단일 언어로만 존재하는 저장소 문서를 한국어로 재작성한다.
- Kotlin KDoc, 일반 주석, `@property`, `@param`, 함수 인자 설명을 한국어로
  자세히 작성한다.
- GitHub issue/PR 제목과 본문은 저장소 규칙에 따라 영어를 유지한다.
- 코드 동작, 공개 API 시그니처, 벤치마크 수치, 명령, 경로, issue/PR 번호는
  변경하지 않는다.

## 제외 대상

| 대상 | 처리 |
|---|---|
| `README.md`, `README.ko.md` | 이번 재작성 범위에서 제외한다. |
| `AGENTS.md`, 하위 `AGENTS.md`, `CLAUDE.md` | LLM-facing operating document이므로 영어를 유지한다. |
| `docs/manual/en`, `docs/manual/ko` | 이미 bilingual pair로 관리되므로 primary rewrite 대상이 아니다. basename parity만 검증한다. |
| `.omx`, `.omc` | runtime/tool 상태이므로 문서 재작성 대상이 아니다. |
| `build/`, `*/build/` | 생성물 또는 리포트 캐시이므로 대상에서 제외한다. |

## 현재 인벤토리

2026-07-28 현재 `origin/develop` 기준 read-only 조사 결과다.

| 표면 | 수량 | 산정 기준 |
|---|---:|---|
| 재작성 대상 단일언어 Markdown | 172 | README, LLM-facing 문서, bilingual manual, `.omx`, `.omc`, build 산출물 제외 |
| 제외 Markdown | 174 | README 계열, `AGENTS.md`, `CLAUDE.md`, `docs/manual/en`, `docs/manual/ko` |
| manual EN 문서 | 52 | `docs/manual/en/**/*.md` |
| manual KO 문서 | 52 | `docs/manual/ko/**/*.md` |
| manual basename 차이 | 0 | `comm -3` 비교 결과 |
| Kotlin 파일 | 518 | `git ls-files '*.kt' ':!:**/build/**'` |

단일언어 Markdown 172개는 다음 묶음으로 나눈다.

| 묶음 | 수량 | 후속 이슈 |
|---|---:|---|
| 루트 상태 문서 | 2 | #402 |
| governance 및 graph DB tradeoff 문서 | 4 | #403 |
| benchmark report 및 test log 문서 | 12 | #404 |
| lessons 문서 | 92 | #405 |
| review report 문서 | 6 | #406 |
| superpowers plans/specs/research/index 문서 | 56 | #407 |

Kotlin 주석 표면은 다음 묶음으로 나눈다.

| 묶음 | 파일 수 | 후속 이슈 |
|---|---:|---|
| `graph/graph-core` 공개 API | 98 중 공개 API 표면 | #409 |
| `graph/graph-core` algorithm 및 virtual-thread 표면 | 98 중 알고리즘/VT 표면 | #410 |
| `graph-io/core`, `csv`, `graphml`, `jackson2`, `jackson3` | 115 | #411 |
| `graph-io/okio` | 22 | #412 |
| backend modules: AGE, Neo4j, Memgraph, FalkorDB, TinkerPop | 94 | #413 |
| `ktor/graph-ktor` | 15 | #414 |
| `spring-boot/graph-spring-boot` | 21 | #415 |
| example modules | 92 | #416 |
| benchmark modules | 52 | #417 |

## 재작성 원칙

- 한국어 문장은 의미를 보존하되 직역보다 유지보수자가 이해하기 쉬운 설명을
  우선한다.
- 식별자, 패키지명, 클래스명, 메서드명, Gradle task, shell 명령, URL,
  Cypher/Gremlin/SQL 구문, 벤치마크 수치와 단위는 원문을 유지한다.
- 공개 API KDoc은 사용자가 오해하기 쉬운 제약, nullable 의미, 예외,
  transaction 경계, suspend/virtual-thread 실행 모델을 설명한다.
- `@property`와 `@param`은 이름만 반복하지 말고 허용 값, 기본값, 실패 조건,
  backend별 의미 차이를 적는다.
- 주석 재작성 중 코드 변경이 필요해 보이면 해당 PR에서는 변경하지 않고 별도
  bug/feature 이슈로 분리한다.
- 벤치마크 문서는 수치, 환경, 명령, 결과 파일명을 바꾸지 않는다.
- lessons/review 문서는 당시의 결정과 증거를 보존하고 문장만 한국어로 정리한다.

## PR별 검증 체크리스트

- [ ] 대상 경로가 이 문서의 포함 범위에 속한다.
- [ ] README, LLM-facing operating doc, bilingual manual primary content,
      `.omx`, `.omc`, build 산출물이 diff에 포함되지 않았다.
- [ ] 대상 문서 또는 주석이 한국어로 재작성되었다.
- [ ] 식별자, 명령, URL, 수치, issue/PR 번호, 코드 블록의 의미가 보존되었다.
- [ ] `git diff --check`가 통과했다.
- [ ] 대상 범위에 맞는 `rg` 검사를 수행했다.
- [ ] 코드 파일을 수정한 경우 필요한 최소 Gradle compile/test를 수행했다.
- [ ] PR body의 마지막 `##` heading이 `## DoD Status`다.
- [ ] PR은 생성까지만 진행했고 merge하지 않았다.

## 전체 완료 전 검증

마지막 sweep 이슈(#418)는 다음을 확인한다.

- 단일언어 Markdown 대상 목록에 영어 전용 본문이 남아 있지 않다.
- Kotlin KDoc과 주석의 English-only 설명이 의도적으로 보존해야 하는 식별자,
  명령, URL, 코드 조각인지 확인되었다.
- `docs/manual/en`과 `docs/manual/ko`의 basename parity가 유지된다.
- README와 LLM-facing 문서가 범위 밖으로 보존되었다.
- 모든 하위 이슈가 PR 생성 상태에 있고 merge는 별도 승인 전까지 보류되어 있다.
