import base64
import json
import os
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from botocore.exceptions import ClientError

import apply_newrelic_license_secret as sync


class ApplyNewRelicLicenseSecretTests(unittest.TestCase):
    def setUp(self):
        self.value = "synthetic-license-not-real"
        self.env = {
            "ENABLE_NEWRELIC_APM": "true",
            "NEW_RELIC_LICENSE_SECRET_ID": "secret-id-synthetic",
            "GITHUB_DEPLOYMENT_ENVIRONMENT": "homolog",
            "AWS_EXPECTED_ACCOUNT_ID": "123456789012",
            "AWS_REGION": "us-east-1",
            "NEW_RELIC_LICENSE_KEY": self.value,
        }
        self.client = SimpleNamespace(
            get_secret_value=lambda **_: {"SecretString": self.value},
            create_secret=lambda **_: self.fail("não deveria criar"),
        )
        self.kube = [
            SimpleNamespace(returncode=0, stdout="", stderr=""),
            SimpleNamespace(returncode=0, stdout=json.dumps({"data": {"licenseKey": base64.b64encode(self.value.encode()).decode()}}), stderr=""),
        ]

    def invoke(self, aws=None, kube=None):
        with patch.dict(os.environ, self.env, clear=True), patch("boto3.client", return_value=aws or self.client) as factory, patch.object(
            sync.subprocess, "run", side_effect=kube if kube is not None else self.kube
        ) as run, patch("builtins.print") as output:
            result = sync.main([])
            return result, factory, run, output

    def test_existing_secret_reused_without_new_version_and_synced(self):
        result, aws_factory, kube, _ = self.invoke()
        self.assertEqual(result, 0)
        aws_factory.assert_called_once_with("secretsmanager", region_name="us-east-1")
        self.assertEqual(kube.call_count, 2)
        self.assertNotIn(self.value, str(kube.call_args_list[0].args[0]))
        self.assertEqual(json.loads(kube.call_args_list[0].kwargs["input"])["metadata"]["namespace"], "default")

    def test_missing_secret_created_from_github_secret_then_synced(self):
        not_found = ClientError({"Error": {"Code": "ResourceNotFoundException"}}, "GetSecretValue")
        aws = SimpleNamespace(
            get_secret_value=unittest.mock.Mock(side_effect=not_found),
            create_secret=unittest.mock.Mock(return_value={}),
        )
        result, _, _, _ = self.invoke(aws)
        self.assertEqual(result, 0)
        aws.create_secret.assert_called_once_with(Name="secret-id-synthetic", SecretString=self.value)

    def test_repeated_bootstrap_is_idempotent(self):
        aws = SimpleNamespace(get_secret_value=unittest.mock.Mock(return_value={"SecretString": self.value}), create_secret=unittest.mock.Mock())
        self.invoke(aws)
        self.invoke(aws)
        aws.get_secret_value.assert_called_with(SecretId="secret-id-synthetic")
        aws.create_secret.assert_not_called()

    def test_different_existing_value_requires_controlled_rotation(self):
        aws = SimpleNamespace(get_secret_value=lambda **_: {"SecretString": "different-synthetic-value"}, create_secret=unittest.mock.Mock())
        result, _, kube, output = self.invoke(aws)
        self.assertEqual(result, 1)
        kube.assert_not_called()
        aws.create_secret.assert_not_called()
        self.assertNotIn(self.value, str(output.call_args_list))

    def test_access_denied_reports_only_missing_permission(self):
        denied = ClientError({"Error": {"Code": "AccessDeniedException"}}, "GetSecretValue")
        aws = SimpleNamespace(get_secret_value=lambda **_: (_ for _ in ()).throw(denied), create_secret=unittest.mock.Mock())
        result, _, kube, output = self.invoke(aws)
        self.assertEqual(result, 1)
        kube.assert_not_called()
        self.assertIn("secretsmanager:GetSecretValue", str(output.call_args_list))
        self.assertNotIn(self.value, str(output.call_args_list))

    def test_missing_configuration_blocks_all_calls(self):
        self.env.pop("AWS_REGION")
        with patch.dict(os.environ, self.env, clear=True), patch("boto3.client") as aws, patch.object(sync.subprocess, "run") as kube:
            self.assertEqual(sync.main([]), 2)
        aws.assert_not_called()
        kube.assert_not_called()

    def test_invalid_region_blocks_all_calls(self):
        self.env["AWS_REGION"] = "us-east-1-wrong"
        with patch.dict(os.environ, self.env, clear=True), patch("boto3.client") as aws, patch.object(sync.subprocess, "run") as kube:
            self.assertEqual(sync.main([]), 2)
        aws.assert_not_called()
        kube.assert_not_called()

    def test_missing_github_license_secret_blocks_aws_calls(self):
        self.env.pop("NEW_RELIC_LICENSE_KEY")
        with patch.dict(os.environ, self.env, clear=True), patch("boto3.client") as aws, patch.object(sync.subprocess, "run") as kube:
            self.assertEqual(sync.main([]), 2)
        aws.assert_not_called()
        kube.assert_not_called()

    def test_kubernetes_secret_is_verified_without_logging_license(self):
        result, _, kube, output = self.invoke()
        self.assertEqual(result, 0)
        self.assertEqual(kube.call_count, 2)
        self.assertNotIn(self.value, str(output.call_args_list))


if __name__ == "__main__":
    unittest.main()
