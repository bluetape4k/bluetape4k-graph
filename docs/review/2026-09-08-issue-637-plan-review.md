# #637 구현 계획 통합 리뷰

대상은 `docs/superpowers/plans/2026-09-08-issue-637-csv-resources-plan.md`와 승인된 설계다. 여섯 독립 관점과 리더 통합 검토를 수행했다.

| 관점 | 역할 / 모델 / effort | 지적과 처리 |
|---|---|---|
| 성능 | code-reviewer / gpt-5.6-luna / max | P0~P3 없음. import당 고정 비용이며 별도 benchmark 불필요 |
| 안정성 | architect / gpt-5.6-sol / high | P1 취소 종료 원인 관찰, P2 IO thread 검증을 구체화. 재검토 전부 해소 |
| 보안 | code-reviewer / gpt-5.6-luna / max | P2 lookup 예외를 miss로 삼지 않도록 명시하고 SecurityException identity/fallback0 테스트 추가 |
| 운영 | code-reviewer / gpt-5.6-luna / max | P1 exact CI filter/job/task와 P2 소유자·증거·rollback·artifact 범위 표 추가. 재검토 전부 해소 |
| 개발자/API | architect / gpt-5.6-sol / high | P1 직렬 명령, P2 javap/기준 선언 및 각 loader classloader 연결 명시. 재검토 전부 해소 |
| 호출자 | code-reviewer / gpt-5.6-luna / max | P2 공개 API 이름을 importCsv/importCsvSuspending으로 수정, 8개 caller 모두 null TCCL 검증. P3 예제 README 불변과 CSV 문서 수정 범위 명시 |
| 리더 통합 | 현재 주 세션 | 실제 loader8개·source·CI filter·testMutex·module API와 AC별 task/command/rollback 대조. 잔여 P0/P1/P2/P3 없음 |

계획은 API stub의 행동 RED 이후 최소 구현, CSV 전체 테스트, 8개 caller 이전과 직렬 backend 검증으로 진행한다. spec/plan은 구현 전에 커밋하고 helper+8caller 구현은 하나의 rollback 단위로 커밋한다. 원격 exact-head CI는 PR 생성 후 검증한다.

Writer SPW-01~05: 한국어 목적·근거·severity/처리·실제 provenance·AC traceability·Markdown readback 완료. Step 3-R: PASS.
