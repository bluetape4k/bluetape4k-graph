# Dependencies 1.1.1 동기화

## 맥락

`bluetape4k-dependencies` 1.1.0 was superseded by 1.1.1 after the artifact
availability audit found generated aliases for non-published mock web
application modules. This repository consumes the shared catalog and should not
pin around the BOM locally.

## 결정

Consume `bluetape4k-dependencies = "1.1.1"` through the standard shared-version
sync path. Keep graph dependency alignment downstream of
`bluetape4k-dependencies` instead of adding local catalog exceptions.

## 결과

PR #208 aligned this repository to the 1.1.1 catalog and merged after CI passed.

## 검증

- GitHub PR #208 status checks passed before merge.
- Workspace-level `scripts/sync-shared-versions.py --workspace .. --check --summary`
  passed after the downstream PRs were merged.

## 향후 지침

When the shared catalog patch fixes publication availability, wait until Maven
Central `repo1` resolves the new version before rerunning downstream CI. If CI
then fails, investigate graph-specific dependency or integration behavior rather
than reverting the catalog sync.
