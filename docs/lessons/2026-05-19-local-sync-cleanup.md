# Local Sync and Cleanup

## Context

After several sprint cycles, the local repository had accumulated 18 stale
branches and 11 worktrees with gone remotes. The default worktree was sitting
on a merged feature branch (`build/gradle-9.5.1-wrapper-bat`) instead of
`develop`. Pulling `develop` was blocked by a persistent `gradlew.bat`
modification that neither `git restore`, `git stash`, nor `git reset --hard`
could resolve.

## Problem 1 — gradlew.bat stuck dirty after reset

### Root cause

`.gitattributes` declares `*.bat text eol=crlf`. The global git config has
`core.autocrlf=input`. On macOS these two settings conflict: `eol=crlf`
instructs git to write CRLF to the working tree, but `core.autocrlf=input`
converts CRLF → LF on checkin. The index recorded CRLF content; the smudge
filter re-applied CRLF on checkout; but git's stat cache remained desynchronised,
causing `gradlew.bat` to appear dirty even after `git reset --hard HEAD`.

### Fix

```bash
git rm --cached gradlew.bat
git reset --hard origin/develop
```

Removing the index entry first breaks the cache lock, allowing `reset --hard`
to rewrite both the index and the working tree cleanly.

### Future guard

Do not try to fix CRLF-attribute conflicts with `git restore`, `git stash`, or
`git checkout -- <file>`. Those commands re-run the smudge filter and re-enter
the same loop. Remove the file from the cache first, then hard-reset.

---

## Problem 2 — cannot checkout develop because it is checked out in a worktree

### Root cause

`develop` was checked out in `.worktrees/fix/issue-157-schema-manager-errors`
(issue already closed and merged). Git refuses to checkout a branch that is
already active in another worktree.

### Fix

1. Confirm the worktree has no unique commits and no meaningful dirty files.
2. `git worktree remove --force .worktrees/fix/issue-157-schema-manager-errors`
3. `git checkout develop`

### Future guard

After closing an issue and merging its PR, immediately remove the corresponding
worktree. Stale worktrees block branch switching and accumulate CRLF/state
noise.

---

## Problem 3 — build/align-dependency-boms had an lz4 security pin

The branch contained `at.yawk.lz4:lz4-java:1.11.0` as a direct version pin in
the local catalog to address CVE GHSA-cmp6-m4wj-q63q on `org.lz4:lz4-java`.
The correct fix is not to pin here but to consume `bluetape4k-dependencies` BOM
where the version is governed centrally.

### Decision

Added `bluetape4k-dependencies = "1.0.1-SNAPSHOT"` to `gradle/libs.versions.toml`
and imported the BOM in `build.gradle.kts` alongside `spring.boot4.dependencies`,
matching the pattern established in `bluetape4k-experimental`. Then deleted
`build/align-dependency-boms`.

### Future guard

Never pin a dependency version in this repo's catalog when the version is owned
by `bluetape4k-dependencies`. Update the central catalog and sync here via
`sync-shared-versions.py`.

---

## Cleanup procedure used

```bash
# 1. Remove gone-remote worktrees
git worktree remove --force .worktrees/<path>

# 2. Delete gone-remote local branches
git branch -D <branch>

# 3. Verify
git branch -vv
git worktree list
```

Branches are safe to delete when:
- `git log --oneline origin/develop..HEAD` returns nothing (all commits merged), AND
- `git status --short` shows only noise files (gradlew.bat, generated output).
