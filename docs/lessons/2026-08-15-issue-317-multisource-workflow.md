# Issue #317: multi-source graph import workflow

## 결정

여러 vertex/edge/schema source와 format·compression·encryption metadata를
manifest로 표현하고, `DISCOVERED → VALIDATED → VERTICES_LOADED → EDGES_LOADED →
VERIFIED → COMPLETED` 상태를 durable job state store에 기록하는 backend-neutral
workflow 계약을 추가했다. 실제 포맷 importer의 batch write와 checkpoint/resume은
#310 계약을 재사용한다.

## 안전 경계

source identity는 opaque 값으로만 기록하며 raw path와 payload를 job report에 넣지
않는다. edge source는 vertex source 뒤에만 전이할 수 있고, invalid dependency는
validation 단계에서 거부한다. scheduler나 backend-native bulk loader는 범위 밖이다.
