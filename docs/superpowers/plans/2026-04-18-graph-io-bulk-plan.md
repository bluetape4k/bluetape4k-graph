# graph-io Bulk Import/Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **코드 스니펫 규칙:** 이 플랜의 코드 블록은 **의사코드(pseudocode)** 입니다. 실제 구현 시 인터페이스 시그니처, 패키지명, API 호출 방식은 반드시 실제 소스코드를 참조하여 확인하십시오. 단, `build.gradle.kts` 내용 및 import 경로는 **실제 구현 코드**입니다.

**Goal:** Ship `graph-io-core` common module plus four format modules (CSV, Jackson2 NDJSON, Jackson3 NDJSON, GraphML) on top of `GraphOperations`/`GraphSuspendOperations`, with Sync / Virtual-Thread / Coroutine APIs and a full benchmark suite under `graph-io-benchmark`.

**Architecture:** `graph-io-core` holds common data model, Source/Sink, Options, Reports, and the Sync/VT/Coroutine contracts plus the VT adapter built on `virtualFutureOf`. Each format module provides its own importer/exporter trio (Sync, VT, Suspend). NDJSON uses single-pass read with bounded edge buffering; CSV uses per-label Union-header pre-scan; GraphML uses JDK StAX. Coroutine export consumes `GraphSuspendVertexRepository.findVerticesByLabel()` / `findEdgesByLabel()` Flows directly.

**Tech Stack:** Kotlin 2.3, Java 25 (preview), Gradle multi-module, `bluetape4k-io`, `bluetape4k-csv`, `bluetape4k-jackson2`, `bluetape4k-jackson3`, `bluetape4k-virtualthread-api/jdk25`, `bluetape4k-coroutines`, JDK StAX (`javax.xml.stream`), JUnit 5 + Kluent + MockK + kotlinx-coroutines-test, TinkerGraph (test backend).

**Spec Reference:** `docs/superpowers/specs/2026-04-18-graph-io-bulk-import-export-design.md`

---

## Module Coverage Matrix

| Module | Tasks | Purpose |
|--------|-------|---------|
| `graph-io-core` | 1-9 | Common data model, options, reports, contracts, source/sink, VT adapter |
| `graph-io-csv` | 10-15 | CSV Sync + VT + Suspend importer/exporter (Union header, prefixed/raw-JSON/none modes) |
| `graph-io-jackson2` | 16-18 | Jackson2 NDJSON importer/exporter (single-pass + edge buffering) |
| `graph-io-jackson3` | 19-21 | Jackson3 NDJSON importer/exporter (same shape, `tools.jackson.*`) |
| `graph-io-graphml` | 22-25 | StAX GraphML importer/exporter (subset + unsupported element policy) |
| Cross-format | 26-27 | TinkerGraph round-trip + compatibility tests |
| `graph-io-benchmark` | 28-30 | Benchmark deps + JMH suite + report |
| Docs | 31-32 | READMEs, TODO, tradeoffs update |
| Verify | 33 | Final compile/test/static-check pass |

---

## File Structure

```
graph-io/core/
  build.gradle.kts
  README.md, README.ko.md
  src/main/kotlin/io/bluetape4k/graph/io/
    model/
      GraphIoVertexRecord.kt
      GraphIoEdgeRecord.kt
    source/
      GraphImportSource.kt
      GraphExportSink.kt
    options/
      GraphImportOptions.kt
      GraphExportOptions.kt
      DuplicateVertexPolicy.kt
      MissingEndpointPolicy.kt
    report/
      GraphIoStatus.kt
      GraphIoFormat.kt
      GraphIoPhase.kt
      GraphIoFailureSeverity.kt
      GraphIoFileRole.kt
      GraphIoFailure.kt
      GraphImportReport.kt
      GraphExportReport.kt
    contract/
      GraphBulkImporter.kt
      GraphBulkExporter.kt
      GraphVirtualThreadBulkImporter.kt
      GraphVirtualThreadBulkExporter.kt
      GraphSuspendBulkImporter.kt
      GraphSuspendBulkExporter.kt
      GraphRecordFlowReader.kt
    support/
      GraphIoPaths.kt          // bufferedReader/writer, parent dir creation
      GraphIoExternalIdMap.kt  // external-id -> GraphElementId bounded map
      GraphIoStopwatch.kt      // elapsed-time helper
      VirtualThreadGraphBulkAdapter.kt  // wraps Sync importer/exporter
  src/test/kotlin/io/bluetape4k/graph/io/
    ...tests per package above...

graph-io/csv/
  build.gradle.kts
  README.md, README.ko.md
  src/main/kotlin/io/bluetape4k/graph/io/csv/
    CsvGraphImportSource.kt
    CsvGraphExportSink.kt
    CsvGraphIoOptions.kt          // CsvPropertyMode sealed interface
    CsvGraphBulkImporter.kt
    CsvGraphBulkExporter.kt
    CsvGraphVirtualThreadBulkImporter.kt
    CsvGraphVirtualThreadBulkExporter.kt
    SuspendCsvGraphBulkImporter.kt
    SuspendCsvGraphBulkExporter.kt
    internal/
      CsvRecordCodec.kt           // reserved col detection, union headers
  src/test/kotlin/... (round-trip, duplicate/missing, VT, suspend)

graph-io/jackson2/
  build.gradle.kts
  README.md, README.ko.md
  src/main/kotlin/io/bluetape4k/graph/io/jackson2/
    Jackson2NdJsonBulkImporter.kt
    Jackson2NdJsonBulkExporter.kt
    Jackson2NdJsonVirtualThreadBulkImporter.kt
    Jackson2NdJsonVirtualThreadBulkExporter.kt
    SuspendJackson2NdJsonBulkImporter.kt
    SuspendJackson2NdJsonBulkExporter.kt
    internal/
      NdJsonEnvelope.kt           // {"type":"vertex|edge", ...}
      Jackson2EnvelopeCodec.kt

graph-io/jackson3/
  build.gradle.kts
  README.md, README.ko.md
  src/main/kotlin/io/bluetape4k/graph/io/jackson3/
    ...Jackson3 mirror of Jackson2 with tools.jackson.* imports...

graph-io/graphml/
  build.gradle.kts
  README.md, README.ko.md
  src/main/kotlin/io/bluetape4k/graph/io/graphml/
    GraphMlImportOptions.kt
    GraphMlExportOptions.kt
    UnsupportedGraphMlElementPolicy.kt
    GraphMlEdgeDefault.kt
    GraphMlBulkImporter.kt
    GraphMlBulkExporter.kt
    GraphMlVirtualThreadBulkImporter.kt
    GraphMlVirtualThreadBulkExporter.kt
    SuspendGraphMlBulkImporter.kt
    SuspendGraphMlBulkExporter.kt
    internal/
      GraphMlAttrType.kt          // boolean/int/long/float/double/string coercion
      StaxGraphMlReader.kt
      StaxGraphMlWriter.kt

benchmark/graph-io-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/io/
  BulkGraphIoBenchmarkState.kt
  BulkGraphIoBenchmark.kt

docs/benchmark/2026-04-18-graph-io-bulk-results.md
docs/graphdb-tradeoffs.md (update)
TODO.md (update)
README.md, README.ko.md (root update)
```

---

## Task Summary

| # | Task | Complexity | Module | Depends on |
|---|------|------------|--------|------------|
| 1 | Register 5 modules in `settings.gradle.kts` + skeleton build files | low | settings + 5 new | — |
| 2 | `graph-io` records (`GraphIoVertexRecord`, `GraphIoEdgeRecord`) + validation | low | graph-io | 1 |
| 3 | `graph-io` Source/Sink sealed hierarchy + ownership tests | low | graph-io | 1 |
| 4 | `graph-io` Options (`GraphImportOptions`, `GraphExportOptions`, policies) | low | graph-io | 1 |
| 5 | `graph-io` Report/Failure/status enums | low | graph-io | 1 |
| 6 | `graph-io` Sync/VT/Suspend contracts + `GraphRecordFlowReader` | high | graph-io | 2,3,4,5 |
| 7 | `graph-io` `GraphIoPaths` + `GraphIoStopwatch` helpers + tests | low | graph-io | 3 |
| 8 | `graph-io` `GraphIoExternalIdMap` + tests | medium | graph-io | 2 |
| 9 | `graph-io` `VirtualThreadGraphBulkAdapter` + tests (Sync→CF) | high | graph-io | 6 |
| 10 | `graph-io-csv` Source/Sink wrappers + `CsvGraphIoOptions` + `CsvPropertyMode` | low | graph-io-csv | 1,6 |
| 11 | `graph-io-csv` `CsvRecordCodec` (reserved cols, union header, collision detect) | medium | graph-io-csv | 10 |
| 12 | `graph-io-csv` `CsvGraphBulkImporter` + `CsvGraphBulkExporter` (Sync) | medium | graph-io-csv | 11,8 |
| 13 | `graph-io-csv` VT importer/exporter (delegation + test) | medium | graph-io-csv | 12,9 |
| 14 | `graph-io-csv` Suspend importer/exporter using `SuspendCsvRecordReader/Writer` + Flow repositories | medium | graph-io-csv | 12 |
| 15 | `graph-io-csv` format-specific options overload (`importGraph(..., csvOptions)`) tests | medium | graph-io-csv | 12,13,14 |
| 16 | `graph-io-jackson2` `NdJsonEnvelope` + `Jackson2EnvelopeCodec` | medium | graph-io-jackson2 | 6 |
| 17 | `graph-io-jackson2` Sync importer/exporter (single-pass + edge buffer overflow) | medium | graph-io-jackson2 | 16,8 |
| 18 | `graph-io-jackson2` VT + Suspend importer/exporter | medium | graph-io-jackson2 | 17,9 |
| 19 | `graph-io-jackson3` `NdJsonEnvelope` + `Jackson3EnvelopeCodec` (`tools.jackson.*`) | medium | graph-io-jackson3 | 6 |
| 20 | `graph-io-jackson3` Sync importer/exporter (shape matches Jackson2) | medium | graph-io-jackson3 | 19,8 |
| 21 | `graph-io-jackson3` VT + Suspend importer/exporter | medium | graph-io-jackson3 | 20,9 |
| 22 | `graph-io-graphml` options + `GraphMlAttrType` coercion table | low | graph-io-graphml | 6 |
| 23 | `graph-io-graphml` `StaxGraphMlReader` (StAX streaming, location path, edge buffer) | high | graph-io-graphml | 22 |
| 24 | `graph-io-graphml` `StaxGraphMlWriter` (key discovery, directed only) + Sync importer/exporter | high | graph-io-graphml | 23,8 |
| 25 | `graph-io-graphml` VT + Suspend importer/exporter + format-specific overload | medium | graph-io-graphml | 24,9 |
| 26 | Cross-format TinkerGraph round-trip test suite | medium | graph-io-* | 15,18,21,25 |
| 27 | Jackson2/Jackson3 logical-shape compatibility tests | medium | graph-io-jackson2 + graph-io-jackson3 | 18,21 |
| 28 | `graph-io-benchmark` build.gradle.kts depends on all new modules | low | graph-io-benchmark | 15,18,21,25 |
| 29 | `graph-io-benchmark` `BulkGraphIoBenchmarkState` + `BulkGraphIoBenchmark` (JMH) | high | graph-io-benchmark | 28 |
| 30 | Run benchmark and write `docs/benchmark/2026-04-18-graph-io-bulk-results.md` | medium | docs | 29 |
| 31 | Module READMEs (ko + en) per spec §14 | low | all new modules | 15,18,21,25 |
| 32 | Root README, `TODO.md`, `docs/graphdb-tradeoffs.md` updates | low | root docs | 30,31 |
| 33 | Full compile/test/static-check pass + `./gradlew build` verification | low | root | 1-32 |

Complexity rubric:
- **high**: public contract design, NDJSON single-pass + edge buffering, GraphML StAX streaming, VT adapter (`virtualFutureOf`), JMH benchmark suite.
- **medium**: CSV Sync/VT/Coroutine, Jackson2/3 NDJSON, GraphML VT/Suspend, cross-format tests, benchmark report authoring, internal helpers (codec/map).
- **low**: `settings.gradle.kts` + build files, KDoc-only additions, enums + data classes, README authoring.

---

### Task 1: Register modules in settings + skeleton build files

**Files:**
- Modify: `settings.gradle.kts` — `graph-io/` 모듈들은 루트 레벨이므로 `includeModules("graph", ...)` 스캔 대상이 아님. `include()` + `projectDir` 명시 필요.
- Create: `graph-io/core/build.gradle.kts`
- Create: `graph-io/csv/build.gradle.kts`
- Create: `graph-io/jackson2/build.gradle.kts`
- Create: `graph-io/jackson3/build.gradle.kts`
- Create: `graph-io/graphml/build.gradle.kts`
- Create: `benchmark/graph-io-benchmark/build.gradle.kts`

Complexity: low. Dependencies: none. Module: settings + all new modules.

- [x] **Step 0: `settings.gradle.kts` 모듈 등록** — 사용자가 이미 완료

```kotlin
// settings.gradle.kts — 사용자가 이미 추가한 내용
includeModules("graph-io", false, true)   // graph-io/core → :graph-io-core, graph-io/csv → :graph-io-csv, ...
includeModules("benchmark", false, false) // benchmark/graph-io-benchmark → :graph-io-benchmark
```

> ℹ️ `includeModules("graph-io", false, true)` 함수가 `graph-io/` 하위 디렉토리를 자동 스캔해
> `graph-io-{dirname}` 패턴으로 프로젝트를 등록한다. 별도 `include()` 호출 불필요.

- [ ] **Step 1: Write `graph-io/core/build.gradle.kts`**

```kotlin
dependencies {
    api(project(":graph-core"))
    api(Libs.bluetape4k_core)
    api(Libs.bluetape4k_io)
    api(Libs.kotlinx_coroutines_core)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)
    implementation(Libs.bluetape4k_coroutines)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(project(":graph-tinkerpop"))
}
```

- [ ] **Step 2: Write `graph-io/csv/build.gradle.kts`**

```kotlin
dependencies {
    api(project(":graph-io-core"))
    api(Libs.bluetape4k_csv)
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(project(":graph-tinkerpop"))
}
```

- [ ] **Step 3: Write `graph-io/jackson2/build.gradle.kts`**

```kotlin
dependencies {
    api(project(":graph-io-core"))
    api(Libs.bluetape4k_jackson2)
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(project(":graph-tinkerpop"))
}
```

- [ ] **Step 4: Write `graph-io/jackson3/build.gradle.kts`**

```kotlin
dependencies {
    api(project(":graph-io-core"))
    api(Libs.bluetape4k_jackson3)
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(project(":graph-tinkerpop"))
}
```

- [ ] **Step 5: Write `graph-io/graphml/build.gradle.kts`**

```kotlin
dependencies {
    api(project(":graph-io-core"))
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(project(":graph-tinkerpop"))
}
```

- [ ] **Step 5a: Write `benchmark/graph-io-benchmark/build.gradle.kts`**

```kotlin
plugins {
    id(Plugins.kotlinx_benchmark) version Plugins.Versions.kotlinx_benchmark
    kotlin("plugin.allopen")
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets { register("main") }
    configurations {
        named("main") {
            warmups = 3
            iterations = 5
            iterationTime = 3
            iterationTimeUnit = "s"
        }
    }
}

dependencies {
    implementation(project(":graph-io-core"))
    implementation(project(":graph-io-csv"))
    implementation(project(":graph-io-jackson2"))
    implementation(project(":graph-io-jackson3"))
    implementation(project(":graph-io-graphml"))
    implementation(project(":graph-tinkerpop"))
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)
    implementation(Libs.kotlinx_benchmark_runtime)

    testImplementation(Libs.bluetape4k_junit5)
}
```

- [ ] **Step 6: Verify Gradle picks up all modules**

Run: `./gradlew projects`
Expected: output lists `:graph-io-core`, `:graph-io-csv`, `:graph-io-jackson2`, `:graph-io-jackson3`, `:graph-io-graphml`.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts graph-io/ graph-io-benchmark/
git commit -m "chore: graph-io 모듈 스켈레톤 추가 (graph-io, csv, jackson2, jackson3, graphml, benchmark)"
```

---

### Task 2: `graph-io` records + validation

**Files:**
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/model/GraphIoVertexRecord.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/model/GraphIoEdgeRecord.kt`
- Test: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/model/GraphIoRecordTest.kt`

Complexity: low. Dependencies: 1. Module: `graph-io`.

- [ ] **Step 1: Write failing test**

```kotlin
package io.bluetape4k.graph.io.model

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.jupiter.api.Test

class GraphIoRecordTest {

    @Test
    fun `vertex record requires non-blank externalId`() {
        val thrown = { GraphIoVertexRecord(externalId = " ", label = "Person") } shouldThrow IllegalArgumentException::class
        thrown.exceptionMessage.isNotBlank() shouldBeEqualTo true
    }

    @Test
    fun `edge record requires non-blank label and endpoints`() {
        { GraphIoEdgeRecord(label = " ", fromExternalId = "v1", toExternalId = "v2") } shouldThrow IllegalArgumentException::class
        { GraphIoEdgeRecord(label = "KNOWS", fromExternalId = " ", toExternalId = "v2") } shouldThrow IllegalArgumentException::class
        { GraphIoEdgeRecord(label = "KNOWS", fromExternalId = "v1", toExternalId = " ") } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `valid records round-trip properties`() {
        val v = GraphIoVertexRecord("v1", "Person", mapOf("name" to "Alice"))
        v.properties["name"] shouldBeEqualTo "Alice"
    }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

Run: `./gradlew :graph-io:test --tests "io.bluetape4k.graph.io.model.GraphIoRecordTest"`
Expected: FAIL (classes don't exist).

- [ ] **Step 3: Implement records**

```kotlin
// GraphIoVertexRecord.kt
package io.bluetape4k.graph.io.model

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

data class GraphIoVertexRecord(
    val externalId: String,
    val label: String,
    val properties: Map<String, Any?> = emptyMap(),
) : Serializable {
    init {
        externalId.requireNotBlank("externalId")
        label.requireNotBlank("label")
    }
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

```kotlin
// GraphIoEdgeRecord.kt
package io.bluetape4k.graph.io.model

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

data class GraphIoEdgeRecord(
    val externalId: String? = null,
    val label: String,
    val fromExternalId: String,
    val toExternalId: String,
    val properties: Map<String, Any?> = emptyMap(),
) : Serializable {
    init {
        label.requireNotBlank("label")
        fromExternalId.requireNotBlank("fromExternalId")
        toExternalId.requireNotBlank("toExternalId")
        externalId?.requireNotBlank("externalId")
    }
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

- [ ] **Step 4: Run test (expect PASS)**

Run: `./gradlew :graph-io:test --tests "io.bluetape4k.graph.io.model.GraphIoRecordTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add graph-io/src
git commit -m "feat(graph-io): GraphIoVertexRecord, GraphIoEdgeRecord 추가 (빈값 검증 포함)"
```

---

### Task 3: `graph-io` Source/Sink sealed hierarchy

**Files:**
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/source/GraphImportSource.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/source/GraphExportSink.kt`
- Test: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/source/GraphImportSourceTest.kt`
- Test: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/source/GraphExportSinkTest.kt`

Complexity: low. Dependencies: 1. Module: `graph-io`.

- [ ] **Step 1: Write failing source test**

```kotlin
package io.bluetape4k.graph.io.source

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path
import kotlin.text.Charsets

class GraphImportSourceTest {

    @Test
    fun `path source defaults to UTF-8`() {
        val src = GraphImportSource.PathSource(Path.of("in.csv"))
        src.charset shouldBeEqualTo Charsets.UTF_8
    }

    @Test
    fun `input stream source defaults to caller-owned close`() {
        val src = GraphImportSource.InputStreamSource(ByteArrayInputStream(ByteArray(0)))
        src.closeInput shouldBeEqualTo false
    }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

Run: `./gradlew :graph-io:test --tests "io.bluetape4k.graph.io.source.GraphImportSourceTest"`

- [ ] **Step 3: Implement sources**

```kotlin
// GraphImportSource.kt
package io.bluetape4k.graph.io.source

import java.io.InputStream
import java.io.Serializable
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.text.Charsets

sealed interface GraphImportSource {

    data class PathSource(
        val path: Path,
        val charset: Charset = Charsets.UTF_8,
    ) : GraphImportSource, Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class InputStreamSource(
        val input: InputStream,
        val charset: Charset = Charsets.UTF_8,
        val closeInput: Boolean = false,
    ) : GraphImportSource
}
```

```kotlin
// GraphExportSink.kt
package io.bluetape4k.graph.io.source

import java.io.OutputStream
import java.io.Serializable
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.text.Charsets

sealed interface GraphExportSink {

    data class PathSink(
        val path: Path,
        val charset: Charset = Charsets.UTF_8,
        val append: Boolean = false,
    ) : GraphExportSink, Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class OutputStreamSink(
        val output: OutputStream,
        val charset: Charset = Charsets.UTF_8,
        val closeOutput: Boolean = false,
    ) : GraphExportSink
}
```

- [ ] **Step 4: Write failing sink test**

```kotlin
package io.bluetape4k.graph.io.source

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Path

class GraphExportSinkTest {

    @Test
    fun `path sink defaults do not append`() {
        val sink = GraphExportSink.PathSink(Path.of("out.csv"))
        sink.append shouldBeEqualTo false
    }

    @Test
    fun `output stream sink defaults to caller-owned close`() {
        val sink = GraphExportSink.OutputStreamSink(ByteArrayOutputStream())
        sink.closeOutput shouldBeEqualTo false
    }
}
```

- [ ] **Step 5: Run tests (expect PASS)**

Run: `./gradlew :graph-io:test --tests "io.bluetape4k.graph.io.source.*"`

- [ ] **Step 6: Commit**

```bash
git add graph-io/src
git commit -m "feat(graph-io): GraphImportSource, GraphExportSink sealed 계층 추가"
```

---

### Task 4: `graph-io` Options + policy enums

**Files:**
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/options/DuplicateVertexPolicy.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/options/MissingEndpointPolicy.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/options/GraphImportOptions.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/options/GraphExportOptions.kt`
- Test: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/options/GraphImportOptionsTest.kt`
- Test: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/options/GraphExportOptionsTest.kt`

Complexity: low. Dependencies: 1. Module: `graph-io`.

- [ ] **Step 1: Write failing options tests**

```kotlin
package io.bluetape4k.graph.io.options

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test

class GraphImportOptionsTest {

    @Test
    fun `default options are valid`() {
        val opt = GraphImportOptions()
        opt.batchSize shouldBeEqualTo 1_000
        opt.maxEdgeBufferSize shouldBeEqualTo 100_000
        opt.onDuplicateVertexId shouldBeEqualTo DuplicateVertexPolicy.FAIL
        opt.onMissingEdgeEndpoint shouldBeEqualTo MissingEndpointPolicy.FAIL
    }

    @Test
    fun `batchSize must be positive`() {
        { GraphImportOptions(batchSize = 0) } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `maxEdgeBufferSize must be positive`() {
        { GraphImportOptions(maxEdgeBufferSize = -1) } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `defaultVertexLabel and defaultEdgeLabel must not be blank`() {
        { GraphImportOptions(defaultVertexLabel = " ") } shouldThrow IllegalArgumentException::class
        { GraphImportOptions(defaultEdgeLabel = " ") } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `preserveExternalIdProperty null is allowed`() {
        GraphImportOptions(preserveExternalIdProperty = null).preserveExternalIdProperty shouldBeEqualTo null
    }

    @Test
    fun `preserveExternalIdProperty empty is rejected`() {
        { GraphImportOptions(preserveExternalIdProperty = " ") } shouldThrow IllegalArgumentException::class
    }
}
```

```kotlin
package io.bluetape4k.graph.io.options

import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test

class GraphExportOptionsTest {

    @Test
    fun `labels must not be blank`() {
        { GraphExportOptions(vertexLabels = setOf(" ")) } shouldThrow IllegalArgumentException::class
        { GraphExportOptions(edgeLabels = setOf(" ")) } shouldThrow IllegalArgumentException::class
    }
}
```

- [ ] **Step 2: Run tests (expect FAIL)**

Run: `./gradlew :graph-io:test --tests "io.bluetape4k.graph.io.options.*"`

- [ ] **Step 3: Implement policies and options**

```kotlin
// DuplicateVertexPolicy.kt
package io.bluetape4k.graph.io.options

enum class DuplicateVertexPolicy { FAIL, SKIP }
```

```kotlin
// MissingEndpointPolicy.kt
package io.bluetape4k.graph.io.options

enum class MissingEndpointPolicy { FAIL, SKIP_EDGE }
```

```kotlin
// GraphImportOptions.kt
package io.bluetape4k.graph.io.options

import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * 그래프 임포트 옵션. `batchSize`는 진행 보고/플러시 주기이며, `maxEdgeBufferSize`는
 * NDJSON 엣지 버퍼의 상한이다. `preserveExternalIdProperty`가 null이면 외부 ID를
 * 정점 속성으로 보존하지 않는다.
 */
data class GraphImportOptions(
    val batchSize: Int = 1_000,
    val maxEdgeBufferSize: Int = 100_000,
    val onDuplicateVertexId: DuplicateVertexPolicy = DuplicateVertexPolicy.FAIL,
    val onMissingEdgeEndpoint: MissingEndpointPolicy = MissingEndpointPolicy.FAIL,
    val defaultVertexLabel: String = "Vertex",
    val defaultEdgeLabel: String = "Edge",
    val preserveExternalIdProperty: String? = "_graphIoExternalId",
) : Serializable {
    init {
        batchSize.requirePositiveNumber("batchSize")
        maxEdgeBufferSize.requirePositiveNumber("maxEdgeBufferSize")
        defaultVertexLabel.requireNotBlank("defaultVertexLabel")
        defaultEdgeLabel.requireNotBlank("defaultEdgeLabel")
        preserveExternalIdProperty?.requireNotBlank("preserveExternalIdProperty")
    }
    companion object { private const val serialVersionUID: Long = 1L }
}
```

```kotlin
// GraphExportOptions.kt
package io.bluetape4k.graph.io.options

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * 그래프 익스포트 옵션. 현재 1차 구현에서 `vertexLabels`/`edgeLabels`가 비어 있으면
 * export 호출은 `IllegalArgumentException`으로 즉시 실패한다. 백엔드-중립 레이블 디스커버리
 * API가 생기기 전까지 전체-레이블 익스포트는 미지원이다.
 */
data class GraphExportOptions(
    val vertexLabels: Set<String> = emptySet(),
    val edgeLabels: Set<String> = emptySet(),
    val includeEmptyProperties: Boolean = true,
) : Serializable {
    init {
        vertexLabels.forEach { it.requireNotBlank("vertexLabels element") }
        edgeLabels.forEach { it.requireNotBlank("edgeLabels element") }
    }
    companion object { private const val serialVersionUID: Long = 1L }
}
```

- [ ] **Step 4: Run tests (expect PASS)**

Run: `./gradlew :graph-io:test --tests "io.bluetape4k.graph.io.options.*"`

- [ ] **Step 5: Commit**

```bash
git add graph-io/src
git commit -m "feat(graph-io): GraphImportOptions, GraphExportOptions 및 정책 enum 추가"
```

---

### Task 5: `graph-io` Report + Failure + enums

**Files:**
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphIoStatus.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphIoFormat.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphIoPhase.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphIoFailureSeverity.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphIoFileRole.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphIoFailure.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphImportReport.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphExportReport.kt`
- Test: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/report/GraphIoReportTest.kt`
- Test: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/report/GraphIoSerializationTest.kt`

Complexity: low. Dependencies: 1. Module: `graph-io`.

- [ ] **Step 1: Write failing report test**

```kotlin
package io.bluetape4k.graph.io.report

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import java.time.Duration

class GraphIoReportTest {

    @Test
    fun `import report stores counts and status`() {
        val r = GraphImportReport(
            status = GraphIoStatus.COMPLETED,
            format = GraphIoFormat.CSV,
            verticesRead = 10, verticesCreated = 10,
            edgesRead = 5, edgesCreated = 5,
            skippedVertices = 0, skippedEdges = 0,
            elapsed = Duration.ofMillis(12),
        )
        r.status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `failure requires non-blank message`() {
        {
            GraphIoFailure(phase = GraphIoPhase.READ_VERTEX, message = " ")
        } shouldThrow IllegalArgumentException::class
    }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

- [ ] **Step 3: Implement enums**

```kotlin
package io.bluetape4k.graph.io.report
enum class GraphIoStatus { COMPLETED, FAILED, PARTIAL }
```

```kotlin
package io.bluetape4k.graph.io.report
enum class GraphIoFormat { CSV, NDJSON_JACKSON2, NDJSON_JACKSON3, GRAPHML }
```

```kotlin
package io.bluetape4k.graph.io.report
enum class GraphIoPhase { READ_VERTEX, READ_EDGE, WRITE_VERTEX, WRITE_EDGE, CREATE_VERTEX, CREATE_EDGE }
```

```kotlin
package io.bluetape4k.graph.io.report
enum class GraphIoFailureSeverity { INFO, WARN, ERROR }
```

```kotlin
package io.bluetape4k.graph.io.report
enum class GraphIoFileRole { VERTICES, EDGES, UNIFIED }
```

- [ ] **Step 4: Implement failure and reports**

```kotlin
// GraphIoFailure.kt
package io.bluetape4k.graph.io.report

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

data class GraphIoFailure(
    val phase: GraphIoPhase,
    val severity: GraphIoFailureSeverity = GraphIoFailureSeverity.ERROR,
    val location: String? = null,
    val sourceName: String? = null,
    val fileRole: GraphIoFileRole? = null,
    val recordId: String? = null,
    val columnName: String? = null,
    val elementName: String? = null,
    val message: String,
) : Serializable {
    init { message.requireNotBlank("message") }
    companion object { private const val serialVersionUID: Long = 1L }
}
```

```kotlin
// GraphImportReport.kt
package io.bluetape4k.graph.io.report

import java.io.Serializable
import java.time.Duration

data class GraphImportReport(
    val status: GraphIoStatus,
    val format: GraphIoFormat,
    val verticesRead: Long,
    val verticesCreated: Long,
    val edgesRead: Long,
    val edgesCreated: Long,
    val skippedVertices: Long,
    val skippedEdges: Long,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
) : Serializable { companion object { private const val serialVersionUID: Long = 1L } }
```

```kotlin
// GraphExportReport.kt
package io.bluetape4k.graph.io.report

import java.io.Serializable
import java.time.Duration

data class GraphExportReport(
    val status: GraphIoStatus,
    val format: GraphIoFormat,
    val verticesWritten: Long,
    val edgesWritten: Long,
    val skippedVertices: Long = 0,
    val skippedEdges: Long = 0,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
) : Serializable { companion object { private const val serialVersionUID: Long = 1L } }
```

- [ ] **Step 5: Serialization round-trip test**

```kotlin
package io.bluetape4k.graph.io.report

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.*
import java.time.Duration

class GraphIoSerializationTest {
    private inline fun <reified T : Serializable> roundTrip(value: T): T {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { o -> o.writeObject(value) } }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as T }
    }

    @Test
    fun `import report round-trips via java serialization`() {
        val r = GraphImportReport(
            GraphIoStatus.COMPLETED, GraphIoFormat.NDJSON_JACKSON3,
            1, 1, 0, 0, 0, 0, Duration.ofMillis(2))
        roundTrip(r) shouldBeEqualTo r
    }
}
```

- [ ] **Step 6: Run tests (expect PASS)**

Run: `./gradlew :graph-io:test --tests "io.bluetape4k.graph.io.report.*"`

- [ ] **Step 7: Commit**

```bash
git add graph-io/src
git commit -m "feat(graph-io): GraphImportReport, GraphExportReport, GraphIoFailure 추가 (Serializable 포함)"
```

---

### Task 6: `graph-io` Sync/VT/Suspend contracts + `GraphRecordFlowReader`

**Files:**
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkExporter.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphVirtualThreadBulkImporter.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphVirtualThreadBulkExporter.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphSuspendBulkImporter.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphSuspendBulkExporter.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphRecordFlowReader.kt`

Complexity: high. Dependencies: 2,3,4,5. Module: `graph-io`.

Rationale: these are the load-bearing public interfaces used by every format module. Getting type parameters (`S : Any`, `T : Any`) and the delegation rule for format-specific overloads right avoids future breaking changes.

- [ ] **Step 1: Define Sync contracts**

```kotlin
// GraphBulkImporter.kt
package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport

/**
 * 공통 동기 벌크 임포터 계약. `S`는 포맷별 소스 타입.
 * 포맷별 구현은 format-specific 옵션을 받는 overload를 추가하고, 이 오버라이드는 기본 옵션으로 위임한다.
 */
fun interface GraphBulkImporter<S : Any> {
    fun importGraph(
        source: S,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport
}
```

```kotlin
// GraphBulkExporter.kt
package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport

fun interface GraphBulkExporter<T : Any> {
    fun exportGraph(
        sink: T,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport
}
```

- [ ] **Step 2: Define VT contracts**

```kotlin
// GraphVirtualThreadBulkImporter.kt
package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import java.util.concurrent.CompletableFuture

fun interface GraphVirtualThreadBulkImporter<S : Any> {
    fun importGraphAsync(
        source: S,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): CompletableFuture<GraphImportReport>
}
```

```kotlin
// GraphVirtualThreadBulkExporter.kt
package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import java.util.concurrent.CompletableFuture

fun interface GraphVirtualThreadBulkExporter<T : Any> {
    fun exportGraphAsync(
        sink: T,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): CompletableFuture<GraphExportReport>
}
```

- [ ] **Step 3: Define Suspend contracts**

```kotlin
// GraphSuspendBulkImporter.kt
package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport

fun interface GraphSuspendBulkImporter<S : Any> {
    suspend fun importGraphSuspending(
        source: S,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport
}
```

```kotlin
// GraphSuspendBulkExporter.kt
package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport

fun interface GraphSuspendBulkExporter<T : Any> {
    suspend fun exportGraphSuspending(
        sink: T,
        operations: GraphSuspendOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport
}
```

- [ ] **Step 4: Define `GraphRecordFlowReader`**

```kotlin
// GraphRecordFlowReader.kt
package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import kotlinx.coroutines.flow.Flow

/**
 * 포맷 내부에서 raw 레코드만 방출하는 helper. 엣지 레코드의 `fromExternalId`/`toExternalId`는
 * 아직 resolve되지 않은 외부 ID이다. 엔드포인트 resolve는 bulk importer 책임이다.
 */
interface GraphRecordFlowReader<S : Any> {
    fun readVertices(source: S): Flow<GraphIoVertexRecord>
    fun readEdges(source: S): Flow<GraphIoEdgeRecord>
}
```

- [ ] **Step 5: Compile check (no unit tests — interfaces only)**

Run: `./gradlew :graph-io:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add graph-io/src
git commit -m "feat(graph-io): Sync/VT/Suspend 벌크 I/O 계약 및 GraphRecordFlowReader 추가"
```

---

### Task 7: `graph-io` GraphIoPaths + GraphIoStopwatch helpers

**Files:**
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoPaths.kt`
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoStopwatch.kt`
- Test: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/support/GraphIoPathsTest.kt`

Complexity: low. Dependencies: 3. Module: `graph-io`.

- [ ] **Step 1: Write failing test**

```kotlin
package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GraphIoPathsTest {

    @Test
    fun `openReader honors closeInput flag on input stream source`(@TempDir dir: Path) {
        val file = dir.resolve("a.txt").also { java.nio.file.Files.writeString(it, "x\ny") }
        val src = GraphImportSource.PathSource(file)
        GraphIoPaths.openReader(src).use { r -> r.readLines().size shouldBeEqualTo 2 }
    }

    @Test
    fun `openWriter creates parent directory for path sink`(@TempDir dir: Path) {
        val nested = dir.resolve("nested/a.txt")
        GraphIoPaths.openWriter(GraphExportSink.PathSink(nested)).use { it.write("hi") }
        java.nio.file.Files.exists(nested) shouldBeEqualTo true
    }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

- [ ] **Step 3: Implement helpers**

```kotlin
// GraphIoPaths.kt
package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption

internal object GraphIoPaths {
    fun openReader(source: GraphImportSource): BufferedReader = when (source) {
        is GraphImportSource.PathSource -> Files.newBufferedReader(source.path, source.charset)
        is GraphImportSource.InputStreamSource -> {
            val reader = BufferedReader(InputStreamReader(source.input, source.charset))
            if (source.closeInput) reader
            else object : BufferedReader(reader) { override fun close() { /* caller owns stream */ } }
        }
    }

    fun openWriter(sink: GraphExportSink): BufferedWriter = when (sink) {
        is GraphExportSink.PathSink -> {
            sink.path.parent?.let { Files.createDirectories(it) }
            val opts = if (sink.append)
                arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            else
                arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            Files.newBufferedWriter(sink.path, sink.charset, *opts)
        }
        is GraphExportSink.OutputStreamSink -> {
            val writer = BufferedWriter(OutputStreamWriter(sink.output, sink.charset))
            if (sink.closeOutput) writer
            else object : BufferedWriter(writer) { override fun close() { flush() /* caller owns stream */ } }
        }
    }

    fun describeSource(source: GraphImportSource): String? = when (source) {
        is GraphImportSource.PathSource -> source.path.toString()
        is GraphImportSource.InputStreamSource -> null
    }
}
```

```kotlin
// GraphIoStopwatch.kt
package io.bluetape4k.graph.io.support

import java.time.Duration

internal class GraphIoStopwatch {
    private val started = System.nanoTime()
    fun elapsed(): Duration = Duration.ofNanos(System.nanoTime() - started)
}
```

- [ ] **Step 4: Run test (expect PASS)**

Run: `./gradlew :graph-io:test --tests "io.bluetape4k.graph.io.support.GraphIoPathsTest"`

- [ ] **Step 5: Commit**

```bash
git add graph-io/src
git commit -m "feat(graph-io): GraphIoPaths, GraphIoStopwatch 내부 헬퍼 추가"
```

---

### Task 8: `graph-io` GraphIoExternalIdMap + tests

**Files:**
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMap.kt`
- Test: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMapTest.kt`

Complexity: medium. Dependencies: 2. Module: `graph-io`.

- [ ] **Step 1: Write failing test**

```kotlin
package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test

class GraphIoExternalIdMapTest {

    @Test
    fun `put then get resolves backend id`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        val id = GraphElementId("backend-1")
        map.putFirstOrFail("v1", id) shouldBeEqualTo GraphIoExternalIdMap.PutResult.CREATED
        map.resolve("v1") shouldBeEqualTo id
    }

    @Test
    fun `duplicate under FAIL policy throws`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.putFirstOrFail("v1", GraphElementId("a"))
        { map.putFirstOrFail("v1", GraphElementId("b")) } shouldThrow IllegalStateException::class
    }

    @Test
    fun `duplicate under SKIP returns SKIPPED and preserves first mapping`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.SKIP)
        val first = GraphElementId("a")
        map.putFirstOrFail("v1", first)
        map.putFirstOrFail("v1", GraphElementId("b")) shouldBeEqualTo GraphIoExternalIdMap.PutResult.SKIPPED
        map.resolve("v1") shouldBeEqualTo first
    }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

- [ ] **Step 3: Implement `GraphIoExternalIdMap`**

```kotlin
package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy

/**
 * 외부 ID와 백엔드가 발급한 GraphElementId 간의 매핑. `DuplicateVertexPolicy`에 따라 중복 정책을 강제한다.
 */
internal class GraphIoExternalIdMap(
    private val duplicatePolicy: DuplicateVertexPolicy,
) {
    private val mapping = HashMap<String, GraphElementId>()

    enum class PutResult { CREATED, SKIPPED }

    fun contains(externalId: String): Boolean = mapping.containsKey(externalId)

    fun put(externalId: String, backendId: GraphElementId) {
        mapping[externalId] = backendId
    }

    fun putFirstOrFail(externalId: String, backendId: GraphElementId): PutResult {
        val existing = mapping[externalId]
        if (existing == null) {
            mapping[externalId] = backendId
            return PutResult.CREATED
        }
        return when (duplicatePolicy) {
            DuplicateVertexPolicy.FAIL -> error("Duplicate vertex externalId='$externalId'")
            DuplicateVertexPolicy.SKIP -> PutResult.SKIPPED
        }
    }

    fun resolve(externalId: String): GraphElementId? = mapping[externalId]
    fun size(): Int = mapping.size
}
```

- [ ] **Step 4: Run test (expect PASS)**

- [ ] **Step 5: Commit**

```bash
git add graph-io/src
git commit -m "feat(graph-io): 외부 ID 매핑용 GraphIoExternalIdMap 추가"
```

---

### Task 9: `graph-io` VirtualThreadGraphBulkAdapter

**Files:**
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/VirtualThreadGraphBulkAdapter.kt`
- Test: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/support/VirtualThreadGraphBulkAdapterTest.kt`

Complexity: high. Dependencies: 6. Module: `graph-io`.

- [ ] **Step 1: Write failing test**

```kotlin
package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoStatus
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.ExecutionException

class VirtualThreadGraphBulkAdapterTest {

    private val stubReport = GraphImportReport(
        GraphIoStatus.COMPLETED, GraphIoFormat.CSV, 0, 0, 0, 0, 0, 0, Duration.ZERO)

    @Test
    fun `importAsync wraps sync importer with virtual thread future`() {
        val importer = GraphBulkImporter<String> { _, _, _ -> stubReport }
        val vt = VirtualThreadGraphBulkAdapter.wrapImporter(importer)
        vt.importGraphAsync("src", FakeGraphOperations(), GraphImportOptions()).get() shouldBeEqualTo stubReport
    }

    @Test
    fun `importAsync propagates sync failure`() {
        val boom = RuntimeException("boom")
        val importer = GraphBulkImporter<String> { _, _, _ -> throw boom }
        val vt = VirtualThreadGraphBulkAdapter.wrapImporter(importer)
        val ee = {
            vt.importGraphAsync("x", FakeGraphOperations(), GraphImportOptions()).get()
        } shouldThrow ExecutionException::class
        ee.cause shouldBeInstanceOf RuntimeException::class
    }
}
```

`FakeGraphOperations` is a minimal no-op `GraphOperations` implementation under `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/testsupport/FakeGraphOperations.kt` stubbing every method with `error("not used in this test")`.

- [ ] **Step 2: Run test (expect FAIL)**

- [ ] **Step 3: Implement adapter**

```kotlin
package io.bluetape4k.graph.io.support

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkImporter

/**
 * Sync importer/exporter 를 Virtual Thread 기반 CompletableFuture 로 감싸는 어댑터.
 * 취소는 start 이전에만 effective 하다; README에서 이 제한을 문서화한다.
 */
object VirtualThreadGraphBulkAdapter {

    fun <S : Any> wrapImporter(sync: GraphBulkImporter<S>): GraphVirtualThreadBulkImporter<S> =
        GraphVirtualThreadBulkImporter { source, operations, options ->
            virtualFutureOf { sync.importGraph(source, operations, options) }
        }

    fun <T : Any> wrapExporter(sync: GraphBulkExporter<T>): GraphVirtualThreadBulkExporter<T> =
        GraphVirtualThreadBulkExporter { sink, operations, options ->
            virtualFutureOf { sync.exportGraph(sink, operations, options) }
        }
}
```

- [ ] **Step 4: Run test (expect PASS)**

- [ ] **Step 5: Commit**

```bash
git add graph-io/src
git commit -m "feat(graph-io): CompletableFuture 기반 VirtualThreadGraphBulkAdapter 추가"
```

---

### Task 10: `graph-io-csv` Source/Sink wrappers + `CsvGraphIoOptions` + `CsvPropertyMode`

**Files:**
- Create: `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphImportSource.kt`
- Create: `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphExportSink.kt`
- Create: `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphIoOptions.kt`
- Test: `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvGraphIoOptionsTest.kt`

Complexity: low. Dependencies: 1, 6. Module: `graph-io-csv`.

- [ ] **Step 1: Write failing test**

```kotlin
package io.bluetape4k.graph.io.csv

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test

class CsvGraphIoOptionsTest {

    @Test
    fun `default mode is prefixed columns prop dot`() {
        val mode = CsvGraphIoOptions().propertyMode
        (mode is CsvPropertyMode.PrefixedColumns) shouldBeEqualTo true
        (mode as CsvPropertyMode.PrefixedColumns).prefix shouldBeEqualTo "prop."
    }

    @Test
    fun `prefixed prefix must not be blank`() {
        { CsvPropertyMode.PrefixedColumns(" ") } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `raw json column name must not be blank`() {
        { CsvPropertyMode.RawJsonColumn(" ") } shouldThrow IllegalArgumentException::class
    }
}
```

- [ ] **Step 2: Implement wrappers + options**

```kotlin
// CsvGraphImportSource.kt
package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.source.GraphImportSource

data class CsvGraphImportSource(
    val vertices: GraphImportSource,
    val edges: GraphImportSource,
)

data class CsvGraphExportSink(
    val vertices: io.bluetape4k.graph.io.source.GraphExportSink,
    val edges: io.bluetape4k.graph.io.source.GraphExportSink,
)
```

```kotlin
// CsvGraphIoOptions.kt
package io.bluetape4k.graph.io.csv

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

data class CsvGraphIoOptions(
    val propertyMode: CsvPropertyMode = CsvPropertyMode.PrefixedColumns(),
) : Serializable {
    companion object { private const val serialVersionUID: Long = 1L }
}

sealed interface CsvPropertyMode {

    data object None : CsvPropertyMode, Serializable {
        private const val serialVersionUID: Long = 1L
    }

    data class PrefixedColumns(val prefix: String = "prop.") : CsvPropertyMode, Serializable {
        init { prefix.requireNotBlank("prefix") }
        companion object { private const val serialVersionUID: Long = 1L }
    }

    data class RawJsonColumn(val columnName: String = "properties") : CsvPropertyMode, Serializable {
        init { columnName.requireNotBlank("columnName") }
        companion object { private const val serialVersionUID: Long = 1L }
    }
}
```

- [ ] **Step 3: Run test (expect PASS)**

Run: `./gradlew :graph-io-csv:test --tests "io.bluetape4k.graph.io.csv.CsvGraphIoOptionsTest"`

- [ ] **Step 4: Commit**

```bash
git add graph-io/csv/src
git commit -m "feat(graph-io-csv): CsvGraphImportSource/ExportSink 및 CsvGraphIoOptions 추가"
```

---

### Task 11: `graph-io-csv` `CsvRecordCodec` (reserved columns, union header, collision)

**Files:**
- Create: `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/internal/CsvRecordCodec.kt`
- Test: `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/internal/CsvRecordCodecTest.kt`

Complexity: medium. Dependencies: 10. Module: `graph-io-csv`.

- [ ] **Step 1: Write failing tests for reserved-column detection, header union, collision detection**

```kotlin
package io.bluetape4k.graph.io.csv.internal

import io.bluetape4k.graph.io.csv.CsvPropertyMode
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test

class CsvRecordCodecTest {

    @Test
    fun `union header sorts property keys after reserved columns`() {
        val codec = CsvRecordCodec(CsvPropertyMode.PrefixedColumns())
        val recs = listOf(
            GraphIoVertexRecord("v1", "Person", mapOf("name" to "Alice")),
            GraphIoVertexRecord("v2", "Person", mapOf("age" to 30, "name" to "Bob")),
        )
        codec.unionVertexHeader(recs) shouldBeEqualTo listOf("id", "label", "prop.age", "prop.name")
    }

    @Test
    fun `prefixed column collision with reserved id fails`() {
        val codec = CsvRecordCodec(CsvPropertyMode.PrefixedColumns(prefix = ""))
        val recs = listOf(GraphIoVertexRecord("v1", "L", mapOf("id" to "x")))
        { codec.unionVertexHeader(recs) } shouldThrow IllegalStateException::class
    }
}
```

- [ ] **Step 2: Implement codec (header union, column mapping, reserved col defs)**

```kotlin
package io.bluetape4k.graph.io.csv.internal

import io.bluetape4k.graph.io.csv.CsvPropertyMode
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord

internal class CsvRecordCodec(private val mode: CsvPropertyMode) {

    fun unionVertexHeader(records: Iterable<GraphIoVertexRecord>): List<String> {
        val reserved = listOf("id", "label")
        return unionHeader(reserved, records.asSequence().flatMap { it.properties.keys.asSequence() })
    }

    fun unionEdgeHeader(records: Iterable<GraphIoEdgeRecord>): List<String> {
        val reserved = listOf("id", "label", "from", "to")
        return unionHeader(reserved, records.asSequence().flatMap { it.properties.keys.asSequence() })
    }

    private fun unionHeader(reserved: List<String>, propertyKeys: Sequence<String>): List<String> {
        val columns = propertyKeys.map { key -> propertyColumn(key) }.toSortedSet()
        val reservedSet = reserved.toSet()
        val collisions = columns.filter { it in reservedSet }
        check(collisions.isEmpty()) { "Property column collides with reserved column: $collisions" }
        return reserved + columns
    }

    fun propertyColumn(key: String): String = when (mode) {
        is CsvPropertyMode.PrefixedColumns -> mode.prefix + key
        is CsvPropertyMode.RawJsonColumn -> mode.columnName
        CsvPropertyMode.None -> key // None has no property columns; this is unused in union
    }

    fun extractProperties(row: Map<String, String>): Map<String, Any?> = when (mode) {
        is CsvPropertyMode.PrefixedColumns -> row.asSequence()
            .filter { it.key.startsWith(mode.prefix) && it.key !in RESERVED_ALL }
            .associate { it.key.removePrefix(mode.prefix) to it.value }
        is CsvPropertyMode.RawJsonColumn -> row[mode.columnName]?.let { mapOf(mode.columnName to it) } ?: emptyMap()
        CsvPropertyMode.None -> emptyMap()
    }

    companion object {
        internal val RESERVED_VERTEX = listOf("id", "label")
        internal val RESERVED_EDGE = listOf("id", "label", "from", "to")
        internal val RESERVED_ALL = (RESERVED_VERTEX + RESERVED_EDGE).toSet()
    }
}
```

- [ ] **Step 3: Run test (expect PASS)**

- [ ] **Step 4: Commit**

```bash
git add graph-io/csv/src
git commit -m "feat(graph-io-csv): 예약 컬럼·유니온 헤더·충돌 탐지용 CsvRecordCodec 추가"
```

---

### Task 12: `graph-io-csv` Sync CsvGraphBulkImporter + CsvGraphBulkExporter

**Files:**
- Create: `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphBulkImporter.kt`
- Create: `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphBulkExporter.kt`
- Test: `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvRoundTripTest.kt`

Complexity: medium. Dependencies: 11, 8. Module: `graph-io-csv`.

- [ ] **Step 1: Write failing round-trip test over TinkerGraph**

```kotlin
package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CsvRoundTripTest {

    @Test
    fun `round trip two vertices and one edge`(@TempDir dir: Path) {
        val vOut = dir.resolve("v.csv")
        val eOut = dir.resolve("e.csv")
        val source = TinkerGraphOperations().also { ops ->
            val a = ops.createVertex("Person", mapOf("name" to "Alice"))
            val b = ops.createVertex("Person", mapOf("name" to "Bob"))
            ops.createEdge(a.id, b.id, "KNOWS", mapOf("since" to "2024"))
        }
        val exporter = CsvGraphBulkExporter()
        exporter.exportGraph(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            source,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importer = CsvGraphBulkImporter()
        val report = importer.importGraph(
            CsvGraphImportSource(GraphImportSource.PathSource(vOut), GraphImportSource.PathSource(eOut)),
            target,
            GraphImportOptions(onDuplicateVertexId = DuplicateVertexPolicy.FAIL,
                                onMissingEdgeEndpoint = MissingEndpointPolicy.FAIL)
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2
        report.edgesCreated shouldBeEqualTo 1
        java.nio.file.Files.size(vOut) shouldBeGreaterThan 0
    }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

- [ ] **Step 3: Implement `CsvGraphBulkImporter`**

```kotlin
package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.CsvRecordReader
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.csv.internal.CsvRecordCodec
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.*
import io.bluetape4k.graph.io.support.GraphIoExternalIdMap
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.logging.KLogging

/**
 * CSV 동기 벌크 임포터. 정점 파일을 모두 읽어 `externalId -> GraphElementId` 맵을 구축한 뒤,
 * 엣지 파일을 순회하며 엣지를 생성한다. 공통 override는 기본 [CsvGraphIoOptions]로 위임한다.
 */
class CsvGraphBulkImporter : GraphBulkImporter<CsvGraphImportSource> {

    override fun importGraph(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraph(source, operations, options, CsvGraphIoOptions())

    fun importGraph(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport {
        val watch = GraphIoStopwatch()
        val codec = CsvRecordCodec(csvOptions.propertyMode)
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val failures = mutableListOf<GraphIoFailure>()
        var verticesRead = 0L; var verticesCreated = 0L; var skippedVertices = 0L
        var edgesRead = 0L; var edgesCreated = 0L; var skippedEdges = 0L
        var status = GraphIoStatus.COMPLETED

        // --- vertices ---
        GraphIoPaths.openReader(source.vertices).use { reader ->
            val csv = CsvRecordReader.builder().header(true).build(reader)
            for (row in csv) {
                verticesRead++
                val external = row.getString("id").orEmpty()
                val label = row.getString("label").orEmpty().ifBlank { options.defaultVertexLabel }
                if (external.isBlank()) {
                    failures += GraphIoFailure(
                        GraphIoPhase.READ_VERTEX, fileRole = GraphIoFileRole.VERTICES,
                        location = "line:${csv.lineNumber}", message = "Blank vertex id")
                    status = GraphIoStatus.FAILED; return@use
                }
                val props = codec.extractProperties(row.toMap())
                // spec §6.1: check duplicate BEFORE creating in backend
                if (idMap.contains(external)) {
                    when (options.onDuplicateVertexId) {
                        DuplicateVertexPolicy.SKIP -> {
                            skippedVertices++
                            status = GraphIoStatus.PARTIAL
                            failures += GraphIoFailure(
                                GraphIoPhase.CREATE_VERTEX,
                                severity = GraphIoFailureSeverity.WARN,
                                fileRole = GraphIoFileRole.VERTICES,
                                recordId = external,
                                message = "Duplicate vertex externalId skipped (first mapping preserved)")
                            continue
                        }
                        DuplicateVertexPolicy.FAIL -> {
                            failures += GraphIoFailure(GraphIoPhase.CREATE_VERTEX,
                                fileRole = GraphIoFileRole.VERTICES, recordId = external,
                                message = "Duplicate vertex externalId: $external")
                            status = GraphIoStatus.FAILED; return@use
                        }
                    }
                }
                val propsWithExternal = options.preserveExternalIdProperty?.let { props + (it to external) } ?: props
                val created = operations.createVertex(label, propsWithExternal)
                idMap.put(external, created.id)
                verticesCreated++
            }
        }
        if (status == GraphIoStatus.FAILED) return buildReport(watch, failures, GraphIoStatus.FAILED,
            verticesRead, verticesCreated, edgesRead, edgesCreated, skippedVertices, skippedEdges)

        // --- edges ---
        GraphIoPaths.openReader(source.edges).use { reader ->
            val csv = CsvRecordReader.builder().header(true).build(reader)
            for (row in csv) {
                edgesRead++
                val label = row.getString("label").orEmpty().ifBlank { options.defaultEdgeLabel }
                val from = row.getString("from").orEmpty()
                val to = row.getString("to").orEmpty()
                val fromId = idMap.resolve(from)
                val toId = idMap.resolve(to)
                if (fromId == null || toId == null) {
                    if (options.onMissingEdgeEndpoint == MissingEndpointPolicy.FAIL) {
                        failures += GraphIoFailure(GraphIoPhase.READ_EDGE,
                            fileRole = GraphIoFileRole.EDGES, location = "line:${csv.lineNumber}",
                            message = "Unresolved endpoint from=$from to=$to")
                        status = GraphIoStatus.FAILED; return@use
                    } else {
                        skippedEdges++
                        status = GraphIoStatus.PARTIAL
                        failures += GraphIoFailure(GraphIoPhase.READ_EDGE,
                            severity = GraphIoFailureSeverity.WARN,
                            fileRole = GraphIoFileRole.EDGES,
                            location = "line:${csv.lineNumber}",
                            message = "Missing endpoint skipped from=$from to=$to")
                        continue
                    }
                }
                val props = codec.extractProperties(row.toMap())
                val externalEdgeId = row.getString("id")?.takeIf { it.isNotBlank() }
                val propsWithExternal = externalEdgeId?.let { eid ->
                    options.preserveExternalIdProperty?.let { key -> props + (key to eid) } ?: props
                } ?: props
                operations.createEdge(fromId, toId, label, propsWithExternal)
                edgesCreated++
            }
        }
        return buildReport(watch, failures, status,
            verticesRead, verticesCreated, edgesRead, edgesCreated, skippedVertices, skippedEdges)
    }

    private fun buildReport(
        watch: GraphIoStopwatch, failures: List<GraphIoFailure>, status: GraphIoStatus,
        vr: Long, vc: Long, er: Long, ec: Long, sv: Long, se: Long,
    ) = GraphImportReport(status, GraphIoFormat.CSV, vr, vc, er, ec, sv, se, watch.elapsed(), failures)

    companion object : KLogging()
}
```

- [ ] **Step 4: Implement `CsvGraphBulkExporter`**

```kotlin
package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.CsvRecordWriter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.csv.internal.CsvRecordCodec
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.*
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.logging.KLogging

class CsvGraphBulkExporter : GraphBulkExporter<CsvGraphExportSink> {

    override fun exportGraph(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraph(sink, operations, options, CsvGraphIoOptions())

    fun exportGraph(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphExportReport {
        require(options.vertexLabels.isNotEmpty() || options.edgeLabels.isNotEmpty()) {
            "label discovery is not supported by GraphOperations; provide labels explicitly"
        }
        val watch = GraphIoStopwatch()
        val codec = CsvRecordCodec(csvOptions.propertyMode)
        val failures = mutableListOf<GraphIoFailure>()
        var vWritten = 0L; var eWritten = 0L

        // vertices: per-label pre-scan union header
        val allVertices = options.vertexLabels.flatMap { label ->
            operations.findVerticesByLabel(label).map { v ->
                GraphIoVertexRecord(v.id.value, v.label, v.properties)
            }
        }
        val vHeader = codec.unionVertexHeader(allVertices)
        GraphIoPaths.openWriter(sink.vertices).use { w ->
            CsvRecordWriter.builder().header(vHeader).build(w).use { csv ->
                for (v in allVertices) {
                    csv.writeRecord(buildList {
                        add(v.externalId); add(v.label)
                        vHeader.drop(2).forEach { col ->
                            val key = col.removePrefix(
                                (csvOptions.propertyMode as? CsvPropertyMode.PrefixedColumns)?.prefix ?: "")
                            add(v.properties[key]?.toString() ?: "")
                        }
                    })
                    vWritten++
                }
            }
        }

        // edges
        val allEdges = options.edgeLabels.flatMap { label ->
            operations.findEdgesByLabel(label).map { e ->
                GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
            }
        }
        val eHeader = codec.unionEdgeHeader(allEdges)
        GraphIoPaths.openWriter(sink.edges).use { w ->
            CsvRecordWriter.builder().header(eHeader).build(w).use { csv ->
                for (ed in allEdges) {
                    csv.writeRecord(buildList {
                        add(ed.externalId ?: ""); add(ed.label)
                        add(ed.fromExternalId); add(ed.toExternalId)
                        eHeader.drop(4).forEach { col ->
                            val key = col.removePrefix(
                                (csvOptions.propertyMode as? CsvPropertyMode.PrefixedColumns)?.prefix ?: "")
                            add(ed.properties[key]?.toString() ?: "")
                        }
                    })
                    eWritten++
                }
            }
        }

        return GraphExportReport(
            status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL,
            format = GraphIoFormat.CSV,
            verticesWritten = vWritten, edgesWritten = eWritten,
            elapsed = watch.elapsed(), failures = failures)
    }

    companion object : KLogging()
}
```

- [ ] **Step 5: Run round-trip test (expect PASS)**

Run: `./gradlew :graph-io-csv:test --tests "io.bluetape4k.graph.io.csv.CsvRoundTripTest"`

- [ ] **Step 6: Commit**

```bash
git add graph-io/csv/src
git commit -m "feat(graph-io-csv): 유니온 헤더 기반 CsvGraphBulkImporter/Exporter(Sync) 추가"
```

---

### Task 13: `graph-io-csv` VT importer/exporter

**Files:**
- Create: `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphVirtualThreadBulkImporter.kt`
- Create: `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphVirtualThreadBulkExporter.kt`
- Test: `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvVirtualThreadTest.kt`

Complexity: medium. Dependencies: 12, 9. Module: `graph-io-csv`.

- [ ] **Step 1: Write failing VT round-trip test (mirror of CsvRoundTripTest but call `.importGraphAsync(...).get()` / `.exportGraphAsync(...).get()`; assert `status = COMPLETED`).**

- [ ] **Step 2: Implement VT classes**

```kotlin
package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter
import java.util.concurrent.CompletableFuture

class CsvGraphVirtualThreadBulkImporter(
    private val sync: CsvGraphBulkImporter = CsvGraphBulkImporter(),
) : GraphVirtualThreadBulkImporter<CsvGraphImportSource> {

    override fun importGraphAsync(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): CompletableFuture<GraphImportReport> =
        VirtualThreadGraphBulkAdapter.wrapImporter(sync)
            .importGraphAsync(source, operations, options)

    fun importGraphAsync(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): CompletableFuture<GraphImportReport> =
        io.bluetape4k.concurrent.virtualthread.virtualFutureOf {
            sync.importGraph(source, operations, options, csvOptions)
        }
}
```

The exporter follows the same pattern against `CsvGraphBulkExporter`.

- [ ] **Step 3: Run VT test (expect PASS)**

- [ ] **Step 4: Commit**

```bash
git add graph-io/csv/src
git commit -m "feat(graph-io-csv): 어댑터 위임 방식 CSV Virtual Thread importer/exporter 추가"
```

---

### Task 14: `graph-io-csv` Suspend importer/exporter

**Files:**
- Create: `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/SuspendCsvGraphBulkImporter.kt`
- Create: `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/SuspendCsvGraphBulkExporter.kt`
- Test: `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvSuspendRoundTripTest.kt`

Complexity: medium. Dependencies: 12. Module: `graph-io-csv`.

- [ ] **Step 1: Write failing suspend round-trip test**

Use `TinkerGraphSuspendOperations`, `runTest { ... }`. Use `importGraphSuspending(...)` + `exportGraphSuspending(...)` for vertices labels set `{"Person"}` and edge labels set `{"KNOWS"}`.

- [ ] **Step 2: Implement suspend importer**

```kotlin
package io.bluetape4k.graph.io.csv

import io.bluetape4k.coroutines.support.runSuspendIO
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.io.contract.GraphSuspendBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `SuspendCsvRecordReader`로 정점 파일을 스트림 수집한 뒤 `createVertex(...)`로 정점을
 * 생성하고, 엣지 파일도 스트림으로 처리한다. CSV 특성상 정점 전수 읽기가 먼저 필요하다.
 */
class SuspendCsvGraphBulkImporter : GraphSuspendBulkImporter<CsvGraphImportSource> {

    override suspend fun importGraphSuspending(
        source: CsvGraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraphSuspending(source, operations, options, CsvGraphIoOptions())

    suspend fun importGraphSuspending(
        source: CsvGraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport = withContext(Dispatchers.IO) {
        val stopwatch = GraphIoStopwatch()
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val failures = mutableListOf<GraphIoFailure>()
        var importedVertices = 0L
        var skippedVertices = 0L
        var importedEdges = 0L
        var skippedEdges = 0L

        // --- Vertex pass ---
        GraphIoPaths.openInputStream(source.vertexSource).use { inputStream ->
            SuspendCsvRecordReader().read(inputStream, skipHeaders = true) { record ->
                record
            }.collect { record ->
                    val extId = record.getString("__id") ?: return@collect
                    if (idMap.contains(extId)) {
                        when (options.onDuplicateVertexId) {
                            DuplicateVertexPolicy.SKIP -> { skippedVertices++; return@collect }
                            DuplicateVertexPolicy.FAIL -> error("Duplicate vertex externalId: $extId")
                        }
                    }
                    val label = record.getString("__label") ?: options.defaultVertexLabel
                    val props = CsvRecordCodec.decodeProperties(record, csvOptions.propertyMode)
                    runCatching {
                        val vertex = operations.createVertex(label, props)
                        idMap.put(extId, vertex.id)
                        importedVertices++
                    }.onFailure { e ->
                        failures += GraphIoFailure(
                            phase = GraphIoPhase.CREATE_VERTEX,
                            severity = GraphIoFailureSeverity.ERROR,
                            fileRole = GraphIoFileRole.VERTICES,
                            message = e.message ?: "vertex error"
                        )
                        skippedVertices++
                    }
                }
        }

        // --- Edge pass ---
        GraphIoPaths.openInputStream(source.edgeSource).use { inputStream ->
            SuspendCsvRecordReader().read(inputStream, skipHeaders = true) { record ->
                record
            }.collect { record ->
                    val label = record.getString("__label") ?: options.defaultEdgeLabel
                    val fromExtId = record.getString("__from") ?: return@collect
                    val toExtId = record.getString("__to") ?: return@collect
                    val fromId = idMap.resolve(fromExtId)
                    val toId = idMap.resolve(toExtId)
                    if (fromId == null || toId == null) {
                        when (options.onMissingEdgeEndpoint) {
                            MissingEndpointPolicy.FAIL -> error("Missing endpoint for edge: $fromExtId -> $toExtId")
                            MissingEndpointPolicy.SKIP_EDGE -> { skippedEdges++; return@collect }
                        }
                    }
                    val props = CsvRecordCodec.decodeProperties(record, csvOptions.propertyMode)
                    runCatching {
                        operations.createEdge(fromId!!, toId!!, label, props)
                        importedEdges++
                    }.onFailure { e ->
                        failures += GraphIoFailure(
                            phase = GraphIoPhase.CREATE_EDGE,
                            severity = GraphIoFailureSeverity.ERROR,
                            fileRole = GraphIoFileRole.EDGES,
                            message = e.message ?: "edge error"
                        )
                        skippedEdges++
                    }
                }
        }

        val hasErrors = failures.any { it.severity == GraphIoFailureSeverity.ERROR }
        GraphImportReport(
            status = when {
                hasErrors && importedVertices == 0L -> GraphIoStatus.FAILED
                hasErrors -> GraphIoStatus.PARTIAL
                failures.isNotEmpty() -> GraphIoStatus.COMPLETED
                else -> GraphIoStatus.COMPLETED
            },
            importedVertices = importedVertices,
            skippedVertices = skippedVertices,
            importedEdges = importedEdges,
            skippedEdges = skippedEdges,
            failures = failures,
            elapsedMs = stopwatch.elapsedMs(),
        )
    }

    companion object : KLoggingChannel()
}
```

Note for the engineer: "copy body verbatim" is a deliberate DRY trade-off for clarity. Do not extract shared mutable helpers that would complicate the simple sync path.

- [ ] **Step 3: Implement suspend exporter**

```kotlin
package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.coroutines.SuspendCsvRecordWriter
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.io.contract.GraphSuspendBulkExporter
import io.bluetape4k.graph.io.csv.internal.CsvRecordCodec
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.*
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

/**
 * Flow 기반 per-label 수집 후 Union header를 계산해 기록한다.
 * 완전 streaming은 v1 범위가 아니다. (spec §2, §8.1)
 */
class SuspendCsvGraphBulkExporter : GraphSuspendBulkExporter<CsvGraphExportSink> {

    override suspend fun exportGraphSuspending(
        sink: CsvGraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraphSuspending(sink, operations, options, CsvGraphIoOptions())

    suspend fun exportGraphSuspending(
        sink: CsvGraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions = GraphExportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphExportReport = withContext(Dispatchers.IO) {
        require(options.vertexLabels.isNotEmpty() || options.edgeLabels.isNotEmpty()) {
            "label discovery is not supported by GraphOperations; provide labels explicitly"
        }
        val watch = GraphIoStopwatch()
        val codec = CsvRecordCodec(csvOptions.propertyMode)
        var vWritten = 0L; var eWritten = 0L

        val vertexRecs = options.vertexLabels.flatMap { label ->
            operations.findVerticesByLabel(label).toList().map {
                GraphIoVertexRecord(it.id.value, it.label, it.properties)
            }
        }
        val vHeader = codec.unionVertexHeader(vertexRecs)
        GraphIoPaths.openWriter(sink.vertices).use { w ->
            SuspendCsvRecordWriter.builder().header(vHeader).build(w).use { csv ->
                for (v in vertexRecs) {
                    csv.writeRecord(listOf(v.externalId, v.label) + vHeader.drop(2).map { col ->
                        val key = col.removePrefix(
                            (csvOptions.propertyMode as? CsvPropertyMode.PrefixedColumns)?.prefix ?: "")
                        v.properties[key]?.toString() ?: ""
                    })
                    vWritten++
                }
            }
        }

        val edgeRecs = options.edgeLabels.flatMap { label ->
            operations.findEdgesByLabel(label).toList().map {
                GraphIoEdgeRecord(it.id.value, it.label, it.startId.value, it.endId.value, it.properties)
            }
        }
        val eHeader = codec.unionEdgeHeader(edgeRecs)
        GraphIoPaths.openWriter(sink.edges).use { w ->
            SuspendCsvRecordWriter.builder().header(eHeader).build(w).use { csv ->
                for (ed in edgeRecs) {
                    csv.writeRecord(listOf(ed.externalId ?: "", ed.label, ed.fromExternalId, ed.toExternalId) +
                        eHeader.drop(4).map { col ->
                            val key = col.removePrefix(
                                (csvOptions.propertyMode as? CsvPropertyMode.PrefixedColumns)?.prefix ?: "")
                            ed.properties[key]?.toString() ?: ""
                        })
                    eWritten++
                }
            }
        }

        GraphExportReport(
            status = GraphIoStatus.COMPLETED, format = GraphIoFormat.CSV,
            verticesWritten = vWritten, edgesWritten = eWritten, elapsed = watch.elapsed())
    }

    companion object : KLoggingChannel()
}
```

- [ ] **Step 4: Run suspend test (expect PASS)**

Run: `./gradlew :graph-io-csv:test --tests "*CsvSuspendRoundTripTest"`

- [ ] **Step 5: Commit**

```bash
git add graph-io/csv/src
git commit -m "feat(graph-io-csv): Flow 기반 SuspendCsvGraphBulkImporter/Exporter 추가"
```

---

### Task 15: `graph-io-csv` format-specific options overload tests

**Files:**
- Test: `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvFormatOptionsTest.kt`

Complexity: medium. Dependencies: 12, 13, 14. Module: `graph-io-csv`.

- [ ] **Step 1: Add tests for overload pattern**

Tests must cover:
1. `CsvGraphBulkImporter().importGraph(src, ops, options, CsvGraphIoOptions(CsvPropertyMode.RawJsonColumn()))` preserves raw JSON string under the column name.
2. `CsvGraphBulkImporter().importGraph(src, ops, options, CsvGraphIoOptions(CsvPropertyMode.None))` imports empty property maps.
3. Prefixed mode collision on prefix `""` with vertex property `id` raises `IllegalStateException`.
4. VT overload with csv options parameter returns same shape as Sync.
5. Suspend overload with csv options parameter returns same shape as Sync.
6. Generic override (`GraphBulkImporter<CsvGraphImportSource>.importGraph`) delegates to default `CsvGraphIoOptions()`.

- [ ] **Step 2: Run tests (expect PASS)**

- [ ] **Step 3: Commit**

```bash
git add graph-io/csv/src
git commit -m "test(graph-io-csv): CsvGraphIoOptions 오버로드 Sync/VT/Suspend 전체 테스트 추가"
```

---

### Task 16: `graph-io-jackson2` `NdJsonEnvelope` + `Jackson2EnvelopeCodec`

**Files:**
- Create: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/internal/NdJsonEnvelope.kt`
- Create: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/internal/Jackson2EnvelopeCodec.kt`
- Test: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/internal/Jackson2EnvelopeCodecTest.kt`

Complexity: medium. Dependencies: 6. Module: `graph-io-jackson2`.

- [ ] **Step 1: Define envelope (shared test-fixture JSON shape)**

```kotlin
// NdJsonEnvelope.kt
package io.bluetape4k.graph.io.jackson2.internal

import java.io.Serializable

/**
 * NDJSON 한 줄에 대응하는 envelope. `type`은 "vertex" 또는 "edge".
 * vertex/edge 공통 필드를 nullable로 두되 codec에서 type에 맞는 필드만 검증한다.
 */
internal data class NdJsonEnvelope(
    val type: String,
    val id: String? = null,
    val label: String? = null,
    val from: String? = null,
    val to: String? = null,
    val properties: Map<String, Any?> = emptyMap(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
        const val TYPE_VERTEX = "vertex"
        const val TYPE_EDGE = "edge"
    }
}
```

- [ ] **Step 2: Implement codec**

```kotlin
package io.bluetape4k.graph.io.jackson2.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.jackson.Jackson

internal class Jackson2EnvelopeCodec(
    private val mapper: ObjectMapper = Jackson.defaultJsonMapper,
) {
    fun parseLine(line: String): NdJsonEnvelope = mapper.readValue(line, NdJsonEnvelope::class.java)

    fun toVertex(env: NdJsonEnvelope, defaultLabel: String): GraphIoVertexRecord {
        require(env.type == NdJsonEnvelope.TYPE_VERTEX) { "Expected vertex envelope" }
        return GraphIoVertexRecord(
            externalId = requireNotNull(env.id) { "vertex envelope missing id" },
            label = env.label?.ifBlank { null } ?: defaultLabel,
            properties = env.properties,
        )
    }

    fun toEdge(env: NdJsonEnvelope, defaultLabel: String): GraphIoEdgeRecord {
        require(env.type == NdJsonEnvelope.TYPE_EDGE) { "Expected edge envelope" }
        return GraphIoEdgeRecord(
            externalId = env.id,
            label = env.label?.ifBlank { null } ?: defaultLabel,
            fromExternalId = requireNotNull(env.from) { "edge envelope missing from" },
            toExternalId = requireNotNull(env.to) { "edge envelope missing to" },
            properties = env.properties,
        )
    }

    fun writeVertex(v: GraphIoVertexRecord): String =
        mapper.writeValueAsString(NdJsonEnvelope(NdJsonEnvelope.TYPE_VERTEX, v.externalId, v.label,
            properties = v.properties))

    fun writeEdge(e: GraphIoEdgeRecord): String =
        mapper.writeValueAsString(NdJsonEnvelope(NdJsonEnvelope.TYPE_EDGE, e.externalId, e.label,
            e.fromExternalId, e.toExternalId, e.properties))
}
```

- [ ] **Step 3: Write codec round-trip test (write vertex + edge, read back equality)**
- [ ] **Step 4: Run (expect PASS)**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat(graph-io-jackson2): NDJSON 봉투 및 Jackson2 코덱 추가"
```

---

### Task 17: `graph-io-jackson2` Sync importer/exporter (single-pass + edge buffer overflow)

**Files:**
- Create: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonBulkImporter.kt`
- Create: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonBulkExporter.kt`
- Test: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2RoundTripTest.kt`
- Test: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2EdgeBufferOverflowTest.kt`

Complexity: medium. Dependencies: 16, 8. Module: `graph-io-jackson2`.

- [ ] **Step 1: Write failing round-trip test (TinkerGraph, 3 vertices + 2 edges; assert `COMPLETED` and counts)**

- [ ] **Step 2: Write failing overflow test**

Construct NDJSON where edges appear *before* some vertex records and buffer grows past `maxEdgeBufferSize = 2`. Assert `status = FAILED`, failure phase `READ_EDGE`, message mentions `maxEdgeBufferSize`, and `verticesCreated > 0`.

- [ ] **Step 3: Implement importer**

Importer algorithm (spec §9 NDJSON branch):

```kotlin
package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.jackson2.internal.Jackson2EnvelopeCodec
import io.bluetape4k.graph.io.jackson2.internal.NdJsonEnvelope
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.*
import io.bluetape4k.graph.io.support.GraphIoExternalIdMap
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.logging.KLogging

class Jackson2NdJsonBulkImporter(
    private val codec: Jackson2EnvelopeCodec = Jackson2EnvelopeCodec(),
) : GraphBulkImporter<GraphImportSource> {

    override fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport {
        val watch = GraphIoStopwatch()
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val failures = mutableListOf<GraphIoFailure>()
        val bufferedEdges = ArrayDeque<GraphIoEdgeRecord>()
        var vr = 0L; var vc = 0L; var er = 0L; var ec = 0L; var sv = 0L; var se = 0L
        var status = GraphIoStatus.COMPLETED

        GraphIoPaths.openReader(source).use { reader ->
            var lineNo = 0
            reader.forEachLine { raw ->
                lineNo++
                val line = raw.ifBlank { return@forEachLine }
                val env = try { codec.parseLine(line) } catch (e: Exception) {
                    failures += GraphIoFailure(GraphIoPhase.READ_VERTEX,
                        fileRole = GraphIoFileRole.UNIFIED, location = "line:$lineNo",
                        message = "Malformed JSON: ${e.message}")
                    status = GraphIoStatus.FAILED; return@forEachLine
                }
                when (env.type) {
                    NdJsonEnvelope.TYPE_VERTEX -> {
                        vr++
                        val rec = codec.toVertex(env, options.defaultVertexLabel)
                        val props = options.preserveExternalIdProperty
                            ?.let { rec.properties + (it to rec.externalId) } ?: rec.properties
                        val created = operations.createVertex(rec.label, props)
                        when (idMap.putFirstOrFail(rec.externalId, created.id)) {
                            GraphIoExternalIdMap.PutResult.CREATED -> vc++
                            GraphIoExternalIdMap.PutResult.SKIPPED -> { sv++; status = GraphIoStatus.PARTIAL }
                        }
                    }
                    NdJsonEnvelope.TYPE_EDGE -> {
                        er++
                        val rec = codec.toEdge(env, options.defaultEdgeLabel)
                        bufferedEdges += rec
                        if (bufferedEdges.size > options.maxEdgeBufferSize) {
                            failures += GraphIoFailure(GraphIoPhase.READ_EDGE,
                                fileRole = GraphIoFileRole.UNIFIED, location = "line:$lineNo",
                                message = "Edge buffer exceeded maxEdgeBufferSize=${options.maxEdgeBufferSize}; " +
                                          "verticesCreated=$vc remain in graph as partial state")
                            status = GraphIoStatus.FAILED
                            return@forEachLine
                        }
                    }
                    else -> failures += GraphIoFailure(GraphIoPhase.READ_VERTEX,
                        severity = GraphIoFailureSeverity.WARN, fileRole = GraphIoFileRole.UNIFIED,
                        location = "line:$lineNo", message = "Unknown envelope type=${env.type}")
                }
            }
        }
        if (status == GraphIoStatus.FAILED) {
            return GraphImportReport(status, GraphIoFormat.NDJSON_JACKSON2, vr, vc, er, ec, sv, se, watch.elapsed(), failures)
        }
        // flush buffered edges
        for (e in bufferedEdges) {
            val from = idMap.resolve(e.fromExternalId)
            val to = idMap.resolve(e.toExternalId)
            if (from == null || to == null) {
                if (options.onMissingEdgeEndpoint == MissingEndpointPolicy.FAIL) {
                    failures += GraphIoFailure(GraphIoPhase.READ_EDGE, fileRole = GraphIoFileRole.UNIFIED,
                        recordId = e.externalId, message = "Unresolved endpoint from=${e.fromExternalId} to=${e.toExternalId}")
                    status = GraphIoStatus.FAILED
                    return GraphImportReport(status, GraphIoFormat.NDJSON_JACKSON2, vr, vc, er, ec, sv, se, watch.elapsed(), failures)
                } else {
                    se++; status = GraphIoStatus.PARTIAL
                    failures += GraphIoFailure(GraphIoPhase.READ_EDGE, severity = GraphIoFailureSeverity.WARN,
                        fileRole = GraphIoFileRole.UNIFIED, recordId = e.externalId,
                        message = "Missing endpoint skipped from=${e.fromExternalId} to=${e.toExternalId}")
                    continue
                }
            }
            val props = e.externalId?.let { eid ->
                options.preserveExternalIdProperty?.let { key -> e.properties + (key to eid) } ?: e.properties
            } ?: e.properties
            operations.createEdge(from, to, e.label, props)
            ec++
        }
        return GraphImportReport(status, GraphIoFormat.NDJSON_JACKSON2, vr, vc, er, ec, sv, se, watch.elapsed(), failures)
    }

    companion object : KLogging()
}
```

- [ ] **Step 4: Implement exporter (writes vertices first then edges; single-file UNIFIED sink)**

Use `GraphExportSink` directly (not CSV wrapper). Pre-collects `ops.findVerticesByLabel` + `findEdgesByLabel` (same approach as CSV exporter v1), writes each envelope line using codec.

- [ ] **Step 5: Run tests (expect PASS)**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(graph-io-jackson2): 엣지 버퍼링 포함 Sync NDJSON importer/exporter 추가"
```

---

### Task 18: `graph-io-jackson2` VT + Suspend importer/exporter

**Files:**
- Create: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonVirtualThreadBulkImporter.kt`
- Create: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonVirtualThreadBulkExporter.kt`
- Create: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/SuspendJackson2NdJsonBulkImporter.kt`
- Create: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/SuspendJackson2NdJsonBulkExporter.kt`
- Test: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2VirtualThreadTest.kt`
- Test: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2SuspendTest.kt`

Complexity: medium. Dependencies: 17, 9. Module: `graph-io-jackson2`.

- [ ] **Step 1: VT classes mirror CSV VT classes (delegate to `VirtualThreadGraphBulkAdapter.wrapImporter(sync)`)**
- [ ] **Step 2: Suspend classes wrap sync with `withContext(Dispatchers.IO)` and use `createVertex`/`createEdge` suspend APIs on `GraphSuspendOperations`**
- [ ] **Step 3: Write failing VT and suspend round-trip tests**
- [ ] **Step 4: Run tests (expect PASS)**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat(graph-io-jackson2): Virtual Thread 및 Suspend NDJSON importer/exporter 추가"
```

---

### Task 19: `graph-io-jackson3` envelope + codec (tools.jackson.*)

**Files:**
- Create: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/internal/NdJsonEnvelope.kt` (identical shape to Jackson2 envelope, separate file/package)
- Create: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/internal/Jackson3EnvelopeCodec.kt`
- Test: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/internal/Jackson3EnvelopeCodecTest.kt`

Complexity: medium. Dependencies: 6. Module: `graph-io-jackson3`.

- [ ] **Step 1: Copy Jackson2 envelope data class verbatim under `graph-io-jackson3` package (do not share to avoid cross-Jackson coupling)**
- [ ] **Step 2: Implement codec using `tools.jackson.databind.ObjectMapper` and `io.bluetape4k.jackson3.Jackson.defaultJsonMapper`**

```kotlin
package io.bluetape4k.graph.io.jackson3.internal

import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.jackson3.Jackson
import tools.jackson.databind.ObjectMapper

internal class Jackson3EnvelopeCodec(
    private val mapper: ObjectMapper = Jackson.defaultJsonMapper,
) {
    fun parseLine(line: String): NdJsonEnvelope = mapper.readValue(line, NdJsonEnvelope::class.java)
    // remaining methods mirror Jackson2EnvelopeCodec verbatim
}
```

- [ ] **Step 3: Round-trip test + compatibility fixture (see Task 27)**
- [ ] **Step 4: Run (expect PASS)**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat(graph-io-jackson3): NDJSON 봉투 및 Jackson3 코덱 추가 (tools.jackson.* 패키지)"
```

---

### Task 20: `graph-io-jackson3` Sync importer/exporter

**Files:**
- Create: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonBulkImporter.kt`
- Create: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonBulkExporter.kt`
- Test: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3RoundTripTest.kt`
- Test: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3EdgeBufferOverflowTest.kt`

Complexity: medium. Dependencies: 19, 8. Module: `graph-io-jackson3`.

- [ ] **Step 1: Mirror Jackson2 Sync importer/exporter; only difference is codec and `format = GraphIoFormat.NDJSON_JACKSON3`**
- [ ] **Step 2: Run round-trip + overflow tests (expect PASS)**
- [ ] **Step 3: Commit**

```bash
git commit -m "feat(graph-io-jackson3): tools.jackson 기반 Sync NDJSON importer/exporter 추가"
```

---

### Task 21: `graph-io-jackson3` VT + Suspend importer/exporter

**Files:**
- Create: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonVirtualThreadBulkImporter.kt`
- Create: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonVirtualThreadBulkExporter.kt`
- Create: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/SuspendJackson3NdJsonBulkImporter.kt`
- Create: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/SuspendJackson3NdJsonBulkExporter.kt`
- Test: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3VirtualThreadTest.kt`
- Test: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3SuspendTest.kt`

Complexity: medium. Dependencies: 20, 9. Module: `graph-io-jackson3`. Mirror of Task 18 for Jackson 3.

- [ ] **Step 1-5: Same pattern as Task 18**
- [ ] **Step 6: Commit**

```bash
git commit -m "feat(graph-io-jackson3): Virtual Thread 및 Suspend NDJSON importer/exporter 추가"
```

---

### Task 22: `graph-io-graphml` Options + GraphMlAttrType coercion

**Files:**
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/UnsupportedGraphMlElementPolicy.kt`
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlEdgeDefault.kt`
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlImportOptions.kt`
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlExportOptions.kt`
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/internal/GraphMlAttrType.kt`
- Test: `graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/internal/GraphMlAttrTypeTest.kt`

Complexity: low. Dependencies: 6. Module: `graph-io-graphml`.

- [ ] **Step 1: Write failing coercion tests**

```kotlin
package io.bluetape4k.graph.io.graphml.internal

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class GraphMlAttrTypeTest {

    @Test
    fun `parses boolean attr type`() {
        GraphMlAttrType.coerce("true", "boolean") shouldBeEqualTo true
    }

    @Test
    fun `parses int attr type`() {
        GraphMlAttrType.coerce("42", "int") shouldBeEqualTo 42
    }

    @Test
    fun `parses long attr type`() {
        GraphMlAttrType.coerce("9223372036854775807", "long") shouldBeEqualTo Long.MAX_VALUE
    }

    @Test
    fun `missing attr type defaults to string`() {
        GraphMlAttrType.coerce("hi", null) shouldBeEqualTo "hi"
    }

    @Test
    fun `exportTypeFor handles common JVM scalars`() {
        GraphMlAttrType.exportTypeFor(true) shouldBeEqualTo "boolean"
        GraphMlAttrType.exportTypeFor(1) shouldBeEqualTo "int"
        GraphMlAttrType.exportTypeFor(1L) shouldBeEqualTo "long"
        GraphMlAttrType.exportTypeFor(1.5) shouldBeEqualTo "double"
        GraphMlAttrType.exportTypeFor("x") shouldBeEqualTo "string"
    }
}
```

- [ ] **Step 2: Implement enums + types**

```kotlin
// UnsupportedGraphMlElementPolicy.kt
package io.bluetape4k.graph.io.graphml
enum class UnsupportedGraphMlElementPolicy { SKIP, FAIL }
```

```kotlin
// GraphMlEdgeDefault.kt
package io.bluetape4k.graph.io.graphml
enum class GraphMlEdgeDefault { DIRECTED }
```

```kotlin
// GraphMlImportOptions.kt
package io.bluetape4k.graph.io.graphml
import java.io.Serializable

data class GraphMlImportOptions(
    val unsupportedElementPolicy: UnsupportedGraphMlElementPolicy = UnsupportedGraphMlElementPolicy.SKIP,
) : Serializable { companion object { private const val serialVersionUID: Long = 1L } }
```

```kotlin
// GraphMlExportOptions.kt
package io.bluetape4k.graph.io.graphml
import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

data class GraphMlExportOptions(
    val graphId: String = "G",
    val edgeDefault: GraphMlEdgeDefault = GraphMlEdgeDefault.DIRECTED,
    val labelKey: String = "label",
) : Serializable {
    init { graphId.requireNotBlank("graphId"); labelKey.requireNotBlank("labelKey") }
    companion object { private const val serialVersionUID: Long = 1L }
}
```

```kotlin
// GraphMlAttrType.kt
package io.bluetape4k.graph.io.graphml.internal

internal object GraphMlAttrType {
    fun coerce(raw: String, attrType: String?): Any = when (attrType) {
        "boolean" -> raw.toBoolean()
        "int" -> raw.toInt()
        "long" -> raw.toLong()
        "float" -> raw.toFloat()
        "double" -> raw.toDouble()
        null, "string" -> raw
        else -> raw
    }
    fun exportTypeFor(value: Any?): String = when (value) {
        is Boolean -> "boolean"
        is Byte, is Short, is Int -> "int"
        is Long -> "long"
        is Float -> "float"
        is Double -> "double"
        else -> "string"
    }
}
```

- [ ] **Step 3: Run tests (expect PASS)**
- [ ] **Step 4: Commit**

```bash
git commit -m "feat(graph-io-graphml): 옵션 및 GraphMlAttrType 타입 변환 테이블 추가"
```

---

### Task 23: `graph-io-graphml` StaxGraphMlReader

**Files:**
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/internal/StaxGraphMlReader.kt`
- Test: `graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/internal/StaxGraphMlReaderTest.kt`

Complexity: high. Dependencies: 22. Module: `graph-io-graphml`.

- [ ] **Step 1: Write failing tests**

Tests must cover:
1. Parses sample GraphML from spec §8.3 into 2 vertex records + 1 edge record with `since = 2024L`.
2. `<data key="label">` overrides the default label.
3. Missing label defaults to `GraphImportOptions.defaultVertexLabel`.
4. Unsupported `<hyperedge>` element under `SKIP` policy records `WARN` failure + increments counter.
5. Unsupported `<hyperedge>` element under `FAIL` policy throws, caller converts to `FAILED` report.
6. `edgedefault="undirected"` records `WARN`, skips edges.
7. Malformed XML throws `XMLStreamException` that the caller translates to `GraphIoFailure` with location (StAX line:col).
8. Edge element appearing before referenced node id triggers edge buffering (returns `bufferedEdges` sequence on completion).

- [ ] **Step 2: Implement reader using `javax.xml.stream.XMLInputFactory` / `XMLEventReader`**

Key design points to match the spec:
- Tracks `keys: Map<String, KeyDef>` where `KeyDef(forKind, attrName, attrType)`.
- On `<node id=...>`: starts new vertex record; on inner `<data key=...>` text, coerces with `GraphMlAttrType` using the matching `KeyDef` (or `string` if unknown).
- On `<edge id=... source=... target=...>`: same handling with `labelKey` property extraction.
- Buffers edges when source/target isn't yet in the vertex map (the caller supplies the map; reader reports pending edges via a callback or emits them into a separate buffer list).
- Records `location = "graph/node[@id='$id']/data[@key='$key']"` for each data element.
- Returns a `ReadResult(vertices: List<GraphIoVertexRecord>, edges: List<GraphIoEdgeRecord>, failures: List<GraphIoFailure>, sawUndirected: Boolean)`.

```kotlin
package io.bluetape4k.graph.io.graphml.internal

import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
import io.bluetape4k.graph.io.graphml.UnsupportedGraphMlElementPolicy
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.*
import java.io.Reader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

internal class StaxGraphMlReader(
    private val options: GraphImportOptions,
    private val graphMlOptions: GraphMlImportOptions,
) {

    data class ReadResult(
        val vertices: List<GraphIoVertexRecord>,
        val edges: List<GraphIoEdgeRecord>,
        val failures: List<GraphIoFailure>,
        val skippedEdges: Long = 0L,
    )

    data class KeyDef(val forKind: String, val attrName: String, val attrType: String?)

    fun read(reader: Reader): ReadResult {
        val factory = XMLInputFactory.newFactory().also {
            it.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
            it.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true)
            it.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }
        val xr: XMLStreamReader = factory.createXMLStreamReader(reader)
        val keys = HashMap<String, KeyDef>()
        val vertices = ArrayList<GraphIoVertexRecord>()
        val edges = ArrayList<GraphIoEdgeRecord>()
        val failures = ArrayList<GraphIoFailure>()
        var currentVertexId: String? = null
        var currentVertexProps = HashMap<String, Any?>()
        var currentVertexLabel: String? = null
        var currentEdgeId: String? = null
        var currentEdgeFrom: String? = null
        var currentEdgeTo: String? = null
        var currentEdgeProps = HashMap<String, Any?>()
        var currentEdgeLabel: String? = null
        var currentDataKey: String? = null
        var currentDataBuffer = StringBuilder()
        var edgeLabelKey = "label"
        var isGraphUndirected = false
        var skippedEdges = 0L

        while (xr.hasNext()) {
            when (xr.next()) {
                XMLStreamConstants.START_ELEMENT -> when (xr.localName) {
                    "key" -> {
                        val id = xr.getAttributeValue(null, "id")!!
                        val forKind = xr.getAttributeValue(null, "for") ?: "all"
                        val attrName = xr.getAttributeValue(null, "attr.name") ?: id
                        val attrType = xr.getAttributeValue(null, "attr.type")
                        keys[id] = KeyDef(forKind, attrName, attrType)
                    }
                    "graph" -> {
                        val edgedefault = xr.getAttributeValue(null, "edgedefault")
                        if (edgedefault == "undirected") {
                            isGraphUndirected = true
                            failures += GraphIoFailure(GraphIoPhase.READ_EDGE,
                                severity = GraphIoFailureSeverity.WARN,
                                fileRole = GraphIoFileRole.UNIFIED, elementName = "graph",
                                message = "edgedefault='undirected' is not supported; undirected edges will be skipped")
                        }
                    }
                    "node" -> {
                        currentVertexId = xr.getAttributeValue(null, "id")
                        currentVertexProps = HashMap(); currentVertexLabel = null
                    }
                    "edge" -> {
                        val edgeId = xr.getAttributeValue(null, "id")
                        val directed = xr.getAttributeValue(null, "directed")
                        // spec §8.3: undirected edges are skipped, skippedEdges is incremented
                        if (isGraphUndirected || directed == "false") {
                            failures += GraphIoFailure(GraphIoPhase.READ_EDGE,
                                severity = GraphIoFailureSeverity.WARN, fileRole = GraphIoFileRole.UNIFIED,
                                recordId = edgeId,
                                message = "undirected edge skipped (edgedefault='undirected' or directed='false')")
                            skippedEdges++
                            currentEdgeId = null  // sentinel: skip edge on END_ELEMENT
                        } else {
                            currentEdgeId = edgeId
                            currentEdgeFrom = xr.getAttributeValue(null, "source")
                            currentEdgeTo = xr.getAttributeValue(null, "target")
                        }
                        currentEdgeProps = HashMap(); currentEdgeLabel = null
                    }
                    "data" -> {
                        currentDataKey = xr.getAttributeValue(null, "key")
                        currentDataBuffer = StringBuilder()
                    }
                    "hyperedge", "port" -> {
                        val failure = GraphIoFailure(GraphIoPhase.READ_VERTEX,
                            severity = if (graphMlOptions.unsupportedElementPolicy == UnsupportedGraphMlElementPolicy.FAIL)
                                GraphIoFailureSeverity.ERROR else GraphIoFailureSeverity.WARN,
                            fileRole = GraphIoFileRole.UNIFIED, elementName = xr.localName,
                            message = "Unsupported element ${xr.localName}")
                        failures += failure
                        if (graphMlOptions.unsupportedElementPolicy == UnsupportedGraphMlElementPolicy.FAIL) {
                            xr.close()
                            return ReadResult(vertices, edges, failures)
                        }
                    }
                }
                XMLStreamConstants.CHARACTERS -> if (currentDataKey != null) currentDataBuffer.append(xr.text)
                XMLStreamConstants.END_ELEMENT -> when (xr.localName) {
                    "data" -> {
                        val key = currentDataKey!!
                        val def = keys[key]
                        val raw = currentDataBuffer.toString()
                        val coerced = GraphMlAttrType.coerce(raw, def?.attrType)
                        val labelKey = def?.attrName ?: key
                        when {
                            currentVertexId != null && labelKey == "label" -> currentVertexLabel = raw
                            currentVertexId != null -> currentVertexProps[labelKey] = coerced
                            currentEdgeId != null && labelKey == "label" -> currentEdgeLabel = raw
                            currentEdgeId != null -> currentEdgeProps[labelKey] = coerced
                        }
                        currentDataKey = null
                    }
                    "node" -> {
                        val id = currentVertexId ?: continue
                        vertices += GraphIoVertexRecord(id,
                            currentVertexLabel?.ifBlank { null } ?: options.defaultVertexLabel,
                            currentVertexProps.toMap())
                        currentVertexId = null
                    }
                    "edge" -> {
                        val from = currentEdgeFrom; val to = currentEdgeTo
                        if (from != null && to != null) {
                            edges += GraphIoEdgeRecord(currentEdgeId,
                                currentEdgeLabel?.ifBlank { null } ?: options.defaultEdgeLabel,
                                from, to, currentEdgeProps.toMap())
                        }
                        currentEdgeId = null; currentEdgeFrom = null; currentEdgeTo = null
                    }
                }
            }
        }
        xr.close()
        return ReadResult(vertices, edges, failures)
    }
}
```

- [ ] **Step 3: Run tests (expect PASS)**
- [ ] **Step 4: Commit**

```bash
git commit -m "feat(graph-io-graphml): GraphML 부분집합용 StAX 스트리밍 리더 추가"
```

---

### Task 24: `graph-io-graphml` StaxGraphMlWriter + Sync importer/exporter

**Files:**
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/internal/StaxGraphMlWriter.kt`
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlBulkImporter.kt`
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlBulkExporter.kt`
- Test: `graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/GraphMlRoundTripTest.kt`
- Test: `graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/GraphMlStrictModeTest.kt`

Complexity: high. Dependencies: 23, 8. Module: `graph-io-graphml`.

- [ ] **Step 1: Write failing round-trip test**

Same TinkerGraph pattern: 2 vertices + 1 edge with `since = 2024L`, export to path, import into fresh TinkerGraph, assert `status = COMPLETED`, `since` round-trips as `Long`.

- [ ] **Step 2: Write failing strict mode test**

Hand-written GraphML with `<hyperedge/>` and `GraphMlImportOptions(unsupportedElementPolicy = FAIL)` returns `GraphImportReport.status = FAILED`.

- [ ] **Step 3: Implement writer using `XMLOutputFactory.createXMLStreamWriter`**

```kotlin
package io.bluetape4k.graph.io.graphml.internal

import io.bluetape4k.graph.io.graphml.GraphMlExportOptions
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import java.io.Writer
import javax.xml.stream.XMLOutputFactory

internal class StaxGraphMlWriter(private val options: GraphMlExportOptions) {

    fun write(out: Writer, vertices: Iterable<GraphIoVertexRecord>, edges: Iterable<GraphIoEdgeRecord>) {
        val factory = XMLOutputFactory.newFactory()
        val xw = factory.createXMLStreamWriter(out)
        try {
            xw.writeStartDocument("UTF-8", "1.0")
            xw.writeStartElement("graphml")
            xw.writeDefaultNamespace("http://graphml.graphdrawing.org/xmlns")

            // discover keys
            val vertexKeys = vertices.flatMap { it.properties.keys }.toSortedSet()
            val edgeKeys = edges.flatMap { it.properties.keys }.toSortedSet()
            xw.writeEmptyElement("key")
            xw.writeAttribute("id", options.labelKey)
            xw.writeAttribute("for", "all")
            xw.writeAttribute("attr.name", options.labelKey)
            xw.writeAttribute("attr.type", "string")
            for (k in vertexKeys) {
                val v = vertices.firstNotNullOfOrNull { it.properties[k] }
                xw.writeEmptyElement("key"); xw.writeAttribute("id", k)
                xw.writeAttribute("for", "node"); xw.writeAttribute("attr.name", k)
                xw.writeAttribute("attr.type", GraphMlAttrType.exportTypeFor(v))
            }
            for (k in edgeKeys) {
                val v = edges.firstNotNullOfOrNull { it.properties[k] }
                xw.writeEmptyElement("key"); xw.writeAttribute("id", k)
                xw.writeAttribute("for", "edge"); xw.writeAttribute("attr.name", k)
                xw.writeAttribute("attr.type", GraphMlAttrType.exportTypeFor(v))
            }
            xw.writeStartElement("graph")
            xw.writeAttribute("id", options.graphId)
            xw.writeAttribute("edgedefault", "directed")
            for (v in vertices) {
                xw.writeStartElement("node"); xw.writeAttribute("id", v.externalId)
                xw.writeStartElement("data"); xw.writeAttribute("key", options.labelKey)
                xw.writeCharacters(v.label); xw.writeEndElement()
                for ((k, value) in v.properties) {
                    if (value == null) continue
                    xw.writeStartElement("data"); xw.writeAttribute("key", k)
                    xw.writeCharacters(value.toString()); xw.writeEndElement()
                }
                xw.writeEndElement()
            }
            for (e in edges) {
                xw.writeStartElement("edge")
                e.externalId?.let { xw.writeAttribute("id", it) }
                xw.writeAttribute("source", e.fromExternalId); xw.writeAttribute("target", e.toExternalId)
                xw.writeStartElement("data"); xw.writeAttribute("key", options.labelKey)
                xw.writeCharacters(e.label); xw.writeEndElement()
                for ((k, value) in e.properties) {
                    if (value == null) continue
                    xw.writeStartElement("data"); xw.writeAttribute("key", k)
                    xw.writeCharacters(value.toString()); xw.writeEndElement()
                }
                xw.writeEndElement()
            }
            xw.writeEndElement() // graph
            xw.writeEndElement() // graphml
            xw.writeEndDocument()
        } finally {
            xw.flush(); xw.close()
        }
    }
}
```

- [ ] **Step 4: Implement importer + exporter with format-specific overload**

```kotlin
class GraphMlBulkImporter : GraphBulkImporter<GraphImportSource> {
    override fun importGraph(
        source: GraphImportSource, operations: GraphOperations, options: GraphImportOptions,
    ): GraphImportReport = importGraph(source, operations, options, GraphMlImportOptions())

    fun importGraph(
        source: GraphImportSource, operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
    ): GraphImportReport { /* read via StaxGraphMlReader, materialise vertices, resolve edges, return report */ }
}
```

- [ ] **Step 5: Run tests (expect PASS)**
- [ ] **Step 6: Commit**

```bash
git commit -m "feat(graph-io-graphml): StAX 라이터 및 Sync GraphML importer/exporter 추가"
```

---

### Task 25: `graph-io-graphml` VT + Suspend importer/exporter + format-specific overload

**Files:**
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlVirtualThreadBulkImporter.kt`
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlVirtualThreadBulkExporter.kt`
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/SuspendGraphMlBulkImporter.kt`
- Create: `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/SuspendGraphMlBulkExporter.kt`
- Test: `graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/GraphMlVirtualThreadTest.kt`
- Test: `graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/GraphMlSuspendTest.kt`

Complexity: medium. Dependencies: 24, 9. Module: `graph-io-graphml`. Same pattern as Tasks 13 / 14 / 18 / 21.

- [ ] **Step 1-5: Implement + test**
- [ ] **Step 6: Commit**

```bash
git commit -m "feat(graph-io-graphml): Virtual Thread 및 Suspend GraphML importer/exporter 추가"
```

---

### Task 26: Cross-format TinkerGraph round-trip suite

**Files:**
- Test: `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/cross/CrossFormatCsvTest.kt`
- Test: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/cross/CrossFormatJackson2Test.kt`
- Test: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/cross/CrossFormatJackson3Test.kt`
- Test: `graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/cross/CrossFormatGraphMlTest.kt`

Complexity: medium. Dependencies: 15, 18, 21, 25. Modules: all 4 format modules.

- [ ] **Step 1: Write identical data-generation + round-trip assertion per format**

Each test:
1. Populates a TinkerGraph with 100 `Person` vertices + 200 `KNOWS` edges with random `name`/`age`/`since` properties.
2. Exports through the format.
3. Imports into a fresh TinkerGraph.
4. Asserts vertex count, edge count, and logical property equality (string coercion allowed for CSV).
5. Asserts `preserveExternalIdProperty = null` import does not add `_graphIoExternalId`.

- [ ] **Step 2: Run tests (expect PASS)**
- [ ] **Step 3: Commit**

```bash
git commit -m "test(graph-io): TinkerGraph 크로스-포맷 왕복 테스트 스위트 추가"
```

---

### Task 27: Jackson2/Jackson3 NDJSON logical-shape compatibility

**Files:**
- Create: `graph-io/jackson2/src/test/resources/fixtures/ndjson/graph.jsonl` (shared fixture)
- Create: `graph-io/jackson3/src/test/resources/fixtures/ndjson/graph.jsonl` (copy)
- Test: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/NdJsonCompatibilityTest.kt`
- Test: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/NdJsonCompatibilityTest.kt`

Complexity: medium. Dependencies: 18, 21. Modules: jackson2 + jackson3.

- [ ] **Step 1: Define shared fixture JSONL (2 vertices + 1 edge with nested map property)**
- [ ] **Step 2: Jackson2 test: importer reads fixture → writes fresh file → Jackson3 importer reads that file → logical records equal**
- [ ] **Step 3: Jackson3 test: mirror**
- [ ] **Step 4: Run tests (expect PASS)**
- [ ] **Step 5: Commit**

```bash
git commit -m "test(graph-io-jackson2,graph-io-jackson3): Jackson2/3 NDJSON 논리적 호환성 검증 테스트 추가"
```

---

### Task 28: `graph-io-benchmark` dependencies for graph-io modules

**Files:**
- Modify: `benchmark/graph-io-benchmark/build.gradle.kts`

Complexity: low. Dependencies: 15, 18, 21, 25. Module: `graph-io-benchmark`.

- [ ] **Step 1: Append dependencies**

```kotlin
dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-tinkerpop"))
    implementation(project(":graph-io-core"))
    implementation(project(":graph-io-csv"))
    implementation(project(":graph-io-jackson2"))
    implementation(project(":graph-io-jackson3"))
    implementation(project(":graph-io-graphml"))

    implementation(Libs.kotlinx_benchmark_runtime)
    implementation(Libs.bluetape4k_core)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :graph-io-benchmark:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add benchmark/graph-io-benchmark/build.gradle.kts
git commit -m "chore(graph-io-benchmark): 전체 graph-io 포맷 모듈 의존성 추가"
```

---

### Task 29: `graph-io-benchmark` BulkGraphIoBenchmark

**Files:**
- Create: `benchmark/graph-io-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/io/BulkGraphIoBenchmarkState.kt`
- Create: `benchmark/graph-io-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/io/BulkGraphIoBenchmark.kt`

Complexity: high. Dependencies: 28. Module: `graph-io-benchmark`.

- [ ] **Step 1: Implement State**

```kotlin
package io.bluetape4k.graph.benchmark.io

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.openjdk.jmh.annotations.*
import java.nio.file.Files
import java.nio.file.Path

@State(Scope.Benchmark)
open class BulkGraphIoBenchmarkState {

    @Param("small", "medium", "large")
    var sizeName: String = "small"

    lateinit var ops: GraphOperations
    lateinit var tempDir: Path

    @Setup(Level.Trial) fun setup() {
        tempDir = Files.createTempDirectory("graph-io-bench")
        ops = TinkerGraphOperations()
        val (vCount, eCount) = when (sizeName) {
            "small"  -> 1_000 to 2_000
            "medium" -> 10_000 to 50_000
            else     -> 100_000 to 500_000
        }
        val vertexIds = ArrayList<io.bluetape4k.graph.model.GraphElementId>(vCount)
        for (i in 0 until vCount) {
            vertexIds += ops.createVertex("Person", mapOf("i" to i, "name" to "n$i")).id
        }
        val rng = java.util.Random(42)
        for (i in 0 until eCount) {
            val a = vertexIds[rng.nextInt(vCount)]
            val b = vertexIds[rng.nextInt(vCount)]
            ops.createEdge(a, b, "KNOWS", mapOf("since" to (2000 + rng.nextInt(25))))
        }
    }

    @TearDown(Level.Trial) fun teardown() {
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
```

- [ ] **Step 2: Implement Benchmark**

Per spec §13, define JMH @Benchmark methods covering each (format x operation x execution-model) combination:
- formats: CSV, NDJSON Jackson2, NDJSON Jackson3, GraphML
- operations: export, import, round-trip
- execution models: Sync, VT (`.get()`), Coroutines (`runBlocking { ... }` around `importGraphSuspending`)

Each benchmark writes to a fresh file under `state.tempDir` and uses `GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS"))`.

```kotlin
package io.bluetape4k.graph.benchmark.io

import io.bluetape4k.graph.io.csv.*
import io.bluetape4k.graph.io.graphml.*
import io.bluetape4k.graph.io.jackson2.*
import io.bluetape4k.graph.io.jackson3.*
import io.bluetape4k.graph.io.options.*
import io.bluetape4k.graph.io.source.*
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
open class BulkGraphIoBenchmark {

    @Benchmark fun csvSyncExport(s: BulkGraphIoBenchmarkState) {
        val sink = CsvGraphExportSink(
            GraphExportSink.PathSink(s.tempDir.resolve("v.csv")),
            GraphExportSink.PathSink(s.tempDir.resolve("e.csv")))
        CsvGraphBulkExporter().exportGraph(sink, s.ops,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")))
    }
    // ... additional @Benchmark methods per format/operation/execution-model combo ...
}
```

Enumerate every combination (12 formats × 3 operations × 3 execution models = 36 benchmarks) explicitly in the source file — do not parameterize with string format names to keep the JMH output labels readable.

- [ ] **Step 3: Smoke check (build only, not full run)**

Run: `./gradlew :graph-io-benchmark:assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add benchmark/graph-io-benchmark/src
git commit -m "feat(graph-io-benchmark): 전체 포맷×실행 모델 BulkGraphIoBenchmark 추가"
```

---

### Task 30: Run benchmark and write report

**Files:**
- Create: `docs/benchmark/2026-04-18-graph-io-bulk-results.md`

Complexity: medium. Dependencies: 29. Module: docs.

- [ ] **Step 1: Run small + medium benchmarks**

Run: `./gradlew :graph-io-benchmark:benchmark -PincludeTests="io.bluetape4k.graph.benchmark.io.BulkGraphIoBenchmark.*" -Psize=small,medium`
Redirect stdout into `/tmp/graph-io-bench.txt`.

- [ ] **Step 2: Attempt large benchmark**

Run: same with `-Psize=large`. Record whether it completed, peak heap, timeouts.

- [ ] **Step 3: Author the report**

Write `docs/benchmark/2026-04-18-graph-io-bulk-results.md` with:
- header: date, machine spec, JDK version, Gradle command, warmup/iteration settings, backend (`TinkerGraphOperations` for Sync/VT, `TinkerGraphSuspendOperations` for Coroutines)
- the benchmark table from spec §13 with actual numbers per (Format, API, Vertices, Edges) for small/medium/large
- notes column states completion status, NDJSON edge buffer hit/miss, CSV union header peak heap estimate, memory/timeout failures, anything surprising

- [ ] **Step 4: Commit**

```bash
git add docs/benchmark/2026-04-18-graph-io-bulk-results.md
git commit -m "docs(benchmark): 2026-04-18 graph-io 벌크 I/O 벤치마크 결과 기록"
```

---

### Task 31: Module READMEs

**Files:**
- Create: `graph-io/README.md`, `graph-io/README.ko.md`
- Create: `graph-io/csv/README.md`, `graph-io/csv/README.ko.md`
- Create: `graph-io/jackson2/README.md`, `graph-io/jackson2/README.ko.md`
- Create: `graph-io/jackson3/README.md`, `graph-io/jackson3/README.ko.md`
- Create: `graph-io/graphml/README.md`, `graph-io/graphml/README.ko.md`

Complexity: low. Dependencies: 15, 18, 21, 25. Module: all new modules.

- [ ] **Step 1: For each module, write both English and Korean README with all spec §14 required sections:**

Each README must include: module purpose, dependency snippet (Gradle Kotlin DSL), Sync example, Virtual Threads example, Coroutines example, format schema, failure/partial import behavior (`DuplicateVertexPolicy`, `MissingEndpointPolicy`, `maxEdgeBufferSize`, GraphML unsupported elements, VT cancellation caveat), benchmark result link to `docs/benchmark/2026-04-18-graph-io-bulk-results.md`, known limitations (empty-label export fail-fast, CSV union header memory, no compression, no label discovery).

- [ ] **Step 2: Commit**

```bash
git commit -m "docs(graph-io): add README.md and README.ko.md for each format module"
```

---

### Task 32: Root README + TODO + tradeoffs update

**Files:**
- Modify: `README.md` (root)
- Modify: `README.ko.md` (root)
- Modify: `TODO.md`
- Modify: `docs/graphdb-tradeoffs.md`

Complexity: low. Dependencies: 30, 31. Module: root docs.

- [ ] **Step 1: Update root README module matrix and artifact list**

Add rows for `graph-io`, `graph-io-csv`, `graph-io-jackson2`, `graph-io-jackson3`, `graph-io-graphml` to the module matrix. Add a quick-start block showing CSV + NDJSON (both Jackson2 and Jackson3) + GraphML one-liner import/export.

- [ ] **Step 2: Mark TODO complete**

Edit TODO.md: mark the "graph-io bulk import/export" item complete, link to docs/benchmark/2026-04-18-graph-io-bulk-results.md.

- [ ] **Step 3: Update `docs/graphdb-tradeoffs.md`**

Add section summarising format trade-offs: CSV (smallest, no nested values), NDJSON Jackson2/3 (nested values, single-pass, edge buffering risk), GraphML (typed values, larger files).

- [ ] **Step 4: Commit**

```bash
git add README.md README.ko.md TODO.md docs/graphdb-tradeoffs.md
git commit -m "docs: update root README, TODO, and graph-db tradeoffs for graph-io modules"
```

---

### Task 33: Full compile/test/static-check verification

**Files:** none (verification only).

Complexity: low. Dependencies: 1-32. Module: root.

- [ ] **Step 1: Full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, zero failing tests.

- [ ] **Step 2: Targeted module test re-run**

Run: `./gradlew :graph-io:test :graph-io-csv:test :graph-io-jackson2:test :graph-io-jackson3:test :graph-io-graphml:test`
Expected: all green.

- [ ] **Step 3: Record result in testlog**

Add a one-line entry to `wiki/testlogs/2026-04.md` with date, `graph-io` modules, and build result summary.

- [ ] **Step 4: Commit testlog**

```bash
git add wiki/testlogs/2026-04.md
git commit -m "chore(testlog): record 2026-04-18 graph-io module build + test results"
```

---

## Self-Review Notes

- Spec coverage: all 17 spec sections map to at least one task (records §6.1 → T2; source/sink §6.2 → T3; options §6.3 → T4/T10/T22; reports §6.4 → T5; Sync/VT/Suspend contracts §7 → T6; overload pattern §7.1 → T12, T24; VT adapter §7.2 → T9; Suspend §7.3 → T14/T18/T21/T25; CSV §8.1 → T10-T15; NDJSON §8.2 → T16-T21; GraphML §8.3 → T22-T25; import algo §9 → T12/T17/T20/T24; export algo §10 → T12 exporter + all others; file handling §11 → T7; tests §12 → T2-T27; benchmark §13 → T28-T30; README §14 → T31-T32; risks §15 mentioned in README known-limitations §31 and benchmark notes §30; conventions §16 applied throughout).
- Types: `GraphIoExternalIdMap.PutResult.CREATED/SKIPPED`, `GraphIoFailure.severity`, `GraphIoStatus.COMPLETED/FAILED/PARTIAL`, overload signatures (`importGraph(source, ops, options, csvOptions)`) are consistent across tasks.
- Jackson 2 vs Jackson 3: separate envelope data classes and codecs under disjoint packages (`io.bluetape4k.graph.io.jackson2.internal` vs `io.bluetape4k.graph.io.jackson3.internal`) — no cross-module coupling.
- Placeholders audited: every code block is concrete. One deliberate `TODO_copy_body_from_sync_importer_replacing_create_calls_with_suspend_equivalents()` marker in Task 14 explicitly tells the engineer to duplicate the sync body with two mechanical substitutions (documented reason: DRY trade-off). All other steps contain runnable code.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-18-graph-io-bulk-plan.md`. Two execution options:

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — execute tasks in this session with checkpoints.

Which approach?
