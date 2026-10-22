import unittest

from terraform_plan_metadata import MetadataError, build_metadata


BASE_ENV = {
    "AWS_REGION": "us-east-1",
    "TF_STATE_BUCKET": "autoflow-state-example",
    "TF_STATE_KEY": "application/autoflow/terraform.tfstate",
    "TF_VAR_backend_image_tag": "commit-a",
    "TF_VAR_gateway_cors_origins": "[]",
}


class TerraformPlanMetadataTests(unittest.TestCase):
    def metadata(self, env=None, lockfile=b"provider lock", commit="a" * 40):
        return build_metadata(env or BASE_ENV, "1.16.5", lockfile, commit)

    def test_records_commit_version_backend_and_lockfile_without_variable_values(self):
        metadata = self.metadata()
        self.assertEqual(metadata["commit_sha"], "a" * 40)
        self.assertEqual(metadata["terraform_version"], "1.16.5")
        self.assertEqual(metadata["backend"]["key"], BASE_ENV["TF_STATE_KEY"])
        self.assertNotIn("commit-a", str(metadata))

    def test_same_variables_in_different_environment_order_have_same_fingerprint(self):
        reordered = dict(reversed(list(BASE_ENV.items())))
        self.assertEqual(
            self.metadata()["terraform_variables_sha256"],
            self.metadata(env=reordered)["terraform_variables_sha256"],
        )

    def test_variable_backend_commit_or_lockfile_changes_invalidate_metadata(self):
        baseline = self.metadata()
        changed_var = dict(BASE_ENV, TF_VAR_backend_image_tag="commit-b")
        changed_backend = dict(BASE_ENV, TF_STATE_KEY="application/other/terraform.tfstate")
        self.assertNotEqual(baseline["terraform_variables_sha256"], self.metadata(env=changed_var)["terraform_variables_sha256"])
        self.assertNotEqual(baseline["backend"], self.metadata(env=changed_backend)["backend"])
        self.assertNotEqual(baseline["commit_sha"], self.metadata(commit="b" * 40)["commit_sha"])
        self.assertNotEqual(baseline["provider_lock_sha256"], self.metadata(lockfile=b"different lock")["provider_lock_sha256"])

    def test_missing_backend_or_terraform_cli_overrides_fail_closed(self):
        with self.assertRaises(MetadataError):
            self.metadata(env={"AWS_REGION": "us-east-1"})
        with self.assertRaises(MetadataError):
            self.metadata(env=dict(BASE_ENV, TF_CLI_ARGS_plan="-destroy"))


if __name__ == "__main__":
    unittest.main()
