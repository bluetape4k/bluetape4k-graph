# 이슈 #133 FalkorDB Ktor README Discoverability

## 맥락

The root README example module table listed the TinkerGraph Ktor smoke test but did not list the FalkorDB-backed
Ktor example added for this release.

## 결정

Add `FalkorDBKtorGraphAppTest` as its own row in both `README.md` and `README.ko.md` so English and Korean readers can
discover the FalkorDB Ktor `GraphPlugin` smoke example from the same table.

## 결과

The example module table now lists both the TinkerGraph and FalkorDB Ktor smoke examples.

## 검증

- `rg -n "FalkorDBKtorGraphAppTest|KtorGraphAppTest" README.md README.ko.md examples/ktor-graph-examples` confirms the README rows match real test classes.
- `git diff --check` passed.

## 향후 가드

When adding an example class that demonstrates a supported backend integration, update both root README locales in the
same branch and grep for the real class name before committing.
