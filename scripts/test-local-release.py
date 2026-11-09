#!/usr/bin/env python3
"""Exercise byte preservation, source validation, stale builds and upload recovery."""
import datetime as dt
import contextlib
import io
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location("local_release", Path(__file__).with_name("local-release.py"))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ArchiveFixture:
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.archive = self.root / "archive"
        self.archive.mkdir()
        self.info = release.build.fork_release.metadata(
            dt.datetime(2026, 11, 8, 22, 15, tzinfo=dt.timezone.utc), 1,
            "a" * 40, "b" * 40,
            upstream={"version": "1.0.0", "build": 29, "tag": "v1.0.0-beta.29"})
        self.info.update(tag="local-2026-11-08.221500", local_build=True, targets=["phone", "tv"],
                         working_tree_dirty=True, tracked_tree_dirty=False, untracked_build_inputs=False,
                         validation={"release_lint": True, "unit_tests": False,
                                     "signing_certificate_sha256": "c" * 64})
        for name in release.FILES:
            (self.archive / name).write_text("fixture " + name)
        self.write_info()

    def checksums(self):
        (self.archive / "SHA256SUMS").write_text("".join(
            f"{release.digest(self.archive / name)}  {name}\n" for name in sorted(release.PAYLOAD)))

    def write_info(self):
        (self.archive / "build-info.json").write_text(json.dumps(self.info))
        self.checksums()

class ReleaseTests(ArchiveFixture, unittest.TestCase):
    def test_clean_tracked_source_with_unrelated_untracked_notes_is_publishable(self):
        self.assertEqual(release.validate_archive(self.archive)["source_sha"], "a" * 40)

    def test_detects_modified_apk_or_mapping(self):
        for name in ("silora-tv.apk", "silora-phone-mapping.txt", "build-info.json"):
            with self.subTest(name=name):
                original = (self.archive / name).read_bytes()
                (self.archive / name).write_bytes(b"replacement")
                with self.assertRaisesRegex(ValueError, "Checksum mismatch"):
                    release.validate_archive(self.archive)
                (self.archive / name).write_bytes(original)

    def test_missing_phone_stops_tv_only_publication(self):
        (self.archive / "silora-phone.apk").unlink()
        with self.assertRaisesRegex(ValueError, "both APKs"):
            release.validate_archive(self.archive)

    def test_refuses_extra_files_and_symlinks(self):
        (self.archive / "private.txt").write_text("must not be uploaded")
        with self.assertRaises(ValueError):
            release.validate_archive(self.archive)
        (self.archive / "private.txt").unlink()
        (self.archive / "silora-tv.apk").unlink()
        (self.archive / "silora-tv.apk").symlink_to(self.archive / "silora-phone.apk")
        with self.assertRaisesRegex(ValueError, "linked"):
            release.validate_archive(self.archive)

    def test_rejects_dirty_untracked_source_and_missing_lint_evidence(self):
        cases = [("tracked_tree_dirty", True), ("untracked_build_inputs", True),
                 ("validation", {"release_lint": False}), ("tracked_tree_dirty", None)]
        original = dict(self.info)
        for key, value in cases:
            with self.subTest(key=key):
                self.info = dict(original, **{key: value})
                self.write_info()
                with self.assertRaises(ValueError):
                    release.validate_archive(self.archive)

    def test_rejects_unsafe_or_duplicate_checksum_names(self):
        for line in (f'{"a" * 64}  ../private\n', f'{"a" * 64}  silora-tv.apk\n' * 2):
            (self.archive / "SHA256SUMS").write_text(line)
            with self.assertRaisesRegex(ValueError, "Invalid or duplicate"):
                release.validate_archive(self.archive)

    def test_staging_keeps_apks_mappings_and_original_archive_unchanged(self):
        before = {p.name: p.read_bytes() for p in self.archive.iterdir()}
        staged = self.root / "staged"
        staged.mkdir()
        info = release.stage_files(self.archive, self.info, staged)
        self.assertTrue(release.build.fork_release.BUILD_TAG.fullmatch(info["tag"]))
        self.assertEqual(info["source_sha"], self.info["source_sha"])
        for name in release.PAYLOAD - {"build-info.json"}:
            self.assertEqual((staged / name).read_bytes(), before[name])
        self.assertEqual({p.name: p.read_bytes() for p in self.archive.iterdir()}, before)
        release.validate_archive(staged, manifest_sha256=release.digest(staged / "SHA256SUMS"),
                                 tag=info["tag"], source=info["source_sha"])

    def test_bound_manifest_blocks_replacing_checksums_after_dispatch(self):
        with self.assertRaisesRegex(ValueError, "locally verified release"):
            release.validate_archive(self.archive, manifest_sha256="d" * 64)

    def test_wrong_source_and_tag_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "source differs"):
            release.validate_archive(self.archive, source="d" * 40)
        with self.assertRaisesRegex(ValueError, "tag differs"):
            release.validate_archive(self.archive, tag="../../bad")

    def test_local_verification_uses_archived_apks_and_saved_key(self):
        with mock.patch.object(release.build, "signing_environment", return_value=({"ANDROID_HOME": "/sdk"}, "c" * 64)), \
             mock.patch.object(release.build, "verify_apks") as verify:
            release.verify_local(self.archive, self.info, Path("/private/signing"))
        self.assertEqual(verify.call_args.kwargs["apk_directory"], self.archive)

    def test_wrong_persistent_signing_key_stops_before_apk_verification(self):
        with mock.patch.object(release.build, "signing_environment", return_value=({}, "d" * 64)), \
             mock.patch.object(release.build, "verify_apks") as verify:
            with self.assertRaisesRegex(ValueError, "persistent signing key"):
                release.verify_local(self.archive, self.info, Path("/private/signing"))
            verify.assert_not_called()

    def test_unpushed_or_divergent_source_is_rejected(self):
        with mock.patch.object(release, "api", return_value={"merge_base_commit": {"sha": "d" * 40}}):
            with self.assertRaisesRegex(ValueError, "pushed and included"):
                release.check_source("a" * 40)

    def test_shared_order_check_uses_the_local_authenticated_transport(self):
        with mock.patch.object(release.order, "ensure_newer") as check:
            release.ensure_newer(self.info)
            check.assert_called_once_with(self.info, release.REPO, transport=release.gh)

    def test_existing_tag_is_never_retargeted(self):
        with mock.patch.object(release, "api", return_value=[{"ref": "refs/tags/test", "object": {"sha": "d" * 40}}]), \
             mock.patch.object(release, "gh") as gh:
            with self.assertRaisesRegex(ValueError, "refusing to move"):
                release.check_tag("test", "a" * 40, create=True)
            gh.assert_not_called()

    def test_github_dry_run_has_no_external_side_effects(self):
        with mock.patch.object(sys, "argv", ["local-release.py", "github", "--dry-run"]), \
             mock.patch.object(release, "gh") as gh, contextlib.redirect_stdout(io.StringIO()) as out:
            release.main()
            gh.assert_not_called()
            self.assertIn("fork-manual-release.yml", out.getvalue())

    def test_local_preview_never_creates_tags_drafts_or_workflow_runs(self):
        with mock.patch.object(sys, "argv", ["local-release.py", "local", str(self.archive)]), \
             mock.patch.object(release, "verify_local"), mock.patch.object(release, "check_source"), \
             mock.patch.object(release, "ensure_newer"), mock.patch.object(release, "gh") as gh, \
             contextlib.redirect_stdout(io.StringIO()):
            release.main()
            gh.assert_not_called()

    def test_finalize_is_not_callable_outside_shared_workflow(self):
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(release, "gh") as gh:
            with self.assertRaisesRegex(ValueError, "locked"):
                release.finalize("tag", "a" * 40, "b" * 64)
            gh.assert_not_called()


class FakeGitHubTests(ArchiveFixture, unittest.TestCase):
    """Run real staging/finalization logic against an in-memory GitHub transport."""
    def setUp(self):
        super().setUp()
        output = contextlib.redirect_stdout(io.StringIO())
        output.__enter__()
        self.addCleanup(output.__exit__, None, None, None)
        self.remote = self.root / "remote"
        self.remote.mkdir()
        self.draft = None
        self.hidden_draft_reads = 0
        self.ref = None
        self.commands = []
        self.latest_base = self.info["version_code_base"] - 1
        patch = mock.patch.object(release, "gh", side_effect=self.gh)
        patch.start()
        self.addCleanup(patch.stop)

    def gh(self, *args):
        self.commands.append(args)
        if args[0] == "api":
            if "--method" in args:
                self.ref = {"ref": "refs/tags/" + release.public_tag(self.info), "object": {"sha": "a" * 40}}
                return "{}"
            path = args[1]
            if "releases?" in path:
                if self.draft is not None:
                    self.draft["assets"] = [{"name": p.name} for p in self.remote.iterdir()]
                visible = self.draft
                if visible is not None and self.hidden_draft_reads:
                    self.hidden_draft_reads -= 1
                    visible = None
                return json.dumps([[{"tag_name": "previous", "draft": False,
                                     "prerelease": False, "published_at": "2026-11-07T00:00:00Z"}]
                                   + ([visible] if visible else [])])
            if path.endswith("releases/latest"):
                return json.dumps({"tag_name": "previous"})
            if "git/matching-refs" in path:
                return json.dumps([self.ref] if self.ref else [])
            if "compare/" in path:
                return json.dumps({"merge_base_commit": {"sha": "a" * 40}})
        if args[:2] == ("release", "create"):
            self.draft = {"tag_name": args[2], "draft": True, "prerelease": False, "assets": [], "html_url": "https://example.test/release"}
            return ""
        if args[:2] == ("release", "download"):
            target = Path(args[args.index("--dir") + 1])
            if args[2] == "previous":
                (target / "build-info.json").write_text(json.dumps({"version_code_base": self.latest_base}))
            else:
                for p in self.remote.iterdir():
                    shutil.copyfile(p, target / p.name)
            return ""
        if args[:2] == ("release", "upload"):
            for p in args[3:args.index("--repo")]:
                shutil.copyfile(p, self.remote / Path(p).name)
            return ""
        if args[:2] == ("release", "edit"):
            self.draft["draft"] = False
            return ""
        if args[:2] == ("workflow", "run"):
            return ""
        raise AssertionError(f"Unexpected fake GitHub command: {args}")

    def finish(self):
        env = {"GITHUB_ACTIONS": "true", "GITHUB_WORKFLOW": "Silora Publish Local Build",
               "GITHUB_REPOSITORY": release.REPO, "GITHUB_REF": "refs/heads/main"}
        with mock.patch.dict(os.environ, env):
            release.finalize(release.public_tag(self.info), self.info["source_sha"], release.digest(self.remote / "SHA256SUMS"))

    def test_local_upload_then_locked_publication_keeps_exact_apks(self):
        release.upload_and_dispatch(self.archive, self.info)
        self.assertTrue(self.draft["draft"])
        self.assertEqual({p.name for p in self.remote.iterdir()}, release.FILES)
        self.finish()
        self.assertFalse(self.draft["draft"])
        self.assertEqual((self.remote / "silora-tv.apk").read_bytes(), (self.archive / "silora-tv.apk").read_bytes())
        self.assertFalse(any("gradle" in str(c) for c in self.commands))
        self.assertFalse(any("--clobber" in c for c in self.commands))

    def test_retry_reuses_partial_draft_without_duplicate_release_or_overwrite(self):
        release.upload_and_dispatch(self.archive, self.info)
        (self.remote / "silora-phone.apk").unlink()
        release.upload_and_dispatch(self.archive, self.info)
        self.assertEqual(sum(c[:2] == ("release", "create") for c in self.commands), 1)
        self.assertEqual({p.name for p in self.remote.iterdir()}, release.FILES)

    def test_delayed_new_draft_visibility_uploads_without_repeating_creation(self):
        self.hidden_draft_reads = 2
        with mock.patch.object(release.time, "sleep") as sleep:
            release.upload_and_dispatch(self.archive, self.info)
        self.assertEqual(sleep.call_args_list, [mock.call(2), mock.call(2)])
        self.assertEqual(sum(c[:2] == ("release", "create") for c in self.commands), 1)
        self.assertEqual({p.name for p in self.remote.iterdir()}, release.FILES)
        self.assertEqual(sum(c[:2] == ("workflow", "run") for c in self.commands), 1)

    def test_invisible_new_draft_stops_after_bounded_wait_without_uploading(self):
        self.hidden_draft_reads = 100
        with mock.patch.object(release.time, "sleep") as sleep:
            with self.assertRaisesRegex(ValueError, "Could not verify the new draft"):
                release.upload_and_dispatch(self.archive, self.info)
        self.assertEqual(sleep.call_args_list, [mock.call(2)] * 5)
        self.assertEqual(self.hidden_draft_reads, 94)
        self.assertEqual(sum(c[:2] == ("release", "create") for c in self.commands), 1)
        self.assertFalse(any(c[:2] in (("release", "upload"), ("workflow", "run")) for c in self.commands))
        self.assertEqual(list(self.remote.iterdir()), [])

    def test_new_draft_read_error_stops_without_retrying_or_uploading(self):
        with mock.patch.object(release, "release_for", side_effect=[None, ValueError("API unavailable")]) as lookup, \
             mock.patch.object(release.time, "sleep") as sleep:
            with self.assertRaisesRegex(ValueError, "API unavailable"):
                release.upload_and_dispatch(self.archive, self.info)
        self.assertEqual(lookup.call_count, 2)
        sleep.assert_not_called()
        self.assertFalse(any(c[:2] in (("release", "upload"), ("workflow", "run")) for c in self.commands))

    def test_new_release_that_is_already_public_stops_without_uploading(self):
        with mock.patch.object(release, "release_for", side_effect=[None, {"draft": False}]), \
             mock.patch.object(release.time, "sleep") as sleep:
            with self.assertRaisesRegex(ValueError, "Could not verify the new draft"):
                release.upload_and_dispatch(self.archive, self.info)
        sleep.assert_not_called()
        self.assertFalse(any(c[:2] in (("release", "upload"), ("workflow", "run")) for c in self.commands))

    def test_changed_draft_is_preserved_and_not_published(self):
        release.upload_and_dispatch(self.archive, self.info)
        (self.remote / "silora-tv.apk").write_bytes(b"different build")
        with self.assertRaisesRegex(ValueError, "no assets overwritten"):
            release.upload_and_dispatch(self.archive, self.info)
        self.assertTrue(self.draft["draft"])

    def test_newer_github_build_finishing_during_upload_blocks_local_downgrade(self):
        release.upload_and_dispatch(self.archive, self.info)
        self.latest_base = self.info["version_code_base"] + 10
        with self.assertRaisesRegex(ValueError, "newer than or equal"):
            self.finish()
        self.assertTrue(self.draft["draft"])

    def test_retry_after_publication_does_not_upload_or_promote_again(self):
        release.upload_and_dispatch(self.archive, self.info)
        self.finish()
        self.commands.clear()
        release.upload_and_dispatch(self.archive, self.info)
        self.finish()
        self.assertFalse(any(c[:2] in (("release", "edit"), ("release", "upload"), ("workflow", "run")) for c in self.commands))

    def test_api_failure_stops_before_release_mutation(self):
        with mock.patch.object(release, "gh", side_effect=ValueError("network unavailable")):
            with self.assertRaisesRegex(ValueError, "network unavailable"):
                release.upload_and_dispatch(self.archive, self.info)
        self.assertIsNone(self.draft)


if __name__ == "__main__":
    unittest.main()
