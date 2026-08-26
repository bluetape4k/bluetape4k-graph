package io.bluetape4k.graph.io.workflow

/** 기본 in-memory store와 retry harness에 공통 TCK를 적용한다. */
class InMemoryGraphImportJobStateStoreContractTest :
    AbstractGraphImportJobStateStoreRetryContractTest() {

    override fun createStore(): GraphImportJobStateStore = InMemoryGraphImportJobStateStore()

    override fun createFailureStore(): GraphImportJobStateStoreFailureHarness =
        FailingInMemoryGraphImportJobStateStore()

    override fun createRetryingStore(): GraphImportJobStateStoreRetryHarness =
        RetryingInMemoryGraphImportJobStateStore()

    private class FailingInMemoryGraphImportJobStateStore : GraphImportJobStateStoreFailureHarness {
        private val delegate = InMemoryGraphImportJobStateStore()
        private var failNextSave = false

        override fun load(jobId: String): GraphImportWorkflowReport? = delegate.load(jobId)

        override fun save(report: GraphImportWorkflowReport) {
            if (failNextSave) {
                failNextSave = false
                error("contract save failure")
            }
            delegate.save(report)
        }

        override fun failNextSave() {
            failNextSave = true
        }
    }

    private class RetryingInMemoryGraphImportJobStateStore : GraphImportJobStateStoreRetryHarness {
        private val delegate = InMemoryGraphImportJobStateStore()
        private var interveningReport: GraphImportWorkflowReport? = null

        override var saveInvocations: Int = 0
            private set

        override fun load(jobId: String): GraphImportWorkflowReport? = delegate.load(jobId)

        override fun save(report: GraphImportWorkflowReport) {
            saveInvocations++
            delegate.save(report)
        }

        override fun arrangeRetry(interveningReport: GraphImportWorkflowReport) {
            this.interveningReport = interveningReport
        }

        override fun update(
            jobId: String,
            transform: (GraphImportWorkflowReport?) -> GraphImportWorkflowReport,
        ): GraphImportWorkflowReport {
            val first = transform(load(jobId))
            val retryReport = interveningReport
            if (retryReport == null) {
                require(first.jobId == jobId) { "state update jobId must match the requested jobId" }
                save(first)
                return first
            }

            interveningReport = null
            require(retryReport.jobId == jobId) { "state update jobId must match the requested jobId" }
            save(retryReport)

            val retried = transform(load(jobId))
            require(retried.jobId == jobId) { "state update jobId must match the requested jobId" }
            save(retried)
            return retried
        }
    }
}
