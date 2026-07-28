# Snapshot 검증선

## 맥락

After the previous release, snapshot validation needed the repository reopened
on the next development line while consuming the matching upstream bluetape4k
snapshot.

## 결정

Set `baseVersion=0.4.2`, keep `snapshotVersion=` empty, and consume
`bluetape4k-bom:1.9.2-SNAPSHOT`.

## 결과

The repository can publish `0.4.2-SNAPSHOT` through `publish-snapshot.yml`
without checking a snapshot suffix into `gradle.properties`.

## 검증

Pending in the snapshot validation train.
