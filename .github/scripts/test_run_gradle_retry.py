#!/usr/bin/env python3
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("run-gradle-retry.sh")


class RunGradleRetryTest(unittest.TestCase):
    def run_helper(self, root: Path, command: list[str]) -> subprocess.CompletedProcess[str]:
        output = root / "github-output"
        summary = root / "step-summary"
        env = {
            **os.environ,
            "RUNNER_TEMP": str(root / "runner-temp"),
            "RETRY_NAME": "test-helper",
            "RETRY_MAX_ATTEMPTS": "3",
            "RETRY_DELAY_SECONDS": "0",
            "GITHUB_OUTPUT": str(output),
            "GITHUB_STEP_SUMMARY": str(summary),
        }
        return subprocess.run(
            [str(SCRIPT), *command],
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_evidence_root_failure_is_nonzero(self) -> None:
        env = {
            **os.environ,
            "RUNNER_TEMP": "/dev/null",
            "RETRY_NAME": "unwritable",
            "RETRY_MAX_ATTEMPTS": "1",
            "RETRY_DELAY_SECONDS": "0",
        }
        result = subprocess.run(
            [str(SCRIPT), "bash", "-c", "exit 0"],
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

        self.assertEqual(result.returncode, 74)
        self.assertIn("retry evidence failure", result.stderr)

    def test_tee_failure_is_not_reported_as_command_success(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fake_bin = root / "bin"
            fake_bin.mkdir()
            fake_tee = fake_bin / "tee"
            fake_tee.write_text("#!/usr/bin/env bash\nexit 17\n", encoding="utf-8")
            fake_tee.chmod(0o755)
            env = {
                **os.environ,
                "PATH": f"{fake_bin}:{os.environ['PATH']}",
                "RUNNER_TEMP": str(root / "runner-temp"),
                "RETRY_NAME": "tee-failure",
                "RETRY_MAX_ATTEMPTS": "1",
                "RETRY_DELAY_SECONDS": "0",
            }
            result = subprocess.run(
                [str(SCRIPT), "bash", "-c", "exit 0"],
                env=env,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )

        self.assertEqual(result.returncode, 74)
        self.assertIn("tee failed", result.stderr)

    def test_first_attempt_success_is_not_retry_only(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            result = self.run_helper(Path(temporary), ["bash", "-c", "exit 0"])
            output = (Path(temporary) / "github-output").read_text(encoding="utf-8")
            summary = (Path(temporary) / "step-summary").read_text(encoding="utf-8")

        self.assertEqual(result.returncode, 0)
        self.assertIn("retry_status=success", output)
        self.assertIn("retry_attempts=1", output)
        self.assertIn("retry_count=0", output)
        self.assertIn("| status | `success` |", summary)
        self.assertNotIn("success_after_retry", summary)

    def test_retry_success_preserves_first_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            marker = root / "marker"
            command = [
                "bash",
                "-c",
                f"if [ ! -e '{marker}' ]; then touch '{marker}'; exit 7; fi",
            ]
            result = self.run_helper(root, command)
            output = (root / "github-output").read_text(encoding="utf-8")
            summary = (root / "step-summary").read_text(encoding="utf-8")
            first_failure = root / "runner-temp" / "bluetape4k-retry" / "test-helper" / "first-failure.log"
            first_failure_text = first_failure.read_text(encoding="utf-8")

        self.assertEqual(result.returncode, 0)
        self.assertIn("retry_status=success_after_retry", output)
        self.assertIn("retry_attempts=2", output)
        self.assertIn("retry_count=1", output)
        self.assertIn("pass-after-retry", summary)
        self.assertIn("exit code 7", first_failure_text)

    def test_exhausted_retries_return_last_exit_code(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            result = self.run_helper(root, ["bash", "-c", "exit 9"])
            output = (root / "github-output").read_text(encoding="utf-8")
            first_failure = root / "runner-temp" / "bluetape4k-retry" / "test-helper" / "first-failure.log"
            first_failure_text = first_failure.read_text(encoding="utf-8")

        self.assertEqual(result.returncode, 9)
        self.assertIn("retry_status=failed", output)
        self.assertIn("retry_attempts=3", output)
        self.assertIn("retry_count=2", output)
        self.assertIn("exit code 9", first_failure_text)


if __name__ == "__main__":
    unittest.main()
