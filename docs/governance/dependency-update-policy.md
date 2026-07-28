# 의존성 업데이트 정책

## 현재 상태

`bluetape4k-graph`는 bluetape4k dependency graph에서 leaf repository다. Gradle과
Maven library version은 `bluetape4k-dependencies`에서 중앙 관리한다.

## 정책

- 공유 library version은 먼저 `bluetape4k-dependencies`에서 변경한다.
- 중앙 업데이트는 shared sync script로 이 저장소에 materialize한다.
- 이 저장소의 Dependabot 설정은 GitHub Actions 업데이트로 제한한다.
- 조직의 중앙 dependency-governance model이 바뀌기 전까지 이 저장소에서
  Renovate를 활성화하지 않는다.

## Dependabot 범위

`.github/dependabot.yml`은 의도적으로 `develop` branch의 GitHub Actions 업데이트만
추적한다. Gradle package update는 leaf repository가 중앙 BOM 및 version catalog와
드리프트하지 않도록 제외한다.

## 검증 계약

- `.github/dependabot.yml`을 수정한 뒤에는 파일을 parse한다.
- 중앙 version sync 작업에서는 중앙 dependency repository의 관련
  `sync-shared-versions.py`와 `sync-dependabot-ignores.py` check를 실행한다.
- repository별 Dependabot 범위를 바꾸기 전에는 집중 이슈에 의도적 예외를
  기록한다.
