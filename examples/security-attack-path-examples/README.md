# security-attack-path-examples

> 🇰🇷 [한국어 문서](README.ko.md)

This example models a compact security attack-path graph for entry assets, hosts, principals, credentials,
vulnerabilities, permissions, and crown-jewel systems. It demonstrates backend-independent traversal for path
explanation, privilege escalation, risk ranking, and remediation impact.

> This is an educational graph-modeling example, not a security scanner. It does not ingest vulnerability feeds, run
> probes, validate exploitability, or make production security decisions.

## Scenario

The sample graph starts from an internet-facing entry asset, reaches a public web host, pivots through an exploit into a
service account, discovers an admin credential, and reaches a customer database through an admin permission. A backup
vault is modeled as an unreachable crown jewel so tests can distinguish reachable and unreachable targets.

## Architecture

![security attack path examples architecture](../../docs/images/readme-diagrams/examples-security-attack-path-examples-architecture-01.png)

## Graph Model

| Element | Label | Key properties | Purpose |
|---|---|---|---|
| Entry asset | `EntryAsset` | `assetId`, `exposure`, `status` | External or internal starting point. |
| Host | `Host` | `hostId`, `exposure`, `criticality`, `status` | Workload, data store, or protected system. |
| Principal | `Principal` | `principalId`, `kind`, `privilege`, `status` | Human, service, or assumed identity. |
| Credential | `Credential` | `credentialId`, `kind`, `status` | Token or credential material used for pivots. |
| Vulnerability | `Vulnerability` | `vulnerabilityId`, `severity`, `status` | Educational risk signal for path ranking. |
| Permission | `Permission` | `permissionId`, `privilege`, `status` | Capability that can control a protected host. |

## Traversal Goals

| Question | API |
|---|---|
| What is the shortest active path from an entry asset to a crown jewel? | `shortestAttackPath(sourceAssetId, targetHostId)` |
| Which attack paths have the strongest risk signals? | `rankedAttackPaths(sourceAssetId, targetHostId)` |
| How can a low-privilege service account reach admin privilege? | `privilegeEscalationPaths(startPrincipalId)` |
| Which crown jewels are unreachable from a source? | `unreachableCrownJewels(sourceAssetId)` |
| Which crown jewels become unreachable after cutting one edge? | `remediationImpact(blockedEdgeId)` |

## Sample Dataset

The module bundles graph-io CSV fixtures under `src/main/resources/sample-data/security-attack-path/`.

| File | Contents |
|---|---|
| `vertices.csv` | entry assets, hosts, principals, credentials, vulnerabilities, and permissions. |
| `edges.csv` | reachability, exploit, compromise, credential, permission, and asset-control edges. |

```kotlin
val ops = TinkerGraphOperations()
val service = SecurityAttackPathService(ops)
service.initialize()

SecurityAttackPathSampleDatasetLoader.importCsv(ops)
val path = service.shortestAttackPath("internet", "customer-db")
val blocked = service.remediationImpact("edge-credential-admin")
```

## Expected Output

| Query | Expected IDs |
|---|---|
| `shortestAttackPath("internet", "customer-db")` | `internet`, `web-edge`, `vuln-web-rce`, `web-service`, `ci-admin-token`, `domain-admin`, `db-admin`, `customer-db` |
| `privilegeEscalationPaths("web-service")` | `web-service`, `ci-admin-token`, `domain-admin` |
| `unreachableCrownJewels("internet")` | `backup-vault` |
| `remediationImpact("edge-credential-admin")` | `customer-db` |

## Running Tests

```bash
./gradlew :security-attack-path-examples:test
```

The first slice uses TinkerGraph smoke coverage and graph-io CSV loader tests.
