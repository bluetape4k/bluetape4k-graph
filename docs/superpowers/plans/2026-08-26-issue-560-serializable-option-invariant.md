# #560 Serializable option 역직렬화 invariant 실행 계획

1. live issue와 #584 exact head, 기존 option constructor invariant 및 중복
   serialization 작업을 inventory한다.
2. round-trip 및 변조 payload 거부 테스트를 먼저 추가해 TDD RED를 기록한다.
3. concrete option과 `MissingWeightPolicy.UseDefault`의 private `readObject`와
   동일한 constructor invariant를 구현한다.
4. graph-core 전체 test, Detekt, forbidden assertion scan, ABI/read-back을
   순서대로 실행한다.
5. EN/KO KDoc·README, 7-Tier review, lesson, WIP/CHANGELOG를 갱신하고 Lore
   commit을 만든다.
6. #584 exact head 위에 PR을 생성하고 hosted CI/Examples terminal 결과와
   metadata를 read-back한다. 전체 train merge는 최종 승인 단계까지 보류한다.

완료 기준은 모든 option round-trip PASS, 대표 numeric/nested invalid payload의
`InvalidObjectException` 및 메시지 PASS, graph-core 검증 green, PR exact-head
hosted receipt PASS이다.
