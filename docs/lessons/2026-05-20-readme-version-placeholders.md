# README Version placeholder

## 맥락

Root and `graph-core` README dependency snippets hardcoded older artifact versions while `gradle.properties` had moved on.

## 결정

Use `<version>` placeholders in user-facing dependency snippets to avoid repeating source-version drift in README examples.

## 검증

Check README examples for stale numeric bluetape4k graph versions and keep localized README snippets structurally synchronized.

## 향후 지침

Avoid hardcoded bluetape4k artifact versions in README snippets unless documenting a historical release.
