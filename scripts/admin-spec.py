#!/usr/bin/env python3
"""Fork: extract the admin API from silo-server's OpenAPI contract.

The admin console (shared/.../admin, androidApp and androidTvApp admin screens)
builds its generic screens from this file at runtime, so new server admin
operations appear without app changes.

Usage:
    python3 -I scripts/admin-spec.py <silo-server>/contracts/api/v2/openapi.json

Writes android-shared/src/androidMain/assets/admin/openapi-admin.json with
every admin operation (/api/v2/admin paths and other operations marked
x-silo-class acting_admin) and the schemas they reference, minus examples, the
profile headers the client manages, and other fields the console ignores.
"""
import json
import pathlib
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
        responses[code] = {"schema": trim_schema(schema)} if schema else {}
    out["responses"] = responses
    return out


def main(argv):
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    spec = json.loads(pathlib.Path(argv[1]).read_text())
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
        "info": {"version": spec.get("info", {}).get("version", "")},
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
