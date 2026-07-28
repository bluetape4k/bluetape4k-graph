# Merge / Upsert 구현 계획

## Related Spec

- Spec: `docs/superpowers/specs/2026-05-09-merge-upsert-design.md`
- 이슈: #34
- Branch/worktree: `feat/issue-34-merge-upsert`

## Task List

| Task | Complexity | Modules / Files | Work | Verification |
|------|------------|-----------------|------|--------------|
| T1 | high | `graph-core/src/main/kotlin/io/bluetape4k/graph/repository` | Add `GraphMergeOperations`, `GraphSuspendMergeOperations`, sync/suspend extension functions, and common validation helpers with Korean KDoc | `./gradlew :graph-core:compileKotlin :graph-core:compileTestKotlin --no-daemon` |
| T2 | medium | `graph-core/src/test` | Add extension/capability and validation tests using `assertFailsWith` | `./gradlew :graph-core:test --tests '*Merge*' --no-daemon` |
| T3 | high | `graph-neo4j` | Implement native vertex/edge `MERGE` for sync and suspend operations; wire caching wrapper invalidation | `./gradlew :graph-neo4j:test --tests '*Merge*' --no-daemon` |
| T4 | high | `graph-memgraph` | Implement native Cypher merge and verify Memgraph-specific parameter behavior | `./gradlew :graph-memgraph:test --tests '*Merge*' --no-daemon` |
| T5 | high | `graph-falkordb` | Implement FalkorDB merge with per-property parameters and Testcontainer validation | `./gradlew :graph-falkordb:test --tests '*Merge*' --no-daemon` |
| T6 | high | `graph-age`, `graph-age/src/main/kotlin/.../sql/AgeSql.kt` | Add AGE transactional match/update/create fallback because current AGE rejects `ON CREATE SET` / `ON MATCH SET` | `./gradlew :graph-age:test --tests '*Merge*' --no-daemon` |
| T7 | high | `graph-tinkerpop` | Implement TinkerGraph upsert via Gremlin traversal and delegate suspend methods | `./gradlew :graph-tinkerpop:test --tests '*Merge*' --no-daemon` |
| T8 | high | backend tests | Add create-branch, match/update-branch, duplicate-prevention, edge idempotency, invalid input, and suspend tests | Targeted backend test commands |
| T9 | medium | `README.md`, `README.ko.md`, module READMEs | Document merge/upsert API and capability notes in English and Korean | README review + compile unaffected |
| T10 | low | `docs/superpowers/index/2026-05.md`, `docs/superpowers/INDEX.md` | Add in-progress spec/plan index row and update counts | file review |
| T11 | high | all changed modules | Run compile, targeted tests, full test, diff check, and 6-tier review | `./gradlew test --no-daemon`, `git diff --check` |

## Implementation Notes

- Keep merge as a capability interface plus extension functions, not direct members on `GraphOperations`.
- Common validation should live in core and be reused by every backend before DDL/query construction.
- Use separate parameter names for match and set maps, for example `match_email` and `set_name`, to avoid collisions and to make overlap rejection explicit.
- `mergeVertex` rejects empty `matchProperties`; `mergeEdge` allows empty `matchProperties` because endpoints and label are part of the identity.
- `matchProperties` rejects null values; `setProperties` follows backend update semantics.
- Caching wrappers must invalidate read caches and write memoization maps for merge operations.
- Existing tests must continue to use `io.bluetape4k.assertions.assertFailsWith`.

## Backend Query Sketches

### Cypher Backends

```cypher
MERGE (n:Label {key: $match_key})
ON CREATE SET n.other = $set_other
ON MATCH SET n.other = $set_other
RETURN n
```

For empty `setProperties`, omit both `ON CREATE SET` and `ON MATCH SET`.

Relationship merge:

```cypher
MATCH (a), (b)
WHERE elementId(a) = $fromId AND elementId(b) = $toId
MERGE (a)-[r:TYPE {key: $match_key}]->(b)
ON CREATE SET r.other = $set_other
ON MATCH SET r.other = $set_other
RETURN r
```

FalkorDB uses `id(a) = toInteger($fromId)` instead of `elementId`.

### AGE

Generate Cypher literals through `AgePropertySerializer` after validation and wrap with `AgeSql.cypher`.
Use one Exposed transaction for `MATCH -> UPDATE/CREATE` because the tested AGE image does not accept
`MERGE ... ON CREATE SET ... ON MATCH SET`.

### TinkerGraph

Use traversal get-or-create, then apply `setProperties` outside the `coalesce` branch so both existing and newly created elements receive updates.

## Test Matrix

| Behavior | Core | Neo4j | Memgraph | FalkorDB | AGE | TinkerGraph |
|----------|------|-------|----------|----------|-----|-------------|
| Unsupported extension failure | yes | no | no | no | no | no |
| Validation rejects unsafe label/property | yes | yes | yes | yes | yes | yes |
| Vertex create branch | no | yes | yes | yes | yes | yes |
| Vertex match/update branch | no | yes | yes | yes | yes | yes |
| Vertex repeated call count remains one | no | yes | yes | yes | yes | yes |
| Edge create branch | no | yes | yes | yes | yes | yes |
| Edge match/update branch | no | yes | yes | yes | yes | yes |
| Edge repeated call count remains one | no | yes | yes | yes | yes | yes |
| Suspend variants | no | yes | yes | yes | yes | yes |

## Step 3-P Risk Mitigations

- Security: core validation tests plus backend invalid-identifier tests.
- Reliability: repeated merge calls assert counts and returned IDs/properties.
- Compatibility: capability extension pattern avoids direct facade interface changes.
- Concurrency: backend-native MERGE is preferred; no generic read-then-write fallback in core. AGE keeps a backend-local transactional fallback because native update branches are unavailable.
- Performance: schema/index support from #32 should be documented as the recommended companion for high-cardinality merge keys.

## Step 3-R 리뷰 메모

| Perspective | Finding | Plan Change |
|-------------|---------|-------------|
| Implementer | Core validation must land first so backend code stays consistent. | T1/T2 precede backend work. |
| Test engineer | Idempotency requires count assertions, not just returned object equality. | T8 includes duplicate-prevention checks. |
| Architect | Direct repository changes would break existing implementers. | Capability pattern is mandatory. |
| Delivery | FalkorDB and AGE support must be proven in containers because docs and client behavior may diverge. | T5/T6 require targeted Testcontainer tests. |
| Operations | Merge can be expensive without indexes. | Docs mention #32 schema indexes as companion setup. |
