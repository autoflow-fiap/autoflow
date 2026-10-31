#!/usr/bin/env python3
"""Gera metadados sem valores Terraform para vincular um plan à sua configuração."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Mapping


class MetadataError(RuntimeError):
    """A configuração atual não pode ser vinculada ao plan com segurança."""


def build_metadata(environment: Mapping[str, str], terraform_version: str,
                   lockfile: bytes, commit_sha: str) -> dict[str, str | dict[str, str]]:
    required = ("AWS_REGION", "TF_STATE_BUCKET", "TF_STATE_KEY")
    missing = [name for name in required if not environment.get(name)]
    if missing:
        raise MetadataError("Configuração incompleta do backend: " + ", ".join(missing))
    if not terraform_version or not commit_sha:
        raise MetadataError("Versão Terraform ou commit ausente.")
    if any(name.startswith("TF_CLI_ARGS") for name in environment):
        raise MetadataError("TF_CLI_ARGS não é permitido no job que aplica um plan salvo.")

    terraform_variables = {
        name: value for name, value in sorted(environment.items()) if name.startswith("TF_VAR_")
    }
    variables_bytes = json.dumps(terraform_variables, sort_keys=True, separators=(",", ":")).encode()
    return {
        "commit_sha": commit_sha,
        "terraform_version": terraform_version,
        "provider_lock_sha256": hashlib.sha256(lockfile).hexdigest(),
        "terraform_variables_sha256": hashlib.sha256(variables_bytes).hexdigest(),
        "backend": {
            "bucket": environment["TF_STATE_BUCKET"],
            "key": environment["TF_STATE_KEY"],
            "region": environment["AWS_REGION"],
            "encrypt": "true",
            "use_lockfile": "true",
            "workspace": environment.get("TF_WORKSPACE", "default"),
        },
    }


def current_metadata(lockfile_path: Path) -> dict[str, str | dict[str, str]]:
    try:
        version_output = subprocess.run(
            ["terraform", "version", "-json"], check=True, capture_output=True, text=True
        )
        version = json.loads(version_output.stdout)["terraform_version"]
        commit = subprocess.run(
            ["git", "rev-parse", "HEAD"], check=True, capture_output=True, text=True
        ).stdout.strip()
        if os.environ.get("GITHUB_SHA") and commit != os.environ["GITHUB_SHA"]:
            raise MetadataError("Checkout não corresponde ao commit do evento GitHub.")
        lockfile = lockfile_path.read_bytes()
    except MetadataError:
        raise
    except (OSError, subprocess.CalledProcessError, KeyError, json.JSONDecodeError) as error:
        raise MetadataError("Não foi possível coletar commit, Terraform ou lockfile.") from error
    return build_metadata(os.environ, version, lockfile, commit)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lock-file", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        metadata = current_metadata(args.lock_file)
        args.output.write_text(json.dumps(metadata, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    except (MetadataError, OSError) as error:
        print(f"Metadados do plan inválidos: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
