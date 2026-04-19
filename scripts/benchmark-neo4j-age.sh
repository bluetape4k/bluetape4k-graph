#!/usr/bin/env bash
# Wrapper: run AGE + Neo4j kotlinx-benchmarks and emit combined JSON summary on the last stdout line.
# Output shape:
#   {"primary": <combined_mean_us>,
#    "sub_scores": {"age_<bench>": X, ..., "neo4j_<bench>": Y, ...}}

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

AGE_MODULE=":graph-age-benchmark"
NEO4J_MODULE=":graph-neo4j-benchmark"
AGE_REPORT_ROOT="$REPO_ROOT/benchmark/graph-age-benchmark/build/reports/benchmarks/main"
NEO4J_REPORT_ROOT="$REPO_ROOT/benchmark/graph-neo4j-benchmark/build/reports/benchmarks/main"

# Remove previous runs so we can pick the freshest report deterministically.
rm -rf "$AGE_REPORT_ROOT" "$NEO4J_REPORT_ROOT" 2>/dev/null || true

# Run both benchmarks; stream raw output to stderr so only the JSON line hits stdout.
./gradlew "$AGE_MODULE:benchmark" --rerun --console=plain 1>&2
./gradlew "$NEO4J_MODULE:benchmark" --rerun --console=plain 1>&2

pick_report() {
    local root="$1"
    local file
    file="$(fd --type f --extension json . "$root" 2>/dev/null | head -n1 || true)"
    if [[ -z "${file}" ]]; then
        shopt -s nullglob
        local candidates=("$root"/*/*.json)
        shopt -u nullglob
        if ((${#candidates[@]} > 0)); then
            file="${candidates[-1]}"
        fi
    fi
    echo "$file"
}

AGE_REPORT_FILE="$(pick_report "$AGE_REPORT_ROOT")"
NEO4J_REPORT_FILE="$(pick_report "$NEO4J_REPORT_ROOT")"

if [[ -z "${AGE_REPORT_FILE}" || ! -f "${AGE_REPORT_FILE}" ]]; then
    echo "ERROR: AGE benchmark JSON report not found under $AGE_REPORT_ROOT" 1>&2
    exit 1
fi
if [[ -z "${NEO4J_REPORT_FILE}" || ! -f "${NEO4J_REPORT_FILE}" ]]; then
    echo "ERROR: Neo4j benchmark JSON report not found under $NEO4J_REPORT_ROOT" 1>&2
    exit 1
fi

# Parse JSON with python3 (always available on macOS + CI).
python3 - "$AGE_REPORT_FILE" "$NEO4J_REPORT_FILE" <<'PY'
import json, sys

age_path, neo4j_path = sys.argv[1], sys.argv[2]

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

def load(path, prefix):
    with open(path) as fh:
        data = json.load(fh)
    all_us = []
    sub = {}
    for item in data:
        name = item.get("benchmark", "")
        prim = item.get("primaryMetric", {})
        score = float(prim.get("score", 0.0))
        unit = str(prim.get("scoreUnit", "us/op"))
        us = to_us(score, unit)
        all_us.append(us)
        short = name.rsplit(".", 1)[-1]
        sub[f"{prefix}_{short}"] = round(us, 3)
    return all_us, sub

age_scores, age_sub = load(age_path, "age")
neo4j_scores, neo4j_sub = load(neo4j_path, "neo4j")

combined = age_scores + neo4j_scores
primary = round(sum(combined) / len(combined), 3) if combined else 0.0

sub = {}
sub.update(age_sub)
sub.update(neo4j_sub)

# Emit exactly one JSON line on stdout (the last line of stdout, per contract).
print(json.dumps({"primary": primary, "sub_scores": sub}))
PY
