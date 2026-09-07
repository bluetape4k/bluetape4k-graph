# Dependency graph 보안 경계와 publication floor

## 배경

Dependabot은 `settings.gradle.kts`가 제출한 Gradle dependency graph에서 runtime,
test, buildscript, Dokka와 detached configuration을 구분하지 않고 여러 취약 버전을
보고했다. 일부는 오래된 중앙 catalog가 실제 module classpath에 선택한 버전이었고,
일부는 build-tool metadata가 해석한 오래된 transitive dependency였다. Alert를 숨기는
것이 아니라 실제 graph와 공개 publication을 함께 안전하게 만들어야 했다.

## 결정

공유 의존성은 최신 검증 commit
`55b5269bddd2bd041d5f282abcd0238dc242c171`의 `bluetape4k-dependencies`
catalog를 사용한다. HttpClient5, HttpCore5, ClassGraph와 Tomcat의 안전 버전은 이
catalog에서 가져오고, 각 하위 모듈의 dependency management와 graph BOM에도 같은
버전을 내보낸다.

중앙 catalog에 없는 `commons-configuration2`와 `jsoup`만 local catalog에 둔다.
TinkerPop 3.8.1이 끌어오는 `commons-configuration2:2.9.0`은 graph-tinkerpop의 명시적
runtime dependency `2.15.1`로 대체해 Gradle과 Maven 소비자 모두 같은 안전 버전을
선택하게 한다. 루트 buildscript와 Dokka metadata는 하위 모듈 dependency management의
적용 대상이 아니므로, 루트 project configuration에서 관찰된 artifact만 좁게 보정한다.

Dependency submission은 manual dispatch가 선택한 exact ref를 checkout한다. 기존처럼
develop을 강제로 checkout하거나 `detachedConfiguration*`을 제외하지 않는다. 따라서
검증 대상 SHA와 제출 graph가 일치하고, build-tool dependency도 숨기지 않는다.

Publication POM audit에는 여섯 runtime dependency의 최소 managed version을 추가했다.
모든 공개 POM이 floor를 내보내지 않거나 안전 버전보다 낮으면 Maven effective-model
검증 전에 실패한다.

## 기각한 대안

`dependency-graph-exclude-configurations`로 detached configuration을 제거하면 현재
alert 수는 줄지만 실제 build/plugin graph도 제출에서 사라진다. 보안 수정 증거가 될 수
없어 기각했다.

TinkerPop 3.8.2 source tag와 POM은 `commons-configuration2:2.15.1`로 갱신됐지만,
2026-09-07 현재 Maven Central과 구성한 Sonatype repository에 `gremlin-core`와
`tinkergraph-gremlin` 3.8.2 artifact가 없다. 해석 가능한 artifact가 생기기 전에는
3.8.1과 명시적 security floor를 유지한다.

## 검증

- graph-tinkerpop runtime graph에서 `commons-configuration2:2.9.0 → 2.15.1`을 확인했다.
- graph-tinkerpop 121개, graph-ktor 19개 테스트가 통과했다.
- graph-spring-boot 59개 중 58개가 통과하고 기존 1개가 pending이었다.
- Ruby POM audit 9개 테스트/22 assertions와 Maven integration 2개/6 assertions가
  통과했다.
- 16개 publication POM, 1,923개 dependency entry와 16개 Maven effective model을
  검증했다.
- benchmark build를 제외한 전체 `build -x test`가 통과했다.
- `actionlint`와 `git diff --check`가 통과했다.

## 향후 지침

Dependabot dependency graph alert는 먼저 debug submission artifact에서 project와
configuration을 확인한다. runtime dependency는 중앙 catalog, BOM과 공개 POM이
소유하고, build-tool metadata만 root 범위에서 다룬다. Configuration 제외는 보안
수정으로 사용하지 않는다. Upstream source tag가 있어도 Maven artifact 해석과 실제
module test가 모두 통과하기 전에는 버전을 채택하지 않는다.

## 추적

- GitHub issue: [#605](https://github.com/bluetape4k/bluetape4k-graph/issues/605)
