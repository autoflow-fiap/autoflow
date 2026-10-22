import json
import os
import unittest
from unittest.mock import patch

import eks_preflight


IDENTITY = {"Account": "123456789012"}
ACTIVE_CLUSTER = {"cluster": {"status": "ACTIVE"}}
ACTIVE_GROUP = {"nodegroup": {"status": "ACTIVE", "health": {"issues": []}}}


def node(ready):
    return {"status": {"conditions": [{"type": "Ready", "status": "True" if ready else "False"}]}}


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {
            "AWS_EXPECTED_ACCOUNT_ID": "123456789012", "AWS_REGION": "us-east-1",
            "EKS_PREFLIGHT_TIMEOUT_SECONDS": "1", "EKS_PREFLIGHT_POLL_INTERVAL_SECONDS": "1",
        }, clear=False)
        self.env.start()
        self.addCleanup(self.env.stop)
        self.sequence = [json.dumps(IDENTITY), json.dumps(ACTIVE_CLUSTER), json.dumps(ACTIVE_GROUP), "arn:aws:eks:us-east-1:123456789012:cluster/eks-autoflow", json.dumps({"items": [node(True), node(True)]})]

    def execute(self, **kwargs):
        merged = dict(kwargs)
        side_effect = merged.pop("side_effect", None)
        with patch.object(eks_preflight, "run", side_effect=side_effect or self.sequence.copy()), patch.object(eks_preflight.time, "sleep"):
            return eks_preflight.main()

    def test_wrong_account(self):
        with patch.dict(os.environ, {"AWS_EXPECTED_ACCOUNT_ID": "000000000000"}), patch.object(eks_preflight, "run", return_value=json.dumps(IDENTITY)):
            with self.assertRaisesRegex(eks_preflight.PreflightError, "Conta AWS incorreta"):
                eks_preflight.main()

    def test_invalid_or_expired_credentials(self):
        with patch.object(eks_preflight, "run", side_effect=eks_preflight.PreflightError("Credenciais AWS inválidas/expiradas")):
            with self.assertRaisesRegex(eks_preflight.PreflightError, "Credenciais AWS"):
                eks_preflight.main()

    def test_cluster_unavailable_times_out(self):
        with patch.object(eks_preflight, "run", side_effect=[json.dumps(IDENTITY), eks_preflight.PreflightError("Falha em aws eks describe-cluster: indisponível")]), patch.object(eks_preflight.time, "monotonic", side_effect=[0, 0, 0, 2, 2]), patch.object(eks_preflight.time, "sleep"):
            with self.assertRaisesRegex(eks_preflight.PreflightError, "Timeout"):
                eks_preflight.main()

    def test_degraded_nodegroup_reports_health(self):
        degraded = {"nodegroup": {"status": "DEGRADED", "health": {"issues": [{"code": "NodeCreationFailure"}]}}}
        with patch.object(eks_preflight, "run", side_effect=[json.dumps(IDENTITY), json.dumps(ACTIVE_CLUSTER), json.dumps(degraded)]), patch.object(eks_preflight.time, "monotonic", side_effect=[0, 0, 0, 2, 2]), patch.object(eks_preflight.time, "sleep"):
            with self.assertRaisesRegex(eks_preflight.PreflightError, "NodeCreationFailure"):
                eks_preflight.main()

    def test_zero_nodes_times_out(self):
        self.sequence[-1] = json.dumps({"items": []})
        with patch.object(eks_preflight, "run", side_effect=self.sequence), patch.object(eks_preflight.time, "monotonic", side_effect=[0, 0, 0, 2, 2]), patch.object(eks_preflight.time, "sleep"):
            with self.assertRaisesRegex(eks_preflight.PreflightError, "Nós insuficientes"):
                eks_preflight.main()

    def test_one_ready_node_times_out(self):
        self.sequence[-1] = json.dumps({"items": [node(True), node(False)]})
        with patch.object(eks_preflight, "run", side_effect=self.sequence), patch.object(eks_preflight.time, "monotonic", side_effect=[0, 0, 0, 2, 2]), patch.object(eks_preflight.time, "sleep"):
            with self.assertRaisesRegex(eks_preflight.PreflightError, "1 Ready"):
                eks_preflight.main()

    def test_two_ready_nodes_pass(self):
        self.assertEqual(self.execute(), 0)

    def test_command_failure_is_reported(self):
        with patch.object(eks_preflight, "run", side_effect=eks_preflight.PreflightError("Comando não encontrado: kubectl")), patch.object(eks_preflight.time, "monotonic", side_effect=[0, 0, 0, 2, 2]), patch.object(eks_preflight.time, "sleep"):
            with self.assertRaisesRegex(eks_preflight.PreflightError, "kubectl"):
                eks_preflight.main()

    def test_wrong_context_fails_immediately(self):
        values = self.sequence[:3] + ["outro-cluster"]
        with patch.object(eks_preflight, "run", side_effect=values):
            with self.assertRaisesRegex(eks_preflight.PreflightError, "Contexto kubectl incorreto"):
                eks_preflight.main()

    def test_pipeline_plans_then_applies_on_hosted_runner_after_environment_gate(self):
        from pathlib import Path
        workflow = Path(__file__).parents[1] / ".github" / "workflows" / "pipeline.yml"
        content = workflow.read_text(encoding="utf-8")
        plan = content.split("  deploy-plan:", 1)[1].split("  deploy-apply:", 1)[0]
        apply = content.split("  deploy-apply:", 1)[1].split("  smoke-tests:", 1)[0]
        self.assertIn("runs-on: ubuntu-latest", plan)
        self.assertNotIn("environment:", plan)
        self.assertIn("github.event_name == 'push'", plan)
        self.assertIn("EKS_LEASE_KEY: ci/eks-endpoint-lease.json", plan)
        self.assertIn("eks_endpoint_access.py authorize", plan)
        for orphaned_resource in (
            "kubectl get deployment autoflow-backend",
            "kubectl get deployment autoflow-frontend",
            "kubectl get service autoflow-backend",
            "kubectl get service autoflow-frontend",
            "kubectl get configmap autoflow-config",
            "kubectl get service autoflow-backend-gateway",
            "kubectl get service autoflow-frontend-gateway",
            "kubectl get configmap autoflow-gateway-nginx",
        ):
            self.assertIn(orphaned_resource, plan)
        self.assertIn("terraform plan -input=false -no-color -out=infra.tfplan", plan)
        self.assertIn("python ../scripts/review_terraform_plan.py", plan)
        self.assertIn("actions/upload-artifact@v4", plan)
        self.assertIn("retention-days: 1", plan)
        self.assertIn("openssl enc -aes-256-cbc", plan)
        self.assertIn("plan_sha256", plan)
        self.assertIn("runs-on: ubuntu-latest", apply)
        self.assertIn("environment: homolog", apply)
        self.assertIn("select(.type == \"required_reviewers\")", apply)
        self.assertIn("github.event_name == 'push'", apply)
        self.assertIn("actions/download-artifact@v4", apply)
        self.assertIn("sha256sum --check --status", apply)
        self.assertIn("cmp --silent", apply)
        self.assertIn("openssl enc -d -aes-256-cbc", apply)
        self.assertIn("terraform apply -input=false infra.tfplan", apply)
        self.assertNotIn("terraform plan", apply)
        self.assertIn("allow_destructive", content)
        push_authorization = "ALLOW_DESTRUCTIVE: ${{ github.event_name == 'push' && 'true' || (github.event_name == 'workflow_dispatch' && inputs.allow_destructive && 'true' || 'false') }}"
        self.assertIn(push_authorization, plan)
        self.assertIn(push_authorization, apply)
        self.assertIn("review_terraform_plan.py", apply)
        self.assertIn("kubectl rollout status deployment/autoflow-backend", apply)
        self.assertNotIn("smoke_api_gateway.py", apply)
        smoke = content.split("  smoke-tests:", 1)[1]
        self.assertIn("needs: deploy-apply", smoke)
        self.assertIn("runs-on: ubuntu-latest", smoke)
        self.assertIn("smoke_api_gateway.py", smoke)
        self.assertIn("inputs.smoke_tests_only", smoke)
        self.assertIn("API_GATEWAY_CORS_ORIGINS: ${{ vars.API_GATEWAY_CORS_ORIGINS }}", smoke)
        self.assertIn("eks_endpoint_access.py cleanup", apply)
        self.assertIn("kubectl rollout status deployment/autoflow-frontend", apply)
        self.assertNotIn("deployment-blocked-private-eks:", content)
        self.assertIn("ref: ${{ github.sha }}", plan)
        self.assertIn("ref: ${{ github.sha }}", apply)
        self.assertIn("terraform_plan_metadata.py", plan)
        self.assertIn("terraform_plan_metadata.py", apply)

    def test_images_are_published_only_from_develop_push_with_commit_sha(self):
        from pathlib import Path
        workflow = Path(__file__).parents[1] / ".github" / "workflows" / "pipeline.yml"
        content = workflow.read_text(encoding="utf-8")
        publish = content.split("  docker-publish:", 1)[1].split("  deploy-plan:", 1)[0]
        self.assertIn("github.event_name == 'push'", publish)
        self.assertIn("refs/heads/develop", publish)
        self.assertIn("needs: [backend-build, frontend-build, terraform-static]", publish)
        self.assertNotIn("snyk-scan", publish)
        self.assertNotIn("environment:", publish)
        self.assertIn("${{ github.sha }}", publish)
        self.assertIn("username: ${{ vars.DOCKERHUB_USERNAME }}", publish)
        self.assertIn("password: ${{ secrets.DOCKERHUB_PASSWORD }}", publish)
        self.assertNotIn("pull_request", publish)
        self.assertNotIn(":latest", content)

    def test_snyk_failures_are_non_blocking_and_uploaded_as_alerts(self):
        from pathlib import Path
        workflow = Path(__file__).parents[1] / ".github" / "workflows" / "pipeline.yml"
        content = workflow.read_text(encoding="utf-8")
        snyk_job = content.split("  snyk-scan:", 1)[1].split("  docker-publish:", 1)[0]
        self.assertEqual(snyk_job.count("continue-on-error: true"), 2)
        self.assertIn("snyk-open-source.sarif", snyk_job)
        self.assertIn("snyk-container.sarif", snyk_job)
        self.assertIn("github/codeql-action/upload-sarif@v3", snyk_job)


if __name__ == "__main__":
    unittest.main()
