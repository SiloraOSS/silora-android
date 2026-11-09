#!/usr/bin/env python3
"""Check the one-command handoff without compiling or publishing real APKs."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class BuildAndPublishTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="silora helper ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "checkout with spaces"
        self.repo.mkdir()
        self.scratch = self.root / "temporary files"
        self.scratch.mkdir()
        shutil.copyfile(Path(__file__).resolve().parents[1] / "helper.sh", self.repo / "helper.sh")
        (self.repo / "helper.sh").chmod(0o755)
        # A different archive with a misleading name/date must never be picked.
        other = self.repo / "dist/local-2099-12-31.235959"
        other.mkdir(parents=True)
        os.utime(other, (4_000_000_000, 4_000_000_000))
        build = self.repo / "build.sh"
        build.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
root = pathlib.Path(__file__).resolve().parent
args = sys.argv[1:]
(root / 'build-call.json').write_text(json.dumps({'args': args, 'cwd': str(pathlib.Path.cwd())}))
receipt = pathlib.Path(args[args.index('--output-file') + 1])
(root / 'receipt-path').write_text(str(receipt))
mode = os.environ.get('TEST_MODE', 'success')
output = root / 'dist/local-2026-11-08.120000'
output.mkdir()
print('Unrelated log path: ' + str(root / 'dist/local-2099-12-31.235959'))
if mode != 'missing-receipt':
    receipt.write_text(str(root if mode == 'invalid-receipt' else output) + '\\n')
if mode == 'failed-build':
    sys.exit(7)
''')
        publisher = self.repo / "release.sh"
        publisher.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
root = pathlib.Path(__file__).resolve().parent
(root / 'release-call.json').write_text(json.dumps({'args': sys.argv[1:], 'cwd': str(pathlib.Path.cwd())}))
sys.exit(9 if os.environ.get('TEST_MODE') == 'failed-publish' else 0)
''')
        build.chmod(0o755)
        publisher.chmod(0o755)

    def run_helper(self, mode="success", args=()):
        result = subprocess.run([str(self.repo / "helper.sh"), *args], cwd=self.root,
                                env=dict(os.environ, TMPDIR=str(self.scratch), TEST_MODE=mode),
                                capture_output=True, text=True)
        self.assertEqual(list(self.scratch.iterdir()), [], "Temporary build receipt must always be removed")
        return result

    def test_publishes_exact_current_archive_after_full_checks_from_another_directory(self):
        result = self.run_helper()
        self.assertEqual(result.returncode, 0, result.stderr)
        built = json.loads((self.repo / "build-call.json").read_text())
        self.assertEqual(built['args'][:3], ['--lint', '--tests', '--output-file'])
        self.assertEqual(built['cwd'], str(self.repo))
        published = json.loads((self.repo / "release-call.json").read_text())
        self.assertEqual(published['args'], ['local', str(self.repo / 'dist/local-2026-11-08.120000'), '--publish'])
        self.assertEqual(published['cwd'], str(self.repo))

    def test_checkout_symlink_still_uses_the_physical_archive_path(self):
        alias = self.root / "checkout alias"
        alias.symlink_to(self.repo, target_is_directory=True)
        result = subprocess.run([str(alias / "helper.sh")], cwd=self.root,
                                env=dict(os.environ, TMPDIR=str(self.scratch), TEST_MODE="success"),
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        published = json.loads((self.repo / "release-call.json").read_text())
        self.assertEqual(published['args'][1], str(self.repo / 'dist/local-2026-11-08.120000'))
        self.assertEqual(list(self.scratch.iterdir()), [])

    def test_failed_build_never_publishes_even_if_it_wrote_a_path(self):
        result = self.run_helper('failed-build')
        self.assertEqual(result.returncode, 7)
        self.assertFalse((self.repo / "release-call.json").exists())

    def test_missing_build_receipt_never_falls_back_to_an_existing_archive(self):
        result = self.run_helper('missing-receipt')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.repo / "release-call.json").exists())

    def test_invalid_build_receipt_never_publishes(self):
        result = self.run_helper('invalid-receipt')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.repo / "release-call.json").exists())

    def test_publish_failure_propagates_and_cleans_the_receipt(self):
        self.assertEqual(self.run_helper('failed-publish').returncode, 9)

    def test_help_and_unknown_arguments_do_not_build_or_publish(self):
        for args, expected in ((('--help',), 0), (('--skip-tests',), 2)):
            with self.subTest(args=args):
                result = self.run_helper(args=args)
                self.assertEqual(result.returncode, expected)
                self.assertFalse((self.repo / "build-call.json").exists())
                self.assertFalse((self.repo / "release-call.json").exists())


if __name__ == "__main__":
    unittest.main()
