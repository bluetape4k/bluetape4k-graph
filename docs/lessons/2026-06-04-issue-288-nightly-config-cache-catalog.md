# 2026-06-04 Issue 288 Nightly Config Cache And Catalog

## 맥락

Nightly workflows use snapshot and BOM-managed dependencies, so stale Gradle/configuration state can surface versionless dependency coordinates.

## 결정

Keep Nightly Gradle commands on `--no-configuration-cache` and keep local bluetape4k aliases versioned through their BOM ref.

## 결과

Nightly commands no longer rely on configuration cache during dependency refresh, and repo-local catalog aliases avoid `group:artifact:.` coordinates.

## 검증

- Planned: `actionlint`, `git diff --check`, command audit, catalog alias audit.

## 향후 규칙

For Nightly jobs that refresh snapshots, disable both Gradle action cache and configuration cache unless a repo-specific proof says otherwise.
