#!/usr/bin/env python3
"""Verifica identidade AWS e prontidão do EKS sem modificar recursos."""

import json
import os
import subprocess
import sys
import time


class PreflightError(Exception):
    pass


def run(command, *, timeout):
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=timeout)
        return result.stdout.strip()
    except subprocess.TimeoutExpired as exc:
        raise PreflightError(f"Timeout executando {' '.join(command)}") from exc
    except FileNotFoundError as exc:
        raise PreflightError(f"Comando não encontrado: {command[0]}") from exc
    except subprocess.CalledProcessError as exc:
        detail = (exc.stderr or exc.stdout or "").strip()
        if command[:3] == ["aws", "sts", "get-caller-identity"]:
            raise PreflightError("Credenciais AWS inválidas/expiradas ou sem permissão para sts:GetCallerIdentity") from exc
        raise PreflightError(f"Falha em {' '.join(command[:3])}: {detail or 'comando retornou erro'}") from exc


def main():
    account_expected = os.getenv("AWS_EXPECTED_ACCOUNT_ID", "").strip()
    region = os.getenv("AWS_REGION", "").strip()
    cluster = os.getenv("EKS_CLUSTER_NAME", "eks-autoflow").strip()
    nodegroup = os.getenv("EKS_NODEGROUP_NAME", "nodeg-autoflow").strip()
    required_nodes = int(os.getenv("EKS_MIN_READY_NODES", "2"))
    timeout = int(os.getenv("EKS_PREFLIGHT_TIMEOUT_SECONDS", "600"))
    interval = int(os.getenv("EKS_PREFLIGHT_POLL_INTERVAL_SECONDS", "15"))
    if not account_expected or not region:
        raise PreflightError("Configure AWS_EXPECTED_ACCOUNT_ID e AWS_REGION no ambiente homolog")
    if timeout < 1 or interval < 1 or required_nodes < 1:
        raise PreflightError("Timeout, intervalo e mínimo de nós devem ser inteiros positivos")

    identity = json.loads(run(["aws", "sts", "get-caller-identity", "--output", "json"], timeout=20))
    account_actual = identity.get("Account")
    if account_actual != account_expected:
        raise PreflightError(f"Conta AWS incorreta: esperada {account_expected}, recebida {account_actual}")
    identity_region = os.getenv("AWS_DEFAULT_REGION") or region
    if identity_region != region:
        raise PreflightError(f"Região AWS incorreta: esperada {region}, configurada {identity_region}")

    deadline = time.monotonic() + timeout
    last_issue = "EKS ainda inicializando"
    while time.monotonic() < deadline:
        remaining = max(1, min(30, int(deadline - time.monotonic())))
        try:
            cluster_data = json.loads(run(["aws", "eks", "describe-cluster", "--name", cluster, "--region", region, "--output", "json"], timeout=remaining))["cluster"]
            if cluster_data.get("status") != "ACTIVE":
                last_issue = f"Cluster {cluster} está {cluster_data.get('status', 'sem status')}, esperado ACTIVE"
                raise RuntimeError(last_issue)
            group_data = json.loads(run(["aws", "eks", "describe-nodegroup", "--cluster-name", cluster, "--nodegroup-name", nodegroup, "--region", region, "--output", "json"], timeout=remaining))["nodegroup"]
            health = group_data.get("health", {}).get("issues", [])
            if group_data.get("status") != "ACTIVE" or health:
                last_issue = f"Node Group {nodegroup} está {group_data.get('status', 'sem status')}; problemas de saúde: {json.dumps(health, ensure_ascii=False)}"
                raise RuntimeError(last_issue)
            context = run(["kubectl", "config", "current-context"], timeout=remaining)
            if any(value not in context for value in (cluster, account_expected, region)):
                raise PreflightError(f"Contexto kubectl incorreto: '{context}' não identifica o cluster/conta/região esperados")
            nodes = json.loads(run(["kubectl", "get", "nodes", "-o", "json"], timeout=remaining)).get("items", [])
            ready = [n for n in nodes if any(c.get("type") == "Ready" and c.get("status") == "True" for c in n.get("status", {}).get("conditions", []))]
            if len(ready) >= required_nodes:
                print(f"Preflight EKS aprovado: conta {account_actual}, região {region}, cluster {cluster}, Node Group {nodegroup}, {len(ready)} nós Ready.")
                return 0
            last_issue = f"Nós insuficientes: {len(ready)} Ready de {len(nodes)} registrados; mínimo exigido {required_nodes}"
        except PreflightError as exc:
            if str(exc).startswith("Contexto kubectl incorreto"):
                raise
            last_issue = str(exc)
        except RuntimeError:
            pass
        except (KeyError, json.JSONDecodeError) as exc:
            last_issue = f"Resposta AWS/kubectl inválida: {exc}"
        remaining = deadline - time.monotonic()
        if remaining > 0:
            print(f"Aguardando EKS: {last_issue}", file=sys.stderr)
            time.sleep(min(interval, remaining))
    raise PreflightError(f"Timeout após {timeout}s aguardando EKS operacional: {last_issue}")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (PreflightError, ValueError) as error:
        print(f"Preflight EKS falhou: {error}", file=sys.stderr)
        sys.exit(1)
