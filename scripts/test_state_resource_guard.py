import subprocess
import unittest
from types import SimpleNamespace
from unittest.mock import patch
from state_resource_guard import check

class StateResourceGuardTests(unittest.TestCase):
    @patch("state_resource_guard.subprocess.run")
    def test_empty_environment(self, run):
        run.side_effect = [subprocess.CalledProcessError(1, "aws", stderr="NoSuchKey 404"), subprocess.CalledProcessError(1, "kubectl", stderr="NotFound")]
        self.assertEqual(check("bucket", "app/state", ["kubectl", "get", "deployment"]), "state-and-resource-absent")
    @patch("state_resource_guard.subprocess.run")
    def test_state_exists(self, run):
        run.return_value = SimpleNamespace(stdout="{}")
        self.assertEqual(check("bucket", "app/state", ["kubectl"]), "state-present")
    @patch("state_resource_guard.subprocess.run")
    def test_resource_without_state_fails(self, run):
        run.side_effect = [subprocess.CalledProcessError(1, "aws", stderr="NoSuchKey"), SimpleNamespace(stdout="NAME autoflow-backend")]
        with self.assertRaisesRegex(RuntimeError, "State app/state ausente, mas o recurso consultado já existe"):
            check("bucket", "app/state", ["kubectl"])
    @patch("state_resource_guard.subprocess.run", side_effect=subprocess.CalledProcessError(1, "aws", stderr="AccessDenied"))
    def test_permission_error_fails_closed(self, _run):
        with self.assertRaises(RuntimeError): check("bucket", "app/state", ["kubectl"])

if __name__ == "__main__": unittest.main()
