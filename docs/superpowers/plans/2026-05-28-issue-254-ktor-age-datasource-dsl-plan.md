# 이슈 #254 - graph-ktor 관리형 AGE DataSource DSL 계획

## 완료 조건

- 속성 검증을 포함한 `ageDataSource { ... }`를 추가한다.
- Hikari 풀을 생성하고 `Database.connect(dataSource)`를 호출하며 동기/suspend AGE 연산을 연결한다.
- Ktor 중지 시 플러그인이 소유한 AGE 연산과 플러그인이 생성한 Hikari 풀을 닫는다.
- 기존 호출자 소유 `age(graphName)` helper를 유지한다.
- 잘못된 속성에 대한 검증 테스트를 추가한다.
- PostgreSQL AGE Testcontainers를 사용하는 Ktor `testApplication` smoke 검증을 추가한다.
- 영문 및 한국어 `graph-ktor` README 파일을 업데이트한다.
- 변경 기록과 lesson note를 업데이트한다.
- 컴파일, 테스트 및 공백 검사를 검증한다.

## 구현 단계

1. `ManagedAgeDataSourceGraphPluginConfig`와 `GraphPluginConfig.ageDataSource`를 추가한다.
2. 선택적 DSL 구현을 위해 HikariCP를 compile-only dependency로 추가한다.
3. AGE Ktor 런타임 smoke가 관리형 설정을 사용하도록 전환한다.
4. 잘못된 속성에 대한 fail-fast 테스트를 추가한다.
5. 수명 주체와 선택적 Hikari 의존성을 문서화한다.
6. 대상 `graph-ktor` compile/test 검사를 실행한다.
