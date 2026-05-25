# Ignore Graph Outputs

## Context

Local graph runs can create repository-root `output/` or `outputs/`
directories.

## Decision

Ignore both generated output directories in `.gitignore`.

## Outcome

Generated graph output artifacts no longer appear as untracked source changes.

## Verification

- `git diff --check`
- `git status --ignored --short` shows `outputs/` as ignored
