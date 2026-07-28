# 이슈 #134 FalkorDB Auto-Configuration KDoc Language

## 맥락

Several public KDoc blocks in `GraphFalkorDBAutoConfiguration` were still Korean after the FalkorDB Spring Boot
auto-configuration changed for the 0.3.x release line.

## 결정

Translate the public bean and nested health configuration KDoc to English and keep the one-line summary plus
`## Behavior / Contract` style used for durable public API documentation.

## 결과

The FalkorDB driver, synchronous operations, coroutine operations, virtual-thread adapter, health configuration, and
health indicator KDoc blocks now describe their registration and ownership contracts in English.

## 검증

- `rg -n "[가-힣]" spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphFalkorDBAutoConfiguration.kt` returns no Korean text.
- `./gradlew :bluetape4k-graph-spring-boot:compileKotlin --console=plain --no-daemon` passed.
- IntelliJ diagnostics were unavailable for this worktree because the IDE MCP did not have the worktree opened as a project; Gradle compile was used as the fallback.

## 향후 가드

When Spring Boot auto-configuration bean signatures or nested configuration classes change, check public KDoc language
in the same diff. For bluetape4k public API KDoc, use English even when adjacent older modules still have Korean KDoc.
