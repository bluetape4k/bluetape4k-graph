# #539 graph-io export 구현 계획

## 순서

1. **기준선과 계약 고정**
   - live #539, 종료된 #469/#471, CSV/GraphML source·writer·repository chunk
     API·README·테스트를 읽고 Type A/refactor 범위와 immutable spool 결정을 기록한다.
   - CSV 47개와 GraphML 44개 baseline test를 receipt에 남긴다.
2. **RED 회귀 테스트**
   - `GraphIoRecordSpool` record/key round-trip과 temporary file cleanup을 먼저 고정한다.
   - CSV/GraphML sync/suspend fake operations가 첫 chunk snapshot 이후 mutation을
     반환해도 output이 첫 snapshot만 포함하는지 검증한다.
   - chunk size, empty graph, multi-label/property union, output ownership,
     cancellation·failure close를 추가한다.
3. **최소 구현**
   - graph-io-core에 dependency 없는 bounded binary spool을 추가한다.
   - CSV sync/suspend를 full list에서 chunk append/replay로 전환한다.
   - GraphML sync/suspend를 두 번째 live traversal에서 spool replay로 전환한다.
   - blocking file/output 구간은 `Dispatchers.IO`, cancellation cleanup은
     `NonCancellable`로 정렬한다.
4. **문서·정적 검증**
   - core/CSV/GraphML README 영어·한국어와 public KDoc에 heap/disk bound,
     snapshot 시점, output ownership, failure/cancellation 계약을 기록한다.
   - `git diff --check`, forbidden assertion scan, detekt, compile을 실행한다.
5. **7-Tier review와 후속 이슈**
   - exact HEAD에서 architecture/code review를 독립 수행한다.
   - P0/P1은 수정하고 남은 P2/P3는 Korean GitHub issue로 생성한다.
6. **receipt·커밋·DoD**
   - required checks, fresh module tests, review/lesson, changed paths를 receipt에
     붙이고 Lore trailers 커밋을 만든다.
   - PR/merge/push/이슈 close는 수행하지 않는다.

## 롤백

- exporter 변경을 되돌리고 spool helper를 제거하면 기존 live traversal 구현으로
  복귀할 수 있다.
- spool 파일 lifecycle 또는 suspend cancellation 검증이 실패하면 source 조회와
  sink write를 분리한 최소 helper부터 되돌려 원인을 고립한다.
- Testcontainers는 이 이슈 범위가 아니며, graph-io module tests는 worktree와
  Gradle process를 공유하지 않도록 순차 실행한다.
