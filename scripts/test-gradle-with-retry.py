#!/usr/bin/env python3
"""Exercise rate-limit recovery and fail-closed Gradle failure handling."""
import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import unittest
from unittest import mock


spec = importlib.util.spec_from_file_location("gradle_retry", Path(__file__).with_name("gradle-with-retry.py"))
retry = importlib.util.module_from_spec(spec)
spec.loader.exec_module(retry)

RATE_LIMIT = """FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':android-shared:extractReleaseAnnotations'.
> Could not resolve all files for configuration ':android-shared:detachedConfiguration1'.
   > Could not resolve org.codehaus.groovy:groovy:3.0.22.
     Required by:
         project :android-shared > com.android.tools.lint:lint-gradle:31.10.1
      > Could not resolve org.codehaus.groovy:groovy:3.0.22.
         > Could not get resource 'https://repo.maven.apache.org/maven2/org/codehaus/groovy/groovy/3.0.22/groovy-3.0.22.pom'.
            > Could not GET 'https://repo.maven.apache.org/maven2/org/codehaus/groovy/groovy/3.0.22/groovy-3.0.22.pom'. Received status code 429 from server: Too Many Requests

* Try:
> Run with --stacktrace option to get the stack trace.

BUILD FAILED in 28s
"""
COMPILE_FAILURE = """FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':androidApp:compileReleaseKotlin'.
> Compilation error. See log for more details

* Try:
> Run with --info option to get more log output.
"""


class ClassificationTests(unittest.TestCase):
    def test_lint_dependency_rate_limit_from_failed_release_is_retryable(self):
        self.assertTrue(retry.rate_limited_dependencies(RATE_LIMIT))
        self.assertTrue(retry.rate_limited_dependencies(RATE_LIMIT.replace('Could not GET', 'Could not HEAD')))

    def test_multiple_rate_limited_dependencies_are_retryable(self):
        cause = RATE_LIMIT.split('   > Could not resolve', 1)[1].split('\n* Try:', 1)[0]
        combined = RATE_LIMIT.replace('\n* Try:', '\n   > Could not resolve' + cause + '\n* Try:')
        self.assertTrue(retry.rate_limited_dependencies(combined))

    def test_other_http_failures_and_unavailable_dependencies_stop(self):
        for status in ('401', '403', '404', '500', '503'):
            with self.subTest(status=status):
                self.assertFalse(retry.rate_limited_dependencies(RATE_LIMIT.replace('code 429', 'code ' + status)))
        self.assertFalse(retry.rate_limited_dependencies(RATE_LIMIT.replace(
            'Received status code 429 from server: Too Many Requests', 'Read timed out')))

    def test_mixed_dependency_failure_stops_even_with_a_rate_limit(self):
        mixed = RATE_LIMIT.replace('\n* Try:', "\n   > Could not GET 'https://example.com/other.pom'. "
                                   'Received status code 403 from server: Forbidden\n\n* Try:')
        self.assertFalse(retry.rate_limited_dependencies(mixed))

    def test_unrelated_rate_limit_output_does_not_retry_compilation_tests_or_lint(self):
        for reason in ('Compilation error.', 'There were failing tests.', 'Lint found errors in the project.'):
            with self.subTest(reason=reason):
                log = 'Received status code 429 from server: Too Many Requests\n' + COMPILE_FAILURE
                self.assertFalse(retry.rate_limited_dependencies(log.replace('Compilation error.', reason)))
        self.assertFalse(retry.rate_limited_dependencies(RATE_LIMIT + COMPILE_FAILURE))

    def test_missing_failure_summary_or_unknown_leaf_stops(self):
        self.assertFalse(retry.rate_limited_dependencies('HTTP 429: Too Many Requests'))
        self.assertFalse(retry.rate_limited_dependencies(RATE_LIMIT.replace('> Could not GET', '> Unknown failure GET')))


class RetryTests(unittest.TestCase):
    def run_attempts(self, outcomes):
        with mock.patch.object(retry, 'run_once', side_effect=outcomes) as run, \
                mock.patch.object(retry.time, 'sleep') as sleep, contextlib.redirect_stdout(io.StringIO()) as output:
            code = retry.run_gradle([':androidApp:lintRelease', '--console=plain'])
        self.assertTrue(all(call.args == (['./gradlew', ':androidApp:lintRelease', '--console=plain'],)
                            for call in run.call_args_list))
        return code, run.call_count, sleep.call_args_list, output.getvalue()

    def test_success_returns_immediately_even_if_output_mentions_rate_limits(self):
        code, attempts, waits, _ = self.run_attempts([(0, RATE_LIMIT)])
        self.assertEqual((code, attempts, waits), (0, 1, []))

    def test_rate_limit_waits_two_minutes_then_recovers(self):
        code, attempts, waits, output = self.run_attempts([(1, RATE_LIMIT), (0, 'BUILD SUCCESSFUL')])
        self.assertEqual((code, attempts, waits), (0, 2, [mock.call(120)]))
        self.assertIn('attempt 2/3', output)

    def test_persistent_rate_limit_stops_after_three_attempts(self):
        code, attempts, waits, output = self.run_attempts([(7, RATE_LIMIT)] * 3)
        self.assertEqual((code, attempts, waits), (7, 3, [mock.call(120), mock.call(120)]))
        self.assertIn('::error::', output)

    def test_new_code_failure_after_rate_limit_stops_without_another_wait(self):
        code, attempts, waits, _ = self.run_attempts([(1, RATE_LIMIT), (2, COMPILE_FAILURE)])
        self.assertEqual((code, attempts, waits), (2, 2, [mock.call(120)]))

    def test_ordinary_failure_is_not_retried(self):
        code, attempts, waits, _ = self.run_attempts([(2, COMPILE_FAILURE)])
        self.assertEqual((code, attempts, waits), (2, 1, []))

    def test_cancellation_and_killed_processes_are_not_retried(self):
        for original, expected in ((-15, 143), (-9, 137), (130, 130), (137, 137), (143, 143)):
            with self.subTest(code=original):
                code, attempts, waits, _ = self.run_attempts([(original, RATE_LIMIT)])
                self.assertEqual((code, attempts, waits), (expected, 1, []))

    def test_process_streams_both_output_channels_and_preserves_exit_status(self):
        command = [sys.executable, '-c', "import sys; print('stdout'); print('stderr', file=sys.stderr); sys.exit(7)"]
        with contextlib.redirect_stdout(io.StringIO()) as output:
            code, log = retry.run_once(command)
        self.assertEqual(code, 7)
        self.assertIn('stdout\n', log)
        self.assertIn('stderr\n', log)
        self.assertEqual(log, output.getvalue())


if __name__ == '__main__':
    unittest.main()
