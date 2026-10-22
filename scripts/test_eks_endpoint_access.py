import unittest
import subprocess
from unittest.mock import patch

import eks_endpoint_access as endpoint


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

        def fake_aws(*args):
            self.calls.append(args)
            if args[1] == "describe-cluster":
                return {"cluster": {"resourcesVpcConfig": {"publicAccessCidrs": sorted(self.cidrs)}}}
            if args[1] == "update-cluster-config":
                values = next(value for value in args if value.startswith("publicAccessCidrs="))
                self.cidrs = set(values.split("=", 1)[1].split(","))
                return {"update": {"id": "upd-1"}}
            if args[1] == "describe-update":
                return {"update": {"status": "Successful"}}
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
        endpoint.cleanup("eks-autoflow", "us-east-1", {
            "temporary_cidr": "198.51.100.23/32",
            "original_cidrs": ["189.6.8.245/32", "203.0.113.9/32"],
        })
        self.assertEqual(self.cidrs, {"189.6.8.245/32", "203.0.113.9/32"})
        self.assertEqual([args[1] for args in self.calls].count("describe-update"), 1)

    def test_missing_permanent_cidr_fails_without_endpoint_update(self):
        self.cidrs.remove("189.6.8.245/32")
        with self.assertRaisesRegex(RuntimeError, "189.6.8.245/32"):
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
        self.assertIn("group: autoflow-application-develop", text)
        self.assertIn("cancel-in-progress: false", text)
        self.assertNotIn("self-hosted", text)
        self.assertIn("workflow_dispatch", text)


if __name__ == "__main__":
    unittest.main()
