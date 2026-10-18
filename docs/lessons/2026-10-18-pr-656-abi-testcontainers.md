# PR #656 재개 때 ABI 기준선과 Docker 환경을 확인한다

## 맥락

PR #656의 코드 정리 변경을 재개하며 Kotlin/JVM 공개 API 호환성과 컨테이너 기반 백엔드 테스트를 다시 검증했다. 작업은 원래 PR head에서 분리한 worktree에서 이어갔다.

## 판단과 발견

- ABI 비교 기준은 PR head가 아니라 정확한 `develop` base `667a8c93bee7136269a70b26aa91d33567ad7d91`이어야 한다. PR head `c73190626b19919d1ca1ebfebd73540871c23a0d`에는 검토 대상 변경이 이미 포함되어 있어 기준으로 사용하면 회귀를 놓칠 수 있다.
- Colima는 정상이어도 테스트를 실행한 프로세스가 Docker 환경 변수를 상속하지 못할 수 있다. `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`만 전달하면 호스트 daemon 탐색이 해결되지 않으므로 `DOCKER_HOST`도 같은 테스트 명령에 설정하고 Testcontainers가 선택한 socket 경로를 로그에서 확인한다.
- 시간 측정 검증은 `Duration.toMillis()`가 1ms 미만 값을 0으로 절삭할 수 있다. 양수 여부를 검사할 때에는 `toNanos()`를 사용한다.

## 결과

`GraphPath` List factory, 기존 `BfsDfsOptions` BFS/DFS 오버로드, `MissingWeightException` 상위 타입·생성자 서명을 유지하면서 새 traversal API를 함께 제공한다. graph-io report와 Okio facade의 기준 public descriptor도 보존했다. 민감한 traversal/query 로그를 제거했고 GraphIoRecordSpool replay registry 경합을 같은 lock으로 보호했다.

독립 리뷰 lane은 사용량 제한 때문에 판정을 내지 못했다. 제한된 liveness probe 후 실패 상태를 receipt에 남겼고, 독립 attestation으로 취급하지 않는 inline fallback 리뷰를 완료했다. 결과는 `docs/review/2026-10-19-pr-656-inline-fallback.md`에 기록했다. 새 PR HEAD의 CI는 push 전이라 대기 중이며 병합하지 않았다.

## 검증

- graph-core 432개(6 skip), graph-io-core 191개 테스트와 Detekt를 재실행했고 모두 통과했다. 전체 `./gradlew build -x test --max-workers=1`도 통과했다.
- graph-io CSV, Jackson2/3, GraphML, Okio, TinkerPop, AGE, FalkorDB, Neo4j, Memgraph 백엔드 대상 테스트 및 Detekt는 앞선 직렬 실행에서 통과했다.
- `javap -public -s`로 깨끗한 `develop` 산출물과 현재 산출물을 비교했다. 확인한 core API, graph-io report/options, `JacksonOkioExtensionsKt` facade에서 기존 descriptor 손실은 없었다.
- Testcontainers 로그에서 `unix:///Users/debop/.colima/default/docker.sock` 선택을 확인했다.
- 앞선 컨테이너 실행 실패는 테스트 프로세스에 `DOCKER_HOST`가 전달되지 않은 실행 환경 문제였다. 이 증거는 코드 테스트 실패로 분류하지 않는다.
- graph-core 여섯 skip은 `BfsDfsRunnerTest`의 기존 `@Disabled("use GraphVisitorTest")` 테스트다. 새 검증에서 실패가 없었으나 이 여섯 테스트를 활성화해 실행하지 않았다.
- 새 커밋의 GitHub exact-head CI와 전체 공개 jar ABI 전수 비교는 완료되지 않았다.

## 다음 작업 지침

호환성 비교는 PR의 변경을 포함한 head가 아니라 정확한 target base 산출물과 수행한다. Colima/Testcontainers 검증은 해당 Gradle 프로세스의 `DOCKER_HOST`와 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 값을 확인하고, 모듈 간 컨테이너 테스트는 직렬로 실행한다. 실패가 발생하면 재시도 전에 Testcontainers provider 로그와 테스트 프로세스 환경을 확인한다.
