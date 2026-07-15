# 운영

실제 경계에 맞춰 다음 지표를 잡는다.

- Driver/DataSource pool 사용률, 획득 시간, 실패 수
- 연산별 질의 지연과 오류율, 백엔드 오류 코드
- 트랜잭션 commit, rollback, 재시도, timeout, 취소 수
- batch/import 처리량, 부분 처리 수, 대기 간선, 거부 레코드
- 스키마·인덱스 목록과 실행 계획 변화
- Ktor/Spring 시작·종료 때의 자원 소유권 사건

`GraphSession.close()`는 외부에서 주입한 자원의 소유권을 가져가지 않는다. [`GraphSession.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSession.kt)에 이 경계가 적혀 있고, 프레임워크 페이지는 컨테이너가 close action을 등록하는 때를 설명한다.

장애가 나면 비밀값을 지운 질의·인자, 백엔드 오류, 트랜잭션 상태, graph-io report, 서버/컨테이너 버전, 취소 신호를 남긴다. 원자성이 확인되지 않은 batch를 다시 실행하기 전에는 실제 저장 개수를 확인한다. merge도 선택한 백엔드에서 키 의미를 검증한 뒤 재시도 수단으로 쓴다.

백업과 복구는 백엔드 기능이다. 파일 작업 완료만 믿지 말고 복구한 스키마, 개수, 대표 경로, 외부 ID 연결을 애플리케이션 수준에서 확인한다.
