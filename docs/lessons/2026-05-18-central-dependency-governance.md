# Central Dependency Governance 동기화

## 맥락

Downstream Dependabot PRs were updating shared dependency versions one repository at a time, creating version drift across the bluetape4k organization.

## 결정

Shared dependency versions should be changed in `bluetape4k-dependencies` first, then materialized into this repository with `sync-shared-versions.py`. This repository also ignores centrally governed dependency names in Dependabot so future PRs route through the central source of truth.

## 결과

The local version catalog and `.github/dependabot.yml` now follow the central dependency-governance policy.

## 검증

- `sync-shared-versions.py --write --check --summary` for this repository
- `sync-dependabot-ignores.py --write --check --summary` for this repository
- `git diff --check`

## 향후 가드

Do not merge repo-local Dependabot PRs for centrally governed dependencies. Update `bluetape4k-dependencies`, then sync this repository.
