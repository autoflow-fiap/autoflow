"""Bloqueia adoção acidental de recursos quando o State remoto está ausente."""
import argparse
import subprocess
import sys

NOT_FOUND = ("nosuchkey", "no such key", "404", "resourcenotfound", "notfound", "not found")

def check(bucket, key, command):
    try:
        subprocess.run(["aws", "s3api", "head-object", "--bucket", bucket, "--key", key], check=True, capture_output=True, text=True)
        return "state-present"
    except subprocess.CalledProcessError as error:
        if not any(marker in (error.stderr or "").lower() for marker in NOT_FOUND):
            raise RuntimeError("Não foi possível classificar o Terraform State (erro AWS/permissão); operação bloqueada.") from error
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True)
    except subprocess.CalledProcessError as error:
        if any(marker in (error.stderr or "").lower() for marker in NOT_FOUND): return "state-and-resource-absent"
        raise RuntimeError("A consulta do recurso AWS falhou; não é seguro assumir que ele está ausente.") from error
    if result.stdout.strip().lower() not in {"", "none", "null"}:
        raise RuntimeError(f"State {key} ausente, mas o recurso consultado já existe. Apply bloqueado; restaurar o State ou importar/reconciliar após revisão.")
    return "state-and-resource-absent"

def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--bucket", required=True); parser.add_argument("--key", required=True); parser.add_argument("probe", nargs=argparse.REMAINDER)
    args = parser.parse_args(); command = args.probe[1:] if args.probe and args.probe[0] == "--" else args.probe
    try:
        if not command: raise ValueError("Consulta AWS de recurso necessária para state ausente.")
        print(check(args.bucket, args.key, command)); return 0
    except (RuntimeError, ValueError) as error:
        print(str(error), file=sys.stderr); return 1

if __name__ == "__main__": raise SystemExit(main())
