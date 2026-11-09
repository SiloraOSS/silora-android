#!/usr/bin/env python3
"""Cross-workflow update ordering, first-release bootstrap and fail-closed reads."""
import datetime as dt
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest import mock


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(path))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


order = load("release_order", "release-order.py")
fork = load("fork_release", "fork-release.py")


class OrderTests(unittest.TestCase):
    def setUp(self):
        self.base = 500_000_000
        self.entries = [{"draft": False, "prerelease": False, "published_at": "2026-11-08"}]
        self.commands = []
        self.latest = {"version_code_base": self.base}

    def command(self, *args):
        self.commands.append(args)
        if args[:1] == ("api",):
            if "releases?" in args[1]:
                return json.dumps([self.entries])
            if args[1].endswith("releases/latest"):
                return json.dumps({"tag_name": "published"})
        if args[:2] == ("release", "download"):
            (Path(args[args.index("--dir") + 1]) / "build-info.json").write_text(json.dumps(self.latest))
            return ""
        self.fail(str(args))

    def test_later_build_can_follow_either_workflow_or_local_publication(self):
        for method in ("manual", "automation", "local"):
            with self.subTest(method=method):
                order.ensure_newer({"version_code_base": self.base + 1, "method": method}, transport=self.command)
        self.assertTrue(all("--repo" not in c or c[c.index("--repo") + 1] == order.REPO for c in self.commands))

    def test_older_and_equal_builds_cannot_move_latest_backwards(self):
        for delta in (-1, 0):
            with self.subTest(delta=delta), self.assertRaisesRegex(ValueError, "newer than or equal"):
                order.ensure_newer({"version_code_base": self.base + delta}, transport=self.command)

    def test_first_release_and_only_unpublished_drafts_can_bootstrap(self):
        for entries in ([], [{"draft": True, "prerelease": False, "published_at": None}],
                        [{"draft": False, "prerelease": True, "published_at": "2026-11-08"}]):
            self.entries = entries
            self.commands.clear()
            order.ensure_newer({"version_code_base": self.base}, transport=self.command)
            self.assertEqual(len(self.commands), 1)

    def test_lookup_failure_never_means_permission_to_publish(self):
        def fail(*args):
            raise subprocess.CalledProcessError(1, ["gh", "api"])
        with self.assertRaises(subprocess.CalledProcessError):
            order.ensure_newer({"version_code_base": self.base}, transport=fail)

    def test_metadata_download_failure_blocks_latest(self):
        def fail(*args):
            if args[:2] == ("release", "download"):
                raise subprocess.CalledProcessError(1, ["gh", "release", "download"])
            return self.command(*args)
        with self.assertRaises(subprocess.CalledProcessError):
            order.ensure_newer({"version_code_base": self.base + 1}, transport=fail)

    def test_bad_candidate_metadata_stops_before_network_reads(self):
        for value in (None, True, "500000000", 0, -1, 1_050_000_000):
            with self.subTest(value=value), mock.patch.object(order, "gh") as gh:
                with self.assertRaisesRegex(ValueError, "invalid Android version"):
                    order.ensure_newer({"version_code_base": value})
                gh.assert_not_called()

    def test_missing_or_invalid_latest_version_blocks_publication(self):
        for metadata in ({}, {"version_code_base": True}, {"version_code_base": "bad"}):
            self.latest = metadata
            with self.subTest(metadata=metadata), self.assertRaisesRegex(ValueError, "invalid Android version"):
                order.ensure_newer({"version_code_base": self.base + 1}, transport=self.command)

    def test_workflow_counters_do_not_control_android_update_order(self):
        upstream = {"version": "1.0.0", "build": 29, "tag": "v1.0.0-beta.29"}
        first = dt.datetime(2026, 11, 8, 22, tzinfo=dt.timezone.utc)
        args = ("a" * 40, "b" * 40)
        manual = fork.metadata(first, 999, *args, upstream=upstream, run_id=500)
        automation = fork.metadata(first + dt.timedelta(seconds=1), 1, *args, upstream=upstream, run_id=501)
        local = fork.metadata(first + dt.timedelta(seconds=2), 1, *args, upstream=upstream)
        self.assertLess(manual["version_code_base"], automation["version_code_base"])
        self.assertLess(automation["version_code_base"], local["version_code_base"])
        # A repeated visible day/build label does not stop an Android update.
        self.assertEqual(manual["version_name"], local["version_name"])


if __name__ == "__main__":
    unittest.main()
