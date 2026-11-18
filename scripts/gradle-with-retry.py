#!/usr/bin/env python3
"""Retry Gradle dependency rate limits without retrying other build failures."""
import re
import subprocess
import sys
import time


MAX_ATTEMPTS = 3
RETRY_DELAY_SECONDS = 120


def rate_limited_dependencies(log):
    sections = re.findall(
        r"^\* What went wrong:\n(.*?)(?=^\* (?:Try|Exception is):|\Z)",
        log, re.MULTILINE | re.DOTALL,
    )
    if not sections:
        return False
    for section in sections:
        if not re.search(r"> Could not resolve all (?:files|dependencies|artifacts) for configuration ", section):
            return False
        # Gradle indents nested causes. Every leaf must be an HTTP 429;
        # a mixed failure (such as a 403 or a compilation error) must stop.
        causes = [match for line in section.splitlines()
                  if (match := re.fullmatch(r"(\s*)> (.+)", line))]
        leaves = [cause[2] for index, cause in enumerate(causes)
                  if index + 1 == len(causes) or len(causes[index + 1][1]) <= len(cause[1])]
        if not leaves or not all(re.fullmatch(
                r"Could not (?:GET|HEAD) 'https?://[^']+'\. Received status code 429 from server: .*",
                leaf) for leaf in leaves):
            return False
    return True


def run_once(command):
    lines = []
    with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          text=True, errors="replace", bufsize=1) as process:
        for line in process.stdout:
            print(line, end="", flush=True)
            lines.append(line)
        return process.wait(), "".join(lines)


def run_gradle(arguments):
    command = ["./gradlew", *arguments]
    for attempt in range(1, MAX_ATTEMPTS + 1):
        code, log = run_once(command)
        if code <= 0 or code in (130, 137, 143):
            return code if code >= 0 else 128 - code
        if not rate_limited_dependencies(log):
            return code
        if attempt == MAX_ATTEMPTS:
            print(f"::error::Dependency downloads remain rate-limited after {attempt} attempts.", flush=True)
            return code
        print(f"::warning::Dependency server returned HTTP 429. Waiting {RETRY_DELAY_SECONDS} seconds "
              f"before Gradle attempt {attempt + 1}/{MAX_ATTEMPTS}.", flush=True)
        time.sleep(RETRY_DELAY_SECONDS)


if __name__ == "__main__":
    try:
        sys.exit(run_gradle(sys.argv[1:]))
    except KeyboardInterrupt:
        sys.exit(130)
