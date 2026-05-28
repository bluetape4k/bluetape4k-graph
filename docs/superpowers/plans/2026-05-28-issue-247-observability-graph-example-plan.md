# Issue 247 Observability Graph Example Plan

## DoD

- [x] Add `observability-graph-examples` Gradle module under `examples/`.
- [x] Implement sync and suspend observability incident services.
- [x] Add bundled graph-io CSV fixtures and loader.
- [x] Add abstract backend tests plus concrete TinkerGraph, Neo4j, Memgraph, AGE, and FalkorDB classes.
- [x] Add English and Korean README files with scenario, Architecture Diagram, graph model, traversal goals, sample data,
  and expected output.
- [x] Register the module in root README locale set, agent guidance module lists, Examples workflow, and changelog.
- [x] Verify compile, targeted tests, workflow YAML, module registration, and whitespace.

## Validation Commands

```bash
./gradlew :observability-graph-examples:compileKotlin :observability-graph-examples:compileTestKotlin --no-daemon
./gradlew :observability-graph-examples:test --no-daemon
./gradlew projects --no-daemon
actionlint .github/workflows/examples.yml
git diff --check
```

## Notes

Container-backed tests must run in one sequential lane. README architecture imagery is stored under
`docs/images/readme-diagrams/` with a PNG embed and matching SVG source.
