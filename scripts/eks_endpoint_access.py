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
    bucket, key = os.environ.get("EKS_LEASE_BUCKET"), os.environ.get("EKS_LEASE_KEY")
    if not bucket or not key:
        raise RuntimeError("Configure EKS_LEASE_BUCKET e EKS_LEASE_KEY para a reconciliação durável")
    return bucket, key


def load_lease():
    bucket, key = location()
    result = subprocess.run(["aws", "s3", "cp", f"s3://{bucket}/{key}", "-"], capture_output=True, text=True)
    if result.returncode:
        if any(marker in result.stderr for marker in ("404", "Not Found", "NoSuchKey")):
            return None
        raise RuntimeError("Falha ao ler lease persistido do endpoint EKS")
    return json.loads(result.stdout)


def save_lease(lease):
    bucket, key = location()
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", delete=False) as stream:
        json.dump(lease, stream)
        path = stream.name
    try:
        subprocess.run(["aws", "s3", "cp", path, f"s3://{bucket}/{key}"], check=True, capture_output=True, text=True)
    finally:
        os.unlink(path)


def remove_lease():
    bucket, key = location()
    subprocess.run(["aws", "s3", "rm", f"s3://{bucket}/{key}"], check=True, capture_output=True, text=True)


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
    if lease:
        cleanup(cluster, region, lease)


def authorize(cluster, region, ip):
    cidr = str(ipaddress.ip_network(ip + "/32", strict=True))
    reconcile(cluster, region)
    current = set(config(cluster, region)["publicAccessCidrs"])
    if "189.6.8.245/32" not in current:
        raise RuntimeError("CIDR permanente 189.6.8.245/32 ausente; operação bloqueada")
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
