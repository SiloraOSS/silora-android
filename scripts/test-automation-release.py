#!/usr/bin/env python3
"""Release safety and interruption recovery tests; no real GitHub mutations."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest import mock


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


release = load("automation_release", "automation-release.py")
dispatch = load("dispatch_release", "dispatch-automation-release.py")
SOURCE, UPSTREAM = "a" * 40, "b" * 40
TAG = "build-2026-11-04.130000.123"


def published(**changes):
    return dict(tag_name=TAG, draft=False, prerelease=False, published_at="2026-11-04T22:00:00Z",
                html_url="https://github.com/SiloraOSS/silora-android/releases/tag/" + TAG,
                assets=[{"name": name, "size": 100} for name in release.REQUIRED], **changes)


class PrepareTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.env = dict(SOURCE_SHA=SOURCE, UPSTREAM_SHA=UPSTREAM, GITHUB_REPOSITORY=release.REPO,
                        GITHUB_REF="refs/heads/main", GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_SHA=SOURCE,
                        GITHUB_RUN_ID="123", PUBLISH="true", GITHUB_OUTPUT=str(root / "output"),
                        GITHUB_STEP_SUMMARY=str(root / "summary"))
        self.addCleanup(mock.patch.stopall)
        mock.patch.dict(os.environ, self.env).start()
        self.run = mock.patch.object(release, "run", return_value=SOURCE).start()
        self.entries = []
        self.api = mock.patch.object(release, "api", side_effect=self.answer).start()

    def answer(self, path, **kwargs):
        if path.startswith("compare/"):
            return {"merge_base_commit": {"sha": UPSTREAM}}
        if path.startswith("releases?"):
            return self.entries
        if path == "git/ref/heads/main":
            return {"object": {"sha": SOURCE}}
        if path.startswith("commits/"):
            return {"sha": SOURCE}
        if path == "git/matching-refs/tags/build-":
            return []
        if path.startswith("git/ref/tags/"):
            return {"object": {"sha": SOURCE}}
        self.fail(path)

    def test_source_race_and_invalid_sha_fail_before_any_api_or_tag_write(self):
        for value in ("c" * 40, "a\nshould_build=true", "main"):
            with self.subTest(value=value), mock.patch.dict(os.environ, SOURCE_SHA=value):
                with self.assertRaises(ValueError):
                    release.prepare()
        self.api.assert_not_called()
        self.run.assert_not_called()

    def test_duplicate_complete_release_skips_without_tag_mutation(self):
        self.entries = [published()]
        release.prepare()
        self.assertIn("should_build=false", Path(self.env["GITHUB_OUTPUT"]).read_text())
        self.assertFalse(any("POST" in call.args for call in self.run.call_args_list))

    def test_failed_or_incomplete_release_does_not_suppress_retry(self):
        for alteration in ({"draft": True}, {"prerelease": True}, {"assets": []}):
            with self.subTest(alteration=alteration):
                self.entries = [published() | alteration]
                self.run.reset_mock()
                release.prepare()
                self.assertTrue(any("POST" in call.args for call in self.run.call_args_list))

    def test_validation_does_not_query_releases_or_create_refs(self):
        with mock.patch.dict(os.environ, PUBLISH="false"):
            release.prepare()
        self.assertEqual(len(self.api.call_args_list), 1)  # official ancestry only
        self.assertFalse(any("POST" in call.args for call in self.run.call_args_list))

    def test_api_failure_is_not_treated_as_no_changes(self):
        self.api.side_effect = subprocess.CalledProcessError(1, ["gh", "api"])
        with self.assertRaises(subprocess.CalledProcessError):
            release.prepare()
        self.assertFalse(any("POST" in call.args for call in self.run.call_args_list))

    def test_main_moving_while_queued_blocks_publication(self):
        answer = self.answer
        self.api.side_effect = lambda path, **kw: {"object": {"sha": "c" * 40}} if path == "git/ref/heads/main" else answer(path, **kw)
        with self.assertRaisesRegex(ValueError, "Main advanced"):
            release.prepare()
        self.assertFalse(any("POST" in call.args for call in self.run.call_args_list))

    def test_rerun_reuses_reserved_tag_and_checks_source(self):
        answer = self.answer
        self.api.side_effect = lambda path, **kw: [{"ref": "refs/tags/" + TAG}] if path == "git/matching-refs/tags/build-" else answer(path, **kw)
        release.prepare()
        self.assertIn("tag=" + TAG, Path(self.env["GITHUB_OUTPUT"]).read_text())
        self.assertFalse(any("POST" in call.args for call in self.run.call_args_list))

    def test_reserved_tag_cannot_be_retargeted_on_a_rerun(self):
        answer = self.answer
        def changed(path, **kwargs):
            if path == "git/matching-refs/tags/build-":
                self.assertTrue(kwargs["pages"])
                return [{"ref": "refs/tags/" + TAG}]
            if path == "git/ref/tags/" + TAG:
                return {"object": {"sha": "c" * 40}}
            return answer(path, **kwargs)
        self.api.side_effect = changed
        with self.assertRaisesRegex(ValueError, "different source"):
            release.prepare()
        self.assertFalse(any("POST" in call.args for call in self.run.call_args_list))

    def test_wrong_upstream_snapshot_is_rejected(self):
        self.api.return_value = {"merge_base_commit": {"sha": "c" * 40}}
        self.api.side_effect = None
        with self.assertRaisesRegex(ValueError, "official upstream"):
            release.prepare()


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = Path(self.tmp.name) / "dist"
        self.folder.mkdir()
        self.env = dict(SOURCE_SHA=SOURCE, UPSTREAM_SHA=UPSTREAM, TAG=TAG, TITLE="Silora release",
                        GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1", PUBLISH="true",
                        RUNNER_TEMP=self.tmp.name, GITHUB_STEP_SUMMARY=str(Path(self.tmp.name) / "summary"))
        self.addCleanup(mock.patch.stopall)
        mock.patch.dict(os.environ, self.env).start()
        (Path(self.tmp.name) / "automation-verification.json").write_text(json.dumps({
            "source_sha": SOURCE, "upstream_sha": UPSTREAM, "signing_certificate_sha256": "c" * 64}))
        (self.folder / "silora-phone.apk").write_bytes(b"verified signed phone")
        (self.folder / "silora-tv.apk").write_bytes(b"verified signed tv")
        (self.folder / "RELEASE_NOTES.md").write_text("Release notes\n")
        (self.folder / "build-info.json").write_text(json.dumps({
            "source_sha": SOURCE, "upstream_checked_sha": UPSTREAM, "tag": TAG}))
        release.receipt(self.folder)
        self.entries = []
        self.corrupt = False
        self.api = mock.patch.object(release, "api", side_effect=self.answer).start()
        self.run = mock.patch.object(release, "run", side_effect=self.command).start()

    def answer(self, path, **kwargs):
        if path.startswith("git/ref/"):
            return {"object": {"sha": SOURCE}}
        if path.startswith("releases?"):
            return self.entries
        if path.startswith("releases/tags/"):
            return published()
        if path.startswith("commits/"):
            return {"sha": SOURCE}
        self.fail(path)

    def command(self, *args):
        if args[:3] == ("gh", "release", "download"):
            dest = Path(args[args.index("--dir") + 1])
            for path in self.folder.iterdir():
                shutil.copyfile(path, dest / path.name)
            if self.corrupt:
                (dest / "silora-tv.apk").write_bytes(b"corrupt")
        return ""

    def mutations(self):
        return [call.args[2] for call in self.run.call_args_list if call.args[:2] == ("gh", "release")]

    def test_publish_requires_explicit_authorization(self):
        with mock.patch.dict(os.environ, PUBLISH="false"):
            with self.assertRaises(ValueError):
                release.publish(self.folder)
        self.run.assert_not_called()

    def test_both_apks_and_receipt_are_checked_before_upload(self):
        (self.folder / "silora-phone.apk").write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "Checksum"):
            release.publish(self.folder)
        self.api.assert_not_called()
        self.run.assert_not_called()

    def test_new_release_and_interrupted_draft_verify_download_before_publishing(self):
        for entries in ([], [published() | {"draft": True}]):
            with self.subTest(entries=entries):
                self.entries = entries
                self.run.reset_mock()
                release.publish(self.folder)
                actions = self.mutations()
                self.assertLess(actions.index("download"), actions.index("edit"))
                self.assertIn("upload", actions)
                self.assertEqual("create" in actions, not entries)

    def test_corrupted_download_leaves_release_unpublished(self):
        self.corrupt = True
        with self.assertRaisesRegex(ValueError, "Checksum"):
            release.publish(self.folder)
        self.assertNotIn("edit", self.mutations())

    def test_uncertain_success_reconciles_public_files_without_overwriting(self):
        self.entries = [published()]
        release.publish(self.folder)
        self.assertEqual(self.mutations(), ["download"])

    def test_incomplete_public_release_is_never_overwritten(self):
        self.entries = [published() | {"assets": []}]
        with self.assertRaisesRegex(ValueError, "incomplete public"):
            release.publish(self.folder)
        self.assertEqual(self.mutations(), [])

    def test_moved_tag_blocks_every_release_mutation(self):
        self.api.side_effect = lambda path, **kw: {"object": {"sha": "c" * 40}}
        with self.assertRaisesRegex(ValueError, "no longer points"):
            release.publish(self.folder)
        self.run.assert_not_called()

    def test_release_success_requires_public_api_confirmation(self):
        answer = self.answer
        self.api.side_effect = lambda path, **kw: published() | {"draft": True} if path.startswith("releases/tags/") else answer(path, **kw)
        with self.assertRaisesRegex(ValueError, "did not confirm"):
            release.publish(self.folder)

    def test_artifacts_from_another_run_are_rejected_before_github_queries(self):
        with mock.patch.dict(os.environ, GITHUB_RUN_ID="999"):
            with self.assertRaisesRegex(ValueError, "different workflow run"):
                release.publish(self.folder)
        self.api.assert_not_called()

    def test_checksum_manifest_cannot_omit_an_apk_or_traverse_paths(self):
        for text in ("", f"{'a' * 64}  ../secret\n"):
            with self.subTest(text=text):
                (self.folder / "SHA256SUMS").write_text(text)
                with self.assertRaises(ValueError):
                    release.checksums(self.folder)


class DispatchTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.args = argparse.Namespace(source_sha=SOURCE, upstream_sha=UPSTREAM,
                                       receipt_dir=Path(self.tmp.name), publish=True, retry_reviewed=None)
        self.path = Path(self.tmp.name) / f"{SOURCE}-release.json"
        self.addCleanup(mock.patch.stopall)
        self.find = mock.patch.object(dispatch, "find_run", return_value=None).start()
        self.run = mock.patch.object(dispatch.release, "run").start()
        mock.patch.object(dispatch.release, "api", return_value={"object": {"sha": SOURCE}}).start()
        mock.patch.object(dispatch.time, "sleep").start()
        self.active = dict(id=42, status="in_progress", conclusion=None, html_url="https://github.com/example/run/42")

    def test_active_or_successful_run_is_adopted_without_dispatch(self):
        self.find.return_value = self.active
        dispatch.dispatch(self.args)
        self.run.assert_not_called()
        self.assertEqual(json.loads(self.path.read_text())["run_id"], 42)

    def test_reconciliation_preserves_original_dispatch_time(self):
        self.path.write_text(json.dumps(dict(source_sha=SOURCE, upstream_sha=UPSTREAM,
            publish=True, workflow=release.WORKFLOW, status="dispatch_pending", dispatch_time="original")))
        self.find.return_value = self.active
        dispatch.dispatch(self.args)
        self.assertEqual(json.loads(self.path.read_text())["dispatch_time"], "original")

    def test_changed_remote_main_prevents_dispatch(self):
        dispatch.release.api.return_value = {"object": {"sha": "c" * 40}}
        with self.assertRaisesRegex(ValueError, "main differs"):
            dispatch.dispatch(self.args)
        self.run.assert_not_called()
        self.assertFalse(self.path.exists())

    def test_uncertain_dispatch_is_never_blindly_retried(self):
        self.run.side_effect = subprocess.CalledProcessError(1, ["gh", "workflow", "run"])
        with self.assertRaises(subprocess.CalledProcessError):
            dispatch.dispatch(self.args)
        self.assertEqual(json.loads(self.path.read_text())["status"], "dispatch_pending")
        self.run.reset_mock()
        with self.assertRaisesRegex(ValueError, "uncertain"):
            dispatch.dispatch(self.args)
        self.run.assert_not_called()

    def test_failed_run_requires_its_reviewed_id(self):
        self.find.return_value = self.active | dict(status="completed", conclusion="failure")
        with self.assertRaisesRegex(ValueError, "review"):
            dispatch.dispatch(self.args)
        self.run.assert_not_called()

    def test_uncertain_reviewed_retry_cannot_repeat_the_dispatch(self):
        self.find.return_value = self.active | dict(status="completed", conclusion="failure")
        self.args.retry_reviewed = 42
        self.run.side_effect = subprocess.CalledProcessError(1, ["gh", "workflow", "run"])
        with self.assertRaises(subprocess.CalledProcessError):
            dispatch.dispatch(self.args)
        self.run.reset_mock()
        with self.assertRaisesRegex(ValueError, "already have been accepted"):
            dispatch.dispatch(self.args)
        self.run.assert_not_called()

    def test_accepted_dispatch_is_matched_to_its_exact_run(self):
        self.find.side_effect = [None, self.active]
        dispatch.dispatch(self.args)
        self.assertIn(f"source_sha={SOURCE}", self.run.call_args.args)
        self.assertIn(f"upstream_sha={UPSTREAM}", self.run.call_args.args)
        self.assertIn("publish=true", self.run.call_args.args)
        self.assertEqual(json.loads(self.path.read_text())["run_id"], 42)


class RunMatchingTest(unittest.TestCase):
    def test_paginated_runs_must_match_both_shas_and_mode(self):
        good = dict(id=42, head_sha=SOURCE, display_title=f"Silora release {SOURCE} upstream {UPSTREAM}")
        wrong_upstream = good | dict(id=43, display_title=f"Silora release {SOURCE} upstream {'c' * 40}")
        wrong_source = good | dict(id=44, head_sha="d" * 40)
        validation = good | dict(id=45, display_title=f"Silora validation {SOURCE} upstream {UPSTREAM}")
        pages = [{"workflow_runs": [wrong_upstream, wrong_source, validation]}, {"workflow_runs": [good]}]
        with mock.patch.object(dispatch.release, "run", return_value=json.dumps(pages)) as run:
            self.assertEqual(dispatch.find_run(SOURCE, UPSTREAM, True)["id"], 42)
        self.assertIn("--paginate", run.call_args.args)


class NotificationTest(unittest.TestCase):
    def setUp(self):
        self.notify = load("automation_notifications", "notify-fork-automation.py")
        self.event = {"action": "completed", "workflow_run": {
            "name": self.notify.WORKFLOW, "conclusion": "success", "display_title": f"Silora validation {SOURCE}",
            "run_number": 4, "html_url": "https://github.com/example/run/4"}}

    def message(self, results):
        return self.notify.notification(self.event, [{"name": name, "conclusion": value} for name, value in results.items()], ["123"])

    def test_validation_success_does_not_claim_publication(self):
        payload = self.message(dict(prepare="success", build="success", publish="skipped"))
        self.assertIn("No release was published", payload["embeds"][0]["description"])
        self.assertEqual(payload["allowed_mentions"]["users"], [])

    def test_duplicate_success_is_explicit(self):
        payload = self.message(dict(prepare="success", build="skipped", publish="skipped"))
        self.assertIn("already published", payload["embeds"][0]["description"])

    def test_release_success_requires_publish_job_success(self):
        self.event["workflow_run"]["display_title"] = f"Silora release {SOURCE}"
        payload = self.message(dict(prepare="success", build="success", publish="success"))
        self.assertIn("published after checksum", payload["embeds"][0]["description"])

    def test_only_failures_ping_configured_users(self):
        self.event["workflow_run"]["conclusion"] = "failure"
        payload = self.message(dict(prepare="failure"))
        self.assertEqual(payload["allowed_mentions"]["users"], ["123"])
        self.assertEqual(payload["allowed_mentions"]["parse"], [])

    def test_custom_run_name_uses_verified_workflow_identity_before_sending(self):
        trigger = {"action": "in_progress", "workflow_run": {
            "name": f"Silora release {SOURCE} upstream {UPSTREAM}", "workflow_id": 123,
            "head_branch": "main", "event": "workflow_dispatch", "run_number": 4,
            "head_repository": {"full_name": release.REPO}, "html_url": "https://github.com/example/run/4"}}
        for workflow_path in (self.notify.WORKFLOW_PATH, ".github/workflows/unrelated.yml"):
            with self.subTest(path=workflow_path), \
                    mock.patch.object(self.notify.Path, "read_text", return_value=json.dumps(trigger)), \
                    mock.patch.dict(os.environ, GITHUB_EVENT_PATH="event.json", DISCORD_FAILURE_USER_IDS="111111111111111111"), \
                    mock.patch.object(self.notify.common.subprocess, "run", return_value=subprocess.CompletedProcess(
                        ["gh", "api"], 0, stdout=json.dumps({"path": workflow_path}))), \
                    mock.patch.object(self.notify.Path, "write_text"), \
                    mock.patch.object(self.notify.common, "deliver", return_value="555555555555555555") as send:
                if workflow_path == self.notify.WORKFLOW_PATH:
                    self.notify.main()
                    send.assert_called_once()
                    self.assertIn("automated release started", send.call_args.args[2]["embeds"][0]["title"])
                else:
                    with self.assertRaises(self.notify.common.NotificationError):
                        self.notify.main()
                    send.assert_not_called()

    def test_repeated_notification_skips_and_uncertain_intent_blocks_sending(self):
        trigger = {"action": "in_progress", "workflow_run": {"id": 42, "run_attempt": 1}}
        for existing, needed in (([True], False), ([False, False], True), ([False, True], None)):
            with self.subTest(existing=existing), tempfile.TemporaryDirectory() as tmp, \
                    mock.patch.object(self.notify, "trigger", return_value=trigger), \
                    mock.patch.object(self.notify, "previous_marker", side_effect=existing), \
                    mock.patch.object(self.notify.Path, "write_text"), \
                    mock.patch.dict(os.environ, GITHUB_OUTPUT=str(Path(tmp) / "output"), GITHUB_RUN_ID="999"), \
                    mock.patch.object(self.notify.common, "deliver") as send:
                if needed is None:
                    with self.assertRaisesRegex(self.notify.common.NotificationError, "prior send intent"):
                        self.notify.prepare()
                else:
                    self.notify.prepare()
                    self.assertIn(f"should_notify={str(needed).lower()}", (Path(tmp) / "output").read_text())
                send.assert_not_called()


if __name__ == "__main__":
    unittest.main()
