#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "announce_silora_release", Path(__file__).with_name("announce-silora-release.py"),
)
announce = importlib.util.module_from_spec(spec)
spec.loader.exec_module(announce)

TAG = "build-2026-11-21.151829.123"
SOURCE = "a" * 40
URL = f"https://github.com/{announce.release.REPO}/releases/tag/{TAG}"
MESSAGE = f"https://discord.com/channels/{announce.receipts.GUILD}/{announce.CHANNEL}/1234567890123456789"
HIGHLIGHTS = (
    "- Watch History can load older items after an empty page. *(Silora)*\n"
    "- The TV sign-in address displays correctly in Hebrew. *(Silo upstream)*"
)
RELEASE = {
    "id": 123,
    "tag_name": TAG,
    "html_url": URL,
    "name": "Silora build 31",
    "body": "Silora build 31\n\n- Fixed a thing.\n\n## Downloads\n\n- `silora-phone.apk`: Phone.",
    "published_at": "2026-11-21T00:00:00Z",
    "draft": False,
    "prerelease": False,
    "assets": [{"name": name, "size": 100} for name in announce.release.REQUIRED],
}


class ReleaseAnnouncementTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.ready = Path(temp.name) / "silora-updates.ready"
        ready = patch.object(announce, "UPDATES_READY", self.ready)
        ready.start()
        self.addCleanup(ready.stop)

    def test_verifies_the_exact_public_tag(self):
        with patch.object(announce.release, "api", side_effect=[RELEASE, {"sha": SOURCE}]) as api:
            self.assertEqual(announce.verified_release(TAG, SOURCE), RELEASE)
        self.assertEqual([call.args[0] for call in api.call_args_list], [f"releases/tags/{TAG}", f"commits/{TAG}"])

    def test_rejects_a_draft_incomplete_release_or_different_source(self):
        for data, sha in ((dict(RELEASE, draft=True), SOURCE),
                          (dict(RELEASE, assets=[]), SOURCE), (RELEASE, "b" * 40)):
            with self.subTest(data=data, sha=sha):
                with patch.object(announce.release, "api", side_effect=[data, {"sha": sha}]):
                    with self.assertRaisesRegex(ValueError, "complete public release"):
                        announce.verified_release(TAG, SOURCE)

    def test_embed_uses_the_release_date_and_pins_the_downloads(self):
        body = announce.embed_body(RELEASE, HIGHLIGHTS)
        self.assertEqual(body.count("Silora build 31"), 1)
        self.assertIn("Nov 20, 2026", body)
        self.assertIn(f"### What changed\n\n{HIGHLIGHTS}", body)
        self.assertIn(f"/releases/download/{TAG}/silora-phone.apk", body)
        self.assertIn(f"/releases/download/{TAG}/silora-tv.apk", body)
        self.assertIn(URL, body)
        self.assertNotIn("## Downloads", body)

    def test_oversized_highlights_are_rejected_in_utf16_units(self):
        with self.assertRaisesRegex(ValueError, "fit one Discord embed"):
            announce.embed_body(RELEASE, f"- {'📺' * 2000} *(Silora)*")

    def test_highlights_require_a_source_label_for_each_bullet(self):
        for line in ("- Missing source.", "- Unrecognized source. *(GitHub)*", "## Another list"):
            with self.subTest(line=line):
                with self.assertRaisesRegex(ValueError, "one bullet per line"):
                    announce.embed_body(RELEASE, f"{HIGHLIGHTS}\n{line}")

    def test_verified_delivery_is_reused_without_second_post(self):
        dry_run = subprocess.CompletedProcess([], 0, "Dry run", "")
        posted = subprocess.CompletedProcess([], 0, f"Posted and verified: {MESSAGE}\n", "")
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(announce, "STATE", Path(directory)):
                with patch.object(announce.subprocess, "run", side_effect=[dry_run, posted]) as run:
                    self.assertEqual(announce.publish(TAG, SOURCE, RELEASE, HIGHLIGHTS, "nonce"), MESSAGE)
                    self.assertEqual(announce.publish(TAG, SOURCE, RELEASE, HIGHLIGHTS, "nonce"), MESSAGE)
                    self.assertEqual(run.call_count, 2)
                    self.assertEqual(run.call_args_list[0].args[0][-1], "--dry-run")
                    command = run.call_args_list[1].args[0]
                    self.assertIn(announce.CHANNEL, command)
                    self.assertIn("--embed", command)
                    self.assertIn("--no-roles", command)
                    self.assertNotIn("--dry-run", command)

    def test_verified_bot_deployment_enables_the_release_ping_and_controls(self):
        self.ready.touch()
        dry_run = subprocess.CompletedProcess([], 0, "Dry run", "")
        posted = subprocess.CompletedProcess([], 0, f"Posted and verified: {MESSAGE}\n", "")
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(announce, "STATE", Path(directory)):
                with patch.object(announce.subprocess, "run", side_effect=[dry_run, posted]) as run:
                    self.assertEqual(announce.publish(TAG, SOURCE, RELEASE, HIGHLIGHTS, "nonce"), MESSAGE)
                    self.assertIn("--silora-updates", run.call_args.args[0])
                    self.assertNotIn("--no-roles", run.call_args.args[0])

    def test_unconfirmed_delivery_preserves_the_message_id_and_stops_retries(self):
        dry_run = subprocess.CompletedProcess([], 0, "Dry run", "")
        failed = subprocess.CompletedProcess([], 1, "", "Announcement created as message 123, but read-back failed.")
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(announce, "STATE", Path(directory)):
                with patch.object(announce.subprocess, "run", side_effect=[dry_run, failed]) as run:
                    for _ in range(2):
                        with self.assertRaisesRegex(ValueError, "delivery is uncertain"):
                            announce.publish(TAG, SOURCE, RELEASE, HIGHLIGHTS, "nonce")
                    self.assertEqual(run.call_count, 2)
                    receipt = next(Path(directory).rglob("announcement.json"))
                    self.assertEqual(json.loads(receipt.read_text())["message_id"], "123")

    def test_audit_preflight_exits_before_any_post(self):
        dry_run = subprocess.CompletedProcess([], 0, "Audit channel: 9876 (#staff)", "")
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(announce, "STATE", Path(directory)):
                with patch.object(announce.subprocess, "run", return_value=dry_run) as run:
                    with self.assertRaisesRegex(ValueError, "extra audit message"):
                        announce.publish(TAG, SOURCE, RELEASE, HIGHLIGHTS, "nonce")
                    run.assert_called_once()
                    self.assertFalse(list(Path(directory).rglob("announcement.json")))


if __name__ == "__main__":
    unittest.main()
