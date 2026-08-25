# #556 backend bounded chunk·기준 데이터 변경 TCK 실행 계획

## 순서

1. **기준선 고정**
   - live #556과 선행 PR #580 exact head `31c959c984f0cbee3666283392491b646c8e0e99`를
     확인하고 branch base를 고정한다.
   - CSV/GraphML 기존 chunk-only fake와 graph-core fallback test를 읽고 mutation
     seam을 최소 범위로 정한다.
2. **RED 회귀**
   - CSV sync/suspend 및 GraphML sync/suspend fake가 첫 chunk 후 property를
     변경하도록 만들고 stage snapshot assertion을 먼저 추가한다.
   - graph-core sync/suspend fallback의 eager full lookup count assertion을 추가한다.
3. **최소 구현 및 문서**
   - production exporter/API는 변경하지 않는다.
   - root capability matrix와 graph-io core/CSV/GraphML EN·KO README에 fallback의
     전체 materialization 가능성과 TCK의 비범위(transaction snapshot 아님)를 적는다.
   - 이 설계와 7-Tier review, lesson receipt를 한국어로 작성한다.
4. **검증**
   - targeted four exporter tests와 graph-core fallback tests를 먼저 실행한다.
   - graph-io core/CSV/GraphML full test, Detekt, 금지 assertion scan, `git diff --check`를
     순차 실행한다.
5. **7-Tier 및 PR**
   - P0/P1=0인지 source·test·docs를 재검토한다.
   - Lore 커밋 후 exact head, stacked base, hosted CI/Examples, labels/assignee/
     milestone/mergeability를 read-back하고 PR을 만든다. merge는 전체 train 마지막
     승인 단계까지 수행하지 않는다.

## 롤백

- test-only 변경은 네 mutation fake와 graph-core lookup-count test를 되돌리면
  기존 회귀만 남는다.
- README/receipt 변경은 production API와 독립적이며 해당 문서 commit만 되돌릴 수
  있다.
- 실제 backend capability를 승격해야 한다면 별도 issue와 cursor/transaction
  evidence를 먼저 만든다.

