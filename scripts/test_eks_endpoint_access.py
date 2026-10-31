import unittest
import subprocess
import os
from pathlib import Path
from unittest.mock import patch

import eks_endpoint_access as endpoint


class LeaseStorageTests(unittest.TestCase):
    def test_missing_lease_object_is_distinguished_from_read_failures(self):
        missing = subprocess.CompletedProcess(
            ["aws", "s3api", "get-object"],
            1,
            stderr="An error occurred (NoSuchKey) when calling the GetObject operation: missing",
        )
        with patch.dict(os.environ, {
            "AWS_EXPECTED_ACCOUNT_ID": "123456789012",
            "EKS_LEASE_BUCKET": "state-bucket",
            "EKS_LEASE_KEY": "ci/eks-endpoint-lease-autoflow-homolog.json",
        }), patch("eks_endpoint_access.subprocess.run", return_value=missing):
            self.assertIsNone(endpoint.load_lease())

    def test_lease_read_failures_are_diagnostic_and_do_not_expose_cli_output(self):
        failures = (
            (
                "An error occurred (AccessDenied) when calling the GetObject operation: SECRET_VALUE",
                "AccessDenied",
            ),
            (
                "An error occurred (ExpiredToken) when calling the GetObject operation: SECRET_VALUE",
                "ExpiredToken",
            ),
            (
                "An error occurred (SECRET_VALUE) when calling the GetObject operation: denied",
                "UnknownAWSException",
            ),
            ("Could not connect to the endpoint URL: SECRET_VALUE", "rede"),
        )
        for stderr, diagnostic in failures:
            result = subprocess.CompletedProcess(["aws", "s3api", "get-object"], 1, stderr=stderr)
            with self.subTest(diagnostic=diagnostic), patch.dict(os.environ, {
                "AWS_EXPECTED_ACCOUNT_ID": "123456789012",
                "EKS_LEASE_BUCKET": "state-bucket",
                "EKS_LEASE_KEY": "ci/eks-endpoint-lease-autoflow-homolog.json",
            }), patch("eks_endpoint_access.subprocess.run", return_value=result):
                with self.assertRaisesRegex(RuntimeError, diagnostic) as raised:
                    endpoint.load_lease()
                self.assertNotIn("SECRET_VALUE", str(raised.exception))

    def test_unknown_lease_document_fails_closed_without_removing_or_overwriting_it(self):
        def write_unknown_lease(arguments, **_kwargs):
            Path(arguments[-3]).write_text("{}", encoding="utf-8")
            return subprocess.CompletedProcess(arguments, 0, stdout="{}")

        with patch.dict(os.environ, {
            "AWS_EXPECTED_ACCOUNT_ID": "123456789012",
            "EKS_LEASE_BUCKET": "state-bucket",
            "EKS_LEASE_KEY": "ci/eks-endpoint-lease-autoflow-homolog.json",
        }), patch("eks_endpoint_access.subprocess.run", side_effect=write_unknown_lease):
            with self.assertRaisesRegex(RuntimeError, "desconhecido"):
                endpoint.load_lease()

    def test_reconcile_does_not_touch_eks_for_unknown_lease_content(self):
        def write_unknown_lease(arguments, **_kwargs):
            Path(arguments[-3]).write_text('{"temporary_cidr":"0.0.0.0/0"}', encoding="utf-8")
            return subprocess.CompletedProcess(arguments, 0, stdout="{}")

        with patch.dict(os.environ, {
            "AWS_EXPECTED_ACCOUNT_ID": "123456789012",
            "EKS_LEASE_BUCKET": "state-bucket",
            "EKS_LEASE_KEY": "ci/eks-endpoint-lease-autoflow-homolog.json",
        }), patch("eks_endpoint_access.subprocess.run", side_effect=write_unknown_lease), patch.object(endpoint, "aws") as eks:
            with self.assertRaisesRegex(RuntimeError, "desconhecido"):
                endpoint.reconcile("eks-autoflow", "us-east-1")
        eks.assert_not_called()

    def test_valid_lease_is_read_from_expected_bucket_owner(self):
        expected = {"temporary_cidr": "198.51.100.23/32", "original_cidrs": ["189.6.8.245/32"]}

        def write_lease(arguments, **_kwargs):
            Path(arguments[-3]).write_text('{"temporary_cidr":"198.51.100.23/32","original_cidrs":["189.6.8.245/32"]}', encoding="utf-8")
            return subprocess.CompletedProcess(arguments, 0, stdout="{}")

        with patch.dict(os.environ, {
            "AWS_EXPECTED_ACCOUNT_ID": "123456789012",
            "EKS_LEASE_BUCKET": "state-bucket",
            "EKS_LEASE_KEY": "ci/eks-endpoint-lease-autoflow-homolog.json",
        }), patch("eks_endpoint_access.subprocess.run", side_effect=write_lease) as aws:
            self.assertEqual(endpoint.load_lease(), expected)
        command = aws.call_args.args[0]
        self.assertEqual(command[command.index("--expected-bucket-owner") + 1], "123456789012")

    def test_lease_write_is_conditional_and_existing_lease_is_never_overwritten(self):
        lease = {"temporary_cidr": "198.51.100.23/32", "original_cidrs": ["189.6.8.245/32"]}
        conflict = subprocess.CompletedProcess(
            ["aws", "s3api", "put-object"],
            1,
            stderr="An error occurred (PreconditionFailed) when calling the PutObject operation: SECRET_VALUE",
        )
        with patch.dict(os.environ, {
            "AWS_EXPECTED_ACCOUNT_ID": "123456789012",
            "EKS_LEASE_BUCKET": "state-bucket",
            "EKS_LEASE_KEY": "ci/eks-endpoint-lease-autoflow-homolog.json",
        }), patch("eks_endpoint_access.subprocess.run", return_value=conflict) as aws:
            with self.assertRaisesRegex(RuntimeError, "recusada a sobrescrita") as raised:
                endpoint.save_lease(lease)
        self.assertNotIn("SECRET_VALUE", str(raised.exception))
        command = aws.call_args.args[0]
        self.assertEqual(command[command.index("--if-none-match") + 1], "*")
        self.assertIn("--expected-bucket-owner", command)

    def test_lease_configuration_requires_bucket_key_and_expected_account(self):
        with patch.dict(os.environ, {"EKS_LEASE_BUCKET": "", "EKS_LEASE_KEY": "", "AWS_EXPECTED_ACCOUNT_ID": ""}):
            with self.assertRaisesRegex(RuntimeError, "EKS_LEASE_BUCKET e EKS_LEASE_KEY"):
                endpoint.location()
        with patch.dict(os.environ, {
            "EKS_LEASE_BUCKET": "state-bucket",
            "EKS_LEASE_KEY": "ci/lease.json",
            "AWS_EXPECTED_ACCOUNT_ID": "invalid",
        }):
            with self.assertRaisesRegex(RuntimeError, "AWS_EXPECTED_ACCOUNT_ID"):
                endpoint.location()


class EndpointTests(unittest.TestCase):

    def test_aws_update_failure_reports_service_error_without_traceback(self):
        self.aws_patch.stop()
        failure = subprocess.CalledProcessError(
            254,
            ["aws", "eks", "update-cluster-config"],
            stderr="aws: [ERROR]: An error occurred (ResourceInUseException) when calling the UpdateClusterConfig operation: update in progress",
        )
        with patch("eks_endpoint_access.subprocess.run", side_effect=failure):
            with self.assertRaisesRegex(RuntimeError, "ResourceInUseException.*update in progress"):
                endpoint.aws("eks", "update-cluster-config", "--name", "eks-autoflow")

    def setUp(self):
        self.cidrs = {"189.6.8.245/32", "203.0.113.9/32"}
        self.calls = []
        self.lease = None
        self.pending_updates = []
        self.update_status = {}
        self.update_conflicts = 0
        self.update_error = None
        self.conflict_cidrs = set()

        def fake_aws(*args):
            self.calls.append(args)
            if args[1] == "describe-cluster":
                return {"cluster": {"resourcesVpcConfig": {"publicAccessCidrs": sorted(self.cidrs)}}}
            if args[1] == "update-cluster-config":
                if self.update_error:
                    raise self.update_error
                if self.update_conflicts:
                    self.update_conflicts -= 1
                    self.cidrs.update(self.conflict_cidrs)
                    raise endpoint.AwsOperationError("eks update-cluster-config", subprocess.CalledProcessError(
                        254, "aws", stderr="An error occurred (ResourceInUseException) when calling UpdateClusterConfig: update in progress"
                    ))
                values = next(value for value in args if value.startswith("publicAccessCidrs="))
                self.cidrs = set(values.split("=", 1)[1].split(","))
                return {"update": {"id": "upd-1"}}
            if args[1] == "describe-update":
                return {"update": {"status": self.update_status.get(args[-1], "Successful")}}
            if args[1] == "list-updates":
                return {"updateIds": list(self.pending_updates)}
            raise AssertionError(args)

        self.aws_patch = patch.object(endpoint, "aws", side_effect=fake_aws)
        self.aws_patch.start()
        self.addCleanup(self.aws_patch.stop)
        self.save_patch = patch.object(endpoint, "save_lease", side_effect=lambda lease: setattr(self, "lease", lease))
        self.load_patch = patch.object(endpoint, "load_lease", side_effect=lambda: self.lease)
        self.remove_patch = patch.object(endpoint, "remove_lease", side_effect=lambda: setattr(self, "lease", None))
        for mocked in (self.save_patch, self.load_patch, self.remove_patch):
            mocked.start()
            self.addCleanup(mocked.stop)

    @patch("eks_endpoint_access.time.sleep")
    def test_authorize_adds_only_runner_32_and_preserves_existing(self, _sleep):
        endpoint.authorize("eks-autoflow", "us-east-1", "198.51.100.23")
        self.assertEqual(self.cidrs, {"189.6.8.245/32", "203.0.113.9/32", "198.51.100.23/32"})
        self.assertEqual(self.lease["original_cidrs"], ["189.6.8.245/32", "203.0.113.9/32"])

    @patch("eks_endpoint_access.time.sleep")
    def test_cleanup_waits_and_restores_the_original_allowlist(self, _sleep):
        self.cidrs.add("198.51.100.23/32")
        self.cidrs.add("192.0.2.19/32")
        endpoint.cleanup("eks-autoflow", "us-east-1", {
            "temporary_cidr": "198.51.100.23/32",
            "original_cidrs": ["189.6.8.245/32", "203.0.113.9/32"],
        })
        self.assertEqual(self.cidrs, {"189.6.8.245/32", "203.0.113.9/32", "192.0.2.19/32"})
        self.assertEqual([args[1] for args in self.calls].count("describe-update"), 1)

    def test_missing_permanent_cidr_fails_without_endpoint_update(self):
        self.cidrs.remove("189.6.8.245/32")
        with self.assertRaisesRegex(RuntimeError, "189.6.8.245/32"):
            endpoint.authorize("eks-autoflow", "us-east-1", "198.51.100.23")
        self.assertFalse(any(args[1] == "update-cluster-config" for args in self.calls))

    @patch("eks_endpoint_access.time.sleep")
    def test_waits_for_existing_update_before_adding_runner_cidr(self, _sleep):
        self.pending_updates = ["existing-update"]
        endpoint.authorize("eks-autoflow", "us-east-1", "198.51.100.23")
        operations = [args[1] for args in self.calls]
        self.assertLess(operations.index("list-updates"), operations.index("update-cluster-config"))
        self.assertIn("198.51.100.23/32", self.cidrs)

    @patch("eks_endpoint_access.time.sleep")
    def test_retries_only_resource_in_use_conflicts(self, _sleep):
        self.update_conflicts = 1
        self.conflict_cidrs = {"203.0.113.44/32"}
        endpoint.authorize("eks-autoflow", "us-east-1", "198.51.100.23")
        self.assertEqual([args[1] for args in self.calls].count("update-cluster-config"), 2)
        self.assertIn("198.51.100.23/32", self.cidrs)
        self.assertIn("203.0.113.44/32", self.cidrs)

    @patch("eks_endpoint_access.time.sleep")
    def test_resource_in_use_retries_are_bounded(self, _sleep):
        self.update_conflicts = 99
        with patch.object(endpoint, "UPDATE_RETRIES", 2):
            with self.assertRaisesRegex(endpoint.AwsOperationError, "ResourceInUseException"):
                endpoint.authorize("eks-autoflow", "us-east-1", "198.51.100.23")
        self.assertEqual([args[1] for args in self.calls].count("update-cluster-config"), 2)

    @patch("eks_endpoint_access.time.sleep")
    def test_permission_error_is_not_retried_or_ignored(self, _sleep):
        self.update_error = endpoint.AwsOperationError("eks update-cluster-config", subprocess.CalledProcessError(
            254, "aws", stderr="An error occurred (AccessDeniedException) when calling UpdateClusterConfig: denied"
        ))
        with self.assertRaisesRegex(endpoint.AwsOperationError, "AccessDeniedException"):
            endpoint.authorize("eks-autoflow", "us-east-1", "198.51.100.23")
        self.assertEqual([args[1] for args in self.calls].count("update-cluster-config"), 1)
        self.assertEqual(self.cidrs, {"189.6.8.245/32", "203.0.113.9/32"})
        self.assertIsNotNone(self.lease)

    @patch("eks_endpoint_access.time.sleep")
    def test_active_update_timeout_keeps_temporary_cidr_and_lease(self, _sleep):
        self.cidrs.add("198.51.100.23/32")
        self.pending_updates = ["stuck-update"]
        self.update_status["stuck-update"] = "InProgress"
        self.lease = {
            "temporary_cidr": "198.51.100.23/32", "original_cidrs": ["189.6.8.245/32"]
        }
        with patch.object(endpoint, "UPDATE_WAIT_ATTEMPTS", 2):
            with self.assertRaisesRegex(RuntimeError, "Tempo limite aguardando atualização"):
                endpoint.cleanup("eks-autoflow", "us-east-1", self.lease)
        self.assertIn("198.51.100.23/32", self.cidrs)
        self.assertIsNotNone(self.lease)
        self.assertFalse(any(args[1] == "update-cluster-config" for args in self.calls))

    def test_rejects_unrestricted_cidr(self):
        self.cidrs.add("0.0.0.0/0")
        with self.assertRaisesRegex(RuntimeError, "CIDR público irrestrito"):
            endpoint.authorize("eks-autoflow", "us-east-1", "198.51.100.23")
        self.assertFalse(any(args[1] == "update-cluster-config" for args in self.calls))

    @patch("eks_endpoint_access.time.sleep")
    def test_reconcile_cleans_lease_after_interrupted_run(self, _sleep):
        self.lease = {"temporary_cidr": "198.51.100.23/32", "original_cidrs": sorted(self.cidrs)}
        self.cidrs.add("198.51.100.23/32")
        endpoint.reconcile("eks-autoflow", "us-east-1")
        self.assertEqual(self.cidrs, {"189.6.8.245/32", "203.0.113.9/32"})
        self.assertIsNone(self.lease)

    def test_workflow_serializes_deploys_and_uses_hosted_runners(self):
        from pathlib import Path
        workflow = Path(__file__).parents[1] / ".github/workflows/pipeline.yml"
        text = workflow.read_text(encoding="utf-8")
        self.assertIn("group: autoflow-eks-endpoint-access-${{ github.ref }}", text)
        self.assertNotIn("group: autoflow-application-${{ github.ref }}", text)
        self.assertIn("cancel-in-progress: false", text)
        environment_lease = "EKS_LEASE_KEY: ci/eks-endpoint-lease-autoflow-${{ github.ref == 'refs/heads/main' && 'production' || 'homolog' }}.json"
        self.assertEqual(text.count(environment_lease), 2)
        self.assertNotIn("self-hosted", text)
        self.assertIn("workflow_dispatch", text)


if __name__ == "__main__":
    unittest.main()
