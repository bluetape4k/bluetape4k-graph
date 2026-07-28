# 이슈 #49 graph-okio DAEAD Encryption

- 맥락: Issue #49 needed encrypted graph I/O for `graph-okio`; `bluetape4k-okio` already exposes deterministic DAEAD chunk source/sink primitives in the current `1.8.0-SNAPSHOT` dependency line.
- 결정: Reuse the existing DAEAD chunk format instead of introducing Tink Streaming AEAD. Keep high-level encrypted helpers limited to single-stream formats because CSV is a paired-file format.
- 결과: `graph-okio` now exposes DAEAD and GZip+DAEAD helpers for path/source/sink chaining plus synchronous bulk import/export convenience methods.
- 검증: `./gradlew :graph-okio:compileKotlin :graph-okio:compileTestKotlin --no-daemon`, `./gradlew :graph-okio:test --no-daemon`, targeted DAEAD round-trip tests, `git diff --check`, and pattern greps for banned assertion/concurrency/KDoc language regressions.
- Future rule: For encrypted OkIO work, validate the resolved dependency version with `dependencyInsight` and test both raw chunk helpers and end-to-end bulk graph round trips on `FakeFileSystem`.
