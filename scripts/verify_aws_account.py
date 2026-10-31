"""Valida que a identidade AWS autenticada corresponda à conta configurada."""

from __future__ import annotations

import os
import re
import subprocess
import sys

ACCOUNT_ID_PATTERN = re.compile(r"[0-9]{12}\Z")


def validate_account(expected: str | None, actual: str | None) -> None:
    if expected is None or not expected.strip():
        raise ValueError("AWS_EXPECTED_ACCOUNT_ID não está configurada.")
    if ACCOUNT_ID_PATTERN.fullmatch(expected) is None:
        raise ValueError("AWS_EXPECTED_ACCOUNT_ID deve conter exatamente 12 dígitos.")
    if actual is None or ACCOUNT_ID_PATTERN.fullmatch(actual) is None:
        raise ValueError("A conta retornada pela identidade AWS é inválida.")
    if actual != expected:
        raise ValueError("A identidade AWS não corresponde à conta esperada.")


def main() -> int:
    try:
        result = subprocess.run(
            ["aws", "sts", "get-caller-identity", "--query", "Account", "--output", "text"],
            check=True,
            capture_output=True,
            text=True,
        )
        validate_account(os.environ.get("AWS_EXPECTED_ACCOUNT_ID"), result.stdout.strip())
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        print(f"Falha na validação da identidade AWS: {error}", file=sys.stderr)
        return 1

    print("Identidade AWS validada para a conta configurada.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
