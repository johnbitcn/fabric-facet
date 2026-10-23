#!/usr/bin/env python3
"""Sync the English README to Modrinth, excluding the official project pages line."""

import json
import os
from pathlib import Path
import sys
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import HTTPRedirectHandler, Request, build_opener


PROJECT_PAGES_LINE = "Official project pages: [Modrinth](https://modrinth.com/project/lW1ulmxe) | [CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/facet)"


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the API token to a redirect target.
        return None


def build_description(readme: str) -> str:
    lines = readme.splitlines(keepends=True)
    if sum(line.rstrip("\r\n") == PROJECT_PAGES_LINE for line in lines) != 1:
        raise ValueError("README must contain exactly one official project pages line")
    body = "".join(line for line in lines if line.rstrip("\r\n") != PROJECT_PAGES_LINE)
    if not body.strip():
        raise ValueError("Modrinth description must not be empty")
    return body


def sync_description(project: str, token: str, body: str) -> None:
    if not project or not token:
        raise ValueError("MODRINTH_PROJECT_ID and MODRINTH_TOKEN are required")
    url = "https://api.modrinth.com/v2/project/" + quote(project, safe="")
    headers = {"Authorization": token, "Content-Type": "application/json",
               "User-Agent": "johnbitcn/fabric-facet (Modrinth description sync)"}
    opener = build_opener(NoRedirect())
    request = Request(url, data=json.dumps({"body": body}).encode("utf-8"),
                      headers=headers, method="PATCH")
    with opener.open(request, timeout=30) as response:
        if response.status != 204:
            raise RuntimeError("Modrinth PATCH did not return HTTP 204")
    with opener.open(Request(url, headers=headers), timeout=30) as response:
        if json.load(response)["body"] != body:
            raise RuntimeError("Modrinth description read-back does not match README")


def main() -> int:
    try:
        root = Path(__file__).resolve().parent.parent
        body = build_description((root / "README.md").read_bytes().decode("utf-8"))
        sync_description(os.environ.get("MODRINTH_PROJECT_ID", ""),
                         os.environ.get("MODRINTH_TOKEN", ""), body)
    except HTTPError as error:
        error.close()
        print(f"Modrinth description sync failed: HTTP {error.code}. "
              "Check API availability, PROJECT_WRITE scope and project edit permission.", file=sys.stderr)
        return 1
    except (OSError, ValueError, KeyError, RuntimeError):
        print("Modrinth description sync failed. Check README format, secrets, "
              "API connectivity and description read-back.", file=sys.stderr)
        return 1
    print("Modrinth project description updated and read-back verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
