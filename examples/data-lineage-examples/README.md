# data-lineage-examples

> 🇰🇷 [한국어 문서](README.ko.md)

This example models a compact data lineage graph for datasets, tables, columns, pipeline jobs, dashboards, owners, and
data quality checks. It demonstrates bounded graph traversal for schema-change impact analysis without building a full
catalog product.

## Scenario

The sample graph traces `raw.orders` through ingestion and revenue mart jobs into executive and operations dashboards.
It answers which dashboards a source table or column change can affect, which upstream tables feed a dashboard metric,
who owns a broken job and its impacted dashboards, and why a failing data quality check reaches a business metric.

## Graph Model

| Element | Label | Key properties | Purpose |
|---|---|---|---|
| Dataset | `Dataset` | `datasetId`, `domain`, `status` | Groups physical and curated tables. |
| Table | `Table` | `tableId`, `domain`, `status` | Source, curated, and mart assets. |
| Column | `Column` | `columnId`, `dataType`, `status` | Column-level impact roots. |
| Pipeline job | `PipelineJob` | `jobId`, `schedule`, `status` | Transforms upstream assets into downstream tables. |
| Dashboard | `Dashboard` | `dashboardId`, `metric`, `status` | Business-facing metric endpoint. |
| Owner | `Owner` | `ownerId`, `team`, `channel` | Accountable team for jobs and dashboards. |
| Quality check | `QualityCheck` | `checkId`, `severity`, `status` | Failing control tied to source columns. |

## Traversal Goals

| Question | API |
|---|---|
| Which dashboards are affected by a source table change? | `impactedDashboardsBySourceTable(tableId)` |
| Which dashboards are affected by a source column change? | `impactedDashboardsByColumn(columnId)` |
| Which upstream tables feed a dashboard metric? | `upstreamTablesForDashboard(dashboardId)` |
| Which owners should be paged for a broken job? | `ownersForBrokenJob(jobId)` |
| Which dashboards are affected by a failing data quality check? | `dashboardsAffectedByQualityCheck(checkId)` |
| Which bounded lineage path explains an impact? | `explainLineagePath(sourceTableId, dashboardId)` |

## Sample Dataset

The module bundles graph-io CSV fixtures under `src/main/resources/sample-data/data-lineage/`.

| File | Contents |
|---|---|
| `vertices.csv` | datasets, tables, columns, pipeline jobs, dashboards, owners, and checks. |
| `edges.csv` | containment, job input/output, dashboard feed, owner, and validation edges. |

```kotlin
val ops = TinkerGraphOperations()
val service = DataLineageImpactService(ops)
service.initialize()

DataLineageSampleDatasetLoader.importCsv(ops)
val dashboards = service.impactedDashboardsBySourceTable("raw.orders")
val paths = service.explainLineagePath("raw.orders", "exec-revenue")
```

## Expected Output

| Query | Expected IDs |
|---|---|
| `impactedDashboardsBySourceTable("raw.orders")` | `exec-revenue`, `ops-quality` |
| `impactedDashboardsByColumn("raw.orders.customer_id")` | `exec-revenue`, `ops-quality` |
| `upstreamTablesForDashboard("exec-revenue")` | `mart.revenue_daily`, `curated.orders_enriched`, `raw.orders`, `raw.payments` |
| `ownersForBrokenJob("build-revenue-mart")` | `finance-analytics` |
| `dashboardsAffectedByQualityCheck("check-orders-customer")` | `exec-revenue`, `ops-quality` |

## Running Tests

```bash
./gradlew :data-lineage-examples:test
```

The first slice uses TinkerGraph smoke coverage and graph-io CSV loader tests.
