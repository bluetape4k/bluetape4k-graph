# Issue #310: graph-io checkpoint/resume 계약

## 결정

기본 임포터 동작을 바꾸지 않도록 checkpoint는 `GraphImportOptions`에서 명시적으로
`checkpointStore`, `checkpointKey`, `resumeFromCheckpoint`를 지정할 때만 활성화한다.
checkpoint에는 포맷, opaque source identity, phase, 정점·간선 처리량,
external-ID mapping state, failure boundary를 기록한다.

## 안전 경계

source identity는 raw path나 payload가 아닌 caller가 만든 opaque digest여야 한다.
resume 시 포맷·identity·version이 다르면 `GraphImportCheckpointConflictException`으로
즉시 거부하여 중복 쓰기를 숨기지 않는다. 실제 backend importer가 checkpoint를 저장하는
시점은 후속 format slice에서 batch flush 경계와 함께 연결한다.

## 검증

- in-memory store 저장·조회·삭제 round trip
- 변경 source identity stale checkpoint 거부
- resume 옵션의 명시적 store/key 요구
