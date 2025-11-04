#!/usr/bin/env python3
"""
Add a default Postman test script to every request in a collection JSON file.

Usage:
    python scripts/add_postman_tests.py path/to/collection.json

By default, inserts a test that asserts the response status code is one of
200, 201, 204, or 409. Existing test events are left untouched.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
from typing import Dict, Iterable, List, MutableMapping, MutableSequence, Tuple


DEFAULT_CODES = (200, 201, 204, 409)


def build_test_exec(allowed_codes: Iterable[int]) -> List[str]:
    codes = ", ".join(str(code) for code in allowed_codes)
    script = f"""pm.test(pm.info.requestName + " - Status OK", function () {{
    pm.expect(pm.response.code).to.be.oneOf([{codes}]);
}});"""
    return [line.rstrip() for line in script.splitlines()]


def ensure_tests(
    items: MutableSequence[MutableMapping[str, object]],
    script_exec: List[str],
) -> Tuple[int, int]:
    updated = 0
    skipped = 0

    for item in items:
        children = item.get("item")
        if isinstance(children, list):
            child_updated, child_skipped = ensure_tests(children, script_exec)
            updated += child_updated
            skipped += child_skipped

        if "request" not in item:
            continue

        events = item.setdefault("event", [])
        if not isinstance(events, list):
            raise TypeError("Expected 'event' to be a list in request item")

        has_test_event = False
        for event in events:
            if isinstance(event, dict) and event.get("listen") == "test":
                has_test_event = True
                break

        if has_test_event:
            skipped += 1
            continue

        events.append(
            {
                "listen": "test",
                "script": {
                    "type": "text/javascript",
                    "exec": script_exec,
                },
            }
        )
        updated += 1

    return updated, skipped


def load_collection(path: pathlib.Path) -> Dict[str, object]:
    with path.open("r", encoding="utf-8") as handle:
        try:
            return json.load(handle)
        except json.JSONDecodeError as error:
            raise SystemExit(f"Failed to parse JSON: {error}") from error


def write_collection(path: pathlib.Path, payload: Dict[str, object]) -> None:
    tmp_path = path.with_suffix(path.suffix + ".tmp")
    with tmp_path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=True)
        handle.write("\n")
    tmp_path.replace(path)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Inject a default Postman test into every request."
    )
    parser.add_argument(
        "collection",
        type=pathlib.Path,
        help="Path to the Postman collection JSON file to update",
    )
    parser.add_argument(
        "--codes",
        metavar="CODES",
        default=",".join(str(code) for code in DEFAULT_CODES),
        help="Comma-separated list of acceptable HTTP status codes "
        f"(default: {','.join(str(code) for code in DEFAULT_CODES)})",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    collection_path: pathlib.Path = args.collection

    if not collection_path.exists():
        raise SystemExit(f"Collection file not found: {collection_path}")

    try:
        allowed_codes = tuple(int(code.strip()) for code in args.codes.split(","))
    except ValueError as error:
        raise SystemExit(f"Invalid status code in --codes: {error}") from error

    data = load_collection(collection_path)

    items = data.get("item")
    if not isinstance(items, list):
        raise SystemExit("Collection JSON does not contain an 'item' array at the root.")

    script_exec = build_test_exec(allowed_codes)
    updated, skipped = ensure_tests(items, script_exec)

    write_collection(collection_path, data)

    print(
        f"Updated {updated} request(s) with new tests; "
        f"skipped {skipped} existing request(s)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
