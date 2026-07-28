# Local Sync와 Cleanup

## 맥락

여러 sprint cycle 이후 로컬 repository에는 remote가 사라진 stale branch 18개와
worktree 11개가 누적되어 있었다. 기본 worktree는 `develop`이 아니라 이미 merge된
feature branch인 `build/gradle-9.5.1-wrapper-bat`에 머물러 있었다. `develop` pull은
`git restore`, `git stash`, `git reset --hard`로도 해소되지 않는 `gradlew.bat`
변경 때문에 막혀 있었다.

## 문제 1 - reset 이후에도 `gradlew.bat`가 dirty로 남음

### 원인

`.gitattributes`는 `*.bat text eol=crlf`를 선언한다. 전역 git config에는
`core.autocrlf=input`이 설정되어 있다. macOS에서는 두 설정이 충돌한다.
`eol=crlf`는 working tree에 CRLF를 쓰도록 지시하지만, `core.autocrlf=input`은
checkin 시 CRLF를 LF로 변환한다. index에는 CRLF content가 기록되고 checkout 때
smudge filter가 CRLF를 다시 적용했지만 git stat cache가 어긋나면서
`git reset --hard HEAD` 이후에도 `gradlew.bat`가 dirty로 보였다.

### 수정

```bash
git rm --cached gradlew.bat
git reset --hard origin/develop
```

index entry를 먼저 제거하면 cache lock이 끊기고, `reset --hard`가 index와
working tree를 모두 깨끗하게 다시 쓸 수 있다.

### 향후 가드

CRLF attribute 충돌은 `git restore`, `git stash`, `git checkout -- <file>`로
고치려 하지 않는다. 이 명령들은 smudge filter를 다시 실행해 같은 loop로 되돌아간다.
먼저 파일을 cache에서 제거한 뒤 hard-reset한다.

---

## 문제 2 - `develop`이 다른 worktree에서 checkout되어 전환할 수 없음

### 원인

`develop`은 `.worktrees/fix/issue-157-schema-manager-errors`에서 checkout되어
있었다. 해당 issue는 이미 closed/merged 상태였다. Git은 다른 worktree에서 이미
활성화된 branch를 checkout하지 않는다.

### 수정

1. worktree에 고유 commit이 없고 의미 있는 dirty file도 없는지 확인한다.
2. `git worktree remove --force .worktrees/fix/issue-157-schema-manager-errors`
3. `git checkout develop`

### 향후 가드

issue를 닫고 PR을 merge한 뒤에는 해당 worktree를 즉시 제거한다. stale worktree는
branch 전환을 막고 CRLF/state noise를 누적시킨다.

---

## 문제 3 - `build/align-dependency-boms`에 `lz4` security pin이 있었음

해당 branch는 `org.lz4:lz4-java`의 CVE GHSA-cmp6-m4wj-q63q 대응을 위해
로컬 catalog에 `at.yawk.lz4:lz4-java:1.11.0` direct version pin을 포함하고
있었다. 올바른 수정은 이 repository에서 직접 pin하는 것이 아니라 version을 중앙에서
관리하는 `bluetape4k-dependencies` BOM을 소비하는 것이다.

### 결정

`gradle/libs.versions.toml`에 `bluetape4k-dependencies = "1.0.1-SNAPSHOT"`을
추가하고, `bluetape4k-experimental`에 확립된 pattern에 맞춰 `build.gradle.kts`에서
`spring.boot4.dependencies`와 함께 BOM을 import했다. 이후 `build/align-dependency-boms`
branch를 삭제했다.

### 향후 가드

version ownership이 `bluetape4k-dependencies`에 있으면 이 repository catalog에서
dependency version을 직접 pin하지 않는다. 중앙 catalog를 갱신하고
`sync-shared-versions.py`로 여기까지 동기화한다.

---

## 사용한 정리 절차

```bash
# 1. remote가 사라진 worktree 제거
git worktree remove --force .worktrees/<path>

# 2. remote가 사라진 local branch 삭제
git branch -D <branch>

# 3. 검증
git branch -vv
git worktree list
```

branch는 다음 조건을 만족할 때 삭제해도 안전하다.
- `git log --oneline origin/develop..HEAD`가 아무것도 반환하지 않는다. 즉 모든 commit이 merge됐다.
- `git status --short`가 noise file만 보여준다. 예: `gradlew.bat`, generated output.
