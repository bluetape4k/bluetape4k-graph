#!/usr/bin/env bash
# Wrapper: run AGE + Neo4j kotlinx-benchmarks and emit a combined JSON summary
# on the last stdout line.
#
# Stable output schema: bluetape4k.graph.backend-benchmark-summary.v1
# - primary: arithmetic mean of all AGE + Neo4j benchmark scores in us/op.
# - sub_scores: backend-prefixed operation scores in us/op.
# - benchmarks: per-result rows with backend, benchmark, operation, params, and units.
# Lower values are better for this wrapper.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

AGE_MODULE="${BENCHMARK_AGE_MODULE:-:graph-age-benchmark}"
NEO4J_MODULE="${BENCHMARK_NEO4J_MODULE:-:graph-neo4j-benchmark}"
AGE_REPORT_ROOT="${BENCHMARK_AGE_REPORT_ROOT:-$REPO_ROOT/benchmark/graph-age-benchmark/build/reports/benchmarks/main}"
NEO4J_REPORT_ROOT="${BENCHMARK_NEO4J_REPORT_ROOT:-$REPO_ROOT/benchmark/graph-neo4j-benchmark/build/reports/benchmarks/main}"

if [[ "${BENCHMARK_SKIP_RUN:-false}" != "true" ]]; then
    # Remove previous runs so we can pick the freshest report deterministically.
    rm -rf "$AGE_REPORT_ROOT" "$NEO4J_REPORT_ROOT" 2>/dev/null || true

    # Run both benchmarks; stream raw output to stderr so only the JSON line hits stdout.
    ./gradlew "$AGE_MODULE:benchmark" --rerun --console=plain 1>&2
    ./gradlew "$NEO4J_MODULE:benchmark" --rerun --console=plain 1>&2
fi

pick_report() {
    local root="$1"
    local file
    file="$(find "$root" -type f -name '*.json' -print 2>/dev/null | sort | tail -n1 || true)"
    echo "$file"
}

report_missing() {
    local backend="$1"
    local root="$2"
    local module="$3"
    local env_name="$4"

    echo "ERROR: ${backend} benchmark JSON report not found." 1>&2
    echo "  searched: ${root}" 1>&2
    echo "  expected: a kotlinx-benchmark/JMH JSON file under the report root" 1>&2
    if [[ "${BENCHMARK_SKIP_RUN:-false}" == "true" ]]; then
        echo "  hint: unset BENCHMARK_SKIP_RUN or set ${env_name} to an existing report root" 1>&2
    else
        echo "  hint: run ./gradlew ${module}:benchmark and check the benchmark task output" 1>&2
    fi
}

AGE_REPORT_FILE="$(pick_report "$AGE_REPORT_ROOT")"
NEO4J_REPORT_FILE="$(pick_report "$NEO4J_REPORT_ROOT")"

if [[ -z "${AGE_REPORT_FILE}" || ! -f "${AGE_REPORT_FILE}" ]]; then
    report_missing "AGE" "$AGE_REPORT_ROOT" "$AGE_MODULE" "BENCHMARK_AGE_REPORT_ROOT"
    exit 1
fi
if [[ -z "${NEO4J_REPORT_FILE}" || ! -f "${NEO4J_REPORT_FILE}" ]]; then
    report_missing "Neo4j" "$NEO4J_REPORT_ROOT" "$NEO4J_MODULE" "BENCHMARK_NEO4J_REPORT_ROOT"
    exit 1
fi

# Parse JSON with python3 (always available on macOS + CI).
python3 - "$AGE_REPORT_FILE" "$NEO4J_REPORT_FILE" <<'PY'
import json
import math
import sys

age_path, neo4j_path = sys.argv[1], sys.argv[2]
schema = "bluetape4k.graph.backend-benchmark-summary.v1"


class BenchmarkReportError(Exception):
    pass


def fail(message):
    print(f"ERROR: {message}", file=sys.stderr)
    sys.exit(1)


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
    raise BenchmarkReportError(f"unsupported scoreUnit '{unit}'; expected ns/op, us/op, ms/op, or s/op")

def load(path, prefix):
    try:
        with open(path) as fh:
            data = json.load(fh)
    except OSError as exc:
        raise BenchmarkReportError(f"cannot read {prefix} report {path}: {exc}") from exc
    except json.JSONDecodeError as exc:
        raise BenchmarkReportError(f"cannot parse {prefix} report {path}: {exc}") from exc

    if not isinstance(data, list):
        raise BenchmarkReportError(f"Expected JMH result list for {prefix} report: {path}")
    if not data:
        raise BenchmarkReportError(f"Empty JMH result list for {prefix} report: {path}")

    all_us = []
    sub = {}
    rows = []
    for index, item in enumerate(data):
        if not isinstance(item, dict):
            raise BenchmarkReportError(f"Expected object at {prefix} report row {index}: {path}")
        name = item.get("benchmark", "")
        if not name:
            raise BenchmarkReportError(f"Missing benchmark name at {prefix} report row {index}: {path}")
        prim = item.get("primaryMetric", {})
        if not isinstance(prim, dict):
            raise BenchmarkReportError(f"Missing primaryMetric object for {name} in {path}")
        try:
            score = float(prim["score"])
        except KeyError as exc:
            raise BenchmarkReportError(f"Missing primaryMetric.score for {name} in {path}") from exc
        except (TypeError, ValueError) as exc:
            raise BenchmarkReportError(f"Invalid primaryMetric.score for {name} in {path}: {prim.get('score')}") from exc
        if not math.isfinite(score):
            raise BenchmarkReportError(f"Non-finite primaryMetric.score for {name} in {path}: {prim.get('score')}")
        unit = str(prim.get("scoreUnit", "us/op"))
        us = to_us(score, unit)
        all_us.append(us)
        short = name.rsplit(".", 1)[-1]
        params = {str(k): str(v) for k, v in item.get("params", {}).items()}
        param_suffix = ""
        if params:
            param_suffix = "[" + ",".join(f"{key}={value}" for key, value in sorted(params.items())) + "]"
        key = f"{prefix}_{short}{param_suffix}"
        rounded = round(us, 3)
        sub[key] = rounded
        rows.append(
            {
                "backend": prefix,
                "key": key,
                "benchmark": name,
                "operation": short,
                "params": params,
                "score": rounded,
                "unit": "us/op",
                "sourceScore": score,
                "sourceUnit": unit,
                "source": path,
            }
        )
    return all_us, sub, rows

try:
    age_scores, age_sub, age_rows = load(age_path, "age")
    neo4j_scores, neo4j_sub, neo4j_rows = load(neo4j_path, "neo4j")
except BenchmarkReportError as exc:
    fail(str(exc))

combined = age_scores + neo4j_scores
primary = round(sum(combined) / len(combined), 3)

sub = {}
sub.update(age_sub)
sub.update(neo4j_sub)

# Emit exactly one JSON line on stdout (the last line of stdout, per contract).
print(json.dumps({
    "schema": schema,
    "primary": primary,
    "unit": "us/op",
    "direction": "lower_is_better",
    "sources": {
        "age": age_path,
        "neo4j": neo4j_path,
    },
    "sub_scores": sub,
    "benchmarks": age_rows + neo4j_rows,
}))
PY
