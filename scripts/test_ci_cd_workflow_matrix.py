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

    def test_opt_in_push_plan_and_manual_apply_only_publish_after_approval(self):
        content = (WORKFLOWS / "pipeline.yml").read_text(encoding="utf-8")
        triggers = content.split("permissions:", 1)[0]
        gate = section(content, "gate", "backend-build")
        publish = section(content, "docker-publish", "deploy-plan")
        plan = section(content, "deploy-plan", "deploy-apply")
        apply = section(content, "deploy-apply", "smoke-tests")
        self.assertIn("on:\n  workflow_dispatch:", triggers)
        self.assertIn("  push:\n    branches: [develop, main]", triggers)
        self.assertNotIn("  pull_request:", triggers)
        self.assertIn("python scripts/validate_cd_trigger.py", gate)
        self.assertIn("AUTO_CD_ENABLED_HOMOLOG: ${{ vars.AUTO_CD_ENABLED_HOMOLOG }}", gate)
        self.assertIn("AUTO_CD_ENABLED_PRODUCTION: ${{ vars.AUTO_CD_ENABLED_PRODUCTION }}", gate)
        self.assertNotIn("configure-aws-credentials", gate)
        self.assertNotIn("terraform plan", gate)
        self.assertIn("inputs.apply == true", publish)
        self.assertIn("github.event_name == 'workflow_dispatch'", publish)
        self.assertIn("TERRAFORM_APPLY_APPROVAL_CONFIGURED", publish)
        self.assertIn('test "$APPROVAL_CONFIGURED" = true', publish)
        self.assertIn("environment: ${{ github.ref == 'refs/heads/main' && 'production' || 'homolog' }}", publish)
        self.assertIn("inputs.apply != true && needs.docker-publish.result == 'skipped'", plan)
        self.assertIn("inputs.apply == true && github.event_name == 'workflow_dispatch' && needs.docker-publish.result == 'success'", plan)
        self.assertIn("github.event_name == 'workflow_dispatch'", plan)
        self.assertIn("github.event_name == 'push'", plan)
        self.assertIn("needs.gate.outputs.auto_cd_enabled == 'true'", plan)
        self.assertIn("needs.gate.result == 'success'", plan)
        self.assertIn("github.event_name == 'workflow_dispatch'", apply)
        self.assertIn("inputs.apply == true", apply)
        self.assertIn("needs: [gate, deploy-plan]", apply)
        self.assertIn("TERRAFORM_APPLY_APPROVAL_CONFIGURED", apply)
        self.assertIn("environment: ${{ github.ref == 'refs/heads/main' && 'production' || 'homolog' }}", plan)
        self.assertIn("environment: ${{ github.ref == 'refs/heads/main' && 'production' || 'homolog' }}", apply)
        self.assertIn("github.ref_protected", apply)
        self.assertNotIn("github.event_name == 'push'", publish + apply)
        self.assertNotIn("feature/", publish + plan + apply)

    def test_cd_validates_injected_environment_values_without_admin_api(self):
        content = (WORKFLOWS / "pipeline.yml").read_text(encoding="utf-8")
        publish = section(content, "docker-publish", "deploy-plan")
        plan = section(content, "deploy-plan", "deploy-apply")
        apply = section(content, "deploy-apply", "smoke-tests")
        required_variables = (
            "AWS_REGION",
            "AWS_EXPECTED_ACCOUNT_ID",
            "AWS_TERRAFORM_STATE_BUCKET",
            "AWS_TF_STATE_KEY",
            "AWS_RDS_STATE_KEY",
        )
        required_secrets = (
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_SESSION_TOKEN",
            "TF_PLAN_ENCRYPTION_KEY",
        )

        for job in (publish, plan, apply):
            with self.subTest(job="publish" if job == publish else "plan" if job == plan else "apply"):
                self.assertNotIn("gh api", job)
                self.assertNotIn("/environments/", job)
                self.assertNotIn("REVIEWERS=", job)
                self.assertNotIn("GH_TOKEN", job)
                self.assertIn("environment: ${{ github.ref == 'refs/heads/main' && 'production' || 'homolog' }}", job)

        publish_config = publish.split("Exigir configuração de Apply antes da publicação", 1)[1].split("\n      - ", 1)[0]
        self.assertIn("DOCKERHUB_USERNAME: ${{ vars.DOCKERHUB_USERNAME }}", publish_config)
        self.assertIn("DOCKERHUB_PASSWORD: ${{ secrets.DOCKERHUB_PASSWORD }}", publish_config)
        self.assertIn('test -n "$DOCKERHUB_PASSWORD"', publish_config)
        self.assertNotRegex(publish_config, r"(?m)^\s*(?:echo|printf)\b[^\n]*\$DOCKERHUB_PASSWORD\b")

        for job in (plan, apply):
            with self.subTest(job="plan" if job == plan else "apply"):
                step = job.split("Validar valores injetados pelo GitHub Environment", 1)[1].split("\n      - ", 1)[0]
                self.assertIn('[[ -n "${!name}" ]] ||', step)
                for name in required_variables:
                    self.assertIn(f"{name}: ${{{{ vars.{name} }}}}", step)
                for name in required_secrets:
                    self.assertIn(f"{name}: ${{{{ secrets.{name} }}}}", step)
                    self.assertNotRegex(step, rf"(?m)^\s*(?:echo|printf)\b[^\n]*\${name}\b")

        apply_config = apply.split("Validar valores injetados pelo GitHub Environment", 1)[1].split("\n      - ", 1)[0]
        self.assertIn("TERRAFORM_APPLY_APPROVAL_CONFIGURED: ${{ vars.TERRAFORM_APPLY_APPROVAL_CONFIGURED }}", apply_config)
        self.assertIn('test "$TERRAFORM_APPLY_APPROVAL_CONFIGURED" = true', apply_config)
        self.assertIn('if [[ "$ENABLE_NEWRELIC_APM" == true ]]; then', apply_config)
        self.assertIn("NEW_RELIC_LICENSE_KEY: ${{ secrets.NEW_RELIC_LICENSE_KEY }}", apply_config)
        for secret in (
            "AUTOFLOW_DB_USERNAME",
            "AUTOFLOW_DB_PASSWORD",
            "AUTOFLOW_JWT_SECRET",
            "AUTOFLOW_MAIL_PASSWORD",
            "AUTOFLOW_PUBLIC_TOKEN_SECRET",
        ):
            self.assertIn(f"{secret}: ${{{{ secrets.{secret} }}}}", apply_config)
            self.assertNotRegex(apply_config, rf"(?m)^\s*(?:echo|printf)\b[^\n]*\${secret}\b")

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

    def test_smoke_tests_use_the_environment_validated_for_each_branch(self):
        content = (WORKFLOWS / "pipeline.yml").read_text(encoding="utf-8")
        smoke = section(content, "smoke-tests")

        self.assertIn("needs: [gate, deploy-apply]", smoke)
        self.assertIn("environment: ${{ needs.gate.outputs.environment }}", smoke)
        self.assertIn("GITHUB_DEPLOYMENT_ENVIRONMENT: ${{ needs.gate.outputs.environment }}", smoke)
        self.assertIn("inputs.smoke_tests_only", smoke)
        self.assertIn("needs.deploy-apply.result == 'success'", smoke)
        self.assertIn("github.ref == 'refs/heads/develop' || github.ref == 'refs/heads/main'", smoke)
        self.assertIn("github.ref != 'refs/heads/main' || github.ref_protected", smoke)
        self.assertIn("autoflow-${GITHUB_DEPLOYMENT_ENVIRONMENT}", smoke)
        self.assertNotIn("autoflow-homolog", smoke)

    def test_smoke_tests_validate_injected_environment_values_without_admin_api(self):
        content = (WORKFLOWS / "pipeline.yml").read_text(encoding="utf-8")
        smoke = section(content, "smoke-tests")
        configuration_check = smoke.split("id: environment-config", 1)[1].split("- uses: aws-actions/configure-aws-credentials@v4", 1)[0]

        self.assertNotIn("actions: read", smoke)
        self.assertNotIn("GH_TOKEN", smoke)
        self.assertNotIn("gh api", smoke)
        self.assertNotIn("environments/$GITHUB_DEPLOYMENT_ENVIRONMENT", smoke)
        self.assertIn("API_GATEWAY_CORS_ORIGINS: ${{ vars.API_GATEWAY_CORS_ORIGINS }}", smoke)
        self.assertIn("AWS_REGION: ${{ vars.AWS_REGION }}", smoke)
        self.assertIn("AWS_EXPECTED_ACCOUNT_ID: ${{ vars.AWS_EXPECTED_ACCOUNT_ID }}", smoke)
        for secret in ("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN"):
            self.assertIn(f"{secret}: ${{{{ secrets.{secret} }}}}", configuration_check)
        self.assertIn('[[ -n "${!name}" ]]', configuration_check)
        self.assertIn("API_GATEWAY_CORS_ORIGINS AWS_REGION AWS_EXPECTED_ACCOUNT_ID AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN", configuration_check)
        self.assertLess(smoke.index("id: environment-config"), smoke.index("aws-actions/configure-aws-credentials@v4"))
        self.assertIn("python scripts/verify_aws_account.py", smoke)

    def test_optional_smoke_credentials_do_not_fall_back_to_repository_secrets_in_production(self):
        content = (WORKFLOWS / "pipeline.yml").read_text(encoding="utf-8")
        smoke = section(content, "smoke-tests")
        configuration_check = smoke.split("id: environment-config", 1)[1].split("- uses: aws-actions/configure-aws-credentials@v4", 1)[0]

        self.assertIn("AUTOFLOW_HOMOLOG_TEST_CPF_CNPJ", configuration_check)
        self.assertIn("AUTOFLOW_HOMOLOG_TEST_PASSWORD", configuration_check)
        self.assertIn("AUTOFLOW_PRODUCTION_TEST_CPF_CNPJ", configuration_check)
        self.assertIn("AUTOFLOW_PRODUCTION_TEST_PASSWORD", configuration_check)
        self.assertIn("REPOSITORY_TEST_CPF_CNPJ: ${{ env.GITHUB_DEPLOYMENT_ENVIRONMENT == 'homolog' && secrets.AUTOFLOW_TEST_CPF_CNPJ || '' }}", configuration_check)
        self.assertIn("REPOSITORY_TEST_PASSWORD: ${{ env.GITHUB_DEPLOYMENT_ENVIRONMENT == 'homolog' && secrets.AUTOFLOW_TEST_PASSWORD || '' }}", configuration_check)
        self.assertIn("test_credentials_source=environment", configuration_check)
        self.assertIn("test_credentials_source=repository", configuration_check)
        self.assertIn("test_credentials_source=none", configuration_check)
        self.assertIn("Os Secrets de teste do Environment selecionado precisam ser fornecidos juntos.", configuration_check)
        self.assertIn("Credenciais globais de teste não podem ser usadas fora de homolog.", configuration_check)
        self.assertIn("steps.environment-config.outputs.test_credentials_source == 'repository' && env.GITHUB_DEPLOYMENT_ENVIRONMENT == 'homolog'", smoke)


if __name__ == "__main__":
    unittest.main()
