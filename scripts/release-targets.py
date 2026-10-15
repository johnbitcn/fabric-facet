#!/usr/bin/env python3
"""Validate the explicit stable targets selected for one release."""

import argparse
from pathlib import Path
import re
import sys


def load_targets(manifest: Path, settings: Path) -> list[str]:
    available = set(re.findall(r"^\s*include 'versions:([^']+)'\s*$",
                               settings.read_text(), re.MULTILINE))
    targets = []
    for line in manifest.read_text().splitlines():
        target = line.split("#", 1)[0].strip()
        if not target:
            continue
        if target not in available:
            raise ValueError(f"Unknown release target: {target}")
        if "-snapshot-" in target:
            raise ValueError(f"Use the independent preview workflow for {target}")
        if target in targets:
            raise ValueError(f"Duplicate release target: {target}")
        targets.append(target)
    if not targets:
        raise ValueError(f"No release targets selected in {manifest}")
    return targets


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="release-notes/<tag>.targets")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        targets = load_targets(args.manifest, root / "settings.gradle")
    except (OSError, ValueError) as error:
        print(f"Release target selection failed: {error}", file=sys.stderr)
        return 1
    print("\n".join(targets))
    return 0


if __name__ == "__main__":
    sys.exit(main())
