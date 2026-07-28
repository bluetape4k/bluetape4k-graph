# 이슈 251 Network Topology Example

## 맥락

0.5.0 milestone에 network topology graph 예제를 추가했다. 기존 CSV fixture 기반 예제들과 같은 범위로, path finding과 failure-impact traversal을 테스트 가능한 형태로 제한했다.

## 결정

네트워크 시뮬레이터나 weighted routing policy를 만들지 않고 Site/Device/Segment/Service 정점과 link/hosting/membership edge만 사용했다. 장애 영향은 core에서 service host까지의 baseline active path가 존재하지만 실패 device/link를 제외하면 path가 사라지는 경우로 정의했다.

## 결과

`network-topology-examples` 모듈은 shortest path, service impact, isolated segment, redundant route API와 sync/suspend TinkerGraph smoke tests를 제공한다. Root README/README.ko, repo-local `AGENTS.md`, Examples workflow, CHANGELOG에 새 모듈을 등록했다.

## 검증

- `./gradlew :network-topology-examples:test --no-daemon`: 6 passing
- `./gradlew :network-topology-examples:build --no-daemon`: success
- `./gradlew projects --no-daemon`: `:network-topology-examples` registered
- `git diff --check`: success

## 향후 지침

Weighted routing, dynamic link state simulation, or policy-aware path selection should be separate issues. Keep this example focused on deterministic traversal over compact fixtures unless the acceptance criteria explicitly widen.
