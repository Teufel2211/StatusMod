#!/usr/bin/env python3
"""
Fallback uploader for Modrinth and CurseForge.

Intended for CI fallback only when the main mc-publish step fails.
"""

from __future__ import annotations

import glob
import json
import os
import sys
import time
from pathlib import Path

import requests


def parse_game_versions() -> list[str]:
    raw = os.environ.get("GAME_VERSIONS", "").strip()
    if not raw:
        return ["1.20.1"]
    parts = [p.strip() for p in raw.replace("\n", ",").split(",")]
    return [p for p in parts if p]


def parse_release_type() -> str:
    value = os.environ.get("RELEASE_TYPE", "release").strip().lower()
    if value not in {"release", "beta", "alpha"}:
        return "release"
    return value


def read_changelog() -> str:
    env_text = os.environ.get("RELEASE_CHANGELOG", "").strip()
    if env_text:
        return env_text

    changelog_path = Path("CHANGELOG.md")
    if changelog_path.exists():
        try:
            text = changelog_path.read_text(encoding="utf-8").strip()
            if text:
                return text[:4000]
        except Exception:
            pass
    return "Automated fallback upload from GitHub Actions."


def infer_version(explicit: str, jar_path: Path) -> str:
    if explicit:
        return explicit.lstrip("v")
    name = jar_path.stem
    if "-" in name:
        return name.rsplit("-", 1)[-1]
    return "unspecified"


def post_with_retry(url: str, headers: dict, *, files=None, data=None, json_body=None, retries: int = 3) -> requests.Response:
    last_exc = None
    for attempt in range(1, retries + 1):
        try:
            response = requests.post(url, headers=headers, files=files, data=data, json=json_body, timeout=120)
            if response.status_code < 500:
                return response
        except requests.RequestException as exc:
            last_exc = exc
        time.sleep(1.5 * attempt)
    if last_exc:
        raise last_exc
    raise RuntimeError("Upload failed after retries")


def upload_modrinth(
    jar_path: Path,
    project_id: str,
    token: str,
    version: str,
    game_versions: list[str],
    changelog: str,
    release_type: str,
) -> bool:
    if not project_id or not token:
        print("Skipping Modrinth upload: missing MODRINTH_PROJECT_ID or MODRINTH_TOKEN")
        return False

    url = "https://api.modrinth.com/v2/version"
    payload = {
        "project_id": project_id,
        "version_number": version,
        "version_type": release_type,
        "name": f"StatusMod {version}",
        "loaders": ["fabric"],
        "game_versions": game_versions,
        "changelog": changelog,
        "featured": False,
    }

    with jar_path.open("rb") as jar_file:
        files = {
            "data": (None, json.dumps(payload), "application/json"),
            "file": (jar_path.name, jar_file, "application/java-archive"),
        }
        headers = {"Authorization": token}
        response = post_with_retry(url, headers, files=files)

    print(f"Modrinth response: {response.status_code}")
    if response.status_code >= 400:
        print(response.text)
        return False
    return True


def upload_curseforge(
    jar_path: Path,
    project_id: str,
    api_key: str,
    version: str,
    game_versions: list[str],
    changelog: str,
    release_type: str,
) -> bool:
    if not project_id or not api_key:
        print("Skipping CurseForge upload: missing CURSEFORGE_PROJECT_ID or CURSEFORGE_API_KEY")
        return False

    url = f"https://api.curseforge.com/v1/mods/{project_id}/files"
    headers = {"x-api-key": api_key}
    metadata = {
        "displayName": f"StatusMod {version}",
        "changelog": changelog,
        "releaseType": release_type,
        "gameVersions": game_versions,
    }
    java_version = os.environ.get("CURSEFORGE_JAVA_VERSION", "").strip()
    if java_version:
        metadata["javaVersion"] = java_version

    with jar_path.open("rb") as jar_file:
        files = {
            "file": (jar_path.name, jar_file, "application/java-archive"),
            "metadata": (None, json.dumps(metadata), "application/json"),
        }
        response = post_with_retry(url, headers, files=files)

    print(f"CurseForge response: {response.status_code}")
    if response.status_code >= 400:
        print(response.text)
        return False
    return True


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: upload_release.py path/to/mod.jar")
        return 2

    matches = glob.glob(sys.argv[1])
    if not matches:
        print(f"No file matched: {sys.argv[1]}")
        return 1

    jar_path = Path(matches[0])
    if not jar_path.exists():
        print(f"JAR not found: {jar_path}")
        return 1

    version = infer_version(os.environ.get("VERSION", "").strip(), jar_path)
    game_versions = parse_game_versions()
    release_type = parse_release_type()
    changelog = read_changelog()

    modrinth_ok = upload_modrinth(
        jar_path,
        os.environ.get("MODRINTH_PROJECT_ID", "").strip(),
        os.environ.get("MODRINTH_TOKEN", "").strip(),
        version,
        game_versions,
        changelog,
        release_type,
    )
    curseforge_ok = upload_curseforge(
        jar_path,
        os.environ.get("CURSEFORGE_PROJECT_ID", "").strip(),
        os.environ.get("CURSEFORGE_API_KEY", "").strip(),
        version,
        game_versions,
        changelog,
        release_type,
    )

    if not (modrinth_ok and curseforge_ok):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
