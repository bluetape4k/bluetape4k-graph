# 네트워크 경로의 활성 endpoint 계약

## 배경

`network-topology-examples`의 경로 탐색은 `CONNECTED_TO` edge와 다음 device의 상태만
확인하고 source device는 탐색 전에 확인하지 않았다. 그 결과 비활성 source에서 활성
target으로 이어지는 경로와 비활성 source의 자기 경로가 반환될 수 있었다. sync와
suspend 구현에 같은 탐색 로직이 있어 두 API의 동작 계약도 함께 고정해야 했다.

## 결정 또는 발견

`shortestDevicePath`의 내부 탐색 진입점에서 source와 target이 `Device`이면서
`status == "active"`인지 먼저 확인한다. 기존 `failedDeviceIds`의 source·target 검사와
활성 edge 및 다음 device 필터는 유지한다. `redundantDevicePaths`에도 같은 양 endpoint
검사를 추가해 시작 source가 비활성일 때 후보 경로를 만들지 않도록 한다.

sync와 suspend service는 동일한 상태 predicate를 사용한다. 따라서 직접 device path,
service path, isolated segment 판정, redundant path가 비활성 endpoint를 일관되게
제외하고, `failedDeviceIds`가 지정된 direct/service path의 실패 endpoint 및 중간 device
제외 계약도 보존한다.

## 결과

비활성 source·target은 경로 결과에서 제외된다. 활성 edge를 통해 도달할 수 있더라도
비활성 source는 route의 시작점이 될 수 없고, source와 target이 같은 비활성 device인
경우에도 자기 경로를 반환하지 않는다. 이 규칙은 TinkerGraph 기반 sync와 suspend
예제에서 같은 방식으로 적용된다.

## 검증

- 상위 실행에서 독립 TinkerGraph 회귀 테스트 6개가 수정 전 의도된 동작으로 RED임을
  확인했다.
- `NetworkTopologyActivePathRegressionTest`가 비활성 source→활성 target, 비활성
  자기 경로, 비활성 destination, `failedDeviceIds`, service path, redundant path를
  sync/suspend 각각 검증하도록 추가했다.
- `git diff --check`를 수정 후 실행한다.
- 전체 Gradle 테스트와 빌드는 상위 에이전트가 직렬 검증 게이트에서 실행한다.

## 향후 지침

새로운 network topology traversal을 추가할 때는 edge 상태 필터만으로 endpoint 상태를
대체하지 않는다. sync와 suspend 구현에 동일한 endpoint predicate와 회귀 사례를 함께
반영하고, `failedDeviceIds`를 받는 API에서는 탐색 전에 source·target·중간 device의
실패 규칙을 확인한다.

## 추적

- GitHub issue: [#636](https://github.com/bluetape4k/bluetape4k-graph/issues/636)
