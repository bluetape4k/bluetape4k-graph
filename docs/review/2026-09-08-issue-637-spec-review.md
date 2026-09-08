# #637 설계 통합 리뷰

대상: `docs/superpowers/specs/2026-09-08-issue-637-csv-resources-design.md`와 현재 8개 loader/CSV parser의 자원 소유권. Type A Step 2-R에서 여섯 독립 관점과 리더 통합 검토를 수행했다.

| 관점 | 실제 역할 / 모델 / effort | 결과와 반영 |
|---|---|---|
| 성능 | code-reviewer / gpt-5.6-luna / max | P0~P3 없음. import당 고정 open/dispatch 비용이며 record별 추가 비용 없음 |
| 안정성 | architect / gpt-5.6-sol / high | 최초 P1: callback context와 취소 cleanup 알고리즘 모호. outer IO nested use + inner callerContext로 고정 후 동일 관점 재검토 CLEAR |
| 보안 | code-reviewer / gpt-5.6-luna / max | P2: resource/loader 신뢰 경계. trusted configuration 계약을 반영; 새 resolver API는 기존 계약 보존 범위 밖이므로 도입하지 않음 |
| 운영 | code-reviewer / gpt-5.6-luna / max | P2: exact task·ABI·게시 후 복구 절차를 계획에 명시하도록 반영 |
| 개발자/API | architect / gpt-5.6-sol / high | P2: nullable fallback으로 기존 listOfNotNull 의미 보존, 둘 다 null 경계 AC 추가 |
| 호출자 | code-reviewer / gpt-5.6-luna / max | P2: lazy 소비 escape 금지, P3: 상대 resource 경로 규칙을 명시 |
| 리더 통합 | 현재 주 세션 | source lifetime·Kotlin use·예외 순서·AC와 수정 요구를 대조. P0=0/P1=0, P2/P3 처리 완료 |

알고리즘 수정은 승인된 공통화·기존 행동 보존 범위를 구체화하며 신규 dependency나 source 소유권 이전을 추가하지 않는다. 후속 계획은 callback dispatcher, 실제 cancelAndJoin, close suppressed 순서와 8개 caller 결과를 검증해야 한다. 이 문서는 구현·테스트·CI 성공 증거가 아니다.

Writer SPW-01~05: 한국어 개발자용 목적과 source 기준, severity/처리 표, 실제 provenance, 수정 spec/AC와 Markdown을 readback했다. Step 2-R: PASS.
