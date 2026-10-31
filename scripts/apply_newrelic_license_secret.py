#!/usr/bin/env python3
"""Sincroniza a licença do Secrets Manager ao Secret Kubernetes sem registrá-la."""

from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import json
import os
import re
import subprocess
import sys


FINGERPRINT_INPUTS = (
    "ENABLE_NEWRELIC_APM",
    "NEW_RELIC_LICENSE_SECRET_ID",
    "GITHUB_DEPLOYMENT_ENVIRONMENT",
)
DEFAULT_NAMESPACE = "default"
DEFAULT_SECRET_NAME = "newrelic-license"
DEFAULT_SECRET_KEY = "licenseKey"


class ConfigurationError(ValueError):
    """Configuração externa ausente ou inválida."""


def required_environment(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ConfigurationError(f"{name} ausente ou vazio.")
    return value


def validate_configuration() -> tuple[bool, str | None, str, str, str]:
    enabled = required_environment("ENABLE_NEWRELIC_APM")
    if enabled not in {"true", "false"}:
        raise ConfigurationError("ENABLE_NEWRELIC_APM deve ser true ou false.")
    deployment_environment = required_environment("GITHUB_DEPLOYMENT_ENVIRONMENT")
    if deployment_environment not in {"homolog", "production"}:
        raise ConfigurationError("GITHUB_DEPLOYMENT_ENVIRONMENT deve ser homolog ou production.")
    region = required_environment("AWS_REGION")
    if re.fullmatch(r"[a-z]{2}(?:-gov)?-[a-z]+-[0-9]+", region) is None:
        raise ConfigurationError("AWS_REGION deve identificar uma região AWS válida.")
    if deployment_environment == "homolog" and enabled != "true":
        raise ConfigurationError("APM New Relic deve estar habilitado no environment homolog.")
    namespace = DEFAULT_NAMESPACE
    secret_name = DEFAULT_SECRET_NAME
    secret_key = DEFAULT_SECRET_KEY
    if enabled == "false":
        return False, None, namespace, secret_name, secret_key

    secret_id = required_environment("NEW_RELIC_LICENSE_SECRET_ID")
    return True, secret_id, namespace, secret_name, secret_key


def config_fingerprint() -> str:
    validate_configuration()
    values = {name: os.environ.get(name, "") for name in FINGERPRINT_INPUTS}
    payload = json.dumps(values, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def main(arguments: list[str] | None = None) -> int:
    arguments = sys.argv[1:] if arguments is None else arguments
    try:
        enabled, secret_id, namespace, secret_name, secret_key = validate_configuration()
    except ConfigurationError as error:
        print(f"Sincronização da licença New Relic bloqueada: {error}", file=sys.stderr)
        return 2

    if arguments == ["validate"]:
        print("Configuração de ativação e referência do Secret New Relic válidas.")
        return 0

    if len(arguments) == 2 and arguments[0] == "fingerprint":
        try:
            with open(arguments[1], "w", encoding="ascii") as output:
                output.write(f"{config_fingerprint()}\n")
        except (OSError, ConfigurationError) as error:
            print(f"Fingerprint New Relic não gerado: {error}", file=sys.stderr)
            return 2
        print("Fingerprint não secreto da configuração New Relic registrado.")
        return 0

    if len(arguments) == 2 and arguments[0] == "verify-fingerprint":
        try:
            with open(arguments[1], encoding="ascii") as expected_file:
                expected = expected_file.read().strip()
            if not re.fullmatch(r"[a-f0-9]{64}", expected) or config_fingerprint() != expected:
                raise ConfigurationError("Configuração New Relic mudou entre plan e apply.")
        except (OSError, ConfigurationError) as error:
            print(f"Verificação da configuração New Relic bloqueada: {error}", file=sys.stderr)
            return 1
        print("Configuração New Relic idêntica à vinculada ao plan.")
        return 0

    if arguments:
        print("Uso: apply_newrelic_license_secret.py [validate|fingerprint|verify-fingerprint]", file=sys.stderr)
        return 2

    if not enabled:
        print("APM New Relic desabilitado; Secret existente foi preservado.")
        return 0

    license_value = os.environ.get("NEW_RELIC_LICENSE_KEY", "")
    if not license_value or "\x00" in license_value:
        print("NEW_RELIC_LICENSE_KEY ausente ou inválida no GitHub Actions Environment Secret.", file=sys.stderr)
        return 2

    try:
        import boto3
        from botocore.exceptions import ClientError

        client = boto3.client("secretsmanager", region_name=required_environment("AWS_REGION"))
        try:
            existing = client.get_secret_value(SecretId=secret_id).get("SecretString")
        except ClientError as error:
            code = error.response.get("Error", {}).get("Code", "")
            if code == "ResourceNotFoundException":
                try:
                    client.create_secret(Name=secret_id, SecretString=license_value)
                except ClientError as create_error:
                    code = create_error.response.get("Error", {}).get("Code", "")
                    if code == "ResourceExistsException":
                        try:
                            existing = client.get_secret_value(SecretId=secret_id).get("SecretString")
                        except ClientError:
                            print("Leitura do Secret após criação concorrente bloqueada; verificar secretsmanager:GetSecretValue.", file=sys.stderr)
                            return 1
                        else:
                            pass
                    else:
                        permission = "secretsmanager:CreateSecret" if code == "AccessDeniedException" else "permissão Secrets Manager necessária"
                        print(f"Bootstrap do Secret bloqueado; verificar {permission}.", file=sys.stderr)
                        return 1
                else:
                    existing = license_value
                    print("Secret New Relic criado no Secrets Manager sem exibir o conteúdo.")
            else:
                permission = "secretsmanager:GetSecretValue" if code == "AccessDeniedException" else "acesso ao Secret no Secrets Manager"
                print(f"Leitura do Secret bloqueada; verificar {permission}.", file=sys.stderr)
                return 1
        except Exception as error:
            print(f"Acesso ao AWS Secrets Manager indisponível ({type(error).__name__}); conteúdo omitido.", file=sys.stderr)
            return 1
    except (ImportError, OSError, ConfigurationError) as error:
        print(f"Bootstrap do Secret New Relic indisponível: {type(error).__name__}.", file=sys.stderr)
        return 1

    if not isinstance(existing, str) or not existing or "\x00" in existing:
        print("AWS Secrets Manager não retornou uma license key SecretString válida.", file=sys.stderr)
        return 1
    if not hmac.compare_digest(existing.encode("utf-8"), license_value.encode("utf-8")):
        print("A licença cadastrada difere da fonte GitHub; rotação controlada necessária. Nenhum valor foi alterado.", file=sys.stderr)
        return 1
    license_value = existing

    manifest = {
        "apiVersion": "v1",
        "kind": "Secret",
        "metadata": {"name": secret_name, "namespace": namespace},
        "type": "Opaque",
        "stringData": {secret_key: license_value},
    }
    try:
        applied = subprocess.run(
            ["kubectl", "apply", "--namespace", namespace, "--filename", "-"],
            input=json.dumps(manifest, ensure_ascii=False).encode("utf-8"),
            capture_output=True,
            check=False,
        )
    except OSError:
        print("Falha ao atualizar o Secret Kubernetes; conteúdo omitido.", file=sys.stderr)
        return 1
    if applied.returncode:
        if b"Forbidden" in applied.stderr or b"forbidden" in applied.stderr.lower():
            print("Sincronização Kubernetes bloqueada; verificar permissão para criar/atualizar Secret no namespace default.", file=sys.stderr)
            return 1
        print("Falha ao atualizar o Secret Kubernetes; detalhes omitidos.", file=sys.stderr)
        return 1

    try:
        verified = subprocess.run(
            ["kubectl", "get", "secret", secret_name, "--namespace", namespace, "--output", "json"],
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError:
        print("Falha ao verificar o Secret Kubernetes; conteúdo omitido.", file=sys.stderr)
        return 1
    if verified.returncode:
        if "Forbidden" in verified.stderr or "forbidden" in verified.stderr.lower():
            print("Validação Kubernetes bloqueada; verificar permissão para ler Secret no namespace default.", file=sys.stderr)
            return 1
        print("Falha ao verificar o Secret Kubernetes; conteúdo omitido.", file=sys.stderr)
        return 1

    try:
        encoded_value = json.loads(verified.stdout).get("data", {}).get(secret_key)
        valid = base64.b64decode(encoded_value, validate=True) == license_value.encode("utf-8")
    except (AttributeError, TypeError, ValueError, binascii.Error, json.JSONDecodeError):
        valid = False
    if not valid:
        print("Secret Kubernetes não corresponde à configuração; conteúdo omitido.", file=sys.stderr)
        return 1

    print("Secret Kubernetes da licença New Relic sincronizado e verificado sem exibir o conteúdo.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
