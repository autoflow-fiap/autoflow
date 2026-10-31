from __future__ import annotations

import unittest
from pathlib import Path

from validate_cd_trigger import TriggerError, auto_cd_enabled_for_environment, validate_trigger, wait_for_ci


REPOSITORY = "autoflow-fiap/autoflow"
REPOSITORY_ID = "12345"
CI_NAME = "AutoFlow CI"
SHA = "a" * 40


def push_event(*, branch: str = "develop", sha: str = SHA, repository_id: str = REPOSITORY_ID):
    return {"ref": f"refs/heads/{branch}", "after": sha, "repository": {"id": int(repository_id), "full_name": REPOSITORY}}


def ci_run(*, branch: str = "develop", sha: str = SHA, status: str = "completed", conclusion: str = "success", name: str = CI_NAME, attempt: int = 1):
    return {"head_branch": branch, "head_sha": sha, "event": "push", "status": status, "conclusion": conclusion, "name": name, "run_number": 10, "run_attempt": attempt, "head_repository": {"full_name": REPOSITORY}}


class ValidateCdTriggerTests(unittest.TestCase):
    def validate_push(self, event=None):
        selected = event or push_event()
        branch = selected["ref"].removeprefix("refs/heads/")
        return validate_trigger("push", selected, auto_cd_enabled="true", repository=REPOSITORY, repository_id=REPOSITORY_ID, github_ref=f"refs/heads/{branch}", github_sha=selected["after"], github_ref_protected="true" if branch == "main" else "false")

    def test_successful_ci_must_match_same_branch_commit_and_workflow(self):
        result = self.validate_push()
        wait_for_ci(lambda: [ci_run()], branch=result["branch"], sha=result["sha"], workflow_name=CI_NAME, repository=REPOSITORY)
        self.assertEqual(result["environment"], "homolog")

    def test_main_maps_to_production_and_requires_protected_ref(self):
        self.assertEqual(self.validate_push(push_event(branch="main"))["environment"], "production")
        with self.assertRaises(TriggerError):
            validate_trigger("push", push_event(branch="main"), auto_cd_enabled="true", repository=REPOSITORY, repository_id=REPOSITORY_ID, github_ref="refs/heads/main", github_sha=SHA, github_ref_protected="false")

    def test_automatic_trigger_is_opt_in(self):
        result = validate_trigger("push", push_event(), auto_cd_enabled="false", repository=REPOSITORY, repository_id=REPOSITORY_ID, github_ref="refs/heads/develop", github_sha=SHA, github_ref_protected="false")
        self.assertEqual(result["auto_cd_enabled"], "false")

    def test_auto_cd_uses_injected_values_and_keeps_environments_independent(self):
        values = {"AUTO_CD_ENABLED_HOMOLOG": "true", "AUTO_CD_ENABLED_PRODUCTION": "false"}
        self.assertTrue(auto_cd_enabled_for_environment("homolog", values))
        self.assertFalse(auto_cd_enabled_for_environment("production", values))
        self.assertFalse(auto_cd_enabled_for_environment("production", {}))
        with self.assertRaises(TriggerError):
            auto_cd_enabled_for_environment("staging", values)

    def test_auto_cd_does_not_query_environment_administration_api(self):
        source = (Path(__file__).parent / "validate_cd_trigger.py").read_text(encoding="utf-8")
        self.assertNotIn("/environments/", source)

    def test_failed_cancelled_and_skipped_ci_never_pass(self):
        for conclusion in ("failure", "cancelled", "skipped", "timed_out", "action_required"):
            with self.subTest(conclusion=conclusion), self.assertRaises(TriggerError):
                wait_for_ci(lambda: [ci_run(conclusion=conclusion)], branch="develop", sha=SHA, workflow_name=CI_NAME, repository=REPOSITORY)

    def test_ci_is_polled_until_success(self):
        results = iter(([], [ci_run(status="in_progress", conclusion=None)], [ci_run()]))
        wait_for_ci(lambda: next(results), branch="develop", sha=SHA, workflow_name=CI_NAME, repository=REPOSITORY, timeout_seconds=30, poll_interval_seconds=1, sleep=lambda _: None)

    def test_wrong_sha_branch_event_or_workflow_never_authorizes_ci(self):
        for run in (ci_run(sha="b" * 40), ci_run(branch="main"), {**ci_run(), "event": "pull_request"}):
            with self.subTest(run=run), self.assertRaises(TriggerError):
                wait_for_ci(lambda run=run: [run], branch="develop", sha=SHA, workflow_name=CI_NAME, repository=REPOSITORY, timeout_seconds=0)
        with self.assertRaises(TriggerError):
            wait_for_ci(lambda: [ci_run(name="Outro workflow")], branch="develop", sha=SHA, workflow_name=CI_NAME, repository=REPOSITORY)

    def test_fork_and_inconsistent_push_context_are_rejected(self):
        fork = ci_run()
        fork["head_repository"] = {"full_name": "fork/autoflow"}
        with self.assertRaises(TriggerError):
            wait_for_ci(lambda: [fork], branch="develop", sha=SHA, workflow_name=CI_NAME, repository=REPOSITORY)
        with self.assertRaises(TriggerError):
            validate_trigger("push", push_event(sha="b" * 40), auto_cd_enabled="true", repository=REPOSITORY, repository_id=REPOSITORY_ID, github_ref="refs/heads/develop", github_sha=SHA, github_ref_protected="false")
        with self.assertRaises(TriggerError):
            self.validate_push(push_event(repository_id="98765"))

    def test_manual_plan_remains_available_when_auto_cd_is_disabled(self):
        result = validate_trigger("workflow_dispatch", {"ref": "refs/heads/develop"}, auto_cd_enabled="", repository=REPOSITORY, repository_id=REPOSITORY_ID, github_ref="refs/heads/develop", github_sha=SHA, github_ref_protected="false")
        self.assertEqual(result["sha"], SHA)
        self.assertEqual(result["environment"], "homolog")

    def test_manual_smoke_context_selects_environment_from_validated_branch(self):
        for branch, environment in (("develop", "homolog"), ("main", "production")):
            with self.subTest(branch=branch):
                result = validate_trigger(
                    "workflow_dispatch",
                    {"ref": f"refs/heads/{branch}"},
                    auto_cd_enabled="",
                    repository=REPOSITORY,
                    repository_id=REPOSITORY_ID,
                    github_ref=f"refs/heads/{branch}",
                    github_sha=SHA,
                    github_ref_protected="true" if branch == "main" else "false",
                )
                self.assertEqual(result["environment"], environment)

    def test_feature_and_pull_request_events_are_rejected(self):
        with self.assertRaises(TriggerError):
            self.validate_push(push_event(branch="feature/unsafe"))
        with self.assertRaises(TriggerError):
            validate_trigger("pull_request", {}, auto_cd_enabled="true", repository=REPOSITORY, repository_id=REPOSITORY_ID, github_ref="refs/pull/1/merge", github_sha=SHA, github_ref_protected="false")


if __name__ == "__main__":
    unittest.main()
