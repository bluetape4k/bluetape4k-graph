# Snapshot Version parameterization

맥락: Central Portal releases should not require editing `gradle.properties`
only to remove `-SNAPSHOT`.

결정: Keep `snapshotVersion=` empty by default and let
`publish-snapshot.yml` pass `-PsnapshotVersion=-SNAPSHOT`.

결과: `develop` stays release-ready, while snapshot publishing remains
explicit in the workflow command.

검증: `actionlint .github/workflows/publish-snapshot.yml`.

향후 가드: Do not reintroduce `snapshotVersion=-SNAPSHOT` as the default in
`gradle.properties`.
