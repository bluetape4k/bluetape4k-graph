# #474 Dependency Submission 운영 계약

## 결정

GitHub Dependency Graph를 저장소에서 활성화하고 `Dependency Submission`
workflow의 `contents: write` 권한과 `gradle/actions/dependency-submission`
단계를 유지한다. workflow를 임의로 성공 처리하거나 snapshot 제출을
건너뛰지 않는다.

## 이유

Dependency Submission action은 Gradle dependency snapshot을 업로드할 때
저장소 Dependency Graph가 꺼져 있으면 실패한다. 이는 Gradle 코드 문제가
아니라 저장소 보안 설정과 workflow 계약의 불일치다. 그래프를 활성화하면
기본 branch에서도 dependency snapshot을 정상적으로 제출할 수 있다.

## 검증 증거

- 저장소 설정: `PUT /repos/bluetape4k/bluetape4k-graph/vulnerability-alerts`
  호출 후 Dependency Graph 활성화 상태를 확인했다.
- workflow: [Dependency Submission run 31809941385](https://github.com/bluetape4k/bluetape4k-graph/actions/runs/31809941385)
  성공.
- 기준 commit: `54a5705369d138d034d8aacfe6e25114681cbddb` (`develop`).

## 운영 주의

새 저장소나 fork에서 같은 workflow가 실패하면 먼저 Dependency Graph와
workflow `contents: write` 권한을 확인한다. dependency snapshot 제출을
무조건 `continue-on-error`로 감추지 않는다.
