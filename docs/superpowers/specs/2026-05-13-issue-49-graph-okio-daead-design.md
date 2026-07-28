# 이슈 #49 Graph OkIO DAEAD chunk encryption 설계

- 이슈: #49
- Date: 2026-05-13
- 범위: `graph-okio`
- Status: Step 2-R reviewed

## 1. 맥락

`graph-okio` currently supports OkIO source/sink abstraction, streaming compression, and atomic path writes. The original OkIO
spec deferred encryption because `bluetape4k-okio` only had `TinkEncryptSink` / `TinkDecryptSource`, and that path was not safe
for large graph files.

That prerequisite has changed. The current `bluetape4k-okio:1.8.0-SNAPSHOT` jar contains:

- `io.bluetape4k.okio.tink.DaeadChunkEncryptSink`
- `io.bluetape4k.okio.tink.DaeadChunkDecryptSource`
- `Sink.asDaeadChunkEncryptSink(...)`
- `Source.asDaeadChunkDecryptSource(...)`

The matching `bluetape4k-tink:1.8.0-SNAPSHOT` jar contains:

- `io.bluetape4k.tink.daead.TinkDeterministicAead`
- `io.bluetape4k.tink.daead.TinkDaeads`

Google Tink documentation states that Deterministic AEAD returns stable ciphertext for the same plaintext and associated data.
That property is useful for some workloads, but it leaks repeated plaintext chunks. `graph-okio` must document that trade-off
instead of presenting DAEAD chunk encryption as general-purpose randomized streaming encryption.

## 2. 결정

Implement #49 using the existing bluetape4k-okio DAEAD chunk format, not Tink `StreamingAead`.

The issue text mentions `StreamingAead`, but the concrete bluetape4k prerequisite available today is DAEAD chunk encryption.
Adopting it keeps the feature local, testable with `FakeFileSystem`, and aligned with the shared okio module. Tink
`StreamingAead` can remain a future issue if a randomized streaming encryption API is needed.

## 3. Public API

Add low-level path helpers to `GraphIoOkioPaths`:

```kotlin
fun openDaeadEncryptedSink(
    sink: BufferedSink,
    daead: TinkDeterministicAead,
    chunkSize: Int = DEFAULT_DAEAD_CHUNK_SIZE,
    associatedData: ByteArray = ByteArray(0),
): BufferedSink

fun openDaeadDecryptedSource(
    source: BufferedSource,
    daead: TinkDeterministicAead,
    associatedData: ByteArray = ByteArray(0),
    maxCiphertextLength: Long = DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH,
): BufferedSource
```

Add convenience helpers:

```kotlin
fun openDaeadEncryptedSink(sink: OkioGraphExportSink, ...)
fun openDaeadDecryptedSource(source: OkioGraphImportSource, ...)
fun openGzipDaeadEncryptedSink(sink: OkioGraphExportSink, ...)
fun openDaeadDecryptedGzipSource(source: OkioGraphImportSource, ...)
```

The compression/encryption order is:

- export: `graph bytes -> gzip -> DAEAD chunk encrypt -> target`
- import: `source -> DAEAD chunk decrypt -> gzip inflate -> graph bytes`

This is compress-then-encrypt. The reverse order is not supported because encrypted output is not meaningfully compressible.

Add high-level export/import helpers for single-stream formats:

```kotlin
fun OkioGraphBulkExporter.exportGraphDaead(...)
fun OkioGraphBulkImporter.importGraphDaead(...)
fun OkioGraphBulkExporter.exportGraphGzipDaead(...)
fun OkioGraphBulkImporter.importGraphDaeadGzip(...)
```

CSV is a two-file format. For this PR, CSV DAEAD convenience helpers are not added to avoid designing paired encrypted
file naming in the same slice. Callers can still use the low-level path helpers directly for custom CSV file pairs.

## 4. Dependency Contract

`graph-okio` will expose `TinkDeterministicAead` in public signatures. 따라서 `gradle/libs.versions.toml` needs a
`bluetape4k-tink` alias, and `graph-io/okio/build.gradle.kts` should declare:

```kotlin
api(libs.bluetape4k.tink)
```

Even if `bluetape4k-okio` already brings `bluetape4k-tink` transitively, this explicit API dependency prevents a public
signature from depending on an incidental transitive edge.

## 5. Security Notes

- DAEAD chunk encryption is deterministic. Repeated plaintext chunks with the same key and associated data produce repeated
  ciphertext chunks.
- Associated data is authenticated but not encrypted.
- `maxCiphertextLength` must remain exposed on decrypt helpers to bound per-chunk allocation for untrusted input.
- This feature protects file contents at rest. It does not provide key management, key rotation, path sanitization, or audit
  logging.
- Wrong key, wrong associated data, truncated chunks, or corrupted ciphertext must fail loudly.

## 6. Test Strategy

Use fast `graph-okio` tests only:

- Low-level DAEAD round trip with `Buffer`.
- Low-level gzip + DAEAD round trip with `FakeFileSystem`.
- High-level Jackson3 NDJSON export/import DAEAD round trip through `TinkerGraphOperations`.
- High-level Jackson3 NDJSON export/import gzip+DAEAD round trip.
- Negative tests for wrong associated data and truncated ciphertext.
- Compile/test `:graph-okio`.

Container-backed backend tests are not needed. This feature is graph-io path plumbing and can be verified with TinkerGraph.

## 7. Documentation

Update `graph-io/okio/README.md` and `README.ko.md`:

- Add DAEAD chunk encryption examples.
- Explain compress-then-encrypt order.
- Document deterministic encryption leakage.
- Mention that CSV two-file encrypted convenience helpers are intentionally out of scope.

New or touched public KDoc must be English.

## 8. 범위 제외

- Tink `StreamingAead`.
- CSV paired encrypted file convenience naming.
- Spring Boot auto-configuration.
- Keyset loading or key management helpers.
- Benchmarking encryption throughput.

## 9. 리뷰 메모

Step 2-R review must verify:

- Public API surface is small enough for #49.
- Deterministic encryption limitations are explicit.
- Dependency exposure is correct.
- Compression/encryption order is correct and testable.
- No mock-only validation is used.

### Step 2-R 리뷰 결과

Claude Code Opus advisor:

- Artifact: `.omx/artifacts/claude-issue-49-spec-plan-20260513-093037.md`
- Result: unavailable. The CLI produced no output for more than two minutes and was terminated.

Codex review findings:

| 우선순위 | 발견 사항 | 결정 |
|---|---|---|
| P1 | Public signatures expose `TinkDeterministicAead`; relying only on transitive `bluetape4k-okio` API dependency would be brittle. | Accepted. Spec requires explicit `api(libs.bluetape4k.tink)`. |
| P1 | DAEAD is deterministic and can leak repeated chunks. | Accepted. Spec and docs tasks require explicit warning. |
| P2 | CSV encrypted file-pair convenience helpers could expand naming/API scope. | Accepted. Deferred from this PR; low-level helpers remain available. |
| P2 | Tests need a wrong associated-data/corruption path, not only happy-path round trips. | Accepted. Test strategy includes both. |

Gate result: P0 = 0, P1 = 0.
