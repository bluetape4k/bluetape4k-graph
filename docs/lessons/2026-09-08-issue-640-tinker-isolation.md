# Snapshot rollback과 외부 mutation을 함께 보호한다

관련 이슈: #640, 상위 #641.

## 원인

transaction끼리만 semaphore를 공유하여, suspend transaction이 대기하는 동안 외부 CRUD가 성공한 뒤 rollback snapshot 복원으로 사라질 수 있었다. transaction gate만으로 전체 쓰기 격리를 증명할 수 없었다.

## 결정

mutation과 snapshot 진입·복원은 같은 ReentrantLock으로 보호한다. 활성 transaction token을 AtomicFU private property에 저장하고, 동기 ThreadLocal과 coroutine context element로 소유권을 전달한다. 외부 CRUD·graph lifecycle·schema 변경은 활성 transaction 동안 명시적인 IllegalStateException으로 거부한다. transaction끼리는 기존 gate로 직렬화하고 같은 context의 중첩 transaction은 기존 범위를 사용한다. 이는 완전한 읽기 격리를 제공하는 변경이 아니다.

## 검증과 시행착오

기존 동작에서 외부 create가 성공한 뒤 rollback 후 사라지는 RED를 확인했다. 회귀 테스트는 명시적인 거부와 rollback 후 정상 쓰기, schema 변경, 취소 후 재사용, 중첩 transaction을 검증한다. TinkerPop 전체 126개 테스트와 detekt가 통과했다. 위임 응답 지연으로 리더가 코드를 회수한 후 반환 타입 노출, inline lambda 전달 및 Flow assertion의 컴파일 오류와 긴 줄 진단을 수정했다. 컴파일 실패는 행동 회귀 증거로 사용하지 않았다.

## 재발 방지

snapshot rollback을 사용하는 adapter는 모든 public mutation 경로가 동일한 소유권 검사에 들어가는지 목록으로 대조한다. bool context 대신 transaction별 token을 사용하여 오래된 context가 다음 transaction의 권한을 얻지 않게 한다. 취소와 예외 이후 새 작업이 진행되는지도 별도로 검증한다.

독립 리뷰의 snapshot dispatcher와 외부 mutation 검증 공백을 반영했다. snapshot만 withContext로 감싸면 반환 시 취소가 active 상태를 남길 수 있어 snapshot·실행·finally 전체를 같은 IO 경계 안에 뒀다. sync/suspend별 13개 mutation과 종료 후 쓰기를 고정한 후 126개 테스트와 detekt를 재검증했다.
