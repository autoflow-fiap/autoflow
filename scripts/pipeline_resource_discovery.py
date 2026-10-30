"""Valida o destino dedicado e descobre recursos do ambiente do deploy."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys


ENVIRONMENTS = {
    "homolog": {
        "branch": "develop",
        "cluster": "eks-autoflow",
        "api": "autoflow-homolog",
    },
    "production": {
        "branch": "main",
        "cluster": "eks-autoflow-production",
        "api": "autoflow-production",
    },
}
REGION_PATTERN = re.compile(r"[a-z]{2}(?:-gov)?-[a-z]+-[0-9]+\Z")
ACCOUNT_PATTERN = re.compile(r"[0-9]{12}\Z")


class DiscoveryError(ValueError):
    """Configuração ou descoberta de recursos inválida."""


def required_environment(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise DiscoveryError(f"{name} ausente ou vazio.")
    return value


def validate_context(event_name: str, git_ref: str, deployment_environment: str) -> str:
    if event_name not in {"push", "workflow_dispatch"}:
        raise DiscoveryError("Descoberta de recursos não é permitida para este evento.")
    if deployment_environment not in ENVIRONMENTS:
        raise DiscoveryError("GitHub Environment de deploy não reconhecido.")

    expected = ENVIRONMENTS[deployment_environment]
    if git_ref != f"refs/heads/{expected['branch']}":
        raise DiscoveryError("Branch e GitHub Environment não correspondem.")
    return deployment_environment


def validate_state_configuration(
    deployment_environment: str,
    state_bucket: str,
    application_state_key: str,
    rds_state_key: str,
) -> None:
    if deployment_environment not in ENVIRONMENTS:
        raise DiscoveryError("GitHub Environment de deploy não reconhecido.")
    if not all((state_bucket.strip(), application_state_key.strip(), rds_state_key.strip())):
        raise DiscoveryError("Bucket e keys Terraform do ambiente são obrigatórios.")
    if application_state_key == rds_state_key:
        raise DiscoveryError("A aplicação e o RDS devem usar Terraform States distintos.")

    prefix = f"{deployment_environment}/"
    if not application_state_key.startswith(prefix) or not rds_state_key.startswith(prefix):
        raise DiscoveryError(f"As keys devem usar o prefixo obrigatório {prefix}.")
    for key in (application_state_key, rds_state_key):
        if any(part in {"", ".", ".."} for part in key.split("/")):
            raise DiscoveryError("As keys Terraform devem ter segmentos de caminho válidos.")


def validate_configuration() -> tuple[str, str]:
    environment = validate_context(
        required_environment("GITHUB_EVENT_NAME"),
        required_environment("GITHUB_REF"),
        required_environment("GITHUB_DEPLOYMENT_ENVIRONMENT"),
    )
    account_id = required_environment("AWS_EXPECTED_ACCOUNT_ID")
    if ACCOUNT_PATTERN.fullmatch(account_id) is None:
        raise DiscoveryError("AWS_EXPECTED_ACCOUNT_ID deve conter exatamente 12 dígitos.")

    region = required_environment("AWS_REGION")
    if REGION_PATTERN.fullmatch(region) is None:
        raise DiscoveryError("AWS_REGION deve identificar uma região AWS válida.")
    validate_state_configuration(
        environment,
        required_environment("TF_STATE_BUCKET"),
        required_environment("TF_STATE_KEY"),
        required_environment("AWS_RDS_STATE_KEY"),
    )
    return environment, region


def run_aws_json(*arguments: str) -> object:
    try:
        result = subprocess.run(
            ["aws", *arguments, "--output", "json"],
            check=True,
            capture_output=True,
            text=True,
        )
        return json.loads(result.stdout)
    except (OSError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        raise DiscoveryError(f"Falha ao consultar recursos AWS ({type(error).__name__}).") from None


def discover_resources(environment: str, region: str) -> tuple[str, str]:
    if environment not in ENVIRONMENTS:
        raise DiscoveryError("GitHub Environment de deploy não reconhecido.")
    if REGION_PATTERN.fullmatch(region) is None:
        raise DiscoveryError("AWS_REGION deve identificar uma região AWS válida.")

    expected = ENVIRONMENTS[environment]
    region_result = subprocess.run(
        [
            "aws",
            "ec2",
            "describe-availability-zones",
            "--region",
            region,
            "--query",
            "AvailabilityZones[0].RegionName",
            "--output",
            "text",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    if region_result.stdout.strip() != region:
        raise DiscoveryError("A região AWS configurada não foi confirmada.")

    clusters = run_aws_json("eks", "list-clusters", "--region", region)
    cluster_names = clusters.get("clusters") if isinstance(clusters, dict) else None
    if not isinstance(cluster_names, list) or expected["cluster"] not in cluster_names:
        raise DiscoveryError(f"Cluster esperado para {environment} não encontrado.")

    apis = run_aws_json("apigatewayv2", "get-apis", "--region", region)
    api_items = apis.get("Items") if isinstance(apis, dict) else None
    matching_apis = [item for item in api_items or [] if isinstance(item, dict) and item.get("Name") == expected["api"]]
    if len(matching_apis) != 1:
        raise DiscoveryError(f"API Gateway esperada para {environment} não encontrada de forma única.")
    endpoint = matching_apis[0].get("ApiEndpoint")
    if not isinstance(endpoint, str) or not endpoint.startswith("https://") or "\n" in endpoint:
        raise DiscoveryError("Endpoint HTTPS da API Gateway está ausente ou inválido.")

    return expected["cluster"], endpoint


def main(arguments: list[str] | None = None) -> int:
    arguments = sys.argv[1:] if arguments is None else arguments
    if arguments not in ([], ["validate-config"], ["discover"]):
        print("Uso: pipeline_resource_discovery.py [validate-config|discover]", file=sys.stderr)
        return 2

    try:
        environment, region = validate_configuration()
        if arguments == ["validate-config"]:
            print("Branch, Environment, conta, região e Terraform States validados.")
            return 0

        cluster_name, gateway_origin = discover_resources(environment, region)
        output_path = required_environment("GITHUB_ENV")
        with open(output_path, "a", encoding="utf-8", newline="\n") as output:
            output.write(f"TF_VAR_cluster_name={cluster_name}\n")
            output.write(f"TF_VAR_gateway_public_origin={gateway_origin}\n")
    except (DiscoveryError, OSError, subprocess.CalledProcessError) as error:
        if isinstance(error, subprocess.CalledProcessError):
            message = "Falha ao verificar a região AWS configurada."
        else:
            message = str(error)
        print(f"Descoberta de recursos bloqueada: {message}", file=sys.stderr)
        return 1

    print(f"Cluster EKS e API Gateway de {environment} selecionados.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
