# Schema / Index manager 구현 계획

## Related Spec

- Spec: `docs/superpowers/specs/2026-05-09-schema-index-manager-design.md`
- 이슈: #32
- Branch/worktree: `feat/issue-32-schema-index-manager`

## Task List

| Task | Complexity | Modules / Files | Work | Verification |
|------|------------|-----------------|------|--------------|
| T1 | high | `graph-core/src/main/kotlin/io/bluetape4k/graph/model`, `graph-core/src/main/kotlin/io/bluetape4k/graph/schema` | Add `GraphIndex`, `GraphConstraint`, schema manager interfaces, capability interfaces, accessors, and DSL overloads with Korean KDoc | `./gradlew :graph-core:compileKotlin :graph-core:compileTestKotlin --no-daemon` |
| T2 | high | `graph-neo4j` | Implement sync/suspend Neo4j schema managers and wire `Neo4jGraphOperations` / `Neo4jGraphSuspendOperations` providers | `./gradlew :graph-neo4j:test --tests '*Schema*' --no-daemon` |
| T3 | high | `graph-memgraph` | Implement sync/suspend Memgraph schema managers using Memgraph DDL syntax and metadata mapping | `./gradlew :graph-memgraph:test --tests '*Schema*' --no-daemon` |
| T4 | medium | `graph-tinkerpop` | Implement sync/suspend TinkerGraph no-op/listable index manager and explicit unsupported uniqueness | `./gradlew :graph-tinkerpop:test --tests '*Schema*' --no-daemon` |
| T5 | high | `graph-age` | Implement AGE manager for tested PostgreSQL-side index support; use explicit unsupported behavior for unverified unique constraints | `./gradlew :graph-age:test --tests '*Schema*' --no-daemon` |
| T6 | high | `graph-falkordb` | Implement FalkorDB index support; implement or explicitly reject unique constraint/drop operations based on driver/server verification | `./gradlew :graph-falkordb:test --tests '*Schema*' --no-daemon` |
| T7 | medium | backend tests | Add sync and suspend integration tests for index create/list/drop and unique constraint behavior per capability matrix | backend targeted test commands |
| T8 | low | `graph-core/README.md`, `graph-core/README.ko.md`, backend README files if needed | Document schema manager API and backend capability matrix | README review + compile unaffected |
| T9 | low | `docs/superpowers/index/2026-05.md`, `docs/superpowers/INDEX.md` | Add spec/plan index entry and update counts | file review |
| T10 | high | all changed modules | Run verification, performance scan, six-tier review, and full tests | `./gradlew test --no-daemon`, `git diff --check` |

## Implementation Notes

- Follow transaction DSL capability pattern instead of adding members directly to `GraphOperations`.
- All DDL identifiers must call `requireSafeIdentifier` before interpolation.
- Use generated names `bt4k_idx_{label}_{property}` and `bt4k_uc_{label}_{property}` where the backend supports named objects.
- Prefer explicit unsupported exceptions over no-op constraints.
- Keep metadata assertions tolerant of backend-specific extra columns.
- `GraphSuspendOperations.schemaManager()` should be a non-suspend accessor; individual manager methods are suspend.

## README / KDoc / AGENTS Impact

- README update is required because the public API surface changes.
- Korean KDoc is required for all public model/interface/extension APIs.
- No AGENTS.md update is expected because no new durable process convention or module is introduced.

## Step 3-P Risk Mitigations

- Security: unit test unsafe label/property rejection for at least core helper and one Cypher backend.
- Reliability: duplicate create/drop should be idempotent where backend supports it; otherwise tests should document exact behavior.
- Compatibility: caching wrappers should not expose schema management unless intentionally implemented.
- Performance: DDL helpers are cold path; avoid repeated runtime regex construction by reusing existing `requireSafeIdentifier`.

## Step 3-R 리뷰 메모

| Perspective | Finding | Plan Change |
|-------------|---------|-------------|
| Implementer | Core accessor and model work should land first to keep backend tasks compile-guided. | T1 remains first and high complexity. |
| Test engineer | Backend metadata shapes differ; tests should assert common fields, not raw row maps. | T7 calls out capability-matrix integration tests. |
| Architect | Direct facade interface changes would create wide source breakage. | Capability pattern is mandatory in implementation notes. |
| Delivery | AGE/FalkorDB uniqueness may not be reliably supported in one slice. | T5/T6 allow explicit unsupported behavior with tests instead of silent success. |
