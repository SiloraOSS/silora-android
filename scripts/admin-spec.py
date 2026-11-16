#!/usr/bin/env python3
"""Fork: extract the admin API from silo-server's OpenAPI contract.

The admin console (shared/.../admin, androidApp and androidTvApp admin screens)
builds its generic screens from this file at runtime, so new server admin
operations appear without app changes.

Usage:
    python3 -I scripts/admin-spec.py <silo-server>/contracts/api/v2/openapi.json [--source LABEL]

The source label (the silo-server commit and date the contract came from) is
read from git when the file is in a silo-server checkout; --source sets it
otherwise. The console shows it, so a mismatch with the connected server can
be diagnosed.

Writes android-shared/src/androidMain/assets/admin/openapi-admin.json with
every admin operation (/api/v2/admin paths and other operations marked
x-silo-class acting_admin) and the schemas they reference, minus examples, the
profile headers the client manages, and other fields the console ignores.
"""
import json
import pathlib
import subprocess
import sys

OUT = pathlib.Path(__file__).resolve().parent.parent / \
    "android-shared/src/androidMain/assets/admin/openapi-admin.json"
METHODS = ("get", "post", "put", "patch", "delete")
DROP_HEADERS = {"X-Profile-Id", "X-Profile-Token", "Origin", "User-Agent", "Sec-WebSocket-Protocol"}
SCHEMA_KEYS = {
    "type", "properties", "required", "items", "additionalProperties", "enum", "format",
    "description", "readOnly", "writeOnly", "minimum", "maximum", "minLength", "maxLength",
    "default", "title", "$ref", "minItems", "maxItems", "uniqueItems",
}
MAX_DESCRIPTION = 280
# Operation extensions the console reads: If-Match guards, raw (non-JSON)
# protocols it can't speak, and how a mutation tolerates a retry.
KEEP_EXTENSIONS = ("x-silo-guarded", "x-silo-raw-protocol", "x-silo-retry-safety")


def trim_text(text):
    text = " ".join(str(text).split())
    return text if len(text) <= MAX_DESCRIPTION else text[: MAX_DESCRIPTION - 1] + "…"


def trim_schema(schema):
    if not isinstance(schema, dict):
        return schema
    out = {}
    for key, value in schema.items():
        if key not in SCHEMA_KEYS:
            continue
        if key == "properties":
            out[key] = {name: trim_schema(prop) for name, prop in value.items()}
        elif key in ("items",):
            out[key] = trim_schema(value)
        elif key == "additionalProperties":
            out[key] = trim_schema(value) if isinstance(value, dict) else value
        elif key == "description":
            out[key] = trim_text(value)
        else:
            out[key] = value
    return out


def collect_refs(node, schemas, needed):
    if isinstance(node, dict):
        ref = node.get("$ref")
        if isinstance(ref, str) and ref.startswith("#/components/schemas/"):
            name = ref.rsplit("/", 1)[1]
            if name not in needed and name in schemas:
                needed.add(name)
                collect_refs(schemas[name], schemas, needed)
        for value in node.values():
            collect_refs(value, schemas, needed)
    elif isinstance(node, list):
        for value in node:
            collect_refs(value, schemas, needed)


def trim_operation(op):
    out = {"operationId": op.get("operationId", "")}
    for key in ("summary", "description"):
        if op.get(key):
            out[key] = trim_text(op[key])
    if op.get("tags"):
        out["tags"] = op["tags"]
    for ext in KEEP_EXTENSIONS:
        if op.get(ext) not in (None, False, ""):
            out[ext] = op[ext]
    params = []
    for param in op.get("parameters", []):
        if param.get("in") == "header" and param.get("name") in DROP_HEADERS:
            continue
        kept = {k: param[k] for k in ("name", "in", "required") if k in param}
        if param.get("description"):
            kept["description"] = trim_text(param["description"])
        kept["schema"] = trim_schema(param.get("schema", {}))
        params.append(kept)
    if params:
        out["parameters"] = params
    body = op.get("requestBody")
    if body:
        content = body.get("content", {})
        media = "application/json" if "application/json" in content else next(iter(content), None)
        if media:
            out["requestBody"] = {
                "required": body.get("required", False),
                "mediaType": media,
                "schema": trim_schema(content[media].get("schema", {})),
            }
    responses = {}
    for code, response in op.get("responses", {}).items():
        if not code.startswith("2"):
            continue
        content = response.get("content", {})
        schema = content.get("application/json", {}).get("schema")
        entry = {"schema": trim_schema(schema)} if schema else {}
        if content and "application/json" not in content:
            # A download (gzip, octet-stream, subtitles): the console can't show it.
            entry["mediaType"] = next(iter(content))
        responses[code] = entry
    out["responses"] = responses
    return out


def git_source(path):
    """`<short commit> <date>` of the last commit that changed [path], or None."""
    try:
        out = subprocess.run(
            ["git", "-C", str(path.parent), "log", "-1", "--format=%h %cs", "--", path.name],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
        dirty = subprocess.run(
            ["git", "-C", str(path.parent), "status", "--porcelain", "--", path.name],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None
    return (out + (" (modified)" if dirty else "")) or None


def main(argv):
    args = argv[1:]
    source = None
    if "--source" in args:
        i = args.index("--source")
        if i + 1 >= len(args):
            print(__doc__, file=sys.stderr)
            return 2
        source = args[i + 1]
        del args[i:i + 2]
    if len(args) != 1:
        print(__doc__, file=sys.stderr)
        return 2
    path = pathlib.Path(args[0]).resolve()
    spec = json.loads(path.read_text())
    source = source or git_source(path)
    schemas = spec.get("components", {}).get("schemas", {})
    paths = {}
    for path, item in spec.get("paths", {}).items():
        # Every admin-only operation, wherever it lives: /api/v2/admin/* plus
        # admin-class operations elsewhere (library management, scans, ...).
        ops = {
            m: trim_operation(op) for m, op in item.items()
            if m in METHODS and (path.startswith("/api/v2/admin") or op.get("x-silo-class") == "acting_admin")
        }
        if ops:
            paths[path] = ops
    needed = set()
    collect_refs(paths, schemas, needed)
    out = {
        "info": {"version": spec.get("info", {}).get("version", ""), **({"source": source} if source else {})},
        "paths": paths,
        "schemas": {name: trim_schema(schemas[name]) for name in sorted(needed)},
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, separators=(",", ":"), sort_keys=True))
    ops = sum(len(v) for v in paths.values())
    print(f"wrote {OUT} ({len(paths)} paths, {ops} operations, {len(needed)} schemas, {OUT.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
