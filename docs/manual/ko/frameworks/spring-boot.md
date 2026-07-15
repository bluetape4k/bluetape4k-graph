# Spring Boot 연동

`GraphAutoConfiguration`은 `GraphProperties`를 연결하고 백엔드별 설정 순서를 잡는다. 이 클래스가 graph bean을 직접 만들지는 않는다. 백엔드 설정은 별도로 등록되며 classpath, property, 기존 bean 조건에 따라 켜진다. 기준 소스는 [`GraphAutoConfiguration.kt`](../../../../spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphAutoConfiguration.kt)다.

생태계 BOM과 버전 없는 `bluetape4k-graph-spring-boot` 좌표를 쓴다. 의도한 백엔드 하나를 설정하고, bean이 없거나 여러 개면 condition report를 먼저 확인한다. 백엔드별 예는 [`GraphNeo4jAutoConfiguration.kt`](../../../../spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfiguration.kt), [`GraphAgeAutoConfiguration.kt`](../../../../spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphAgeAutoConfiguration.kt)에 있다.

Spring이 만든 bean은 컨테이너가 관리한다. 외부에서 주입한 자원은 선언된 소유권을 유지한다. property binding, 사용자 bean이 있을 때의 backoff, 백엔드 선택, 종료는 [`GraphNeo4jAutoConfigurationTest.kt`](../../../../spring-boot/graph-spring-boot/src/test/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfigurationTest.kt) 같은 집중 테스트로 확인한다.

condition 평가, 선택된 백엔드, pool 상태, 종료 순서를 관찰한다. context가 뜬다는 사실만으로 운영 서버 연결까지 검증되지는 않는다.
