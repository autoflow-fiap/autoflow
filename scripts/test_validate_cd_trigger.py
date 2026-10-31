from __future__ import annotations

import subprocess
import unittest
from unittest.mock import patch

from validate_cd_trigger import TriggerError, environment_auto_cd_enabled, fetch_environment_auto_cd, validate_trigger, wait_for_ci


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
        self.assertFalse(environment_auto_cd_enabled([]))
        self.assertFalse(environment_auto_cd_enabled([{"name": "AUTO_CD_ENABLED", "value": "false"}]))
        self.assertTrue(environment_auto_cd_enabled([{"name": "AUTO_CD_ENABLED", "value": "true"}]))
        with self.assertRaises(TriggerError):
            environment_auto_cd_enabled([{"name": "AUTO_CD_ENABLED", "value": "true"}, {"name": "AUTO_CD_ENABLED", "value": "false"}])

    @patch("validate_cd_trigger.subprocess.run")
    def test_automation_variable_is_read_from_the_target_environment(self, run):
        run.return_value = subprocess.CompletedProcess(args=[], returncode=0, stdout="true\n", stderr="")
        self.assertTrue(fetch_environment_auto_cd(REPOSITORY, "production"))
        api_args = run.call_args.args[0]
        self.assertIn("environments/production/variables/AUTO_CD_ENABLED", api_args[-1])
        self.assertIn("--jq", api_args)
        self.assertNotIn("--paginate", api_args)

    @patch("validate_cd_trigger.subprocess.run")
    def test_missing_environment_variable_keeps_automation_disabled(self, run):
        run.side_effect = subprocess.CalledProcessError(returncode=1, cmd=["gh", "api"], stderr="gh: Not Found (HTTP 404)")
        self.assertFalse(fetch_environment_auto_cd(REPOSITORY, "homolog"))

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

    def test_feature_and_pull_request_events_are_rejected(self):
        with self.assertRaises(TriggerError):
            self.validate_push(push_event(branch="feature/unsafe"))
        with self.assertRaises(TriggerError):
            validate_trigger("pull_request", {}, auto_cd_enabled="true", repository=REPOSITORY, repository_id=REPOSITORY_ID, github_ref="refs/pull/1/merge", github_sha=SHA, github_ref_protected="false")


if __name__ == "__main__":
    unittest.main()
