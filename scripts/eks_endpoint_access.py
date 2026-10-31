"""Gerencia lease serializado do CIDR temporário do runner no endpoint EKS."""

import argparse
import ipaddress
import json
import os
import re
import subprocess
import tempfile
import time


UPDATE_RETRIES = 3
UPDATE_WAIT_ATTEMPTS = 60
UPDATE_WAIT_SECONDS = 10
S3_OPERATION_TIMEOUT_SECONDS = 30
AWS_ERROR_PATTERN = re.compile(r"An error occurred \(([^)]+)\) when calling (?:the )?([A-Za-z0-9]+) operation")
PERMANENT_CIDR = "189.6.8.245/32"
SAFE_AWS_ERROR_CODES = {
    "AccessDenied", "AccessDeniedException", "403", "Forbidden",
    "ExpiredToken", "ExpiredTokenException", "InvalidToken", "InvalidAccessKeyId",
    "SignatureDoesNotMatch", "NoSuchKey", "404", "NotFound", "NoSuchBucket",
    "PreconditionFailed", "ConditionalRequestConflict", "InternalError", "ServiceUnavailable",
    "SlowDown", "RequestTimeout",
}
NETWORK_ERROR_MARKERS = (
    "could not connect to the endpoint url",
    "connection was closed",
    "connection timed out",
    "timed out",
    "timeout",
    "temporary failure in name resolution",
    "name or service not known",
    "failed to resolve",
    "getaddrinfo failed",
    "failed to establish a new connection",
    "ssl validation failed",
    "connection reset by peer",
    "network is unreachable",
    "read timed out",
)


class AwsOperationError(RuntimeError):
    def __init__(self, operation, error):
        stderr = error.stderr or ""
        match = re.search(r"An error occurred \(([^)]+)\) when calling ([^:]+)", stderr)
        self.code = match.group(1) if match else "UnknownAWSException"
        self.operation = operation
        detail = next((line.strip() for line in stderr.splitlines() if "An error occurred (" in line), self.code)
        super().__init__(f"Comando AWS '{operation}' falhou: {detail}")


def aws(*args):
    try:
        result = subprocess.run(["aws", *args, "--output", "json"], check=True, capture_output=True, text=True)
    except subprocess.CalledProcessError as error:
        raise AwsOperationError(" ".join(args[:2]), error) from None
    return json.loads(result.stdout or "{}")


def config(cluster, region):
    return aws("eks", "describe-cluster", "--name", cluster, "--region", region)["cluster"]["resourcesVpcConfig"]


def wait_for_update(cluster, region, update_id):
    for _ in range(UPDATE_WAIT_ATTEMPTS):
        update = aws("eks", "describe-update", "--name", cluster, "--region", region, "--update-id", update_id)["update"]
        if update["status"] == "Successful":
            return
        if update["status"] in {"Failed", "Cancelled"}:
            raise RuntimeError(f"Atualização do endpoint EKS terminou como {update['status']}")
        time.sleep(UPDATE_WAIT_SECONDS)
    raise RuntimeError("Tempo limite aguardando atualização do endpoint EKS; lease mantido.")


def wait_for_active_updates(cluster, region):
    for _ in range(UPDATE_WAIT_ATTEMPTS):
        updates = aws("eks", "list-updates", "--name", cluster, "--region", region).get("updateIds", [])
        active = []
        for update_id in updates:
            update = aws(
                "eks", "describe-update", "--name", cluster, "--region", region, "--update-id", update_id
            )["update"]
            if update["status"] == "InProgress":
                active.append(update_id)
        if not active:
            return
        for update_id in active:
            wait_for_update(cluster, region, update_id)
    raise RuntimeError("Tempo limite aguardando atualizações EKS existentes; lease mantido.")


def update_and_wait(cluster, region, cidrs, merge_current):
    cidrs = set(cidrs)
    for attempt in range(UPDATE_RETRIES):
        wait_for_active_updates(cluster, region)
        current = set(config(cluster, region)["publicAccessCidrs"])
        desired = set(merge_current(current))
        if "0.0.0.0/0" in desired:
            raise RuntimeError("CIDR público irrestrito não permitido")
        if desired == current:
            return
        value = "publicAccessCidrs=" + ",".join(sorted(desired))
        try:
            response = aws("eks", "update-cluster-config", "--name", cluster, "--region", region,
                           "--resources-vpc-config", value)
        except AwsOperationError as error:
            if error.code != "ResourceInUseException" or attempt == UPDATE_RETRIES - 1:
                raise
            time.sleep(UPDATE_WAIT_SECONDS)
            continue
        wait_for_update(cluster, region, response["update"]["id"])
        return
    raise RuntimeError("Conflito transitório de atualização EKS; lease mantido.")


def location():
    bucket = os.environ.get("EKS_LEASE_BUCKET", "").strip()
    key = os.environ.get("EKS_LEASE_KEY", "").strip()
    if not bucket or not key:
        raise RuntimeError("Configure EKS_LEASE_BUCKET e EKS_LEASE_KEY para a reconciliação durável")
    owner = os.environ.get("AWS_EXPECTED_ACCOUNT_ID", "").strip()
    if re.fullmatch(r"[0-9]{12}", owner) is None:
        raise RuntimeError("AWS_EXPECTED_ACCOUNT_ID deve conter exatamente 12 dígitos para acessar o lease S3")
    return bucket, key, owner


def aws_error(stderr):
    match = AWS_ERROR_PATTERN.search(stderr or "")
    if match:
        code = match.group(1)
        if code not in SAFE_AWS_ERROR_CODES:
            code = "UnknownAWSException"
        return code, match.group(2)
    return None, None


def lease_operation_error(action, stderr, permissions):
    code, _operation = aws_error(stderr)
    if code in {"AccessDenied", "AccessDeniedException", "403", "Forbidden"}:
        return (
            f"Acesso negado ao S3 (AWS {code}) ao {action} o lease; verifique credenciais temporárias, bucket policy e "
            f"permissões {permissions} (e kms:Decrypt se o bucket usar SSE-KMS)."
        )
    if code in {"ExpiredToken", "ExpiredTokenException", "InvalidToken"}:
        return (
            f"Credenciais AWS temporárias inválidas ou expiradas (AWS {code}) ao {action} o lease; renove "
            "AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY e AWS_SESSION_TOKEN no Environment."
        )
    message = (stderr or "").lower()
    if any(marker in message for marker in NETWORK_ERROR_MARKERS):
        return f"Falha de rede ou endpoint ao {action} o lease no S3; verifique conectividade e região AWS."
    if code:
        return f"Falha ao {action} o lease no S3 (AWS {code}); lease e CIDRs foram mantidos sem alteração."
    return f"Falha ao {action} o lease no S3 sem resposta AWS identificável; verifique credenciais e conectividade."


def run_s3_operation(action, arguments, permissions):
    try:
        result = subprocess.run(
            ["aws", "s3api", *arguments, "--output", "json"],
            capture_output=True,
            text=True,
            timeout=S3_OPERATION_TIMEOUT_SECONDS,
        )
    except subprocess.TimeoutExpired:
        raise RuntimeError(f"Tempo limite ao {action} o lease no S3; lease e CIDRs foram mantidos.") from None
    except OSError:
        raise RuntimeError(f"Não foi possível iniciar a AWS CLI ao {action} o lease no S3.") from None
    if result.returncode:
        raise RuntimeError(lease_operation_error(action, result.stderr, permissions))
    return result


def validate_lease_document(lease):
    expected_keys = {"temporary_cidr", "original_cidrs"}
    if not isinstance(lease, dict) or set(lease) != expected_keys:
        raise RuntimeError("Lease EKS S3 malformado ou desconhecido; mantendo objeto e CIDRs sem alteração.")

    temporary_cidr = lease.get("temporary_cidr")
    original_cidrs = lease.get("original_cidrs")
    if not isinstance(temporary_cidr, str) or not isinstance(original_cidrs, list):
        raise RuntimeError("Lease EKS S3 malformado ou desconhecido; mantendo objeto e CIDRs sem alteração.")
    try:
        temporary_network = ipaddress.ip_network(temporary_cidr, strict=True)
        original_networks = [ipaddress.ip_network(value, strict=True) for value in original_cidrs]
    except (TypeError, ValueError):
        raise RuntimeError("Lease EKS S3 malformado ou desconhecido; mantendo objeto e CIDRs sem alteração.") from None
    if (
        temporary_network.version != 4
        or temporary_network.prefixlen != 32
        or str(temporary_network) != temporary_cidr
        or any(str(network) != value for network, value in zip(original_networks, original_cidrs))
        or len(set(original_cidrs)) != len(original_cidrs)
        or PERMANENT_CIDR not in original_cidrs
    ):
        raise RuntimeError("Lease EKS S3 malformado ou desconhecido; mantendo objeto e CIDRs sem alteração.")
    return {"temporary_cidr": temporary_cidr, "original_cidrs": original_cidrs}


def load_lease():
    bucket, key, owner = location()
    with tempfile.NamedTemporaryFile(delete=False) as stream:
        path = stream.name
    try:
        try:
            result = subprocess.run(
                [
                    "aws", "s3api", "get-object", "--bucket", bucket, "--key", key,
                    "--expected-bucket-owner", owner, path, "--output", "json",
                ],
                capture_output=True,
                text=True,
                timeout=S3_OPERATION_TIMEOUT_SECONDS,
            )
        except subprocess.TimeoutExpired:
            raise RuntimeError("Tempo limite ao ler lease do S3; lease e CIDRs foram mantidos.") from None
        except OSError:
            raise RuntimeError("Não foi possível iniciar a AWS CLI ao ler lease do S3.") from None
        if result.returncode:
            code, operation = aws_error(result.stderr)
            if operation == "GetObject" and code in {"NoSuchKey", "404", "NotFound"}:
                return None
            raise RuntimeError(lease_operation_error("ler", result.stderr, "s3:GetObject e s3:ListBucket"))
        try:
            with open(path, encoding="utf-8") as lease_file:
                return validate_lease_document(json.load(lease_file))
        except (json.JSONDecodeError, UnicodeDecodeError):
            raise RuntimeError("Lease EKS S3 malformado ou desconhecido; mantendo objeto e CIDRs sem alteração.") from None
    finally:
        try:
            os.unlink(path)
        except FileNotFoundError:
            pass


def save_lease(lease):
    bucket, key, owner = location()
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", delete=False) as stream:
        json.dump(lease, stream)
        path = stream.name
    try:
        try:
            result = subprocess.run(
                [
                    "aws", "s3api", "put-object", "--bucket", bucket, "--key", key,
                    "--body", path, "--expected-bucket-owner", owner,
                    "--if-none-match", "*", "--output", "json",
                ],
                capture_output=True,
                text=True,
                timeout=S3_OPERATION_TIMEOUT_SECONDS,
            )
        except subprocess.TimeoutExpired:
            raise RuntimeError("Tempo limite ao gravar lease no S3; CIDRs não foram alterados.") from None
        except OSError:
            raise RuntimeError("Não foi possível iniciar a AWS CLI ao gravar lease no S3.") from None
        if result.returncode:
            code, _operation = aws_error(result.stderr)
            if code in {"PreconditionFailed", "ConditionalRequestConflict"}:
                raise RuntimeError("Lease EKS S3 já existe; recusada a sobrescrita de lease ativo ou desconhecido.")
            raise RuntimeError(lease_operation_error("gravar", result.stderr, "s3:PutObject"))
    finally:
        os.unlink(path)


def remove_lease():
    bucket, key, owner = location()
    run_s3_operation(
        "remover",
        ["delete-object", "--bucket", bucket, "--key", key, "--expected-bucket-owner", owner],
        "s3:DeleteObject",
    )


def cleanup(cluster, region, lease):
    current = set(config(cluster, region)["publicAccessCidrs"])
    desired = (current - {lease["temporary_cidr"]}) | set(lease["original_cidrs"])
    if "0.0.0.0/0" in desired:
        raise RuntimeError("CIDR público irrestrito não permitido; lease mantido")
    if current != desired:
        update_and_wait(
            cluster, region, desired,
            lambda latest: (latest - {lease["temporary_cidr"]}) | set(lease["original_cidrs"]),
        )
    remove_lease()


def reconcile(cluster, region):
    lease = load_lease()
    if lease is not None:
        cleanup(cluster, region, lease)


def authorize(cluster, region, ip):
    cidr = str(ipaddress.ip_network(ip + "/32", strict=True))
    reconcile(cluster, region)
    current = set(config(cluster, region)["publicAccessCidrs"])
    if PERMANENT_CIDR not in current:
        raise RuntimeError(f"CIDR permanente {PERMANENT_CIDR} ausente; operação bloqueada")
    if "0.0.0.0/0" in current:
        raise RuntimeError("CIDR público irrestrito não permitido; operação bloqueada")
    lease = {"temporary_cidr": cidr, "original_cidrs": sorted(current)}
    save_lease(lease)
    if cidr not in current:
        update_and_wait(cluster, region, current | {cidr}, lambda latest: latest | {cidr})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("authorize", "cleanup", "reconcile"))
    parser.add_argument("--cluster", default="eks-autoflow")
    parser.add_argument("--region", default="us-east-1")
    parser.add_argument("--ip")
    args = parser.parse_args()
    if args.action == "authorize":
        authorize(args.cluster, args.region, args.ip)
    elif args.action == "reconcile":
        reconcile(args.cluster, args.region)
    else:
        lease = load_lease()
        if lease:
            cleanup(args.cluster, args.region, lease)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        import sys
        print(f"Falha ao atualizar acesso temporário ao EKS: {error}", file=sys.stderr)
        raise SystemExit(1) from None
