# Migration Performance Report - T-003A

Date: 2026-03-30
Task: T-003A - Hardening OFF perf migration follow-up

## Scope
- Measure Room migration v1 -> v2 performance for OFF dedup on a representative large dataset.
- Produce measurable evidence with p50, p95, and worst-case latency.

## Benchmark Setup
- Test: `AppDatabaseMigrationTest.migration_1_2_largeDataset_performanceEvidence_reportsPercentiles`
- Dataset shape:
  - groups: 240
  - duplicates per group: 8
  - total OFF food rows before migration: 1920
  - meal_entry rows before migration: 1920
  - nutrition_override rows before migration: 1920
- Iterations: 15
- Metric source: elapsed wall-clock time (ms) for opening DB with `MIGRATION_1_2`.

## Results
- p50: 94 ms
- p95: 314 ms
- worst-case: 314 ms
- samples (ms): 89, 91, 92, 92, 92, 92, 93, 94, 97, 97, 102, 104, 105, 150, 314

## Evidence (command output extract)
`MIGRATION_PERF dataset=groups:240 duplicates:8 iterations:15 p50_ms:94 p95_ms:314 worst_ms:314 samples_ms:89,91,92,92,92,92,93,94,97,97,102,104,105,150,314`

## Validation Commands
- `./gradlew --no-daemon :app:testDebugUnitTest --tests com.myfitnessmeals.app.data.local.AppDatabaseMigrationTest.migration_1_2_largeDataset_performanceEvidence_reportsPercentiles -i`
- `./gradlew --no-daemon test`

## Notes
- Full suite completed successfully on this environment.
- One outlier run (314 ms) is visible; median behavior remains near 94 ms.