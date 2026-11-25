"""Validate and package the short, shared Silora release summary."""

import base64
import hashlib
import re
from zipfile import BadZipFile, ZipFile


ASSET = "silora-release-highlights.md"
BULLET = re.compile(r"- (.+) \*\((?:Silora|Silo upstream)\)\*")
MAX_BYTES = 2048


def validate(data):
    if len(data) > MAX_BYTES:
        raise ValueError(f"Release highlights exceed {MAX_BYTES} UTF-8 bytes.")
    lines = [line for line in data.decode("utf-8").replace("\r\n", "\n").split("\n") if line]
    if not 1 <= len(lines) <= 4:
        raise ValueError("Write one to four release highlights.")
    for line in lines:
        match = BULLET.fullmatch(line)
        if not match or not 1 <= len(match[1].strip()) <= 120 or match[1] != match[1].strip():
            raise ValueError("Use '- ' and up to 120 characters per highlight, followed by its origin in italics.")
        if not match[1].isprintable():
            raise ValueError("Release highlights must use printable text.")
    return hashlib.sha256(data).hexdigest()


def stage(data, directory):
    digest = validate(data)
    directory.mkdir(parents=True, exist_ok=True)
    (directory / ASSET).write_bytes(data)
    return digest


def decode(encoded, expected):
    if not encoded and not expected:
        return None
    if len(encoded) > 4 * ((MAX_BYTES + 2) // 3):
        raise ValueError("Encoded release highlights exceed the input limit.")
    data = base64.b64decode(encoded, validate=True)
    if validate(data) != expected:
        raise ValueError("Release highlights do not match the supplied SHA-256.")
    return data


def verify(apk, expected):
    try:
        with ZipFile(apk) as archive:
            data = archive.read(f"assets/{ASSET}")
    except (BadZipFile, KeyError) as error:
        raise ValueError(f"{apk.name}: Missing the bundled release highlights.") from error
    if validate(data) != expected:
        raise ValueError(f"{apk.name}: Bundled release highlights differ from the reviewed summary.")
