# README Version Placeholders

## Context

Root and `graph-core` README dependency snippets hardcoded older artifact versions while `gradle.properties` had moved on.

## Decision

Use `<version>` placeholders in user-facing dependency snippets to avoid repeating source-version drift in README examples.

## Verification

Check README examples for stale numeric bluetape4k graph versions and keep localized README snippets structurally synchronized.

## Future Guidance

Avoid hardcoded bluetape4k artifact versions in README snippets unless documenting a historical release.
