# Release catalog guard

## 맥락

The AWS 0.3.0 release exposed a shared release workflow risk: a stale GitHub
repository variable can override the checked-in `settings.gradle.kts` catalog
default before Gradle compiles build scripts.

## 결정

Stable tag releases use the checked-in catalog default. Manual dispatch can use
an explicit `catalogRef` override, then the repository variable as an
operational fallback.

## 결과

The release workflow logs the selected catalog source and verifies required
catalog aliases before Maven Central publish.

## 검증

Run `actionlint`, validate catalog selection branches locally, and check the
current release catalog contains the required aliases.

## 향후 지침

Treat repository catalog variables as manual release overrides, not as the
release train source of truth.
