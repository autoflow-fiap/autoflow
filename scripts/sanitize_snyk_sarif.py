"""Remove severidades SARIF nulas que impedem o processamento no GitHub Code Scanning."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


def remove_null_security_severity(value: Any) -> Any:
    if isinstance(value, list):
        return [remove_null_security_severity(item) for item in value]
    if isinstance(value, dict):
        sanitized = {}
        for key, item in value.items():
            if key == "security-severity":
                if isinstance(item, bool):
                    continue
                if isinstance(item, (int, float)):
                    if math.isfinite(item):
                        sanitized[key] = item
                    continue
                if isinstance(item, str):
                    try:
                        numeric = float(item)
                    except ValueError:
                        continue
                    if math.isfinite(numeric):
                        sanitized[key] = numeric
                continue
            sanitized[key] = remove_null_security_severity(item)
        return sanitized
    return value


def sanitize(path: Path) -> None:
    try:
        report = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError("Relatório SARIF ausente ou inválido; publicação bloqueada.") from error
    if not isinstance(report, dict) or not isinstance(report.get("runs"), list):
        raise ValueError("Estrutura SARIF inválida; publicação bloqueada.")

    temporary_path = path.with_name(path.name + ".tmp")
    temporary_path.write_text(
        json.dumps(remove_null_security_severity(report), ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    temporary_path.replace(path)


def main() -> int:
    if len(sys.argv) != 2:
        print("Uso: sanitize_snyk_sarif.py <relatório.sarif>", file=sys.stderr)
        return 2
    try:
        sanitize(Path(sys.argv[1]))
    except (OSError, ValueError) as error:
        print(f"Normalização SARIF Snyk falhou: {error}", file=sys.stderr)
        return 1
    print("SARIF Snyk normalizado sem alterar os achados.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
