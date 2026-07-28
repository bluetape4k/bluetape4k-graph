# 2026-06-01 Open 0.6.0 Development

## 맥락

`bluetape4k-graph` `0.5.0` was published and included in
`bluetape4k-dependencies` `1.2.0`.

## 결정

Move the committed `baseVersion` to `0.6.0` while keeping `snapshotVersion=`
empty so release workflows can inject snapshot qualifiers explicitly.
Align the direct `bluetape4k-bom` catalog reference to
`1.11.0-SNAPSHOT`.

## 결과

The repository is ready for the next minor development line.

## 검증

- `gradle.properties` uses `baseVersion=0.6.0`.
- `snapshotVersion=` remains empty.
- `./gradlew help --no-daemon --console=plain` resolves the updated catalog.
