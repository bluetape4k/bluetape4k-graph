# bluetape4k-graph-bom

## Purpose and selection

This platform aligns the published graph modules. Application builds should normally select the graph line through the ecosystem `bluetape4k-dependencies` BOM, then declare graph modules without versions. Do not add an independent graph BOM version beside the ecosystem BOM: two platform authorities can resolve a combination that the release train did not verify.

Choose the BOM when a library catalog or dependency-management layer needs graph constraints. Avoid treating it as a runtime module; it contains no graph API, driver, importer, or server.

## Dependency setup

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-neo4j")
    implementation("io.github.bluetape4k:bluetape4k-graph-io-jackson3")
}
```

Expected: Gradle resolves both modules to the graph version selected by the ecosystem BOM. Confirm it with an exact command:

```bash
./gradlew dependencyInsight --dependency bluetape4k-graph-core --configuration runtimeClasspath
```

If more than one version is selected, inspect imported platforms and explicit constraints before forcing a version. The platform does not provision a database or add backend modules automatically.

## Managed boundary

The release platform constrains core, five database adapters, TinkerPop, graph-io codecs, OkIO, Ktor, and Spring Boot. Examples and benchmarks are intentionally outside the published platform. The authoritative constraint list is the pinned [BOM build](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/bom/build.gradle.kts); the module inventory is registered in [settings.gradle.kts](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/settings.gradle.kts).

There is no transaction, resource, or shutdown behavior in this module. Those semantics belong to the selected backend, graph-io format, or framework integration.

## Operations and Failure diagnosis

Record the ecosystem BOM version in dependency-lock or build-scan evidence. When a transitive graph module drifts, run `dependencyInsight`, locate the competing platform or explicit version, and restore one authority. A successful dependency resolution does not prove server compatibility; run the selected module's focused tests.

```bash
./gradlew :bluetape4k-graph-bom:build
```

Expected: platform metadata is generated without runtime tests. A failure here usually points to publication metadata or a missing project constraint, not graph data.

## Related guides and non-goals

See [repository map](../architecture/repository-map.md), [getting started](../getting-started.md), and the page for each selected module. This page does not recommend a standalone graph version, document backend APIs, or promise compatibility for examples and benchmark projects.
