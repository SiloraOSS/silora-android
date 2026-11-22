#!/usr/bin/env python3
"""Exercise release coordination with independent processes and temporary state."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest


SCRIPT = Path(__file__).with_name("silora-release-lock.py").resolve()


class ReleaseLockTest(unittest.TestCase):
    def setUp(self):
        scratch = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        self.root = Path(scratch.name)
        self.state = self.root / "coordinator"
        self.owner_file = self.state / "owner.json"
        self.command = [sys.executable, str(SCRIPT), "--state-dir", str(self.state)]

    def call(self, *args):
        return subprocess.run([*self.command, *args], cwd=self.root, capture_output=True, text=True, timeout=5)

    def claim(self, execution="manual"):
        result = self.call("claim", "--execution-id", execution)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["execution_id"], execution)
        return json.loads(result.stdout)["token"]

    def expire_owner(self):
        owner = json.loads(self.owner_file.read_text())
        owner["idle_until"] = 0
        self.owner_file.write_text(json.dumps(owner))

    def wait_for(self, path):
        deadline = time.monotonic() + 5
        while not path.exists() and time.monotonic() < deadline:
            time.sleep(0.02)
        self.assertTrue(path.exists(), path)

    def hold_command(self, token):
        started = self.root / "started"
        stop = self.root / "stop"
        command = (
            "import time\n"
            "from pathlib import Path\n"
            "Path('started').touch()\n"
            "deadline = time.monotonic() + 10\n"
            "while not Path('stop').exists() and time.monotonic() < deadline:\n"
            "    time.sleep(0.02)\n"
        )
        worker = subprocess.Popen(
            [*self.command, "run", "--token", token, "--", sys.executable, "-c", command], cwd=self.root,
        )
        self.addCleanup(worker.wait, 12)
        self.addCleanup(stop.touch)
        self.wait_for(started)
        return worker, stop

    def test_manual_claim_blocks_scheduled_claim_until_released(self):
        token = self.claim()
        result = self.call("claim", "--execution-id", "scheduled")
        self.assertEqual(result.returncode, 75)
        self.assertIn("execution manual", result.stderr)
        self.assertEqual(self.owner_file.stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.call("release", "--token", token).returncode, 0)
        self.assertNotEqual(self.claim("scheduled"), token)

    def test_expiry_fences_old_commands_and_releases(self):
        old = self.claim()
        self.expire_owner()
        command = ["run", "--token", old, "--", sys.executable, "-c", "from pathlib import Path; Path('ran').touch()"]
        self.assertIn("expired", self.call(*command).stderr)
        new = self.claim()
        self.assertNotEqual(old, new)
        self.assertNotEqual(self.call(*command).returncode, 0)
        self.assertNotEqual(self.call("release", "--token", old).returncode, 0)
        self.assertFalse((self.root / "ran").exists())
        self.assertEqual(self.call("renew", "--token", new).returncode, 0)

    def test_reservation_survives_expiry_while_a_guarded_command_is_running(self):
        token = self.claim()
        worker, stop = self.hold_command(token)
        self.expire_owner()
        self.assertEqual(self.call("claim", "--execution-id", "scheduled").returncode, 75)
        stop.touch()
        self.assertEqual(worker.wait(5), 0)
        self.assertEqual(self.call("claim", "--execution-id", "scheduled").returncode, 75)

    def test_child_keeps_kernel_lock_when_its_wrapper_is_killed(self):
        worker, stop = self.hold_command(self.claim())
        worker.kill()
        worker.wait(5)
        self.expire_owner()
        self.assertEqual(self.call("claim", "--execution-id", "scheduled").returncode, 75)
        stop.touch()
        deadline = time.monotonic() + 5
        while True:
            result = self.call("claim", "--execution-id", "scheduled")
            if result.returncode != 75 or time.monotonic() >= deadline:
                break
            time.sleep(0.02)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_command_preserves_cwd_and_exit_status(self):
        result = self.call("run", "--token", self.claim(), "--", sys.executable, "-c",
                           "from pathlib import Path; print(Path.cwd()); raise SystemExit(19)")
        self.assertEqual(result.returncode, 19)
        self.assertEqual(result.stdout.strip(), str(self.root))

    def test_reservation_from_previous_boot_can_be_reclaimed(self):
        old = self.claim()
        owner = json.loads(self.owner_file.read_text())
        owner["boot_id"] = "prior-boot"
        self.owner_file.write_text(json.dumps(owner))
        self.assertNotEqual(self.claim("scheduled"), old)


if __name__ == "__main__":
    unittest.main()
