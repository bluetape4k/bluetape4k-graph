#!/usr/bin/env bash
# Wrapper: run Apache AGE kotlinx-benchmark and emit JSON summary on the last stdout line.
# Output shape: {"primary": <mean_us>, "sub_scores": {"createVertex": X, "findVertices": Y, "shortestPath": Z}}

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

MODULE="${BENCHMARK_AGE_MODULE:-:graph-age-benchmark}"
REPORT_ROOT="${BENCHMARK_AGE_REPORT_ROOT:-$REPO_ROOT/benchmark/graph-age-benchmark/build/reports/benchmarks/main}"

if [[ "${BENCHMARK_SKIP_RUN:-false}" != "true" ]]; then
    # Remove previous runs so we can pick the freshest report deterministically.
    rm -rf "$REPORT_ROOT" 2>/dev/null || true

    # Run benchmark; stream raw output to stderr so only the JSON line hits stdout.
    ./gradlew "$MODULE:benchmark" --rerun --console=plain 1>&2
fi

# Pick the newest JSON report produced by the run (fd-based, avoids `ls`).
REPORT_FILE="$(fd --type f --extension json . "$REPORT_ROOT" 2>/dev/null | head -n1 || true)"
if [[ -z "${REPORT_FILE}" ]]; then
    # Fallback for environments without fd: use bash globbing.
    shopt -s nullglob
    candidates=("$REPORT_ROOT"/*/*.json)
    shopt -u nullglob
    if ((${#candidates[@]} > 0)); then
        REPORT_FILE="${candidates[-1]}"
    fi
fi

if [[ -z "${REPORT_FILE}" || ! -f "${REPORT_FILE}" ]]; then
    echo "ERROR: benchmark JSON report not found under $REPORT_ROOT" 1>&2
    exit 1
fi

# Parse JSON with python3 (always available on macOS + CI).
python3 - "$REPORT_FILE" <<'PY'
import json, sys

path = sys.argv[1]
with open(path) as fh:
    data = json.load(fh)

# kotlinx-benchmark writes a JMH-compatible array of result objects.
# Each item has: benchmark (FQ name), primaryMetric.score (mean), primaryMetric.scoreUnit.
def to_us(score, unit):
    unit = unit.lower()
    if unit.startswith("us/op") or unit.startswith("\xb5s/op"):
        return score
    if unit.startswith("ns/op"):
        return score / 1000.0
    if unit.startswith("ms/op"):
        return score * 1000.0
    if unit.startswith("s/op"):
        return score * 1_000_000.0
    return score  # unknown -> pass through

scores_us = []
sub = {}
for item in data:
    name = item.get("benchmark", "")
    prim = item.get("primaryMetric", {})
    score = float(prim.get("score", 0.0))
    unit = str(prim.get("scoreUnit", "us/op"))
    us = to_us(score, unit)
    scores_us.append(us)
    short = name.rsplit(".", 1)[-1]
    sub[short] = round(us, 3)

primary = round(sum(scores_us) / len(scores_us), 3) if scores_us else 0.0

# Emit exactly one JSON line on stdout (the last line of stdout, per contract).
print(json.dumps({"primary": primary, "sub_scores": sub}))
PY
