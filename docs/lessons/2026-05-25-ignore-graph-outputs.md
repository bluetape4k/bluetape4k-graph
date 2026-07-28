# Graph output ignore 처리

## 맥락

Local graph runs can create repository-root `output/` or `outputs/`
directories.

## 결정

Ignore both generated output directories in `.gitignore`.

## 결과

Generated graph output artifacts no longer appear as untracked source changes.

## 검증

- `git diff --check`
- `git status --ignored --short` shows `outputs/` as ignored
