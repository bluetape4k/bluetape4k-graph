# iam-access-graph-examples

> 🇺🇸 [English](README.md)

User, group, role, policy, permission, resource, temporary grant를 IAM access 분석 그래프로 모델링하는 예제입니다.
사용자가 어떤 resource에 접근할 수 있는 이유, grant가 없거나 deny policy로 차단되는 경우, 검토해야 할 inherited
privilege chain을 backend 독립 graph traversal로 설명합니다.

## 예제 시나리오

Engineering 사용자는 group을 통해 staging deploy 권한을 받고, nested privileged group을 통해 production administrator
권한까지 상속받습니다. Auditor는 direct read-only role을 받고, operations 사용자는 임시 break-glass grant를 받으며,
contractor는 deny policy로 차단됩니다.

## Graph Model

| 요소 | Label | 주요 속성 | 목적 |
|---|---|---|---|
| User | `IamUser` | `userId`, `displayName`, `department` | 평가 대상 human identity입니다. |
| Group | `IamGroup` | `groupId`, `name`, `riskTier` | membership과 nested privilege boundary입니다. |
| Role | `IamRole` | `roleId`, `name`, `privilege` | 할당 가능한 permission bundle입니다. |
| Policy | `IamPolicy` | `policyId`, `name`, `effect` | role에 연결된 allow 또는 deny policy입니다. |
| Permission | `IamPermission` | `permissionId`, `action` | `read`, `deploy`, `delete` 같은 action입니다. |
| Resource | `IamResource` | `resourceId`, `resourceType`, `classification` | 보호 대상입니다. |
| Session grant | `IamSessionGrant` | `grantId`, `reason`, `expiresAt` | temporary break-glass access입니다. |

## Traversal Goals

| 질문 | API |
|---|---|
| 이 사용자가 왜 resource에서 action을 수행할 수 있는가? | `explainAccess(userId, resourceId, action)` |
| 어떤 nested group이 admin access를 부여하는가? | `riskyPrivilegeChains(userId)` |
| approved least-privilege set을 초과하는 grant는 무엇인가? | `excessivePermissions(userId, approvedActionsByResource)` |

## Walkthrough

```kotlin
val ops = TinkerGraphOperations()
val service = IamAccessGraphService(ops)
service.initialize()
IamAccessSampleGraph.seed(service)

val deploy = service.explainAccess("alice", "staging-service", "deploy")
check(deploy.allowed)
println(deploy.path)

val risky = service.riskyPrivilegeChains("alice")
val denied = service.explainAccess("eve", "prod-db", "delete")
```

## Expected Output

| Query | 예상 결과 |
|---|---|
| `explainAccess("bob", "audit-dashboard", "read")` | `readonly-role`을 통과하는 direct role path입니다. |
| `explainAccess("alice", "staging-service", "deploy")` | `engineering`, `deployer-role`을 통과하는 group-inherited path입니다. |
| `explainAccess("eve", "prod-db", "delete")` | `deny-prod-delete-policy`로 deny됩니다. |
| `explainAccess("bob", "prod-db", "delete")` | matching grant path가 없습니다. |
| `riskyPrivilegeChains("alice")` | `engineering -> platform-admins -> prod-admin-role` nested chain입니다. |
| `explainAccess("carol", "prod-db", "read")` | temporary `break-glass-1001` path입니다. |

## 테스트 실행

```bash
./gradlew :iam-access-graph-examples:test
./gradlew :iam-access-graph-examples:test --tests "*TinkerGraph*"
```

TinkerGraph 테스트는 메모리에서 실행됩니다. Neo4j, Memgraph, Apache AGE, FalkorDB 테스트는 Docker/Testcontainers가 필요합니다.
