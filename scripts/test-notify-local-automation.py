#!/usr/bin/env python3
"""Test local milestones and uncertain sends without real Discord writes."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

SCRIPT = Path(__file__).with_name("notify-local-automation.py")
spec = importlib.util.spec_from_file_location("local_notify", SCRIPT)
notify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(notify)
SOURCE, UPSTREAM = "a" * 40, "b" * 40
HOOK = "1563711680616206427"
MESSAGE = "1567778682934923445"
URL = f"https://discord.com/api/webhooks/{HOOK}/test-token"
REPORT = dict(summary="Merged two upstream fixes and pushed main. No conflicts; fork behavior retained.",
              upstream_sha=UPSTREAM, merge_sha=SOURCE, recovery_ref="refs/codex/upstream-merge/20261105T205830Z",
              validation="12,450 tests, full phone/TV release lint, signed R8 builds, APK verification, mappings and checksums.",
              changelog_line=7, workflow_run_id=123)
LOCAL_TAG = "build-2026-11-19.130000.1795122000000000"
LOCAL_URL = f"https://github.com/{notify.REPO}/releases/tag/{LOCAL_TAG}"


def workflow(**changes):
    return dict(head_sha=SOURCE, head_branch="main", event="workflow_dispatch",
                head_repository={"full_name": notify.REPO}, path=".github/workflows/fork-automation-release.yml",
                display_title=f"Silora release {SOURCE} upstream {UPSTREAM}",
                html_url=f"https://github.com/{notify.REPO}/actions/runs/123", status="in_progress", **changes)


class PayloadTest(unittest.TestCase):
    def test_handoff_matches_requested_summary_and_uses_public_changelog_link(self):
        with mock.patch.object(notify, "workflow_for", return_value=workflow()):
            payload = notify.payload_for("handoff", REPORT, "example")
        body = payload["embeds"][0]["description"]
        for value in (REPORT["summary"], SOURCE, UPSTREAM, REPORT["validation"], REPORT["recovery_ref"],
                      f"/blob/{SOURCE}/CHANGELOG.md#L7", "Publication success is not yet confirmed", "No device testing occurred"):
            self.assertIn(value, body)
        self.assertEqual(payload["allowed_mentions"], dict(parse=[], users=[], roles=[], replied_user=False))
        self.assertNotIn("/home/", body)

    def test_completed_workflow_is_not_described_as_in_progress(self):
        with mock.patch.object(notify, "workflow_for", return_value=workflow() | dict(status="completed", conclusion="failure")):
            body = notify.payload_for("handoff", REPORT, "example")["embeds"][0]["description"]
        self.assertIn("result **failure**", body)
        self.assertNotIn("is in progress", body)
        self.assertNotIn("publication succeeded", body)

    def test_wrong_source_or_validation_only_run_blocks_handoff(self):
        for changed in (dict(head_sha="c" * 40), dict(display_title=f"Silora validation {SOURCE} upstream {UPSTREAM}"),
                        dict(path=".github/workflows/other.yml"), dict(head_branch="feature")):
            with self.subTest(changed=changed), mock.patch.object(notify.subprocess, "run", return_value=subprocess.CompletedProcess(
                    ["gh", "api"], 0, stdout=json.dumps(workflow() | changed))):
                with self.assertRaises(notify.NotificationError):
                    notify.workflow_for(REPORT)

    def test_real_release_run_metadata_is_accepted(self):
        with mock.patch.object(notify.subprocess, "run", return_value=subprocess.CompletedProcess(
                ["gh", "api"], 0, stdout=json.dumps(workflow()))):
            self.assertEqual(notify.workflow_for(REPORT)["head_sha"], SOURCE)

    def test_secret_local_path_and_oversize_report_are_rejected(self):
        for value in (URL, "Changelog: /home/user/private/file", "a" * 4097, "\U0001f600" * 2050):
            with self.subTest(value=value[:30]), self.assertRaises(notify.NotificationError):
                notify.payload_for("validating", REPORT | dict(summary=value), "example")

    def test_merge_progress_does_not_claim_validation_or_push_succeeded(self):
        body = notify.payload_for("validating", REPORT | dict(summary="Two fixes are ready for validation."), "example")["embeds"][0]["description"]
        self.assertIn("before committing and pushing", body)
        self.assertNotIn("Passed:", body)

    def test_release_only_review_distinguishes_approval_from_upload(self):
        report = dict(source_sha=SOURCE, validation="Signed phone and TV builds, lint and unit tests passed.")
        for event, expected in (("release_building", "Running local tests"),
                                ("release_ready", "Waiting for approval"),
                                ("release_uploading", "Uploading the signed APKs")):
            with self.subTest(event=event):
                payload = notify.payload_for(event, report, "example")
                body = payload["embeds"][0]["description"]
                self.assertIn(SOURCE, body)
                self.assertIn(expected, body)
                self.assertNotIn("upstream", body.lower())
                self.assertEqual(payload["allowed_mentions"]["parse"], [])

    def test_publication_requires_a_public_release_and_matching_source_tag(self):
        report = dict(source_sha=SOURCE, tag=LOCAL_TAG)
        release = dict(tag_name=LOCAL_TAG, html_url=LOCAL_URL, published_at="2026-11-19T21:00:00Z",
                       draft=False, prerelease=False, assets=[dict(name="silora-phone.apk"), dict(name="silora-tv.apk")])
        ref = dict(ref=f"refs/tags/{LOCAL_TAG}", object=dict(sha=SOURCE))

        def response(value):
            return subprocess.CompletedProcess(["gh", "api"], 0, stdout=json.dumps(value))

        with mock.patch.object(notify.subprocess, "run", side_effect=[response(release), response(ref)]) as gh:
            embed = notify.payload_for("release_published", report, "example")["embeds"][0]
            self.assertEqual(embed["url"], LOCAL_URL)
            self.assertIn(SOURCE, embed["description"])
            self.assertEqual([call.args[0][-1] for call in gh.call_args_list], [
                f"repos/{notify.REPO}/releases/tags/{LOCAL_TAG}",
                f"repos/{notify.REPO}/git/ref/tags/{LOCAL_TAG}",
            ])
        for changed_release, changed_ref in ((dict(draft=True), {}), (dict(prerelease=True), {}),
                                             (dict(assets=[]), {}), ({}, dict(object=dict(sha=UPSTREAM)))):
            with self.subTest(release=changed_release, ref=changed_ref), mock.patch.object(
                    notify.subprocess, "run", side_effect=[response(release | changed_release), response(ref | changed_ref)]):
                with self.assertRaises(notify.NotificationError):
                    notify.payload_for("release_published", report, "example")

    def test_preview_does_not_need_credentials_or_create_a_ledger(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = Path(tmp) / "state"
            result = subprocess.run(["python3", "-B", str(SCRIPT), "started", "--preview", "--execution-id", "preview",
                                     "--config", str(Path(tmp) / "absent.json"), "--state-dir", str(state)],
                                    capture_output=True, text=True, check=True)
            self.assertIn("automation started", result.stdout)
            self.assertFalse(state.exists())

    def test_readback_rejects_wrong_author_channel_content_or_mentions(self):
        payload = notify.payload_for("started", None, "example")
        message = dict(id=MESSAGE, channel_id=notify.CHANNEL, webhook_id=HOOK,
                       content="", embeds=payload["embeds"], mentions=[])
        config = dict(webhook_id=HOOK)
        for changes in (dict(channel_id="other"), dict(webhook_id="other"),
                        dict(content="unexpected"), dict(mentions=[{"id": "123"}]),
                        dict(embeds=[payload["embeds"][0] | dict(description="changed")])):
            with self.subTest(changes=changes), self.assertRaises(notify.NotificationError):
                notify.verify_message(message | changes, payload, config, MESSAGE)


class DeliveryTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.state = root / "state"
        self.config_path = root / "config.json"
        self.config = dict(webhook_url=URL, webhook_id=HOOK, channel_id=notify.CHANNEL, guild_id=notify.GUILD)
        self.config_path.write_text(json.dumps(self.config))
        self.config_path.chmod(0o600)
        self.execution = "test-execution"
        self.run_folder = self.state / notify.identity(self.execution)
        self.calls = []
        self.message = None
        self.post_error = False
        self.read_error = False
        self.wrong_channel = False
        self.patch = mock.patch.object(notify.common, "discord_request", side_effect=self.request)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def request(self, url, payload=None):
        self.calls.append((url, payload))
        if url == URL:
            return dict(id=HOOK, channel_id="other" if self.wrong_channel else notify.CHANNEL,
                        guild_id=notify.GUILD, type=1)
        if payload is not None:
            event = payload["embeds"][0]["footer"]["text"].split(" · ")[-1]
            intent = json.loads((self.run_folder / f"{event}.json").read_text())
            self.assertEqual(intent["status"], "pending")
            self.assertNotIn("test-token", json.dumps(intent))
            self.message = dict(id=MESSAGE, channel_id=notify.CHANNEL, webhook_id=HOOK,
                                content=payload["content"], embeds=payload["embeds"], mentions=[])
            if self.post_error:
                raise notify.common.NotificationError("Uncertain send")
            return self.message
        if self.read_error:
            raise notify.common.NotificationError("Read-back unavailable")
        return self.message

    def send(self, event="started", report=None, message_id=None):
        return notify.notify(event, report, self.execution, self.config_path, self.state, message_id)

    def posts(self):
        return [call for call in self.calls if call[1] is not None]

    def test_send_is_verified_and_duplicate_has_no_network_call(self):
        result = self.send()
        self.assertEqual(result["status"], "delivered")
        self.assertEqual(len(self.posts()), 1)
        count = len(self.calls)
        self.assertEqual(self.send()["status"], "already_delivered")
        self.assertEqual(len(self.calls), count)
        for path in self.run_folder.glob("*.json"):
            self.assertEqual(path.stat().st_mode & 0o077, 0)
        self.assertEqual(self.run_folder.stat().st_mode & 0o077, 0)

    def test_uncertain_post_is_never_repeated(self):
        self.post_error = True
        with self.assertRaises(notify.common.NotificationError):
            self.send()
        with self.assertRaisesRegex(notify.NotificationError, "uncertain"):
            self.send()
        self.assertEqual(len(self.posts()), 1)

    def test_known_message_survives_failed_readback_and_resumes_with_get_only(self):
        self.read_error = True
        with self.assertRaises(notify.common.NotificationError):
            self.send()
        receipt = json.loads((self.run_folder / "started.json").read_text())
        self.assertEqual(receipt["message_id"], MESSAGE)
        self.read_error = False
        self.assertEqual(self.send()["status"], "delivered")
        self.assertEqual(len(self.posts()), 1)

    def test_unknown_outcome_can_be_reconciled_by_actual_message_id_without_post(self):
        self.post_error = True
        with self.assertRaises(notify.common.NotificationError):
            self.send()
        self.assertEqual(self.send(message_id=MESSAGE)["status"], "delivered")
        self.assertEqual(len(self.posts()), 1)

    def test_wrong_destination_never_posts(self):
        self.wrong_channel = True
        with self.assertRaisesRegex(notify.NotificationError, "destination changed"):
            self.send()
        self.assertEqual(self.posts(), [])
        self.assertFalse((self.run_folder / "started.json").exists())

    def test_changed_event_content_is_not_sent_again(self):
        self.send(report={"note": "first"})
        with self.assertRaisesRegex(notify.NotificationError, "different content"):
            self.send(report={"note": "changed"})
        self.assertEqual(len(self.posts()), 1)

    def test_reconciliation_cannot_send_a_fresh_message(self):
        with self.assertRaisesRegex(notify.NotificationError, "no pending intent"):
            self.send(message_id=MESSAGE)
        self.assertEqual(self.posts(), [])

    def test_terminal_intent_prevents_later_milestones_even_after_interrupted_metadata_update(self):
        self.run_folder.mkdir(parents=True)
        (self.run_folder / "unchanged.json").write_text("{}")
        with self.assertRaisesRegex(notify.NotificationError, "already ended"):
            self.send()
        self.assertEqual(self.posts(), [])

    def test_release_only_can_publish_after_a_reviewed_retry(self):
        report = dict(source_sha=SOURCE)
        self.send("release_building", report)
        self.send("release_uploading", report)
        self.assertEqual(self.send("release_uploading", report)["status"], "already_delivered")
        self.send("release_stopped", report | dict(stage="publication", summary="The publish job failed."))
        with mock.patch.object(notify, "published_release_url", return_value=LOCAL_URL):
            self.send("release_published", report | dict(tag=LOCAL_TAG))
        with self.assertRaisesRegex(notify.NotificationError, "already ended"):
            self.send("release_ready", report | dict(validation="Signed builds passed."))
        self.assertEqual(len(self.posts()), 4)

    def test_local_merge_start_and_release_milestones_share_one_receipt(self):
        report = dict(source_sha=SOURCE)
        self.send("started")
        self.send("release_building", report)
        self.send("release_ready", report | dict(validation="Signed builds passed."))
        self.send("release_uploading", report)
        with mock.patch.object(notify, "published_release_url", return_value=LOCAL_URL):
            self.send("release_published", report | dict(tag=LOCAL_TAG))
        self.assertEqual(len(self.posts()), 5)
        run = json.loads((self.run_folder / "run.json").read_text())
        self.assertEqual(run["terminal_event"], "release_published")

    def test_insecure_config_is_rejected_without_network_access(self):
        self.config_path.chmod(0o644)
        with self.assertRaisesRegex(notify.NotificationError, "mode 600"):
            self.send()
        self.assertEqual(self.calls, [])


if __name__ == "__main__":
    unittest.main()
