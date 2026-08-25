#!/usr/bin/env bash

# Run one CI command with bounded retries while preserving every attempt.
# The caller exposes retry_status/retry_attempts/retry_count through GITHUB_OUTPUT.

set -o pipefail

retry_name="${RETRY_NAME:-gradle}"
max_attempts="${RETRY_MAX_ATTEMPTS:-5}"
retry_delay_seconds="${RETRY_DELAY_SECONDS:-30}"

if ! [[ "$max_attempts" =~ ^[1-9][0-9]*$ ]]; then
  echo "RETRY_MAX_ATTEMPTS must be a positive integer" >&2
  exit 2
fi
if ! [[ "$retry_delay_seconds" =~ ^[0-9]+$ ]]; then
  echo "RETRY_DELAY_SECONDS must be a non-negative integer" >&2
  exit 2
fi
if (( $# == 0 )); then
  echo "usage: RETRY_NAME=<name> RETRY_MAX_ATTEMPTS=<n> $0 command [args...]" >&2
  exit 2
fi

runner_temp="${RUNNER_TEMP:-${TMPDIR:-build}}"
evidence_dir="$runner_temp/bluetape4k-retry/$retry_name"
mkdir -p "$evidence_dir"

first_failure_log=""
attempt=1
exit_code=1
final_status="failed"

while (( attempt <= max_attempts )); do
  log_file="$evidence_dir/attempt-${attempt}.log"
  {
    printf 'attempt=%s\ncommand:' "$attempt"
    printf ' %q' "$@"
    printf '\n'
  } > "$log_file"

  echo "Running $retry_name attempt $attempt/$max_attempts"
  "$@" 2>&1 | tee -a "$log_file"
  exit_code=${PIPESTATUS[0]}

  if (( exit_code == 0 )); then
    if (( attempt == 1 )); then
      final_status="success"
    else
      final_status="success_after_retry"
    fi
    break
  fi

  if [[ -z "$first_failure_log" ]]; then
    first_failure_log="$log_file"
  fi
  echo "Attempt $attempt failed with exit code $exit_code" | tee -a "$log_file"
  if [[ "$first_failure_log" == "$log_file" ]]; then
    cp "$log_file" "$evidence_dir/first-failure.log"
  fi
  if (( attempt < max_attempts )); then
    sleep "$retry_delay_seconds"
  fi
  ((attempt += 1))
done

attempts="$((attempt > max_attempts ? max_attempts : attempt))"
retry_count="$((attempts - 1))"
summary_file="$evidence_dir/summary.env"
{
  printf 'retry_name=%s\n' "$retry_name"
  printf 'status=%s\n' "$final_status"
  printf 'attempts=%s\n' "$attempts"
  printf 'retry_count=%s\n' "$retry_count"
  printf 'first_failure_log=%s\n' "${first_failure_log:-none}"
} > "$summary_file"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    printf 'retry_status=%s\n' "$final_status"
    printf 'retry_attempts=%s\n' "$attempts"
    printf 'retry_count=%s\n' "$retry_count"
  } >> "$GITHUB_OUTPUT"
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    printf '### Retry evidence: `%s`\n\n' "$retry_name"
    printf '| field | value |\n|---|---|\n'
    printf '| status | `%s` |\n' "$final_status"
    printf '| attempts | `%s` |\n' "$attempts"
    printf '| retry count | `%s` |\n' "$retry_count"
    printf '| first failure log | `%s` |\n' "${first_failure_log:-none}"
    if [[ "$final_status" == "success_after_retry" ]]; then
      printf '\n> ⚠️ pass-after-retry: the final command succeeded only after a retry.\n'
    fi
  } >> "$GITHUB_STEP_SUMMARY"
fi

if [[ "$final_status" == "failed" ]]; then
  exit "$exit_code"
fi
