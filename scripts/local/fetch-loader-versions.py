#!/usr/bin/env python3
"""
Fetch loader versions from official Maven indexes (and GitHub fallback) and generate loader-versions.json.
Sources:
  - Forge:      https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml
  - ForgeGradle:https://maven.minecraftforge.net/net/minecraftforge/gradle/ForgeGradle/maven-metadata.xml
  - NeoForge:   https://maven.neoforged.net/releases/net/neoforged/neoforge/ (HTML index)
  - NeoGradle:  https://maven.neoforged.net/releases/net/neoforged/gradle/NeoGradle/maven-metadata.xml
  - Quilt:      https://maven.quiltmc.org/repository/release/org/quiltmc/...
  - GitHub fallback: https://github.com/QuiltMC/*
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Iterable, Optional
from urllib.parse import urljoin

try:
    import requests  # type: ignore
except Exception:
    requests = None


ROOT = Path(__file__).resolve().parents[2]
MATRIX = ROOT / "scripts" / "local" / "mc-matrix-1.21.json"
OUT = ROOT / "scripts" / "local" / "loader-versions.json"

FORGE_META = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"
FORGEGRADLE_META = "https://maven.minecraftforge.net/net/minecraftforge/gradle/ForgeGradle/maven-metadata.xml"
FORGEGRADLE_META_MIRROR = "https://maven.neoforged.net/releases/net/minecraftforge/gradle/ForgeGradle/maven-metadata.xml"
NEOFORGE_INDEX = "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
NEOFORGE_META = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
NEOGRADLE_META = "https://maven.neoforged.net/releases/net/neoforged/NeoGradle/maven-metadata.xml"
QUILT_LOADER_META = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-loader/maven-metadata.xml"
QUILT_LOOM_META = "https://maven.quiltmc.org/repository/release/org/quiltmc/loom/maven-metadata.xml"
QSL_META = "https://maven.quiltmc.org/repository/release/org/quiltmc/qsl/maven-metadata.xml"
QUILT_MAPPINGS_META = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-mappings/maven-metadata.xml"
GITHUB_API = "https://api.github.com/repos/"
MC_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
QUILT_LOOM_MAX = "1.8.5"


def fetch_text(url: str) -> str:
    if requests is None:
        raise RuntimeError("python package 'requests' is required.")
    resp = requests.get(url, timeout=60)
    resp.raise_for_status()
    return resp.text


def parse_maven_metadata(xml_text: str) -> list[str]:
    # minimal parse without full XML dependency
    return re.findall(r"<version>([^<]+)</version>", xml_text)


def version_key(v: str) -> tuple:
    # Split into numeric and non-numeric chunks for rough ordering
    parts = re.split(r"([0-9]+)", v)
    out = []
    for p in parts:
        if p.isdigit():
            out.append(int(p))
        else:
            out.append(p)
    return tuple(out)


def latest_stable(versions: Iterable[str]) -> Optional[str]:
    stable = [v for v in versions if not re.search(r"(alpha|beta|rc)", v, re.IGNORECASE)]
    pool = stable or list(versions)
    if not pool:
        return None
    return sorted(pool, key=version_key)[-1]


def clamp_max_version(v: Optional[str], max_v: str) -> Optional[str]:
    if not v:
        return v
    if version_key(v) > version_key(max_v):
        return max_v
    return v


def choose_latest_with_suffix(versions: Iterable[str], suffix: str) -> Optional[str]:
    matches = [v for v in versions if v.endswith(suffix)]
    if not matches:
        return None
    return sorted(matches, key=version_key)[-1]


def choose_latest_with_prefix(versions: Iterable[str], prefix: str) -> Optional[str]:
    matches = [v for v in versions if v.startswith(prefix)]
    if not matches:
        return None
    return sorted(matches, key=version_key)[-1]


def github_tags(repo: str) -> list[str]:
    if requests is None:
        raise RuntimeError("python package 'requests' is required.")
    url = urljoin(GITHUB_API, f"{repo}/tags?per_page=100")
    resp = requests.get(url, timeout=60, headers={"Accept": "application/vnd.github+json"})
    resp.raise_for_status()
    data = resp.json()
    out: list[str] = []
    for item in data:
        name = str(item.get("name", "")).strip()
        if not name:
            continue
        if name.startswith("v"):
            name = name[1:]
        out.append(name)
    return out


def parse_forge_versions(versions: Iterable[str]) -> dict[str, str]:
    # Forge versions are like "1.20.1-47.2.0"
    out: dict[str, str] = {}
    for v in versions:
        if "-" not in v:
            continue
        mc, forge = v.split("-", 1)
        if mc not in out or version_key(forge) > version_key(out[mc]):
            out[mc] = forge
    return out


def parse_neoforge_versions(index_html: str) -> dict[str, str]:
    # NeoForge index directories like "20.2.24-beta/" (1.20.2)
    dirs = re.findall(r'href="([^"/]+)/"', index_html)
    out: dict[str, str] = {}
    for d in dirs:
        if not re.match(r"^\d+\.\d+\.", d):
            continue
        # map 20.2.x -> 1.20.2, 21.1.x -> 1.21.1
        parts = d.split(".")
        if len(parts) < 3:
            continue
        mc = f"1.{parts[0]}.{parts[1]}"
        # pick latest per mc
        if mc not in out or version_key(d) > version_key(out[mc]):
            out[mc] = d
    return out


def parse_neoforge_versions_from_meta(versions: Iterable[str]) -> dict[str, str]:
    # NeoForge versions like "21.1.90" (MC 1.21.1)
    out: dict[str, str] = {}
    for v in versions:
        m = re.match(r"^(\d+)\.(\d+)\.(\d+).*$", v)
        if not m:
            continue
        mc = f"1.{m.group(1)}.{m.group(2)}"
        if mc not in out or version_key(v) > version_key(out[mc]):
            out[mc] = v
    return out


def normalize_mc_versions(mc_versions: Iterable[str]) -> list[str]:
    # Use official manifest when available, and normalize 1.x.0 -> 1.x when present.
    official: set[str] = set()
    try:
        manifest = fetch_text(MC_MANIFEST)
        data = json.loads(manifest)
        official = {str(v.get("id")) for v in data.get("versions", []) if v.get("type") == "release"}
    except Exception as exc:
        print(f"[warn] mojang manifest fetch failed: {exc}")

    out: list[str] = []
    for v in mc_versions:
        vv = v
        if vv.endswith(".0"):
            base = vv[:-2]
            if not official or base in official:
                vv = base
        if official:
            if vv in official:
                out.append(vv)
        else:
            out.append(vv)
    # preserve order, remove dups
    seen: set[str] = set()
    deduped: list[str] = []
    for v in out:
        if v in seen:
            continue
        seen.add(v)
        deduped.append(v)
    return deduped


def main() -> int:
    if not MATRIX.exists():
        print(f"Missing matrix: {MATRIX}")
        return 1
    matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
    mc_versions = [e["minecraft_version"] for e in matrix if "minecraft_version" in e]
    mc_versions = normalize_mc_versions(mc_versions)

    forge_versions = []
    try:
        forge_versions = parse_maven_metadata(fetch_text(FORGE_META))
    except Exception as exc:
        print(f"[warn] forge metadata fetch failed: {exc}")

    forgegradle_versions = []
    try:
        forgegradle_versions = parse_maven_metadata(fetch_text(FORGEGRADLE_META))
    except Exception as exc:
        print(f"[warn] forgegradle metadata fetch failed: {exc}")
        try:
            forgegradle_versions = parse_maven_metadata(fetch_text(FORGEGRADLE_META_MIRROR))
        except Exception as exc2:
            print(f"[warn] forgegradle mirror fetch failed: {exc2}")

    neoforge_index = ""
    neoforge_versions = []
    try:
        neoforge_index = fetch_text(NEOFORGE_INDEX)
    except Exception as exc:
        print(f"[warn] neoforge index fetch failed: {exc}")
    try:
        neoforge_versions = parse_maven_metadata(fetch_text(NEOFORGE_META))
    except Exception as exc:
        print(f"[warn] neoforge metadata fetch failed: {exc}")

    neogradle_versions = []
    try:
        neogradle_versions = parse_maven_metadata(fetch_text(NEOGRADLE_META))
    except Exception as exc:
        print(f"[warn] neogradle metadata fetch failed: {exc}")

    quilt_loader_versions = []
    quilt_loom_versions = []
    qsl_versions = []
    quilt_mappings = []
    try:
        quilt_loader_versions = parse_maven_metadata(fetch_text(QUILT_LOADER_META))
    except Exception as exc:
        print(f"[warn] quilt-loader metadata fetch failed: {exc}")
    try:
        quilt_loom_versions = parse_maven_metadata(fetch_text(QUILT_LOOM_META))
    except Exception as exc:
        print(f"[warn] quilt-loom metadata fetch failed: {exc}")
    try:
        qsl_versions = parse_maven_metadata(fetch_text(QSL_META))
    except Exception as exc:
        print(f"[warn] qsl metadata fetch failed: {exc}")
    try:
        quilt_mappings = parse_maven_metadata(fetch_text(QUILT_MAPPINGS_META))
    except Exception as exc:
        print(f"[warn] quilt-mappings metadata fetch failed: {exc}")
        quilt_mappings = []

    if not quilt_loader_versions:
        try:
            quilt_loader_versions = github_tags("QuiltMC/quilt-loader")
        except Exception as exc:
            print(f"[warn] quilt-loader github fetch failed: {exc}")

    if not quilt_loom_versions:
        try:
            quilt_loom_versions = github_tags("QuiltMC/quilt-loom")
        except Exception as exc:
            print(f"[warn] quilt-loom github fetch failed: {exc}")

    if not qsl_versions:
        try:
            qsl_versions = github_tags("QuiltMC/quilt-standard-libraries")
        except Exception as exc:
            print(f"[warn] qsl github fetch failed: {exc}")

    if not quilt_mappings:
        try:
            quilt_mappings = github_tags("QuiltMC/quilt-mappings")
        except Exception as exc:
            print(f"[warn] quilt-mappings github fetch failed: {exc}")

    forge_map = parse_forge_versions(forge_versions)
    neoforge_map = {}
    if neoforge_versions:
        neoforge_map = parse_neoforge_versions_from_meta(neoforge_versions)
    elif neoforge_index:
        neoforge_map = parse_neoforge_versions(neoforge_index)

    forgegradle_latest = latest_stable(forgegradle_versions)
    neogradle_latest = latest_stable(neogradle_versions)
    quilt_loader_latest = latest_stable(quilt_loader_versions)
    quilt_loom_latest = clamp_max_version(latest_stable(quilt_loom_versions), QUILT_LOOM_MAX)

    out = {"forge": {}, "neoforge": {}, "quilt": {}}

    for mc in mc_versions:
        # Forge
        forge_ver = forge_map.get(mc)
        if forge_ver:
            out["forge"][mc] = {
                "forge_version": forge_ver,
                "forge_gradle_version": forgegradle_latest or "",
            }
        # NeoForge
        neoforge_ver = neoforge_map.get(mc)
        if neoforge_ver:
            out["neoforge"][mc] = {
                "neoforge_version": neoforge_ver,
                "neogradle_version": neogradle_latest or "",
            }
        # Quilt
        qsl = choose_latest_with_suffix(qsl_versions, f"+{mc}")
        # Quilt mappings are usually like "1.20.4+build.12" (prefix match).
        qm = choose_latest_with_prefix(quilt_mappings, f"{mc}+") if quilt_mappings else None
        if qsl or qm or quilt_loader_latest or quilt_loom_latest:
            out["quilt"][mc] = {
                "quilt_loader_version": quilt_loader_latest or "",
                "quilt_mappings": qm or "",
                "qsl_version": qsl or "",
                "quilt_loom_version": quilt_loom_latest or "",
            }

    OUT.write_text(json.dumps(out, indent=2), encoding="utf-8")
    print(f"Wrote {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
