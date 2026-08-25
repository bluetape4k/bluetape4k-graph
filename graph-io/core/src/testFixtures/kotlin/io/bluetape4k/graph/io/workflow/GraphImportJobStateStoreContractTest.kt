package io.bluetape4k.graph.io.workflow

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * graph-io state store 구현이 공유해야 하는 기본 contract TCK다.
 *
 * durable 구현 테스트는 [createStore]만 제공하면 jobId invariant와 실패 원자성,
 * 최신 report 기반 update 계약을 동일하게 검증할 수 있다.
 */
abstract class AbstractGraphImportJobStateStoreContractTest {

    protected abstract fun createStore(): GraphImportJobStateStore

    protected lateinit var store: GraphImportJobStateStore

    private val jobId = "contract-job"

    @BeforeEach
    fun setUpStore() {
        store = createStore()
        store.save(initialReport())
    }

    @Test
    fun `update transforms the latest report and persists the result`() {
        val updated = store.update(jobId) { current ->
            current shouldBeEqualTo initialReport()
            current.shouldNotBeNull().copy(
                state = GraphImportWorkflowState.VALIDATED,
                elapsed = Duration.ofSeconds(1),
            )
        }

        updated.state shouldBeEqualTo GraphImportWorkflowState.VALIDATED
        updated.elapsed shouldBeEqualTo Duration.ofSeconds(1)
        store.load(jobId) shouldBeEqualTo updated
    }

    @Test
    fun `update creates the first report when the job is absent`() {
        val freshStore = createStore()

        val created = freshStore.update(jobId) { current ->
            current.shouldBeNull()
            GraphImportWorkflowReport(jobId, GraphImportWorkflowState.DISCOVERED)
        }

        created.jobId shouldBeEqualTo jobId
        freshStore.load(jobId) shouldBeEqualTo created
    }

    @Test
    fun `jobId mismatch fails before a report is stored`() {
        assertFailsWith<IllegalArgumentException> {
            store.update(jobId) {
                GraphImportWorkflowReport(
                    jobId = "other-job",
                    state = GraphImportWorkflowState.VALIDATED,
                )
            }
        }

        store.load(jobId) shouldBeEqualTo initialReport()
        store.load("other-job").shouldBeNull()
    }

    @Test
    fun `transform failure leaves the existing report unchanged`() {
        assertFailsWith<IllegalStateException> {
            store.update(jobId) {
                error("contract transform failure")
            }
        }

        store.load(jobId) shouldBeEqualTo initialReport()
    }

    protected fun initialReport(): GraphImportWorkflowReport =
        GraphImportWorkflowReport(
            jobId = jobId,
            state = GraphImportWorkflowState.DISCOVERED,
            elapsed = Duration.ofMillis(10),
        )
}

/**
 * CAS/transaction 구현이 transform 재평가와 저장 경계를 검증할 수 있도록
 * 제공하는 test-fixture harness다. [saveInvocations]는 test-only 관찰값이며,
 * 실제 durable adapter는 충돌 시점에 [arrangeRetry]를 주입하는 adapter 전용
 * harness를 구현해 이 TCK를 재사용한다.
 */
interface GraphImportJobStateStoreRetryHarness : GraphImportJobStateStore {
    val saveInvocations: Int

    fun arrangeRetry(interveningReport: GraphImportWorkflowReport)
}

/**
 * CAS/transaction 구현이 추가로 검증해야 하는 retry contract TCK다.
 */
abstract class AbstractGraphImportJobStateStoreRetryContractTest :
    AbstractGraphImportJobStateStoreContractTest() {

    protected abstract fun createRetryingStore(): GraphImportJobStateStoreRetryHarness

    @Test
    fun `jobId mismatch fails before save is invoked`() {
        val retryingStore = createRetryingStore()
        val initial = initialReport()
        retryingStore.save(initial)
        val savesBeforeMismatch = retryingStore.saveInvocations

        assertFailsWith<IllegalArgumentException> {
            retryingStore.update(initial.jobId) {
                initial.copy(jobId = "other-job")
            }
        }

        retryingStore.saveInvocations shouldBeEqualTo savesBeforeMismatch
        retryingStore.load(initial.jobId) shouldBeEqualTo initial
    }

    @Test
    fun `retry evaluates transform against the latest report and commits only the retry result`() {
        val retryingStore = createRetryingStore()
        val initial = initialReport().copy(elapsed = Duration.ofSeconds(1))
        val intervening = initial.copy(elapsed = Duration.ofSeconds(2))
        retryingStore.save(initial)
        retryingStore.arrangeRetry(intervening)

        var evaluations = 0
        val updated = retryingStore.update(initial.jobId) { current ->
            evaluations++
            when (evaluations) {
                1 -> current shouldBeEqualTo initial
                2 -> current shouldBeEqualTo intervening
            }
            current.shouldNotBeNull().copy(state = GraphImportWorkflowState.VALIDATED)
        }

        evaluations shouldBeEqualTo 2
        updated shouldBeEqualTo intervening.copy(state = GraphImportWorkflowState.VALIDATED)
        retryingStore.load(initial.jobId) shouldBeEqualTo updated
    }
}
