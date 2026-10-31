"""Valida o push e aguarda a CI do mesmo repositório, branch e commit."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
from collections.abc import Callable, Mapping, Sequence
from pathlib import Path
from urllib.parse import urlencode


ALLOWED_BRANCHES = {"develop", "main"}
COMMIT_PATTERN = re.compile(r"[0-9a-fA-F]{40}\Z")


class TriggerError(ValueError):
    """O evento ou a execução de CI não atende ao contrato seguro."""


def validate_trigger(
    event_name: str,
    payload: Mapping[str, object],
    *,
    auto_cd_enabled: str,
    repository: str,
    repository_id: str,
    github_ref: str,
    github_sha: str,
    github_ref_protected: str,
) -> dict[str, str]:
    """Valida branch, commit e origem antes de liberar qualquer job de CD."""
    if event_name == "workflow_dispatch":
        ref = github_ref
        sha = github_sha
        if payload.get("ref") != ref:
            raise TriggerError("A ref do workflow_dispatch diverge do contexto do runner.")
    elif event_name == "push":
        ref_value = payload.get("ref")
        sha_value = payload.get("after")
        if not isinstance(ref_value, str) or not isinstance(sha_value, str):
            raise TriggerError("O evento push não contém ref e commit completos.")
        ref = ref_value
        sha = sha_value
        if ref != github_ref or sha.lower() != github_sha.lower():
            raise TriggerError("O contexto do runner diverge da ref ou do commit do push.")
        source_repository = payload.get("repository")
        if not isinstance(source_repository, Mapping):
            raise TriggerError("O evento push não informa o repositório de origem.")
        if str(source_repository.get("id")) != repository_id or source_repository.get("full_name") != repository:
            raise TriggerError("O push não pertence ao repositório de destino.")
    else:
        raise TriggerError("Somente push autorizado ou workflow_dispatch pode iniciar CD.")

    if not ref.startswith("refs/heads/"):
        raise TriggerError("CD é permitido somente em branches de deploy.")
    branch = ref.removeprefix("refs/heads/")
    if branch not in ALLOWED_BRANCHES:
        raise TriggerError("Somente develop e main podem iniciar CD.")
    if COMMIT_PATTERN.fullmatch(sha) is None:
        raise TriggerError("O commit selecionado não é um SHA completo válido.")
    protected = github_ref_protected == "true"
    if branch == "main" and not protected:
        raise TriggerError("A branch main precisa estar protegida antes de iniciar CD.")

    return {
        "branch": branch,
        "sha": sha.lower(),
        "environment": "production" if branch == "main" else "homolog",
        "branch_protected": str(protected).lower(),
        "auto_cd_enabled": str(event_name == "push" and auto_cd_enabled == "true").lower(),
    }


def _latest_matching_run(
    runs: Sequence[Mapping[str, object]],
    *,
    branch: str,
    sha: str,
    workflow_name: str,
    repository: str,
) -> Mapping[str, object] | None:
    matches = [
        run
        for run in runs
        if run.get("head_branch") == branch
        and str(run.get("head_sha", "")).lower() == sha.lower()
        and run.get("event") == "push"
    ]
    if not matches:
        return None
    for run in matches:
        if run.get("name") != workflow_name:
            raise TriggerError("A execução encontrada não corresponde ao workflow de CI esperado.")
        head_repository = run.get("head_repository")
        if isinstance(head_repository, Mapping) and head_repository.get("full_name") != repository:
            raise TriggerError("A CI encontrada não pertence ao repositório de destino.")
    return max(
        matches,
        key=lambda run: (int(run.get("run_number", 0)), int(run.get("run_attempt", 0))),
    )


def wait_for_ci(
    fetch_runs: Callable[[], Sequence[Mapping[str, object]]],
    *,
    branch: str,
    sha: str,
    workflow_name: str,
    repository: str,
    timeout_seconds: int = 1800,
    poll_interval_seconds: int = 15,
    sleep: Callable[[float], None] = time.sleep,
    monotonic: Callable[[], float] = time.monotonic,
) -> None:
    """Aguarda o workflow CI correspondente e aceita somente conclusão success."""
    deadline = monotonic() + timeout_seconds
    while True:
        run = _latest_matching_run(
            fetch_runs(),
            branch=branch,
            sha=sha,
            workflow_name=workflow_name,
            repository=repository,
        )
        if run is not None and run.get("status") == "completed":
            if run.get("conclusion") != "success":
                raise TriggerError("A CI do mesmo commit concluiu sem success; CD bloqueado.")
            return
        remaining = deadline - monotonic()
        if remaining <= 0:
            raise TriggerError("A CI do mesmo commit não concluiu com success dentro do prazo; CD bloqueado.")
        sleep(min(poll_interval_seconds, remaining))


def fetch_ci_runs(repository: str, branch: str, sha: str) -> Sequence[Mapping[str, object]]:
    """Consulta somente as execuções do workflow ci.yml para o SHA exato."""
    query = urlencode({"branch": branch, "head_sha": sha, "event": "push", "per_page": "100"})
    endpoint = f"repos/{repository}/actions/workflows/ci.yml/runs?{query}"
    try:
        result = subprocess.run(
            ["gh", "api", endpoint],
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        response = json.loads(result.stdout)
    except (OSError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        raise TriggerError("Não foi possível consultar a execução da CI pela API do GitHub.") from error
    runs = response.get("workflow_runs") if isinstance(response, Mapping) else None
    if not isinstance(runs, list):
        raise TriggerError("A API do GitHub retornou uma resposta inválida para as execuções da CI.")
    return [run for run in runs if isinstance(run, Mapping)]


def environment_auto_cd_enabled(variables: Sequence[Mapping[str, object]]) -> bool:
    """Habilita somente pelo valor literal true da Variable do Environment."""
    matching = [item for item in variables if item.get("name") == "AUTO_CD_ENABLED"]
    if len(matching) > 1:
        raise TriggerError("A Variable AUTO_CD_ENABLED está duplicada no Environment.")
    return len(matching) == 1 and matching[0].get("value") == "true"


def fetch_environment_auto_cd(repository: str, environment: str) -> bool:
    """Lê apenas a configuração de habilitação do Environment selecionado."""
    endpoint = f"repos/{repository}/environments/{environment}/variables/AUTO_CD_ENABLED"
    try:
        result = subprocess.run(
            ["gh", "api", "--jq", ".value", endpoint],
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
    except OSError as error:
        raise TriggerError("Não foi possível consultar a Variable AUTO_CD_ENABLED do Environment.") from error
    except subprocess.CalledProcessError as error:
        if "404" in (error.stderr or ""):
            return False
        raise TriggerError("Não foi possível consultar a Variable AUTO_CD_ENABLED do Environment.") from error
    return result.stdout.strip() == "true"


def main() -> int:
    try:
        payload = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
        repository = os.environ["GITHUB_REPOSITORY"]
        ci_workflow_name = os.environ["CI_WORKFLOW_NAME"]
        outputs = validate_trigger(
            os.environ["GITHUB_EVENT_NAME"],
            payload,
            auto_cd_enabled="false",
            repository=repository,
            repository_id=os.environ["EXPECTED_REPOSITORY_ID"],
            github_ref=os.environ.get("GITHUB_REF", ""),
            github_sha=os.environ.get("GITHUB_SHA", ""),
            github_ref_protected=os.environ.get("GITHUB_REF_PROTECTED", "false"),
        )
        if os.environ["GITHUB_EVENT_NAME"] == "push":
            outputs["auto_cd_enabled"] = str(fetch_environment_auto_cd(repository, outputs["environment"])).lower()
            if outputs["auto_cd_enabled"] == "true":
                wait_for_ci(
                    lambda: fetch_ci_runs(repository, outputs["branch"], outputs["sha"]),
                    branch=outputs["branch"],
                    sha=outputs["sha"],
                    workflow_name=ci_workflow_name,
                    repository=repository,
                    timeout_seconds=int(os.environ.get("CI_WAIT_TIMEOUT_SECONDS", "1800")),
                    poll_interval_seconds=int(os.environ.get("CI_WAIT_POLL_INTERVAL_SECONDS", "15")),
                )
        output_path = os.environ.get("GITHUB_OUTPUT")
        if output_path:
            with Path(output_path).open("a", encoding="utf-8") as output_file:
                for name, value in outputs.items():
                    output_file.write(f"{name}={value}\n")
    except (KeyError, OSError, json.JSONDecodeError, TriggerError, ValueError) as error:
        print(f"Início de CD bloqueado: {error}", file=sys.stderr)
        return 1

    print("CI do commit exato aprovada; branch, commit e ambiente do CD validados.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
