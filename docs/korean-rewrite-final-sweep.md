# 한국어 문서/KDoc 재작성 최종 점검

작성일: 2026-07-28

## 범위

- Epic #400의 하위 이슈 #401-#418을 stacked PR train으로 구성했다.
- README 문서, `AGENTS.md`/`CLAUDE.md` 같은 LLM-facing 운영 문서, `docs/manual/en` 및 `docs/manual/ko` primary manual content는 재작성 대상에서 제외했다.
- `docs/manual/en` 및 `docs/manual/ko`는 basename parity 검증 대상으로만 다루었다.
- GitHub issue/PR title/body는 repository policy에 따라 English를 유지했다.

## PR Train

| Issue | PR | Branch | Base |
| --- | --- | --- | --- |
| #401 | #419 | `docs/issue-401-korean-doc-scope` | `develop` |
| #402 | #420 | `docs/issue-402-root-status-ko` | `docs/issue-401-korean-doc-scope` |
| #403 | #421 | `docs/issue-403-governance-ko` | `docs/issue-402-root-status-ko` |
| #404 | #422 | `docs/issue-404-benchmark-docs-ko` | `docs/issue-403-governance-ko` |
| #405 | #423 | `docs/issue-405-lessons-ko` | `docs/issue-404-benchmark-docs-ko` |
| #406 | #424 | `docs/issue-406-review-ko` | `docs/issue-405-lessons-ko` |
| #407 | #425 | `docs/issue-407-superpowers-ko` | `docs/issue-406-review-ko` |
| #408 | #426 | `docs/issue-408-manual-parity` | `docs/issue-407-superpowers-ko` |
| #409 | #427 | `docs/issue-409-core-api-kdoc-ko` | `docs/issue-408-manual-parity` |
| #410 | #428 | `docs/issue-410-core-algo-kdoc-ko` | `docs/issue-409-core-api-kdoc-ko` |
| #411 | #429 | `docs/issue-411-graph-io-kdoc-ko` | `docs/issue-410-core-algo-kdoc-ko` |
| #412 | #430 | `docs/issue-412-graph-okio-kdoc-ko` | `docs/issue-411-graph-io-kdoc-ko` |
| #413 | #431 | `docs/issue-413-backend-kdoc-ko` | `docs/issue-412-graph-okio-kdoc-ko` |
| #414 | #432 | `docs/issue-414-ktor-kdoc-ko` | `docs/issue-413-backend-kdoc-ko` |
| #415 | #433 | `docs/issue-415-spring-kdoc-ko` | `docs/issue-414-ktor-kdoc-ko` |
| #416 | #434 | `docs/issue-416-examples-kdoc-ko` | `docs/issue-415-spring-kdoc-ko` |
| #417 | #435 | `docs/issue-417-benchmark-kdoc-ko` | `docs/issue-416-examples-kdoc-ko` |
| #418 | 생성 예정 | `docs/issue-418-final-sweep` | `docs/issue-417-benchmark-kdoc-ko` |

## 검증

- `docs/manual/en` 문서 수: 52
- `docs/manual/ko` 문서 수: 52
- EN에만 있는 manual basename: 0
- KO에만 있는 manual basename: 0
- 누적 diff에서 README/LLM-facing/manual primary path exclusion을 점검했다.
- #418에서 `docs/lessons/README.md`를 base 상태로 복원해 최종 누적 결과에서 README 변경이 남지 않도록 했다.

## 남은 주의점

- 이 train은 PR 생성까지만 수행한다. Merge는 별도 승인 gate가 필요하다.
- 문서/KDoc/comment 재작성 train이므로 전체 test suite와 JMH 실행은 수행하지 않았다.
- 각 코드 주석 slice는 해당 모듈의 targeted `compileKotlin`으로 문법과 symbol reference를 검증했다.
