import json
import tempfile
import unittest
from pathlib import Path

from sanitize_snyk_sarif import sanitize


class SanitizeSnykSarifTests(unittest.TestCase):
    def test_removes_only_null_security_severity_and_preserves_findings(self):
        report = {
            "version": "2.1.0",
            "runs": [{
                "tool": {"driver": {"rules": [
                    {"id": "null-severity", "properties": {"security-severity": None}},
                    {"id": "string-null", "properties": {"security-severity": "null"}},
                    {"id": "rated", "properties": {"security-severity": "8.0"}},
                    {"id": "malformed", "properties": {"security-severity": "unknown"}},
                ]}},
                "results": [{"ruleId": "null-severity", "message": {"text": "finding"}}],
            }],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.sarif"
            path.write_text(json.dumps(report), encoding="utf-8")

            sanitize(path)

            sanitized = json.loads(path.read_text(encoding="utf-8"))
        rules = sanitized["runs"][0]["tool"]["driver"]["rules"]
        self.assertNotIn("security-severity", rules[0]["properties"])
        self.assertNotIn("security-severity", rules[1]["properties"])
        self.assertEqual(8.0, rules[2]["properties"]["security-severity"])
        self.assertNotIn("security-severity", rules[3]["properties"])
        self.assertEqual(report["runs"][0]["results"], sanitized["runs"][0]["results"])

    def test_invalid_sarif_is_rejected_without_replacing_input(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.sarif"
            path.write_text("not-json", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "ausente ou inválido"):
                sanitize(path)

            self.assertEqual("not-json", path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
