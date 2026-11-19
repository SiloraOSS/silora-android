#!/usr/bin/env python3
"""Retry transient Gradle dependency resolution failures."""
from functools import cache
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


MAX_ATTEMPTS = 3
RETRY_DELAY_SECONDS = 120
MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2"
VERIFICATION_METADATA = Path(__file__).resolve().parents[1] / "gradle" / "verification-metadata.xml"


@cache
def verified_components():
    try:
        metadata = ET.parse(VERIFICATION_METADATA)
    except (OSError, ET.ParseError):
        return set()
    return {
        (component.get("group"), component.get("name"), component.get("version"))
        for component in metadata.findall("{*}components/{*}component")
        if component.find("{*}artifact/{*}sha256") is not None
    }


def dependency_retry_reason(log):
    sections = re.findall(
        r"^\* What went wrong:\n(.*?)(?=^\* (?:Try|Exception is):|\Z)",
        log, re.MULTILINE | re.DOTALL,
    )
    if not sections:
        return None
    missing_verified_dependency = False
    for section in sections:
        if not re.search(r"^> Could not resolve all (?:files|dependencies|artifacts) for configuration ",
                         section, re.MULTILINE):
            return None
        # Gradle indents nested causes. A mixed failure must stop unless every
        # leaf is a retryable dependency resolution failure.
        causes = list(re.finditer(r"^([ \t]*)> (.+)$", section, re.MULTILINE))
        if not causes:
            return None
        for index, cause in enumerate(causes):
            next_cause = causes[index + 1] if index + 1 < len(causes) else None
            if next_cause is not None and len(next_cause[1]) > len(cause[1]):
                continue
            if re.fullmatch(
                r"Could not (?:GET|HEAD) 'https?://[^']+'\. "
                r"Received status code (?:408|429|500|502|503|504) from server: .*",
                cause[2],
            ):
                continue
            missing = re.fullmatch(r"Could not find ([\w.-]+):([\w.-]+):([\w.+-]+)\.", cause[2])
            if missing is None or missing.groups() not in verified_components():
                return None
            group, name, version = missing.groups()
            pom = f"{MAVEN_CENTRAL}/{group.replace('.', '/')}/{name}/{version}/{name}-{version}.pom"
            end = next_cause.start() if next_cause is not None else len(section)
            if f"- {pom}" not in (line.strip() for line in section[cause.end():end].splitlines()):
                return None
            missing_verified_dependency = True
    return "missing" if missing_verified_dependency else "http"


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
        reason = dependency_retry_reason(log)
        if reason is None:
            return code
        if attempt == MAX_ATTEMPTS:
            print(f"::error::Gradle dependency resolution still failing after {attempt} attempts.", flush=True)
            return code
        if reason == "missing":
            failure = "Gradle could not find a verified Maven Central dependency. Refreshing dependency metadata on retry."
            if "--refresh-dependencies" not in command:
                command = [*command, "--refresh-dependencies"]
        else:
            failure = "Dependency server returned a transient HTTP error."
        print(f"::warning::{failure} Waiting {RETRY_DELAY_SECONDS} seconds "
              f"before Gradle attempt {attempt + 1}/{MAX_ATTEMPTS}.", flush=True)
        time.sleep(RETRY_DELAY_SECONDS)


if __name__ == "__main__":
    try:
        sys.exit(run_gradle(sys.argv[1:]))
    except KeyboardInterrupt:
        sys.exit(130)
