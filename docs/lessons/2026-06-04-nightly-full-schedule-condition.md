# 레슨 Learned — Nightly full schedule condition (2026-06-04)

**Related issue**: #284

## 맥락

The Nightly cron minute was staggered to reduce Central snapshot metadata
contention. Full-scope scheduled jobs still compared `github.event.schedule`
against the old Sunday cron string, so weekly full jobs could be skipped.

## 결정

Keep the staggered cron, and update full-scope job conditions to compare against
the repository's current Sunday schedule.

## 검증

- `actionlint .github/workflows/nightly-tests.yml`
- `git diff --check`
- Schedule-condition audit: no old `0 19 * * 0` full-job condition remains.

## 향후 규칙

When changing a scheduled cron string, update every `github.event.schedule`
comparison in the same workflow.
