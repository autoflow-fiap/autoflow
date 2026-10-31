from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"


def section(content: str, job: str, next_job: str | None = None) -> str:
    start = content.index(f"\n  {job}:\n") + 1
    end = content.index(f"\n  {next_job}:\n", start) if next_job else len(content)
    return content[start:end]


class CiCdWorkflowMatrixTests(unittest.TestCase):
    def test_ci_triggers_and_local_builds_do_not_publish_or_deploy(self):
        ci = (WORKFLOWS / "ci.yml").read_text(encoding="utf-8")
        triggers = ci.split("permissions:", 1)[0]
        self.assertIn("branches: [develop, main, 'feature/**']", triggers)
        self.assertIn("branches: [develop, main]", triggers)
        self.assertNotIn("workflow_dispatch:", triggers)
        self.assertNotIn("docker-publish:", ci)
        self.assertNotIn("build-push-action", ci)
        self.assertNotIn("docker/login-action", ci)
        self.assertNotIn("aws-actions/configure-aws-credentials", ci)
        self.assertNotRegex(ci, r"(?im)^\s*run:.*\bterraform\s+(?:plan|apply|destroy)\b")
        docker_build = section(ci, "docker-build", "snyk-scan")
        self.assertIn("needs: [backend-build, frontend-build, terraform-static]", docker_build)
        self.assertIn("docker build --file backend/Dockerfile", docker_build)
        self.assertIn("docker build --file frontend/Dockerfile", docker_build)
        self.assertIn("continue-on-error: true", ci)
        self.assertIn("backend-test-reports", ci)
        self.assertIn("frontend-coverage", ci)

    def test_dispatch_plan_only_never_publishes_or_applies(self):
        content = (WORKFLOWS / "pipeline.yml").read_text(encoding="utf-8")
        triggers = content.split("permissions:", 1)[0]
        publish = section(content, "docker-publish", "deploy-plan")
        plan = section(content, "deploy-plan", "deploy-apply")
        apply = section(content, "deploy-apply", "smoke-tests")
        self.assertIn("on:\n  workflow_dispatch:", triggers)
        self.assertNotIn("  push:", triggers)
        self.assertNotIn("  pull_request:", triggers)
        self.assertIn("inputs.apply == true", publish)
        self.assertIn("environment: ${{ github.ref == 'refs/heads/main' && 'production' || 'homolog' }}", publish)
        self.assertIn("inputs.apply != true && needs.docker-publish.result == 'skipped'", plan)
        self.assertIn("inputs.apply == true && needs.docker-publish.result == 'success'", plan)
        self.assertIn("github.event_name == 'workflow_dispatch'", plan)
        self.assertIn("github.event_name == 'workflow_dispatch'", apply)
        self.assertIn("inputs.apply == true", apply)
        self.assertIn("needs: deploy-plan", apply)
        self.assertIn("required_reviewers", apply)
        self.assertIn("TERRAFORM_APPLY_APPROVAL_CONFIGURED", apply)
        self.assertIn("github.ref_protected", apply)
        self.assertNotIn("github.event_name == 'push'", publish + plan + apply)
        self.assertNotIn("feature/", publish + plan + apply)

    def test_encrypted_plan_and_external_state_guards_remain_in_cd(self):
        content = (WORKFLOWS / "pipeline.yml").read_text(encoding="utf-8")
        plan = section(content, "deploy-plan", "deploy-apply")
        apply = section(content, "deploy-apply", "smoke-tests")
        for marker in ("infra.tfplan.enc", "plan-metadata.json", "newrelic-inputs.sha256"):
            self.assertIn(marker, plan)
            self.assertIn(marker, apply)
        self.assertIn("sha256sum --check --status", apply)
        self.assertIn("terraform_plan_metadata.py", plan)
        self.assertIn("terraform_plan_metadata.py", apply)
        self.assertIn("--expected-bucket-owner", plan)
        self.assertIn("--expected-bucket-owner", apply)
        self.assertIn("AWS_RDS_STATE_KEY", plan)
        self.assertIn("AWS_RDS_STATE_KEY", apply)
        self.assertNotRegex(content, r"(?<!\d)\d{12}(?!\d)")

    def test_smoke_tests_are_dispatch_only_and_do_not_follow_pushes(self):
        content = (WORKFLOWS / "pipeline.yml").read_text(encoding="utf-8")
        smoke = section(content, "smoke-tests")
        self.assertIn("github.event_name == 'workflow_dispatch'", smoke)
        self.assertNotIn("github.event_name == 'push'", smoke)


if __name__ == "__main__":
    unittest.main()
