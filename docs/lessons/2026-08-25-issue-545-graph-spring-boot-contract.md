# #545 graph-spring-boot 계약 정렬 레슨

## 문제

Actuator 상태 요약이 graph/database를 \`default\`로 고정하고 operations bean이
없어도 graph-io를 지원한다고 보고했다. AGE initializer도 typed duplicate
경계를 알지 못한 채 예외 message substring으로 일반 실패를 성공처럼 처리했다.

## 레슨

1. **진단 endpoint는 실제 실행 facade에서 capability를 읽어야 한다.**
   \`GraphOperations.capabilities()\`가 schema 지원을 결정하고, graph-io는
   operations bean과 graph-io contract classpath를 함께 확인해야 한다. 설정
   property만 읽거나 boolean을 고정하면 운영 진단이 실제 상태와 어긋난다.
2. **중복 예외 판정은 domain operation 경계에 둔다.**
   AGE의 \`AgeGraphOperations.createGraph()\`가 SQLState/typed cause predicate를
   소유하므로 Spring initializer는 호출과 lifecycle 로그만 담당해야 한다.
   \`"already exists"\` 문자열을 다시 해석하면 permission·connection failure를
   숨기는 broad catch가 된다.
3. **예외 assertion도 ecosystem 계약의 일부다.**
   Spring Boot backend 테스트는 \`io.bluetape4k.assertions.assertFailsWith<T>\`로
   타입과 실행 경계를 명시해야 하며, AssertJ \`assertThatThrownBy\`를 혼용하지
   않는다.
4. **운영 진단의 Spring 상태는 auto-configuration bean 이름에 종속시키지 않는다.**
   management endpoint가 사용하는 backend properties는 endpoint auto-configuration이
   직접 활성화해 optional backend auto-configuration이 빠져도 설정값을 보존해야 한다.
   driver availability는 \`neo4jDriver\` 같은 생성 bean 이름이 아니라 실제 driver/
   Exposed Database type을 조회해야 사용자 정의 bean 이름과 \`@ConditionalOnMissingBean\`
   경계를 함께 지원할 수 있다.

## 후속 가드

- 새 backend property를 추가하면 management 상태 요약의 graph/database mapping과
  ApplicationContextRunner 대표 테스트를 같은 변경에 포함한다.
- initializer에서 예외 message를 검사하지 말고 backend operation의 typed helper를
  재사용한다.
- graph-io classpath가 optional인 module에서는 문자열 class lookup을 사용해
  \`NoClassDefFoundError\` 없이 capability를 false로 보고한다.
- backend driver bean을 이름으로 가정하지 말고 ApplicationContext의 실제 type
  조회로 custom bean override를 검증한다.
