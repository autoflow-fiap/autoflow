#!/usr/bin/env python3
"""Bloqueia exclusões/substituições e emite somente um resumo redigido do plan."""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


class PlanReviewError(RuntimeError):
    """O plan está incompleto ou contém ações destrutivas não autorizadas."""


def review_plan(plan: dict[str, Any], allow_destructive: bool = False) -> str:
    changes = plan.get("resource_changes")
    if not isinstance(changes, list):
        raise PlanReviewError("JSON do plan sem resource_changes válido.")

    summary: Counter[str] = Counter()
    destructive: list[str] = []
    addresses: list[tuple[str, str]] = []
    for item in changes:
        if not isinstance(item, dict) or not isinstance(item.get("change"), dict):
            raise PlanReviewError("Entrada inválida em resource_changes.")
        address = item.get("address")
        actions = item["change"].get("actions")
        if not isinstance(address, str) or not isinstance(actions, list) or not all(isinstance(a, str) for a in actions):
            raise PlanReviewError("Endereço ou ações inválidos em resource_changes.")
        label = ",".join(actions)
        summary[label] += 1
        if "delete" in actions:
            destructive.append(address)
        if actions != ["no-op"]:
            addresses.append((address, label))

    if destructive and not allow_destructive:
        raise PlanReviewError(
            "Exclusão/substituição bloqueada; revise o plan e reexecute com allow_destructive=true "
            "somente se a mudança tiver autorização explícita. Endereços: " + ", ".join(sorted(destructive))
        )

    lines = ["### Resumo redigido do Terraform Plan", "", "Atributos e valores do plan foram omitidos.", "",
             "| Ação | Recursos |", "| --- | ---: |"]
    for action, count in sorted(summary.items()):
        lines.append(f"| `{action}` | {count} |")
    lines.extend(["", "| Endereço | Ação |", "| --- | --- |"])
    lines.extend(f"| `{address}` | `{action}` |" for address, action in sorted(addresses))
    if not addresses:
        lines.append("| Nenhum recurso alterado | `no-op` |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan-json", required=True, type=Path)
    parser.add_argument("--allow-destructive", choices=("true", "false"), default="false")
    args = parser.parse_args()
    try:
        plan = json.loads(args.plan_json.read_text(encoding="utf-8"))
        if not isinstance(plan, dict):
            raise PlanReviewError("JSON do plan inválido.")
        summary = review_plan(plan, args.allow_destructive == "true")
    except (OSError, json.JSONDecodeError, PlanReviewError) as error:
        print(f"Revisão do plan bloqueada: {error}", file=sys.stderr)
        return 2
    sys.stdout.write(summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
