# 이슈 16 KDoc examples and Dokka validation

## 맥락

Issue #16 required callable Kotlin examples in public API KDoc and a successful Dokka HTML generation path.
The largest remaining gaps were GraphML, Jackson NDJSON, and Spring Boot auto-configuration APIs.

## 결정

Add English KDoc examples to the empty public surfaces first, then validate the full Dokka task instead of only
counting examples. Do not feed normal README files into Dokka as module/package docs; use `dokka.md` when a module
needs Dokka-specific module documentation.

## 결과

- `graph-io` example markers increased from 23 to 69.
- `spring-boot` example markers increased from 0 to 24.
- Full `dokkaGenerateHtml` now succeeds without unresolved-link warnings from the touched validation path.

## 검증

- `git diff --check`
- `./gradlew :graph-io-graphml:dokkaGeneratePublicationHtml :graph-io-jackson2:dokkaGeneratePublicationHtml :graph-io-jackson3:dokkaGeneratePublicationHtml :graph-spring-boot:dokkaGeneratePublicationHtml`
- `./gradlew dokkaGenerateHtml`

## 향후 가드

Do not pass arbitrary README files through Dokka `includes`; Dokka module/package docs need the `# Module` or
`# Package` classifier format and may treat normal Markdown links as unresolved KDoc links.
