# graph-okio Rename Plan

- **Issue**: #76
- **Spec**: docs/superpowers/specs/2026-05-10-graph-okio-rename-design.md
- **Branch**: refactor/graph-okio-rename

## Steps

1. Update `settings.gradle.kts` so `graph-io/okio` is registered only as `:graph-okio`.
2. Replace project dependency references from `:graph-io-okio` to `:graph-okio`.
3. Replace current README, BOM, WIP, code comment, and workflow references from `graph-io-okio` to `graph-okio`.
4. Verify project registration and affected builds.
5. Run 6-Tier review and address findings in one batch.
6. Commit, push, and open a PR closing #76.

## Verification

- `./gradlew projects --no-configuration-cache`
- `./gradlew :graph-okio:test --no-configuration-cache`
- `./gradlew :graph-io-benchmark:compileKotlin --no-configuration-cache`
- `./gradlew :graph-okio:koverXmlReport --no-configuration-cache`
- `./gradlew :graph-okio:generatePomFileForBluetapeGraphPublication --no-configuration-cache`
- `./gradlew :bluetape4k-graph-bom:generatePomFileForBluetapeGraphPublication --no-configuration-cache`
- `git diff --check`

Historical 2026-04 design/testlog files may continue to mention `graph-io-okio` because they record the original module creation context. Current user-facing docs, CI, BOM docs, and generated POM metadata must use `graph-okio`.
