# #605 dependency graph 보안 경계 7-Tier review

## 범위와 기준

- Issue: [#605](https://github.com/bluetape4k/bluetape4k-graph/issues/605)
- Branch: `chore/issue-605-dependency-graph`
- Review base: `fix/issue-617-graph-plugin-close-retry`
  `409c0550a2459766c85b18dc59cf4f4a409ad1a8`
- 구현 review head: `29916659ff3a101b9820f803707efd6cbd29c7e9`
- Scope: 중앙 catalog pin, runtime/security version 선택, graph BOM과 publication
  POM floor, exact-ref dependency submission, Ruby fail-closed audit

독립 `dependency-expert` lane은 Gradle 공식 지침과 upstream POM을 대조해 catalog/BOM
중심 관리, configuration 제외 금지와 TinkerPop 3.8.2 검증을 권고했다. 이 검토는
최종 implementation SHA 이전의 working tree를 대상으로 했으므로 exact-head code
review로 간주하지 않는다. 별도 `code-reviewer` lane은 제한 시간 내 usable verdict를
반환하지 못해 중단했다. 아래 종합은 이 provenance를 독립 검토로 포장하지 않은 main
lane의 exact-diff fallback review다.

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·API·ABI | PASS | benchmark를 제외한 전체 `build -x test`와 변경 module check가 통과했다. Kotlin/Java source와 공개 descriptor는 변경하지 않았고, graph BOM과 generated POM의 dependency constraint만 확장했다. |
| T2 기능·호환성 | PASS | 최신 중앙 catalog가 ClassGraph `4.8.194`, HttpClient5 `5.6.4`, HttpCore5 `5.4.3`, Tomcat core `11.0.25`를 선택한다. TinkerPop 3.8.1의 `commons-configuration2:2.9.0`은 명시적 `2.15.1`로 대체하며 121개 TinkerPop 회귀가 통과했다. |
| T3 실패·validation semantics | PASS | POM이 필수 managed dependency를 누락하거나 minimum version보다 낮거나 해석 불가능한 버전을 내보내면 Ruby audit가 실패한다. 경로 인자를 전달한 검증도 같은 floor를 적용한다. |
| T4 보안·supply chain | PASS/WATCH | 중앙 catalog는 immutable SHA로 build와 CI에 함께 고정하고 dependency-submission workflow의 Action은 기존 full SHA pin을 유지한다. KGP `2.4.0`은 안정 patched release가 없어 숨기지 않고 제출 graph에 남긴다. |
| T5 성능·boundedness | PASS | Root metadata 보정은 상수 크기 coordinate map lookup이며 하위 module configuration에 전역 강제하지 않는다. POM audit는 기존 file/dependency 순회에 여섯 coordinate 비교만 추가한다. |
| T6 ecosystem·소유권 | PASS/WATCH | runtime floor의 권위는 `bluetape4k-dependencies`, graph BOM과 publication dependency management에 둔다. 중앙 catalog에 아직 없는 Commons Configuration과 JSoup만 local catalog에서 임시 소유한다. TinkerPop 3.8.2 artifact 게시 뒤 local override 제거가 필요하다. |
| T7 테스트·CI·문서 | PASS/PENDING | Ruby 11 tests/28 assertions, 16 POM/Maven models, TinkerPop 121, Ktor 19, Spring Boot 59, compile-only 전체 build, `actionlint`, `git diff --check`가 통과했다. PR exact-head CI와 dependency-submission artifact read-back은 PR 생성 후 gate다. |

## Finding과 처리

| 심각도 | Finding | 처리 |
|---|---|---|
| P1 | `detachedConfiguration*`을 dependency submission에서 제외하면 실제 build/plugin dependency도 graph에서 숨길 수 있다. | Configuration filter를 제거하고 manual dispatch가 선택한 exact ref의 전체 graph를 제출한다. |
| P1 | 초기 전역 `allprojects` resolution rule은 runtime/test/build-tool 모든 configuration을 강제해 catalog/BOM 소유권을 침범했다. | Runtime은 catalog와 dependency management/BOM으로 이동하고 root build-tool에서 관찰된 coordinate만 root configuration에 한정했다. |
| P1 | 명시적 POM path 인자를 사용할 때 security floor audit가 생략됐다. | 모든 invocation에 동일한 floor를 적용하고 Maven integration fixture도 실제 managed floor를 포함하게 했다. |
| P2 WATCH | TinkerPop 3.8.2 source POM은 안전한 Commons Configuration을 사용하지만 Maven artifact가 없다. | Central과 configured Sonatype repository의 해석 실패를 확인하고 3.8.1 + 명시적 `2.15.1`을 유지한다. 게시 뒤 upstream version으로 전환한다. |
| P2 WATCH | 최신 중앙 catalog pin은 여러 shared dependency를 함께 갱신한다. | 변경 module test와 전체 compile gate를 통과했다. Merge 전 exact-head Full Nightly로 repository-wide runtime/container 영향을 검증한다. |
| P2 WATCH | Kotlin Gradle Plugin `2.4.0`은 buildSrc classpath에 남지만 안정 patched version이 없다. | 제출 graph에서 숨기거나 pre-release로 올리지 않는다. 중앙 Kotlin policy가 안정 release를 채택할 때 함께 갱신한다. |

최종 blocker는 P0=0, P1=0이다. 남은 P2=3은 unpublished upstream artifact,
repository-wide catalog 검증과 안정 KGP release 대기 항목이며 PR 생성은 막지 않지만
merge 전 exact-head gate에서 다시 확인한다.

## 검증 증거

- TDD RED: `validate_managed_versions` 부재로 2개 unit test가 `NoMethodError`를
  반환했다.
- Intermediate RED: 모든 path invocation에 floor를 적용하자 Maven integration
  fixture 2개가 필수 managed dependency 누락으로 실패했다.
- Ruby GREEN: audit 9 tests/22 assertions, integration 2 tests/6 assertions.
- Publication: 16 POM, 1,923 dependency entries, 16 Maven effective models.
- Dependency insight: Commons Configuration `2.9.0 → 2.15.1`, ClassGraph
  `4.6.18 → 4.8.194`, Tomcat core `11.0.24 → 11.0.25`, HttpClient5 `5.6.4`,
  HttpCore5 `5.4.3`.
- Module checks: graph-tinkerpop 121, graph-ktor 19, graph-spring-boot 59
  (58 pass, 기존 1 pending), Detekt/Kover 포함 성공.
- Repository compile gate: benchmark build를 제외한 전체 `build -x test` 성공.
- TinkerPop 3.8.2 probe: `gremlin-core`와 `tinkergraph-gremlin` artifact를 찾지
  못해 의존성 해석 단계에서 fail-closed.
- `actionlint`, `git diff --check`: PASS.

## DoD Status

- [x] 실제 dependency graph에서 취약 runtime version을 안전한 version으로 선택한다.
- [x] graph BOM과 모든 publication POM에 minimum security floor를 내보낸다.
- [x] POM 누락·하위 버전·invalid version을 fail-closed로 거부한다.
- [x] Dependency submission은 exact ref를 checkout하고 configuration을 숨기지 않는다.
- [x] central/local dependency ownership과 upstream 전환 조건을 기록한다.
- [x] main lane exact-diff review에서 P0/P1=0으로 수렴했다.
- [ ] 독립 exact-head `code-reviewer` verdict는 제한 시간 초과로 `PENDING`이다.
- [ ] PR exact-head hosted CI, dependency graph artifact와 live review/thread read-back은
  PR 생성 후 수행한다.
- [ ] 광범위한 catalog 갱신의 Full Nightly는 train 최종 head에서 수행한다.

최종 로컬 판정: **PASS/WATCH**. PR 생성은 가능하며 merge-ready 판정은 hosted
dependency submission, exact-head CI와 Full Nightly 이후에만 내린다.
