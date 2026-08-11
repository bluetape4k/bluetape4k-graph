# #311 graph-io 진행 리스너와 Micrometer bridge 설계

## 목표

graph-io의 CSV, Jackson 2/3 NDJSON, GraphML, Okio 경로에서 동일한 진행 관찰
계약을 제공한다. 기존 3-인자 동기/suspend/Virtual Thread 호출은 그대로
동작해야 하며, Micrometer는 core에 침투하지 않는 선택 모듈로 제공한다.

## 범위와 제외

포함 범위:

- 동기, `suspend`, Virtual Thread 벌크 import/export 계약의 listener 오버로드
- `STARTED`, `PROGRESS`, `COMPLETED`, `FAILED` 이벤트 순서와 listener 실패 격리
- 포맷별 aggregate 정점·간선·skip·failure·bytes·elapsed 관찰
- 단일 public entrypoint의 `bytes/file` 기준은 논리 bytes와 확인 가능한
  source/sink file 메타데이터다. 여러 파일을 합산하는 file-count metric은
  #311에서 제외하며 경로·이름은 event/tag/log에 노출하지 않는다.
- `bluetape4k-graph-io-micrometer` 선택 모듈과 고정 cardinality meter
- Micrometer가 있는 경우에만 활성화되는 Spring Boot auto-configuration
- 영어/한국어 graph-io 및 Spring Boot README 사용 예제

제외 범위:

- checkpoint/resume (#310)
- backend-native loader SPI (#312)
- backend/Testcontainers 구현
- 경로명, 원시 label, 예외 메시지의 metric tag 사용

## 설계 대안

### A. 옵션 객체에 callback을 추가

`GraphImportOptions`와 `GraphExportOptions`에 callback을 보관한다. 호출부는
간단하지만 공개 `Serializable` 옵션의 상태·복제·직렬화 의미가 관찰 기능에
묶이고, 기존 옵션을 저장하거나 재사용하는 코드에 예기치 않은 참조가 들어간다.
채택하지 않는다.

### B. importer/exporter decorator만 제공

기존 구현을 감싼 decorator가 시작/종료를 관찰한다. 구현 변경량은 작지만
포맷 내부 phase와 실패 시점을 잃고, 직접 포맷 구현을 호출하는 코드 및
Spring Boot bean wiring과 일관된 계약을 만들기 어렵다. 채택하지 않는다.

### C. 공통 계약 listener 오버로드 (채택)

각 공통 계약에 listener 오버로드를 추가하고, 기존 3-인자 메서드는 유지한다.
기존 구현체는 기본 오버로드를 통해 기존 3-인자 경로로 안전하게 위임한다.
이 레거시 fallback은 listener null을 검증하지만 포맷/operation 메타데이터를
알 수 없으므로 이벤트를 합성하지 않는다. 내장 포맷 구현은 알려진 phase 경계에서
누적 `PROGRESS`를 발행한다. 동기, suspend,
Virtual Thread adapter는 같은 reporter를 사용하여 이벤트 의미론을 공유한다.

이 방식은 기존 source/ABI 호출을 깨지 않으며 listener를 사용하지 않는 경로에는
reporter를 만들지 않아 추가 관찰 비용이 없다.

## 공개 API

core에 다음 타입을 추가한다. `runId`는 프로세스 내부에서만 유일한 opaque
sequence이며 metric tag로 사용하지 않는다. 사용자가 직접 event를 만들 때는
`0`을 사용할 수 있고, core reporter가 발행하는 event는 양수 sequence를
사용한다.

```kotlin
enum class GraphIoOperation { IMPORT, EXPORT }

enum class GraphIoProgressEventType {
    STARTED,
    PROGRESS,
    PHASE_COMPLETED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class GraphIoProgressEvent(
    val runId: Long,
    val hasStarted: Boolean = true,
    val type: GraphIoProgressEventType,
    val operation: GraphIoOperation,
    val format: GraphIoFormat,
    val phase: GraphIoPhase? = null,
    val status: GraphIoStatus? = null,
    val vertices: Long = 0,
    val successfulVertices: Long = 0,
    val edges: Long = 0,
    val successfulEdges: Long = 0,
    val skippedVertices: Long = 0,
    val skippedEdges: Long = 0,
    val failures: Long = 0,
    val bytesProcessed: Long? = null,
    val bytesTotal: Long? = null,
    val elapsed: Duration = Duration.ZERO,
    val phaseElapsed: Duration? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

fun interface GraphIoProgressListener {
    fun onEvent(event: GraphIoProgressEvent)

    companion object {
        val NOOP: GraphIoProgressListener = GraphIoProgressListener { }
    }
}
```

`vertices`와 `edges`는 import에서는 읽은 수, export에서는 처리 대상으로
관찰한 수이며 `successfulVertices`/`successfulEdges`는 import의 생성 수 또는
export의 기록 수다. 모든 수와 바이트는 누적 non-negative snapshot이고
성공 수는 관찰 수를 초과할 수 없다. `skipped*`와 `failures`도 누적 수다.
`failures`는 report의 `failures.size`와 동일하게 warning을 포함한 전체 항목
수로 정의한다. `bytesProcessed`는 논리 stream에서 실제 처리한 누적 바이트,
`bytesTotal`은 확인 가능한 입력/출력 전체 크기다. 두 값이 모두 있으면
processed가 total을 초과하지 않는다. path가 없거나 압축/암호화 wrapper가
논리 바이트를 제공하지 않는 stream에서는 둘 다 `null`일 수 있다. 일반 경로
기반 adapter는 terminal 시점에 확인한 파일 크기를 `bytesTotal`로 사용하고,
성공 또는 partial 결과에서만 같은 값을 `bytesProcessed`로 확정한다. 실패
결과에서는 부분 파일을 성공 처리량으로 오인하지 않도록 `bytesProcessed`를
`null`로 유지한다. CSV paired-file은 정점/간선 파일 크기를 overflow 없이
합산하며, 어느 한 파일을 확인할 수 없으면 `null`이다.

`phase == null`은 전체 작업 lifecycle event를 뜻한다. phase 경계를 알 수
있는 구간은 `PROGRESS`와 `PHASE_COMPLETED`에 기존 `GraphIoPhase`를 넣고,
`phaseElapsed`는 해당 phase의 누적 경과 시간으로 채운다. `COMPLETED`와
`FAILED` terminal event의 `status`는 non-null이며, `CANCELLED`에서는 null이다.
`STARTED`/`PROGRESS`/`PHASE_COMPLETED`에서도 status는 null이다. `STARTED`는 모든 count와 elapsed가 0이며, `PHASE_COMPLETED`는
phase와 phaseElapsed가 필수다. event 생성자는 runId/count/bytes/duration의
non-negative 및 성공 수·skip 수·bytes 관계를 검증한다.
`successfulVertices + skippedVertices <= vertices` 및 edge 대응 관계도
생성자와 bridge에서 검증한다. 모든 공개 data class에는
`Serializable`과 실제 JVM `serialVersionUID = 1L`을 둔다.
`hasStarted == false`는 pre-start `CANCELLED`에만 허용하고, 그 외 event는
항상 `true`다.

포맷 구현이 반환하는 기존 report에는 phase별 stopwatch가 없으므로, 포맷
adapter가 report만 전달하는 compatibility 경로에서는 `PHASE_COMPLETED`의
`phaseElapsed`에 report의 전체 `elapsed`를 bounded fallback으로 사용한다.
이 값은 phase 간 상대 비교가 아니라 해당 phase가 관찰되었음을 나타내는
aggregate 관측치이며, Micrometer phase timer에도 한 번씩만 기록한다.
정밀한 phase stopwatch가 필요한 포맷은 후속 변경에서 reader/writer 경계를
직접 reporter에 연결해야 한다. 경로/stream의 논리 bytes를 확인할 수 없는
동일 compatibility 경로에서는 bytes를 `null`로 유지한다.

공통 계약에는 기존 함수와 별도로 다음 overload를 추가한다.

```kotlin
fun importGraph(
    source: S,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): GraphImportReport
```

export, suspend, Virtual Thread에도 같은 마지막 인자 규칙을 적용한다. listener는
의도적으로 required라서 기존 `importGraph(source, operations)`와
`importGraph(source, operations, options)` 호출이 overload ambiguity 없이
그대로 유지된다. listener를 쓰지 않는 호출은 기존 3-인자 함수를 사용한다.
기본 구현은 기존 함수 호출로 위임하므로 외부 구현체가 기존 3-인자 함수만
구현해도 새 계약을 깨지 않는다. 이 경우 listener는 호환성 확인만 수행되고
이벤트는 발행되지 않는 것이 명시된 동작이다. 포맷별 추가
옵션 overload는 listener를 마지막에 받아 동일 reporter에 전달한다.

## Reporter 소유권과 상태 머신

각 public entrypoint가 `GraphIoProgressReporter` 하나를 생성하는 유일한
lifecycle owner다. reporter는 `AtomicReference<NEW|STARTED|TERMINAL>`로
`STARTED`와 terminal(`COMPLETED`, `FAILED`, `CANCELLED`)을 CAS하여 정확히
한 번만 발행하고, cumulative snapshot의 monotonic invariant를 검사한다.
내부 포맷 delegate와 Okio DAEAD/gzip dispatch에는 listener를 다시 전달하지
않고 동일 reporter/context를 전달하여 중복 lifecycle을 만들지 않는다. suspend
재개와 Virtual Thread 경계를 넘어도 reporter 객체 하나를 공유한다. re-entrant
listener callback과 concurrent run은 서로 다른 runId를 사용하며, reporter의
상태는 run별로 격리된다.

한 run 내부에서는 reporter가 callback을 직렬 호출하여 event 순서를 보장하고,
서로 다른 run은 같은 listener에서 동시에 callback될 수 있다. listener는
thread-safe해야 하며, re-entrant callback은 새 run으로 취급한다. progress
빈도는 phase당 최소 1회의 `PHASE_COMPLETED`와 batch boundary snapshot으로
제한하고, 레코드마다 event를 만들지 않는다.

Virtual Thread 계약은 worker `Future`와 public `CompletableFuture`를 연결한
cancellable wrapper를 사용한다. `cancel(false)`는 시작된 worker를 interrupt하지
않는 상태 전용 취소이고, `cancel(true)`는 reporter를 먼저 `CANCELLED`로
CAS한 뒤 worker future에 interrupt를 요청한다. 두 인자 모두 시작 gate와
취소 race의 승자는 하나뿐이며, late completion은 terminal event를 재발행하지
않는다. `CompletableFuture.cancel(...)` 자체가 worker interrupt/종료를
보장하지 않는 JDK 의미론을 우회하기 위해 public future와 worker future를
분리한다.

observer dispatch와 resource cleanup은 분리한다. 모든 entrypoint는
`try/catch/finally`에서 source/sink close와 worker interrupt를 먼저
idempotent하게 완료한 뒤 reporter terminal CAS/dispatch를 수행한다. close
failure는 primary 작업 예외에 suppressed로 붙이고, primary가 없으면 terminal
`FAILED` snapshot으로 기록한다. listener callback이 `Exception`/`Error`를
던져도 이 `finally`를 건너뛰지 않는다. cancel 요청은 callback보다 먼저
interrupt/cleanup signal을 보내며, active gauge 감소는 terminal event를
받은 bridge가 `hasStarted`에 따라 처리한다. composite는 한 delegate의
`Error`가 있어도 나머지 delegate를 계속 호출한 뒤 첫 Error를 다시 던져
bridge metric과 cleanup이 listener 순서에 의해 유실되지 않게 한다.

동기 실행 중 반환된 report는 `COMPLETED` event(type은 report status가
`PARTIAL`/`FAILED`여도 `COMPLETED`, status는 report status)를 발행한다. 예외가
호출자에게 전파되면 `FAILED` event, `CancellationException`·중단 취소이면
`CANCELLED` event(status는 null)를 발행하고 원래 취소/예외를 다시 던진다.
기존 `GraphIoStatus` enum은 source compatibility를 위해 확장하지 않는다.
실행 전 future가 취소된 경우에는 active gauge를 증가시키지 않고
`hasStarted = false`인 `CANCELLED`를 한 번 발행한다.
bridge는 `hasStarted == true`인 terminal에만 active를 감소시킨다.

## 이벤트와 실패 의미론

- 정상 실행: `STARTED` 1회, phase를 아는 구간의 `PROGRESS`/
  `PHASE_COMPLETED`, terminal `COMPLETED` 1회
- I/O/그래프 작업 예외: `STARTED` 이후 가능한 마지막 `PROGRESS`와
  `FAILED` 1회 후 원래 예외를 호출자에게 전파한다.
- suspend cancellation, future cancellation, interrupt는 `CANCELLED` 1회
  후 원래 cancellation/interrupt flag를 보존한다.
- listener의 `onEvent` 일반 `Exception`은 KLogging의 고정 문구 warning으로
  격리한다.
  throwable, exception message, source/sink path, record id는 로그에 쓰지
  않는다. listener 예외를 `GraphIoFailure`에 추가하지 않으며 다음 event와
  import/export 결과를 계속 전달한다. non-terminal callback에서 발생한
  `Error`는 reporter를 즉시 terminalize한 뒤 작업을 중단하고 다시 던진다.
  terminal callback에서 발생한 `Error`는 이미 선점한 terminal 상태를
  유지한다. primary 작업 예외/취소가 이미 있으면 listener Error를 primary에
  suppressed로 붙이고 primary를 다시 던지며, primary가 없을 때만 listener
  Error를 던진다. pre-start future cancellation의 `CANCELLED` callback은
  작업 thread가 존재하지 않으므로 caller thread에서 실행되는 유일한
  예외이며, 이 경로도 active를 증가시키지 않는다.
- terminal event는 report의 status/count/elapsed와 일치해야 하며, 동일
  reporter에서 terminal은 CAS로 정확히 한 번만 발행한다.
- callback은 작업 thread에서 동기 호출한다. core는 callback을 interrupt하거나
  별도 queue에 적재하지 않는다. 따라서 사용자 listener는 non-blocking이어야
  하며, bridge listener는 allocation/registry 호출을 bounded O(1)로 유지한다.
  이 backpressure 책임을 API KDoc와 README에 명시하고 slow-listener 회귀를
  검증한다.
- listener가 `null`인 Java 호출이나 Kotlin 기본값은 `NOOP`으로 정규화하지
  않는다. 각 entrypoint는 작업 시작 전 `Objects.requireNonNull(listener)`를
  호출해 event 없이 `NullPointerException`을 발생시키며, Java compile/run
  smoke가 이 계약을 고정한다.

`GraphIoCompositeProgressListener`는 순서가 있는 listener 목록을 받아
등록 순서대로 dispatch한다.

```kotlin
class GraphIoCompositeProgressListener(
    listeners: Iterable<GraphIoProgressListener>,
) : GraphIoProgressListener {
    private val delegates = listeners.toList()

    override fun onEvent(event: GraphIoProgressEvent) {
        var firstError: Error? = null
        delegates.forEach { delegate ->
            try {
                delegate.onEvent(event)
            } catch (error: Exception) {
                // reporter warning hook에 원인/메시지 없이 기록하고 다음 delegate를 호출한다.
            } catch (error: Error) {
                firstError = firstError ?: error
            }
        }
        firstError?.let { throw it }
    }

    companion object {
        fun of(vararg listeners: GraphIoProgressListener) =
            GraphIoCompositeProgressListener(listeners.asList())
    }
}
```

composite는 delegate별 일반 `Exception`을 reporter warning hook에 한 번씩
전달한 뒤 다음 delegate를 계속 호출하며, `Error`는 앞서 정의한 primary
우선순위 규칙을 따른다. warning hook에는 throwable, 메시지, 경로, record id를
전달하지 않는다. 빈 목록은 `NOOP`과 동등하다. Spring 사용자
listener와 Micrometer bridge를 함께 쓰는 README 예제는 이 composite를
사용한다.

## Micrometer bridge

새 `graph-io/micrometer` 모듈은 `api(project(":bluetape4k-graph-io-core"))`
와 `api("io.micrometer:micrometer-core")`를 사용하고 Micrometer BOM을
`implementation(platform(...))`으로 가져온다. core에는 Micrometer 의존성을
추가하지 않는다.

모듈 추가 시 `settings.gradle.kts`의 자동 포함 조건을 확인하고,
`graph-io/micrometer/build.gradle.kts`, root graph BOM/publication metadata,
module README(EN/KO), Kover aggregation, CI smoke와 Nightly graph-io 범위를
함께 갱신한다. Micrometer version은 중앙 BOM으로 관리하며 로컬 catalog에
중복 version을 추가하지 않는다.

기본 meter 이름과 허용 tag는 다음으로 고정한다.

| meter | type | tags |
|---|---|---|
| `graph.io.runs` | Counter | `operation`, `format`, `status` |
| `graph.io.records` | Counter | `operation`, `format`, `kind` (`vertices`, `edges`, `skipped_vertices`, `skipped_edges`, `failures`) |
| `graph.io.bytes` | Counter | `operation`, `format` |
| `graph.io.duration` | Timer | `operation`, `format`, `status` |
| `graph.io.phase.duration` | Timer | `operation`, `format`, `phase` |
| `graph.io.active` | Gauge | `operation`, `format` |

tag 값은 `lowercase(Locale.ROOT)`로 만든 enum의 고정 이름만 사용한다. dataset
path, source/sink description, label, record id, exception class/message, run id는
tag가 아니다. `graph.io.phase.duration`은 `PHASE_COMPLETED` event의
`phaseElapsed`만 기록하여 누적 snapshot을 중복 계수하지 않는다. bridge는
terminal event의 누적 snapshot만 counter/timer에 반영하고 reporter의 terminal
CAS를 신뢰한다. active gauge는 registry별로 고정된 `(operation, format)`
8개 cell만 만들고 `AtomicLong`을 사용한다. unbounded run map이나 terminal
dedup map을 만들지 않는다. bridge는 `runId == 0`인 수동 event를 metric에
반영하지 않고 고정 문구로 무시한다. 양수 runId event의
monotonic/terminal exactly-once는 core reporter가 보장하며 bridge는 이를
검증하기 위한 unbounded dedup map을 만들지 않는다.

metric mapping은 다음으로 고정한다: `graph.io.records`의 `vertices`와
`edges`는 terminal event의 `successfulVertices`와 `successfulEdges`,
`skipped_vertices`/`skipped_edges`/`failures`는 같은 이름의 누적 필드,
`graph.io.bytes`는 `bytesProcessed`만 기록한다. `graph.io.runs`와
`graph.io.duration`의 status는 반환 report status를 lowercase로 쓰고,
throw된 예외는 `failed`, 취소는 event type에서 `cancelled`로 고정한다.

## Spring Boot 연결

`GraphIoMicrometerAutoConfiguration`을 graph-spring-boot에 추가한다.

- outer auto-configuration에는 `@ConditionalOnClass(name = ["io.micrometer.core.instrument.MeterRegistry",
  "io.bluetape4k.graph.io.micrometer.GraphIoMicrometerProgressListener"])`와
  property 조건만 둔다.
- Micrometer 타입을 실제로 참조하는 `@Configuration(proxyBeanMethods = false)`
  nested class에만 `@ConditionalOnBean(MeterRegistry::class)`와 `@Bean`을 둔다.
- `graph-spring-boot`는 bridge project와 Micrometer core를 `compileOnly`로
  선언하고, bridge 모듈은 core만 의존한다. MeterRegistry는 Boot/Actuator가
  제공하며 이 auto-configuration이 생성하지 않는다.
- `@ConditionalOnProperty(prefix = "bluetape4k.graph.io.metrics", name =
  ["enabled"], havingValue = "true", matchIfMissing = false)`
- bridge bean은 `GraphIoMicrometerProgressListener` 고유 이름으로 항상
  등록하여 사용자 listener bean이 있어도 metric bridge가 조용히 사라지지
  않게 한다. concrete bridge bean은 `autowireCandidate = false`로 두므로
  `@Qualifier`/`@Autowired` 후보로 사용하지 않는다. README와 KDoc은
  `@Resource(name = "graphIoMicrometerProgressListener")` 또는 명시적
  `ApplicationContext.getBean("graphIoMicrometerProgressListener")` lookup만
  허용하고, generic alias bean은 만들지 않는다. 사용자 callback과 metric을
  함께 쓰려면 명시적으로 조회한 bridge와 `GraphIoCompositeProgressListener`를
  구성한다. 따라서 unqualified interface injection에 concrete bridge가
  끼어들지 않는다.

auto-config는 listener bean만 제공하고 MeterRegistry를 교체하거나 importer/
exporter bean을 감싸지 않는다. import/export 계약이 listener를 직접 받으므로
호출자는 auto-config bean을 마지막 listener 인자로 전달한다. 이 opt-in 연결
방식과 사용자 listener + bridge composite 예제를 양쪽 README에 명시한다.
bridge 모듈 또는 Micrometer가 없으면 outer 조건과 `FilteredClassLoader` 검증을
통해 startup class-loading failure 없이 back-off한다.

## 검증 계약

### core

- 기존 3-인자 호출 결과와 새 overload의 report가 동일하다.
- 기존 Kotlin 2·3-인자 호출과 Java consumer compile smoke가 overload
  ambiguity 없이 통과한다.
- 현재 Kotlin 2.4/JVM 25 toolchain의 `-jvm-default=enable` 설정으로,
  기준 SHA `5ec93ef4b98e1654480bf831b13defd5aae057b7`에서 별도 artifact로
  먼저 만든 precompiled 3-인자 구현체 fixture를 새 core와 링크하는 binary
  compatibility smoke를 통과한다.
- 성공/실패 이벤트 순서와 terminal exactly-once를 검증한다.
- 정상 반환된 `PARTIAL`/`FAILED` report는 `COMPLETED` event와 report status를
  함께 사용하고, 호출자에게 전파된 예외만 `FAILED` event가 되는 구분을
  deterministic test로 고정한다.
- concurrent/re-entrant listener, cancellation/interrupt, future
  `cancel(false)`/`cancel(true)`,
  callback 예외·`Error`, decreasing/negative snapshot을 deterministic test로
  검증한다.
- listener 예외가 원래 report/예외를 바꾸지 않는지 검증한다.
- 동기/suspend/Virtual Thread adapter가 동일한 이벤트 type/status를
  전달하는지 검증한다.
- public event model의 Java serialization과 counter invariant를 검증한다.

### 포맷/Okio

- CSV, Jackson2, Jackson3, GraphML의 sync/suspend/Virtual Thread 경로에서
  하나의 reporter를 전달하고 aggregate counts와 phase duration을 확인한다.
- Okio format dispatch, DAEAD/gzip wrapper, suspend Flow/Await, Virtual Thread
  adapter가 listener를 잃지 않고 한 번만 terminal event를 만든다.
- InputStream/OutputStream과 압축/암호화 wrapper에서 bytes가 null이어도
  정상 동작하고 logical bytes 정의를 지킨다.

### Micrometer/Spring Boot

- terminal report가 counter/timer에 한 번만 반영되는지 검증한다.
- phase duration timer가 고정 phase tag로 한 번만 기록되고 active gauge가
  concurrent/cancel/error 경로에서 음수·누수 없이 0으로 돌아오는지 검증한다.
- 허용 tag 집합과 고정 cardinality를 검증하고 path/message가 tag에 없는지
  확인한다.
- property 미설정/false, Micrometer classpath 없음, 사용자 listener bean
  존재 시 각각 disabled/back-off 및 bridge 유지 동작을 `ApplicationContextRunner`
  와 `FilteredClassLoader`로 검증한다.
- 사용자 listener와 bridge concrete bean이 함께 있을 때 qualifier 주입이
  아니라 `@Resource(name=...)`/explicit context lookup이 성공하고,
  unqualified generic listener injection을 auto-config가 새로 만들지 않는지
  검증한다. `autowireCandidate=false` concrete bean에 대한 qualifier
  injection은 계약으로 사용하지 않는다.
- settings auto-inclusion, module build, BOM/publication metadata, README
  locale parity, CI/Nightly/Kover task registration을 `./gradlew projects`와
  metadata smoke로 검증한다. `.github/workflows/nightly-tests.yml`을
  변경하면 local green 뒤 `scope=full` workflow dispatch 결과 URL을 receipt와
  lesson에 기록하고, 변경하지 않으면 그 사실을 기록한다.

## 호환성·성능·보안 결정

- 기존 abstract 3-인자 함수와 source signature를 제거하거나 변경하지 않는다.
- listener를 받는 새 overload의 listener parameter는 required이며, listener
  미사용 경로는 기존 method를 그대로 사용한다. `NOOP` identity는 reporter
  생성 전에 확인하여 event allocation을 만들지 않는다.
- listener 호출은 작업 thread에서 동기 실행하되 listener 실패는 격리한다.
  bridge 자체는 bounded tag와 O(1) registry 호출만 사용하고 사용자 callback의
  blocking은 non-blocking 계약 위반으로 문서화한다.
- 공개 이벤트에는 source/sink 객체나 raw throwable을 넣지 않는다. 운영 로그에도
  path와 exception message를 자동 삽입하지 않는다.
- 새 `graph-io/micrometer` project의 build file, root settings 자동 포함,
  graph BOM/publication metadata, CI/Nightly/Kover task, EN/KO README 및
  Spring Boot `AutoConfiguration.imports`를 구현 산출물로 고정한다.
