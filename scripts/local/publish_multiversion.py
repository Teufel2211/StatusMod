#!/usr/bin/env python3
"""
Upload all locally built multi-version jars to Modrinth and CurseForge.

Expected filename pattern:
  statusmod-<mod_version>-<loader>-<minecraft_version>.jar
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

try:
    import requests  # type: ignore
except Exception:
    requests = None


NAME_PATTERN = re.compile(
    r"^statusmod-(?P<modver>[0-9A-Za-z.\-+]+)-(?P<loader>fabric|forge|neoforge|quilt)-(?P<mcver>[0-9.]+)\.jar$"
)

CURSEFORGE_LOADER_TAG = {
    "fabric": "Fabric",
    "forge": "Forge",
    "neoforge": "NeoForge",
    "quilt": "Quilt",
}


@dataclass
class Artifact:
    path: Path
    mod_version: str
    loader: str
    mc_version: str


def read_fabric_mod_minecraft(jar: Path) -> str:
    try:
        with zipfile.ZipFile(jar, "r") as zf:
            if "fabric.mod.json" not in zf.namelist():
                return ""
            raw = zf.read("fabric.mod.json")
            data = json.loads(raw.decode("utf-8"))
            depends = data.get("depends", {})
            minecraft = depends.get("minecraft", "")
            return str(minecraft).strip()
    except Exception:
        return ""


def collect_artifacts(root: Path) -> list[Artifact]:
    items: list[Artifact] = []
    for jar in sorted(root.rglob("*.jar")):
        if jar.name.endswith("-sources.jar"):
            continue
        m = NAME_PATTERN.match(jar.name)
        if not m:
            continue
        items.append(
            Artifact(
                path=jar,
                mod_version=m.group("modver"),
                loader=m.group("loader"),
                mc_version=m.group("mcver"),
            )
        )
    return items


def post_with_retry(url: str, headers: dict[str, str], *, files: dict, retries: int = 3) -> requests.Response:
    if requests is None:
        raise RuntimeError("python package 'requests' is required for uploading.")
    last_err: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            resp = requests.post(url, headers=headers, files=files, timeout=120)
            if resp.status_code < 500:
                return resp
        except requests.RequestException as exc:
            last_err = exc
        time.sleep(1.5 * attempt)
    if last_err:
        raise last_err
    raise RuntimeError("request failed")


def upload_modrinth(artifact: Artifact, project_id: str, token: str, release_type: str, changelog: str, dry_run: bool) -> bool:
    payload = {
        "project_id": project_id,
        "version_number": f"{artifact.mod_version}+mc{artifact.mc_version}+{artifact.loader}",
        "version_type": release_type,
        "name": f"StatusMod {artifact.mod_version} ({artifact.loader} {artifact.mc_version})",
        "loaders": [artifact.loader],
        "game_versions": [artifact.mc_version],
        "changelog": changelog,
        "featured": False,
    }
    if dry_run:
        print(f"[dry-run] modrinth: {artifact.path.name} -> loader={artifact.loader} mc={artifact.mc_version}")
        return True

    with artifact.path.open("rb") as fh:
        files = {
            "data": (None, json.dumps(payload), "application/json"),
            "file": (artifact.path.name, fh, "application/java-archive"),
        }
        resp = post_with_retry("https://api.modrinth.com/v2/version", {"Authorization": token}, files=files)
    if resp.status_code >= 400:
        print(f"[modrinth] failed {artifact.path.name}: {resp.status_code} {resp.text}")
        return False
    print(f"[modrinth] ok {artifact.path.name}")
    return True


def upload_curseforge(artifact: Artifact, project_id: str, api_key: str, release_type: str, changelog: str, dry_run: bool) -> bool:
    loader_tag = CURSEFORGE_LOADER_TAG.get(artifact.loader, artifact.loader)
    metadata = {
        "displayName": f"StatusMod {artifact.mod_version} ({artifact.loader} {artifact.mc_version})",
        "changelog": changelog,
        "releaseType": release_type,
        "gameVersions": [artifact.mc_version, loader_tag],
    }
    if dry_run:
        print(f"[dry-run] curseforge: {artifact.path.name} -> loader={loader_tag} mc={artifact.mc_version}")
        return True

    with artifact.path.open("rb") as fh:
        files = {
            "file": (artifact.path.name, fh, "application/java-archive"),
            "metadata": (None, json.dumps(metadata), "application/json"),
        }
        url = f"https://api.curseforge.com/v1/mods/{project_id}/files"
        resp = post_with_retry(url, {"x-api-key": api_key}, files=files)
    if resp.status_code >= 400:
        print(f"[curseforge] failed {artifact.path.name}: {resp.status_code} {resp.text}")
        return False
    print(f"[curseforge] ok {artifact.path.name}")
    return True


def main(argv: Iterable[str]) -> int:
    args = list(argv)
    root = Path(args[1]) if len(args) > 1 else Path("dist/multiversion")
    dry_run = "--dry-run" in args

    artifacts = collect_artifacts(root)
    if not artifacts:
        print(f"No artifacts found in: {root}")
        return 1

    project_modrinth = os.environ.get("MODRINTH_PROJECT_ID", "").strip()
    token_modrinth = os.environ.get("MODRINTH_TOKEN", "").strip()
    project_curseforge = os.environ.get("CURSEFORGE_PROJECT_ID", "").strip()
    token_curseforge = os.environ.get("CURSEFORGE_API_KEY", "").strip() or os.environ.get("CURSEFORGE_TOKEN", "").strip()
    release_type = os.environ.get("RELEASE_TYPE", "release").strip().lower()
    if release_type not in {"release", "beta", "alpha"}:
        release_type = "release"
    changelog = os.environ.get("RELEASE_CHANGELOG", "").strip() or "Automated local multi-version release."

    if not dry_run and (not project_modrinth or not token_modrinth or not project_curseforge or not token_curseforge):
        print("Missing required tokens/ids for upload.")
        return 1
    if not dry_run and requests is None:
        print("python package 'requests' is missing.")
        return 1

    ok = True
    for artifact in artifacts:
        jar_declared_mc = read_fabric_mod_minecraft(artifact.path)
        if jar_declared_mc and jar_declared_mc != artifact.mc_version:
            print(
                f"[warn] {artifact.path.name}: filename mc={artifact.mc_version}, "
                f"fabric.mod.json mc={jar_declared_mc}. Using filename value."
            )

        if project_modrinth and token_modrinth:
            ok = upload_modrinth(artifact, project_modrinth, token_modrinth, release_type, changelog, dry_run) and ok
        if project_curseforge and token_curseforge:
            ok = upload_curseforge(artifact, project_curseforge, token_curseforge, release_type, changelog, dry_run) and ok

    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
