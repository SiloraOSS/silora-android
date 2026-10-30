#!/usr/bin/env python3
"""Guard local release signing against fallback keys and incompatible APKs."""
import datetime as dt
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
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


if __name__ == "__main__":
    unittest.main()
