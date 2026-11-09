#!/usr/bin/env python3
"""Guard local release signing against fallback keys and incompatible APKs."""
import contextlib
import datetime as dt
import io
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import subprocess
import sys
import unittest
from unittest import mock


spec = importlib.util.spec_from_file_location("build_local_release", Path(__file__).with_name("build-local-release.py"))
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class LocalReleaseTests(unittest.TestCase):
    def test_missing_key_stops_before_running_tools(self):
        with tempfile.TemporaryDirectory() as directory, mock.patch.object(build, "capture") as capture:
            with self.assertRaisesRegex(ValueError, "Missing release keystore"):
                build.signing_environment(Path(directory))
            capture.assert_not_called()

    def test_saved_credentials_override_global_signing_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "release.jks").write_bytes(b"test key")
            (root / "signing.json").write_text(json.dumps({"password": "saved password", "alias": "saved alias"}))
            with mock.patch.dict(os.environ, {
                "SILO_RELEASE_KEYSTORE": "/wrong/global.jks",
                "SILO_RELEASE_KEYSTORE_PASSWORD": "wrong password",
                "SILO_RELEASE_KEY_ALIAS": "wrong alias",
                "SILO_RELEASE_KEY_PASSWORD": "wrong key password",
                "ALLOW_DEBUG_RELEASE_SIGNING": "true",
            }), mock.patch.object(build, "capture", return_value=b"public certificate") as capture:
                env, fingerprint = build.signing_environment(root)
            self.assertEqual(env["SILO_RELEASE_KEYSTORE"], str(root / "release.jks"))
            self.assertEqual(env["SILO_RELEASE_KEYSTORE_PASSWORD"], "saved password")
            self.assertEqual(env["SILO_RELEASE_KEY_PASSWORD"], "saved password")
            self.assertEqual(env["SILO_RELEASE_KEY_ALIAS"], "saved alias")
            self.assertEqual(env["ALLOW_DEBUG_RELEASE_SIGNING"], "false")
            self.assertNotIn("saved password", capture.call_args.args[0])
            self.assertEqual(fingerprint, hashlib.sha256(b"public certificate").hexdigest())

    def test_invalid_configuration_does_not_expose_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "release.jks").write_bytes(b"test key")
            (root / "signing.json").write_text('{"password": "private text", broken}')
            with self.assertRaises(ValueError) as error:
                build.signing_environment(root)
            self.assertNotIn("private text", str(error.exception))

    def test_wrong_apk_signing_identity_is_rejected(self):
        with mock.patch.object(build, "capture", return_value="Signer #1 certificate SHA-256 digest: abcdef\n"):
            with self.assertRaisesRegex(ValueError, "signing certificate does not match"):
                build.verify_apks({"ANDROID_HOME": "/sdk"}, {}, "123456")

    def test_archived_apks_are_verified_instead_of_current_build_outputs(self):
        commands = []
        info = {"version_code_base": 400_000_000, "version_name": "fixture"}
        def capture(command, **kwargs):
            commands.append(command)
            if command[1] == "verify":
                return "Signer #1 certificate SHA-256 digest: abcdef\n"
            field = command[2]
            return {"application-id": "org.silora.app", "debuggable": "false",
                    "version-code": "800000001", "version-name": "fixture"}[field]
        with mock.patch.object(build, "capture", side_effect=capture):
            build.verify_apks({"ANDROID_HOME": "/sdk"}, info, "abcdef", ("tv",), apk_directory=Path("/archive"))
        self.assertTrue(all(command[-1] == "/archive/silora-tv.apk" for command in commands))

    def test_source_status_separates_untracked_notes_from_compilation_inputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.run(["git", *args], cwd=root, check=True, capture_output=True)
            git("init", "--quiet")
            (root / "build.gradle.kts").write_text("// tracked build input")
            git("add", "build.gradle.kts")
            git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "--quiet", "-m", "fixture")
            (root / "notes.txt").write_text("unrelated local notes")
            with mock.patch.object(build, "ROOT", root):
                self.assertEqual(build.source_status(), {"tracked_tree_dirty": False, "untracked_build_inputs": False})
                (root / "shared").mkdir()
                source = root / "shared/Extra.kt"
                source.write_text("// affects compilation")
                self.assertTrue(build.source_status()["untracked_build_inputs"])
                source.unlink()
                (root / "build.gradle.kts").write_text("// uncommitted change")
                self.assertTrue(build.source_status()["tracked_tree_dirty"])

    def test_wrong_apk_version_is_rejected(self):
        def capture(command, **kwargs):
            if command[1] == "verify":
                return "Signer #1 certificate SHA-256 digest: abcdef\n"
            return {"application-id": "org.silora.app", "debuggable": "false", "version-code": "2"}[command[2]]

        with mock.patch.object(build, "capture", side_effect=capture):
            with self.assertRaisesRegex(ValueError, "unexpected APK version-code"):
                build.verify_apks({"ANDROID_HOME": "/sdk"}, {"version_code_base": 400_000_000, "version_name": "test"}, "abcdef")


class ReleaseArchiveTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        patch = mock.patch.object(build, "ROOT", self.root)
        patch.start()
        self.addCleanup(patch.stop)
        self.info = build.fork_release.metadata(
            dt.datetime(2026, 10, 30, tzinfo=dt.timezone.utc), 1, "a" * 40, "b" * 40,
            upstream={"version": "1.0.0", "build": 28, "tag": "v1.0.0-beta.28"},
        )
        self.info["tag"] = "local-test"
        for module, _ in build.MODULES.values():
            apk = self.root / module / f"build/outputs/apk/release/{module}-universal-release.apk"
            mapping = self.root / module / "build/outputs/mapping/release/mapping.txt"
            apk.parent.mkdir(parents=True)
            mapping.parent.mkdir(parents=True)
            apk.write_bytes(f"APK for {module}".encode())
            mapping.write_bytes(f"mapping for {module}".encode())

    def test_selected_apks_keep_matching_mappings_and_checksums(self):
        for targets in (("phone",), ("tv",), ("phone", "tv")):
            with self.subTest(targets=targets):
                info = dict(self.info, tag="local-" + "-".join(targets))
                output = build.package_release(info, targets)
                expected = {"build-info.json"}
                for target in targets:
                    module = build.MODULES[target][0]
                    expected.update((f"silora-{target}.apk", f"silora-{target}-mapping.txt"))
                    self.assertEqual((output / f"silora-{target}-mapping.txt").read_bytes(),
                                     f"mapping for {module}".encode())
                    self.assertEqual((output / f"silora-{target}.apk").read_bytes(),
                                     f"APK for {module}".encode())
                self.assertEqual({path.name for path in output.iterdir()},
                                 expected | {"SHA256SUMS", "RELEASE_NOTES.md"})
                entries = dict(line.split("  ")[::-1] for line in (output / "SHA256SUMS").read_text().splitlines())
                self.assertEqual(set(entries), expected)
                for name, digest in entries.items():
                    self.assertEqual(digest, hashlib.sha256((output / name).read_bytes()).hexdigest())

    def test_missing_or_empty_mapping_stops_before_packaging(self):
        mapping = self.root / "androidTvApp/build/outputs/mapping/release/mapping.txt"
        for missing in (True, False):
            with self.subTest(missing=missing):
                if missing:
                    mapping.unlink()
                else:
                    mapping.write_bytes(b"")
                with self.assertRaisesRegex(ValueError, "Missing or empty R8 mapping"):
                    build.package_release(self.info, ("phone", "tv"))
                self.assertFalse((self.root / "dist").exists())

    def test_later_build_preserves_archived_mapping_and_existing_folder(self):
        output = build.package_release(self.info, ("tv",))
        mapping = self.root / "androidTvApp/build/outputs/mapping/release/mapping.txt"
        mapping.write_bytes(b"mapping from a later build")
        with self.assertRaisesRegex(ValueError, "output already exists"):
            build.package_release(self.info, ("tv",))
        self.assertEqual((output / "silora-tv-mapping.txt").read_bytes(), b"mapping for androidTvApp")


class OutputHandoffTests(unittest.TestCase):
    def run_build(self, receipt, archive, failure=None):
        info = {"version_name": "fixture", "version_code_base": 400_000_000,
                "build_number": 29, "source_sha": "a" * 40,
                "tracked_tree_dirty": False, "untracked_build_inputs": False}
        def package(*args):
            self.assertEqual(receipt.read_text(), "", "Archive path must not be emitted before packaging completes")
            if failure == "package":
                raise ValueError("packaging failed")
            return archive
        with contextlib.ExitStack() as stack:
            stack.enter_context(mock.patch.object(sys, "argv", ["build-local-release.py", "--lint", "--tests", "--output-file", str(receipt)]))
            stack.enter_context(mock.patch.object(build, "signing_environment", return_value=({"SILO_RELEASE_KEYSTORE": "/fixture/release.jks"}, "c" * 64)))
            stack.enter_context(mock.patch.object(build, "build_metadata", return_value=info))
            runner = stack.enter_context(mock.patch.object(build.subprocess, "run"))
            if failure == "build":
                runner.side_effect = subprocess.CalledProcessError(7, ["fixture build"])
            verifier = stack.enter_context(mock.patch.object(build, "verify_apks"))
            if failure == "verification":
                verifier.side_effect = ValueError("verification failed")
            stack.enter_context(mock.patch.object(build, "source_status", return_value={"tracked_tree_dirty": False, "untracked_build_inputs": False}))
            stack.enter_context(mock.patch.object(build, "capture", return_value="a" * 40))
            stack.enter_context(mock.patch.object(build, "package_release", side_effect=package))
            stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
            build.main()

    def test_completed_build_emits_exact_absolute_archive_path(self):
        with tempfile.TemporaryDirectory(prefix="archive handoff ") as directory:
            root = Path(directory)
            receipt = root / "output-path"
            receipt.write_text("")
            archive = root / "archive with spaces"
            archive.mkdir()
            self.run_build(receipt, archive)
            self.assertEqual(receipt.read_text(), str(archive.resolve()) + "\n")

    def test_build_verification_and_packaging_failures_do_not_emit_a_path(self):
        for failure in ("build", "verification", "package"):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                receipt = root / "output-path"
                receipt.write_text("")
                with self.assertRaises((ValueError, subprocess.CalledProcessError)):
                    self.run_build(receipt, root / "archive", failure)
                self.assertEqual(receipt.read_text(), "")

    def test_check_mode_cannot_emit_an_archive_path(self):
        with mock.patch.object(sys, "argv", ["build-local-release.py", "--check", "--output-file", "/unused"]), \
             mock.patch.object(build, "signing_environment") as signing, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as error:
                build.main()
            self.assertEqual(error.exception.code, 2)
            signing.assert_not_called()


if __name__ == "__main__":
    unittest.main()
