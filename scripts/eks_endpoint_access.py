"""Gerencia lease serializado do CIDR temporário do runner no endpoint EKS."""

import argparse
import ipaddress
import json
import os
import subprocess
import tempfile
import time


def aws(*args):
    try:
        result = subprocess.run(["aws", *args, "--output", "json"], check=True, capture_output=True, text=True)
    except subprocess.CalledProcessError as error:
        output = error.stderr or error.stdout or ""
        detail = next((line.strip() for line in output.splitlines()
                       if "An error occurred (" in line or "aws: [ERROR]:" in line), "")
        operation = " ".join(args[:2])
        if detail:
            raise RuntimeError(f"Comando AWS '{operation}' falhou: {detail}") from None
        raise RuntimeError(f"Comando AWS '{operation}' falhou (exit {error.returncode}; detalhe omitido).") from None
    return json.loads(result.stdout or "{}")


def config(cluster, region):
    return aws("eks", "describe-cluster", "--name", cluster, "--region", region)["cluster"]["resourcesVpcConfig"]


def wait_update(cluster, region, cidrs):
    response = aws("eks", "update-cluster-config", "--name", cluster, "--region", region,
                   "--resources-vpc-config", "publicAccessCidrs=" + ",".join(sorted(set(cidrs))))
    update_id = response["update"]["id"]
    while True:
        update = aws("eks", "describe-update", "--name", cluster, "--region", region, "--update-id", update_id)["update"]
        if update["status"] == "Successful":
            return
        if update["status"] in {"Failed", "Cancelled"}:
            raise RuntimeError(f"Atualização do endpoint EKS terminou como {update['status']}")
        time.sleep(10)


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
    if current != desired:
        wait_update(cluster, region, desired)
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
    lease = {"temporary_cidr": cidr, "original_cidrs": sorted(current)}
    save_lease(lease)
    if cidr not in current:
        wait_update(cluster, region, current | {cidr})


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
