import json
from pathlib import Path
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import pipeline_resource_discovery as discovery
from verify_aws_account import validate_account


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / ".github/workflows/pipeline.yml").read_text(encoding="utf-8")


class PipelineResourceDiscoveryTests(unittest.TestCase):
    def test_environment_maps_to_its_dedicated_cluster_and_api(self):
        self.assertEqual(
            discovery.ENVIRONMENTS["homolog"],
            {"branch": "develop", "cluster": "eks-autoflow", "api": "autoflow-homolog"},
        )
        self.assertEqual(
            discovery.ENVIRONMENTS["production"],
            {"branch": "main", "cluster": "eks-autoflow-production", "api": "autoflow-production"},
        )

    def test_two_clusters_in_one_account_select_only_the_environment_cluster(self):
        responses = [
            SimpleNamespace(stdout="us-east-1\n"),
            SimpleNamespace(stdout=json.dumps({"clusters": ["eks-autoflow", "eks-autoflow-production", "eks-shared"]})),
            SimpleNamespace(stdout=json.dumps({
                "Items": [
                    {"Name": "autoflow-homolog", "ApiEndpoint": "https://homolog.example.invalid"},
                    {"Name": "autoflow-production", "ApiEndpoint": "https://production.example.invalid"},
                ]
            })),
        ]
        with patch.object(discovery.subprocess, "run", side_effect=responses) as aws:
            self.assertEqual(
                discovery.discover_resources("production", "us-east-1"),
                ("eks-autoflow-production", "https://production.example.invalid"),
            )
        self.assertEqual(aws.call_count, 3)

    def test_homolog_discovers_its_cluster_and_gateway_when_production_also_exists(self):
        responses = [
            SimpleNamespace(stdout="us-east-1\n"),
            SimpleNamespace(stdout=json.dumps({"clusters": ["eks-autoflow", "eks-autoflow-production"]})),
            SimpleNamespace(stdout=json.dumps({
                "Items": [
                    {"Name": "autoflow-homolog", "ApiEndpoint": "https://homolog.example.invalid"},
                    {"Name": "autoflow-production", "ApiEndpoint": "https://production.example.invalid"},
                ]
            })),
        ]
        with patch.object(discovery.subprocess, "run", side_effect=responses):
            self.assertEqual(
                discovery.discover_resources("homolog", "us-east-1"),
                ("eks-autoflow", "https://homolog.example.invalid"),
            )

    def test_accounts_may_differ_but_each_environment_fails_closed_on_mismatch(self):
        homolog_account = "1" * 12
        production_account = "2" * 12
        validate_account(homolog_account, homolog_account)
        validate_account(production_account, production_account)
        with self.assertRaisesRegex(ValueError, "não corresponde"):
            validate_account(production_account, homolog_account)

    def test_state_keys_are_distinct_and_production_isolated_by_prefix(self):
        discovery.validate_state_configuration(
            "homolog", "state-bucket", "application/autoflow/terraform.tfstate", "rds/terraform.tfstate"
        )
        discovery.validate_state_configuration(
            "production", "state-bucket", "production/application/terraform.tfstate", "production/rds/terraform.tfstate"
        )
        with self.assertRaisesRegex(discovery.DiscoveryError, "States distintos"):
            discovery.validate_state_configuration("production", "state-bucket", "production/app.tfstate", "production/app.tfstate")
        with self.assertRaisesRegex(discovery.DiscoveryError, "prefixo isolado"):
            discovery.validate_state_configuration("production", "state-bucket", "application/app.tfstate", "rds/rds.tfstate")

    def test_pull_requests_and_feature_branches_cannot_select_deploy_environment(self):
        for event, ref, environment in (
            ("pull_request", "refs/pull/12/merge", "homolog"),
            ("push", "refs/heads/feature/change", "homolog"),
            ("workflow_dispatch", "refs/heads/feat/change", "production"),
            ("push", "refs/heads/main", "homolog"),
        ):
            with self.subTest(event=event, ref=ref, environment=environment), self.assertRaises(discovery.DiscoveryError):
                discovery.validate_context(event, ref, environment)

    def test_apply_is_limited_to_push_or_dispatch_on_develop_and_main(self):
        apply = WORKFLOW.split("  deploy-apply:", 1)[1].split("  smoke-tests:", 1)[0]
        self.assertIn("github.event_name == 'push' || github.event_name == 'workflow_dispatch'", apply)
        self.assertIn("github.ref == 'refs/heads/develop'", apply)
        self.assertIn("github.ref == 'refs/heads/main'", apply)
        self.assertNotIn("pull_request", apply)
        self.assertNotIn("feature/", apply)

    def test_aws_account_validation_precedes_resource_discovery(self):
        for job_name, following_job in (("  deploy-plan:", "\n  deploy-apply:"), ("  deploy-apply:", "\n  smoke-tests:")):
            job = WORKFLOW.split(job_name, 1)[1].split(following_job, 1)[0]
            self.assertLess(job.index("python scripts/verify_aws_account.py"), job.index("pipeline_resource_discovery.py discover"))

    def test_plan_and_apply_bind_environment_region_bucket_and_both_state_keys(self):
        plan = WORKFLOW.split("  deploy-plan:", 1)[1].split("\n  deploy-apply:", 1)[0]
        apply = WORKFLOW.split("  deploy-apply:", 1)[1].split("\n  smoke-tests:", 1)[0]
        selected_environment = "environment: ${{ github.ref == 'refs/heads/main' && 'production' || 'homolog' }}"
        for job in (plan, apply):
            self.assertIn(selected_environment, job)
            self.assertIn("AWS_REGION: ${{ vars.AWS_REGION }}", job)
            self.assertIn("TF_STATE_BUCKET: ${{ vars.AWS_TERRAFORM_STATE_BUCKET }}", job)
            self.assertIn("TF_STATE_KEY: ${{ vars.AWS_TF_STATE_KEY }}", job)
            self.assertIn("AWS_RDS_STATE_KEY: ${{ vars.AWS_RDS_STATE_KEY }}", job)
            self.assertIn("TF_VAR_rds_state_key: ${{ vars.AWS_RDS_STATE_KEY }}", job)
            self.assertIn("--expected-bucket-owner \"$AWS_EXPECTED_ACCOUNT_ID\"", job)
            self.assertIn("pipeline_resource_discovery.py validate-config", job)
            self.assertIn("pipeline_resource_discovery.py discover", job)

    def test_push_never_authorizes_destructive_terraform_operations(self):
        destructive_setting = "ALLOW_DESTRUCTIVE: ${{ github.event_name == 'workflow_dispatch' && inputs.allow_destructive && 'true' || 'false' }}"
        self.assertEqual(WORKFLOW.count(destructive_setting), 2)
        self.assertNotIn("github.event_name == 'push' && 'true'", WORKFLOW)

    def test_docker_publish_uses_secret_and_supports_both_environments(self):
        publish = WORKFLOW.split("  docker-publish:", 1)[1].split("\n  deploy-plan:", 1)[0]
        self.assertIn("password: ${{ secrets.DOCKERHUB_PASSWORD }}", publish)
        self.assertIn("username: ${{ vars.DOCKERHUB_USERNAME }}", publish)
        self.assertIn("refs/heads/develop", publish)
        self.assertIn("refs/heads/main", publish)
        self.assertNotIn("pull_request", publish)

    def test_new_relic_sync_is_available_for_enabled_environment(self):
        apply = WORKFLOW.split("  deploy-apply:", 1)[1].split("\n  smoke-tests:", 1)[0]
        self.assertIn("ENABLE_NEWRELIC_APM: ${{ vars.ENABLE_NEWRELIC_APM }}", apply)
        self.assertIn("NEW_RELIC_LICENSE_KEY: ${{ secrets.NEW_RELIC_LICENSE_KEY }}", apply)
        self.assertIn("if: env.ENABLE_NEWRELIC_APM == 'true'", apply)
        self.assertNotIn("GITHUB_DEPLOYMENT_ENVIRONMENT == 'homolog'", apply)


if __name__ == "__main__":
    unittest.main()
