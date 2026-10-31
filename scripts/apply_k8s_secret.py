#!/usr/bin/env python3
"""Aplica o Secret da aplicação com valores fornecidos pelo ambiente do processo."""

import json
import os
import subprocess
import sys
import base64
import binascii


SECRET_NAME = "autoflow-secrets"
NAMESPACE = "default"
SECRET_KEYS = {
    "SPRING_DATASOURCE_USERNAME": "AUTOFLOW_DB_USERNAME",
    "SPRING_DATASOURCE_PASSWORD": "AUTOFLOW_DB_PASSWORD",
    "JWT_SECRET": "AUTOFLOW_JWT_SECRET",
    "MAIL_PASSWORD": "AUTOFLOW_MAIL_PASSWORD",
    "APP_PUBLIC_TOKEN_SECRET": "AUTOFLOW_PUBLIC_TOKEN_SECRET",
}


def main() -> int:
    missing = [env_name for env_name in SECRET_KEYS.values() if not os.environ.get(env_name)]
    if missing:
        print("Variáveis de ambiente obrigatórias ausentes ou vazias: " + ", ".join(missing), file=sys.stderr)
        return 2

    manifest = {
        "apiVersion": "v1",
        "kind": "Secret",
        "metadata": {"name": SECRET_NAME, "namespace": NAMESPACE},
        "type": "Opaque",
        "stringData": {key: os.environ[env_name] for key, env_name in SECRET_KEYS.items()},
    }
    payload = json.dumps(manifest, ensure_ascii=False).encode("utf-8")
    applied = subprocess.run(
        ["kubectl", "apply", "--namespace", NAMESPACE, "--filename", "-"],
        input=payload,
        capture_output=True,
        check=False,
    )
    if applied.returncode:
        print("Falha ao aplicar o Secret Kubernetes; detalhes omitidos para evitar vazamento.", file=sys.stderr)
        return 1

    verified = subprocess.run(
        ["kubectl", "get", "secret", SECRET_NAME, "--namespace", NAMESPACE, "--output", "json"],
        capture_output=True,
        text=True,
        check=False,
    )
    if verified.returncode:
        print("Falha ao verificar o Secret Kubernetes; detalhes omitidos para evitar vazamento.", file=sys.stderr)
        return 1

    try:
        stored = json.loads(verified.stdout).get("data", {})
        valid = all(
            base64.b64decode(stored[key], validate=True) == os.environ[env_name].encode("utf-8")
            for key, env_name in SECRET_KEYS.items()
        )
    except (KeyError, TypeError, ValueError, binascii.Error, json.JSONDecodeError):
        valid = False
    if not valid:
        print("O Secret Kubernetes nao corresponde as chaves esperadas; valores omitidos.", file=sys.stderr)
        return 1

    print("Secret Kubernetes verificado; todas as chaves esperadas estao presentes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
