# benchmark로 선택 근거 만들기

benchmark는 제한된 작업 부하에 답할 뿐 데이터베이스 전체 순위를 만들지 않는다. Graph 0.5.1에는 공통 graph 연산, graph-io, AGE, Neo4j를 다루는 네 모듈이 있다. [`benchmark/README.md`](../../../../benchmark/README.md)에서 시작해 각 모듈의 작업과 환경을 확인한다.

비교 전에 JVM, CPU·메모리, OS·컨테이너, 서버 이미지·설정, 자료 모양, warmup·측정 횟수, 동시성, driver pool, 트랜잭션 크기, 인덱스, graph 초기화를 고정한다. 같은 의미의 연산인지 확인하고 결과 정확성도 검증한다.

공통 구현 작업은 [`graph-benchmark`](../../../../benchmark/graph-benchmark/README.md), codec과 전송 선택은 [`graph-io-benchmark`](../../../../benchmark/graph-io-benchmark/README.md), AGE와 Neo4j는 해당 백엔드 모듈에서 본다. 서로 다른 환경에서 얻은 수치를 한 표의 순위처럼 비교하지 않는다.

먼저 필요한 의미론과 운영 조건으로 후보를 줄인다. 남은 후보를 운영과 비슷한 자료로 측정하고 지연 분포, 처리량, 할당량, 서버 CPU·메모리, 실행 계획, 재시도, 실패를 함께 본다. 평균이 빨라도 필요한 트랜잭션이나 스키마 의미를 잃으면 대안이 아니다.
