# Issue 10 Domain Examples

## Context

Issue #10 added three graph example modules: fraud detection, recommendation, and knowledge graph. Each module needed
blocking and suspend services, backend-independent tests, README locale pairs, and Nightly coverage.

## Decision

Use the existing abstract-test pattern from `code-graph-examples` and `linkedin-graph-examples`. Keep each module's
domain service thin over `GraphOperations` / `GraphSuspendOperations`, and place container-heavy domain example tests in a
separate Full Nightly job instead of PR test jobs.

## Outcome

The new modules cover TinkerGraph, Neo4j, Memgraph, Apache AGE, and FalkorDB for both blocking and suspend APIs. The
recommendation example uses paired one-hop traversals rather than mixed `maxDepth = 2` results so backend behavior stays
predictable.

The README locale pairs were later expanded into learner-oriented tutorials. Each README now starts from learning goals,
explains why a graph database fits the domain, and includes both a UML class diagram and a sequence/flow diagram before
the API snippet.

## Verification

- `./gradlew projects --no-daemon`
- `./gradlew :fraud-detection-examples:compileKotlin :fraud-detection-examples:compileTestKotlin :recommendation-examples:compileKotlin :recommendation-examples:compileTestKotlin :knowledge-graph-examples:compileKotlin :knowledge-graph-examples:compileTestKotlin --no-daemon`
- `./gradlew :fraud-detection-examples:test --tests '*TinkerGraph*' :recommendation-examples:test --tests '*TinkerGraph*' :knowledge-graph-examples:test --tests '*TinkerGraph*' --no-daemon`
- `./gradlew :fraud-detection-examples:test :recommendation-examples:test :knowledge-graph-examples:test --no-daemon --continue`
- `actionlint .github/workflows/nightly.yml`
- `actionlint .github/workflows/ci.yml`
- `./gradlew build -x test --parallel --no-daemon`
- `rg -n -P "[가-힣]" examples/fraud-detection-examples/src/main examples/recommendation-examples/src/main examples/knowledge-graph-examples/src/main`
- `git diff --check`
- README-only learning-doc update: `git diff --check`

IDE diagnostics were not available in the Codex tool surface for this session, so Gradle compile/test gates were used as
fallback evidence.

## Future Guidance

When adding graph example modules, add a separate Full Nightly job for container-heavy domain examples and keep PR CI on
compile/build unless the module has a fast in-memory test scope that should run on every PR.

For example READMEs, prefer a tutorial structure: learning goals, graph database rationale, architecture diagram, UML
domain diagram, execution flow, API snippet, and test-reading guidance.
