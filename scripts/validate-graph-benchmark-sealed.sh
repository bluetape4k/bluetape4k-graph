#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${1:-HEAD}"

sealed_files=(
  "benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphDbComparisonBenchmark.kt"
  "benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphIoComparisonBenchmark.kt"
  "benchmark/graph-benchmark/scripts/normalize_jmh_report.py"
  "docs/benchmark/graph-benchmark-baseline.json"
)

for file in "${sealed_files[@]}"; do
  if git diff --quiet "${BASE_REF}" -- "${file}"; then
    continue
  fi
  echo "sealed file changed: ${file}" >&2
  exit 1
done

echo "sealed files unchanged against ${BASE_REF}"
