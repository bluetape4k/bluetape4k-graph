# #545 graph-spring-boot 계약 7-Tier review

## 범위와 기준

- Issue: [#545](https://github.com/bluetape4k/bluetape4k-graph/issues/545)
- Branch: \`fix/issue-545-spring-boot-contract\`
- Base: #544 exact head \`e1211e65a2d0804167ed1076b2416add1ecc36fb\`
- Module: \`spring-boot/graph-spring-boot\`
- Review 범위: Actuator management 상태 요약, AGE graph initializer, 세 backend
  auto-configuration exception assertion, EN/KO 문서와 train 기록

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·API | PASS | \`:bluetape4k-graph-spring-boot:test\`가 production/test Kotlin compile을 포함해 완료했고, 기존 2-인자 \`GraphManagementEndpoint\` public primary/JVM 생성자를 유지했다. Spring 추가 state는 private constructor와 내부 factory로만 연결한다. |
| T2 동작 계약 | PASS | backend properties에서 graph/database를 읽고, \`GraphOperations.capabilities()\`의 \`SCHEMA\`를 사용한다. endpoint auto-configuration이 네 backend property를 직접 활성화해 backend auto-configuration이 빠져도 binding을 유지하며, TinkerGraph·Neo4j property·FalkorDB custom-driver \`ApplicationContextRunner\` 회귀가 이를 고정한다. |
| T3 실패·예외 | PASS | AGE initializer는 \`AgeGraphOperations.createGraph()\`의 typed duplicate predicate에만 중복 처리를 위임하고, \`"already exists"\` 문자열을 가진 일반 \`IllegalStateException\`을 다시 던진다. |
| T4 보안·노출 | PASS | endpoint는 query, credential, raw URI를 읽지 않고 backend·graph·database·availability·capability metadata만 반환한다. |
| T5 수명주기·동시성 | PASS | initializer는 Spring \`InitializingBean\` 한 경계에서 동작하며 graph 생성 후에만 성공 로그를 남긴다. endpoint는 read-only이고 graph-io capability는 operations bean과 runtime classpath가 모두 있을 때만 켠다. driver availability는 bean 이름이 아니라 실제 backend driver/AGE Database type을 조회한다. |
| T6 ecosystem·패턴 | PASS | Kotlin null-safe property provider, \`Locale.ROOT\`, 기존 \`capabilities()\` 확장과 \`io.bluetape4k.assertions.assertFailsWith\`를 사용한다. 새 dependency는 없다. |
| T7 문서·운영 | PASS/WATCH | EN/KO README, \`CHANGELOG.md\`, \`WIP.md\`, 이 review/lesson을 갱신했다. hosted exact-head CI와 최종 merge는 train의 마지막 승인 단계로 남아 있다. |

## 검증 증거

- RED: 새 endpoint/initializer 회귀를 먼저 추가했고, 기존 initializer의 broad
  message catch가 일반 예외를 삼키는 실패를 재현했다.
- Targeted: endpoint direct/ApplicationContextRunner 회귀 \`8 passing\`과
  initializer/classpath-filtered graph-io 테스트를 포함해 계약을 고정했다.
- Module test: \`58 passing\`, \`1 pending\` (기존 FalkorDB live Testcontainers
  integration skip), \`BUILD SUCCESSFUL\`.
- Static: \`:bluetape4k-graph-spring-boot:detekt\` PASS.
- Coverage: \`:bluetape4k-graph-spring-boot:koverVerify\` PASS.
- Hygiene: \`git diff --check\` PASS.

## DoD Status

- [x] management 상태 요약이 backend properties와 실제 capability/classpath·bean 상태를 보고한다.
- [x] backend auto-configuration 유무와 무관하게 property binding을 유지하고 custom-named driver를 탐지한다.
- [x] AGE initializer가 typed duplicate 경계를 보존하고 비중복 예외를 숨기지 않는다.
- [x] 세 backend Spring 테스트가 bluetape4k assertions 정책을 사용한다.
- [x] ApplicationContextRunner와 targeted regression을 유지·추가했다.
- [ ] hosted exact-head CI/review와 최종 train merge — 최종 승인 단계에서 수행한다.

최종 판정: **PASS/WATCH**. 구현·로컬 검증은 완료됐고, 외부 CI와 merge는
사용자가 지정한 stacked train 최종 단계 전까지 보류한다.

## 후속 WATCH

- Neo4j/Memgraph가 같은 \`Driver\` type을 공유하고 AGE가 범용 Exposed
  \`Database\` type을 사용하므로, custom context에서 driver identity를
  backend별 qualifier까지 구분하는 정밀도는 후속 P2 범위로 남긴다.
- legacy 2-인자 직접 생성자는 context가 없을 때 session 존재를 driver
  availability로 간주한다. 기존 direct-construction 호환을 우선한 의도된
  fallback이며, 의미를 바꾸려면 별도 API 계약과 이슈가 필요하다.
