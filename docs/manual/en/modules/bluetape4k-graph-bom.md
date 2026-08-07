# bluetape4k-graph-bom

## Before you run

This platform aligns the published graph modules. Application builds should normally select the graph line through the ecosystem `bluetape4k-dependencies` BOM, then declare graph modules without versions. Do not add an independent graph BOM version beside the ecosystem BOM: two platform authorities can resolve a combination that the release train did not verify.

Choose the BOM when a library catalog or dependency-management layer needs graph constraints. Avoid treating it as a runtime module; it contains no graph API, driver, importer, or server.


Execution mode: **standalone consumer-build check**. Replace `<ecosystem-version>` with the approved `bluetape4k-dependencies` version and run `dependencyInsight` from the consuming application.

## Run

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-neo4j")
    implementation("io.github.bluetape4k:bluetape4k-graph-io-jackson3")
}
```

## Expected result

Expected: Gradle resolves both modules to the graph version selected by the ecosystem BOM. Confirm it with an exact command:

```bash
./gradlew dependencyInsight --dependency bluetape4k-graph-core --configuration runtimeClasspath
```

If more than one version is selected, inspect imported platforms and explicit constraints before forcing a version. The platform does not provision a database or add backend modules automatically.

## Managed boundary

The release platform constrains core, five database adapters, TinkerPop, graph-io codecs, OkIO, Ktor, and Spring Boot. Examples and benchmarks are intentionally outside the published platform. The authoritative constraint list is the pinned [BOM build](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/bom/build.gradle.kts); the module inventory is registered in [settings.gradle.kts](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/settings.gradle.kts).

There is no transaction, resource, or shutdown behavior in this module. Those semantics belong to the selected backend, graph-io format, or framework integration.

## Operations checklist

- Keep exactly one ecosystem BOM authority.
- Record the resolved graph version in dependency-lock or build-scan evidence.
- Review graph module drift after every ecosystem BOM update.
- Run the selected runtime module tests separately.

## Failure and recovery

Symptom: `dependencyInsight` shows multiple graph versions. Remove the competing platform or explicit version, refresh dependencies, and rerun the command.

Record the ecosystem BOM version in dependency-lock or build-scan evidence. When a transitive graph module drifts, run `dependencyInsight`, locate the competing platform or explicit version, and restore one authority. A successful dependency resolution does not prove server compatibility; run the selected module's focused tests.

```bash
./gradlew :bluetape4k-graph-bom:build
```

Expected: platform metadata is generated without runtime tests. A failure here usually points to publication metadata or a missing project constraint, not graph data.

## Complete release example

The pinned [BOM build](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/bom/build.gradle.kts) is complete executable release evidence. Run:

```bash
./gradlew :bluetape4k-graph-bom:build
```

Expected: the release test or build completes with the ownership and capability assertions above.

## Non-goals and related guides

See [repository map](../architecture/repository-map.md), [getting started](../getting-started.md), and the page for each selected module. This page does not recommend a standalone graph version, document backend APIs, or promise compatibility for examples and benchmark projects.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### bom Architecture diagram

[![bom Architecture diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/bom-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/bom-architecture-01.svg)

_Release README: [`bom/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/bom/README.md)_

<!-- release-readme-diagrams:end -->
