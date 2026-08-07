# IAM 접근 그래프

## 문제와 백엔드

사용자, 그룹, 역할, 정책, 비상 권한을 펼쳐 실제 접근 경로를 설명합니다. **TinkerGraph**를 써서 컨테이너와 네트워크 편차를 빼고 모델부터 검증합니다. 먼저 [핵심 모델](../architecture/core-model.md)과 [TinkerPop](../backends/tinkerpop.md)을 읽고, 운영 전에는 [선택 가이드](../backends/selection-guide.md)를 적용합니다.

## 그래프 모델

- 정점: IamUser/IamGroup/IamRole/IamPolicy/IamPermission/IamResource/IamSessionGrant
- 간선: MEMBER_OF/HAS_ROLE/ATTACHED_POLICY/GRANTS_PERMISSION/APPLIES_TO/HAS_TEMP_GRANT/TEMPORARY_PERMISSION
- 주요 속성: userId, roleId, policyId, action, resourceId, grantId, expiresAt

## 준비와 릴리스 경계

JDK 21, 커밋 `72c0256e2e1cf61101d29852210e3c827ca93bc0`, 저장소의 Gradle Wrapper가 필요합니다. 예제는 배포되지 않으므로 릴리스 소스를 체크아웃하고 Gradle 프로젝트로 실행합니다. 소비자 애플리케이션에서는 `bluetape4k-dependencies:<ecosystem-version>`만 선택하고 필요한 그래프 모듈은 개별 버전 없이 추가합니다.

## 실행과 관찰

```bash
./gradlew :iam-access-graph-examples:test --tests "io.bluetape4k.graph.examples.iam.TinkerGraphIamAccessGraphTest"
```

테스트는 그룹에서 상속한 접근 경로가 `group:engineering`과 `role:deployer-role`을 지나는지, 임시 비상 접근 경로가 `grant:break-glass-1001`을 지나는지를 따로 검증합니다. 실패하면 사용자·그룹·역할의 연결 방향, 정책 확장, 거부 경계를 확인합니다.

## 코드 읽는 순서

1. [스키마](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/iam-access-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/iam/schema/IamAccessGraphSchema.kt)
2. [서비스](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/iam-access-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/iam/service/IamAccessGraphService.kt)
3. [공통 실행 계약](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/iam-access-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/iam/AbstractIamAccessGraphTest.kt)
4. [TinkerGraph 구체 테스트](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/iam-access-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/iam/IamAccessBackendTests.kt)
5. [빌드 파일](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/iam-access-graph-examples/build.gradle.kts)

[linkedin-graph](./linkedin-graph.md) 다음에 읽고 [fraud-detection](./fraud-detection.md)로 이어가십시오. [동기·코루틴 API](../architecture/paired-apis.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)도 함께 보십시오.

## 확장과 운영 진단

결과를 바꾸는 간선과 단언을 하나 추가하고 suspend API로 반복하십시오. 영속 백엔드 테스트는 직렬로 실행하고 끊어진 경로와 잘못된 입력도 검증하십시오. 이 고정 데이터는 처리량, 군집, 권한, 테넌트 격리, 마이그레이션, 백업, 원격 드라이버 제한 시간, 인덱스 품질을 증명하지 않습니다.
