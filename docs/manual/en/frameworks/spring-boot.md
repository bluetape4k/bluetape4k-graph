# Spring Boot integration

`GraphAutoConfiguration` binds `GraphProperties` and orders backend-specific configurations; it does not create a graph bean by itself. Backend configurations are registered separately and activate from classpath, properties, and missing-bean conditions. Root source: [`GraphAutoConfiguration.kt`](../../../../spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphAutoConfiguration.kt).

Use the ecosystem BOM and an unversioned `bluetape4k-graph-spring-boot` coordinate. Configure exactly one intended backend, then inspect the condition report if beans are absent or ambiguous. Backend examples include [`GraphNeo4jAutoConfiguration.kt`](../../../../spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfiguration.kt) and [`GraphAgeAutoConfiguration.kt`](../../../../spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphAgeAutoConfiguration.kt).

The Spring container owns beans it creates; injected caller-owned resources keep their declared ownership. Verify property binding, backoff when user beans exist, backend selection, and shutdown with focused tests such as [`GraphNeo4jAutoConfigurationTest.kt`](../../../../spring-boot/graph-spring-boot/src/test/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfigurationTest.kt).

Observe condition evaluation, selected backend, pool health, and shutdown ordering. A green context-start test is necessary but does not prove connectivity to the production server.
