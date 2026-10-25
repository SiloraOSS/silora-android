#!/usr/bin/env python3
"""Guard local release signing against fallback keys and incompatible APKs."""
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


if __name__ == "__main__":
    unittest.main()
