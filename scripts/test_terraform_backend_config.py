from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


def remote_init_commands():
    commands = []
    workflows = list((ROOT / ".github/workflows").glob("*.yml"))
    workflows.extend((ROOT / ".github/workflows").glob("*.yaml"))
    for workflow in workflows:
        text = workflow.read_text(encoding="utf-8")
        text = re.sub(r"\\\r?\n\s*", " ", text)
        for match in re.finditer(r"\bterraform(?:\s+-chdir=\S+)?\s+init\b([^\r\n]*)", text):
            command = match.group(0)
            if "-backend=false" not in command:
                commands.append((workflow.name, command))
    return commands


class TerraformBackendConfigTests(unittest.TestCase):
    def test_terraform_1_16_5_rejects_expected_bucket_owner_without_aws(self):
        terraform = shutil.which("terraform")
        self.assertIsNotNone(terraform, "Terraform 1.16.5 precisa estar instalado para este teste.")
        version = subprocess.run(
            [terraform, "version", "-json"], capture_output=True, text=True, check=True
        )
        self.assertEqual(json.loads(version.stdout)["terraform_version"], "1.16.5")

        with tempfile.TemporaryDirectory(prefix="autoflow-s3-backend-compat-") as directory:
            root = Path(directory)
            (root / "main.tf").write_text(
                'terraform {\n  required_version = "= 1.16.5"\n  backend "s3" {}\n}\n',
                encoding="utf-8",
            )
            environment = os.environ.copy()
            for name in (
                "AWS_ACCESS_KEY_ID",
                "AWS_SECRET_ACCESS_KEY",
                "AWS_SESSION_TOKEN",
                "AWS_PROFILE",
            ):
                environment.pop(name, None)
            environment.update(
                {
                    "AWS_CONFIG_FILE": str(root / "no-aws-config"),
                    "AWS_SHARED_CREDENTIALS_FILE": str(root / "no-aws-credentials"),
                    "AWS_EC2_METADATA_DISABLED": "true",
                    "AWS_ENDPOINT_URL": "http://127.0.0.1:9",
                    "AWS_ENDPOINT_URL_S3": "http://127.0.0.1:9",
                    "TF_DATA_DIR": str(root / ".terraform"),
                }
            )
            result = subprocess.run(
                [
                    terraform,
                    "init",
                    "-input=false",
                    "-no-color",
                    "-backend-config=bucket=autoflow-backend-compatibility-test",
                    "-backend-config=key=compatibility/test.tfstate",
                    "-backend-config=region=us-east-1",
                    "-backend-config=expected_bucket_owner=not-a-real-account-id",
                    "-backend-config=encrypt=true",
                    "-backend-config=use_lockfile=true",
                ],
                cwd=root,
                env=environment,
                capture_output=True,
                text=True,
                timeout=60,
            )
            output = result.stdout + result.stderr
            self.assertNotEqual(result.returncode, 0, output)
            self.assertIn("expected_bucket_owner", output)
            self.assertRegex(output, r"Invalid backend configuration argument|Unsupported argument")

    def test_s3_backend_blocks_do_not_declare_unsupported_arguments(self):
        backend_files = []
        for terraform_file in (ROOT / "infra").rglob("*.tf"):
            text = terraform_file.read_text(encoding="utf-8")
            if re.search(r'backend\s+"s3"', text):
                backend_files.append(terraform_file)
                self.assertNotIn("expected_bucket_owner", text, str(terraform_file))
        self.assertEqual(backend_files, [ROOT / "infra/backend.tf"])

    def test_remote_init_uses_supported_account_restricted_s3_arguments(self):
        commands = remote_init_commands()
        self.assertTrue(commands, "Nenhum terraform init remoto foi encontrado nos workflows.")
        for workflow, command in commands:
            with self.subTest(workflow=workflow, command=command):
                self.assertNotIn("expected_bucket_owner", command)
                self.assertIn('backend-config="allowed_account_ids=$TF_VAR_aws_allowed_account_ids"', command)
                self.assertIn('backend-config="encrypt=true"', command)
                self.assertIn('backend-config="use_lockfile=true"', command)

    def test_remote_state_uses_the_externally_supplied_account_allowlist(self):
        data = (ROOT / "infra/data.tf").read_text(encoding="utf-8")
        self.assertNotIn("expected_bucket_owner", data)
        variables = (ROOT / "infra/providers.tf").read_text(encoding="utf-8")
        self.assertIn("allowed_account_ids = var.aws_allowed_account_ids", data)
        allowlist = variables.split('variable "aws_allowed_account_ids"', 1)[1].split("}", 1)[0]
        self.assertNotIn("default", allowlist)
        self.assertIn(
            'TF_VAR_aws_allowed_account_ids: \'["${{ vars.AWS_EXPECTED_ACCOUNT_ID }}"]\'',
            (ROOT / ".github/workflows/pipeline.yml").read_text(encoding="utf-8"),
        )

    def test_environment_selects_external_state_key_without_crossing_environments(self):
        workflow = (ROOT / ".github/workflows/pipeline.yml").read_text(encoding="utf-8")
        self.assertGreaterEqual(
            workflow.count("environment: ${{ github.ref == 'refs/heads/main' && 'production' || 'homolog' }}"),
            2,
        )
        self.assertGreaterEqual(workflow.count("TF_STATE_KEY: ${{ vars.AWS_TF_STATE_KEY }}"), 2)
        self.assertIn("TF_VAR_deployment_environment: ${{ github.ref == 'refs/heads/main' && 'production' || 'homolog' }}", workflow)


if __name__ == "__main__":
    unittest.main()
