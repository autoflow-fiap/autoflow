import base64
import json
import os
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import apply_k8s_secret


class ApplyK8sSecretTests(unittest.TestCase):
    def setUp(self):
        self.values = {name: f"synthetic-{index}-not-a-real-secret" for index, name in enumerate(apply_k8s_secret.SECRET_KEYS.values())}
        self.stored = {
            key: base64.b64encode(self.values[env_name].encode()).decode()
            for key, env_name in apply_k8s_secret.SECRET_KEYS.items()
        }

    def test_applies_and_verifies_values_without_echoing_them(self):
        results = [
            SimpleNamespace(returncode=0, stdout="", stderr=""),
            SimpleNamespace(returncode=0, stdout=json.dumps({"data": self.stored}), stderr=""),
        ]
        with patch.dict(os.environ, self.values, clear=False), patch.object(apply_k8s_secret.subprocess, "run", side_effect=results) as run, patch("builtins.print") as output:
            self.assertEqual(apply_k8s_secret.main(), 0)

        manifest = json.loads(run.call_args_list[0].kwargs["input"])
        self.assertEqual(manifest["kind"], "Secret")
        self.assertEqual(manifest["stringData"], {key: self.values[name] for key, name in apply_k8s_secret.SECRET_KEYS.items()})
        printed = " ".join(str(call.args) for call in output.call_args_list)
        self.assertFalse(any(value in printed for value in self.values.values()))

    def test_missing_environment_value_fails_before_kubectl(self):
        supplied = {key: value for key, value in self.values.items() if key != "AUTOFLOW_DB_PASSWORD"}
        with patch.dict(os.environ, supplied, clear=True), patch.object(apply_k8s_secret.subprocess, "run") as run:
            self.assertEqual(apply_k8s_secret.main(), 2)
        run.assert_not_called()

    def test_secret_mismatch_fails_without_printing_values(self):
        wrong = {key: base64.b64encode(b"wrong-value").decode() for key in self.stored}
        results = [
            SimpleNamespace(returncode=0, stdout="", stderr=""),
            SimpleNamespace(returncode=0, stdout=json.dumps({"data": wrong}), stderr=""),
        ]
        with patch.dict(os.environ, self.values, clear=False), patch.object(apply_k8s_secret.subprocess, "run", side_effect=results):
            self.assertEqual(apply_k8s_secret.main(), 1)


if __name__ == "__main__":
    unittest.main()
