# Issue #49 Graph OkIO DAEAD Chunk Encryption Plan

- Issue: #49
- Spec: `docs/superpowers/specs/2026-05-13-issue-49-graph-okio-daead-design.md`
- Branch: `feat/issue-49-graph-okio-daead`
- Worktree: `.worktrees/feat-issue-49-graph-okio-daead`
- Status: Step 3-R reviewed

## 1. Implementation Tasks

- [ ] Add `bluetape4k-tink` version-catalog alias.
- [ ] Add explicit `api(libs.bluetape4k.tink)` to `graph-io/okio/build.gradle.kts`.
- [ ] Add DAEAD low-level helpers to `GraphIoOkioPaths`.
- [ ] Add single-stream DAEAD and gzip+DAEAD helpers to `OkioGraphBulkImporter` and `OkioGraphBulkExporter`.
- [ ] Add focused tests for low-level and high-level DAEAD paths.
- [ ] Update `graph-io/okio/README.md` and `README.ko.md`.
- [ ] Add/update a lesson entry.

## 2. Validation Tasks

- [ ] `./gradlew :graph-okio:compileKotlin :graph-okio:compileTestKotlin --no-daemon`
- [ ] `./gradlew :graph-okio:test --no-daemon`
- [ ] `git diff --check`
- [ ] Review touched public KDoc for English wording.

## 3. Design Guardrails

- Do not add CSV encrypted convenience helpers in this PR.
- Do not introduce Tink `StreamingAead`.
- Keep helper names explicit: `Daead`, `GzipDaead`, `DaeadGzip`.
- Keep wrong key/associated-data/corrupt input as hard failures.
- Do not change existing gzip or plain import/export behavior.

## 4. PR Notes

PR body must mention:

- DAEAD chunk encryption uses deterministic encryption and leaks repeated chunks.
- CSV paired encrypted file convenience helpers are deferred.
- Validation evidence from `:graph-okio` compile/test.

## 5. Step 3-R Review Result

Claude Code Opus advisor:

- Artifact: `.omx/artifacts/claude-issue-49-spec-plan-20260513-093037.md`
- Result: unavailable. The CLI produced no output for more than two minutes and was terminated.

Codex review findings:

| Priority | Finding | Decision |
|---|---|---|
| P1 | Version catalog update must precede code imports so compile errors identify real API issues, not missing aliases. | Accepted. Task order already starts with catalog/dependency. |
| P1 | README updates must cover both locale files because this is library-user documentation. | Accepted. Plan includes both `README.md` and `README.ko.md`. |
| P2 | Negative tests should include wrong associated data and truncated ciphertext. | Accepted. Covered by test task. |

Gate result: P0 = 0, P1 = 0.
