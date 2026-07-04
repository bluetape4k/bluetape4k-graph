#!/usr/bin/env python3
import os
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("aggregate-kover-coverage.py")


class AggregateKoverCoverageTest(unittest.TestCase):
    def run_script(self, root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(root)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_empty_coverage_root_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = self.run_script(Path(tmp))

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("_No coverage reports found._", result.stdout)
        self.assertIn("error: no Kover XML reports found", result.stderr)

    def test_report_xml_succeeds(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "coverage-core" / "graph" / "graph-core" / "build" / "reports" / "kover"
            report.mkdir(parents=True)
            (report / "report.xml").write_text(
                textwrap.dedent(
                    """\
                    <report>
                      <counter type="INSTRUCTION" missed="2" covered="8"/>
                    </report>
                    """
                ),
                encoding="utf-8",
            )

            result = self.run_script(Path(tmp))

        self.assertEqual(result.returncode, 0)
        self.assertIn("| `graph-core` | 8 | 2 | 80.00% |", result.stdout)
        self.assertEqual(result.stderr, "")


if __name__ == "__main__":
    unittest.main()
