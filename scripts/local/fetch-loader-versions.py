#!/usr/bin/env python3
"""
Fetch loader versions from official Maven indexes and generate loader-versions.json.
Sources:
  - Forge:      https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml
  - NeoForge:   https://maven.neoforged.net/releases/net/neoforged/neoforge/ (HTML index)
  - NeoGradle:  https://maven.neoforged.net/releases/net/neoforged/gradle/NeoGradle/maven-metadata.xml
  - Quilt:      https://maven.quiltmc.org/repository/release/org/quiltmc/...
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
NEOFORGE_INDEX = "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
NEOGRADLE_META = "https://maven.neoforged.net/releases/net/neoforged/gradle/NeoGradle/maven-metadata.xml"
QUILT_LOADER_META = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-loader/maven-metadata.xml"
QUILT_LOOM_META = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-loom/maven-metadata.xml"
QSL_META = "https://maven.quiltmc.org/repository/release/org/quiltmc/qsl/maven-metadata.xml"
QUILT_MAPPINGS_META = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-mappings/maven-metadata.xml"


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


def choose_latest_with_suffix(versions: Iterable[str], suffix: str) -> Optional[str]:
    matches = [v for v in versions if v.endswith(suffix)]
    if not matches:
        return None
    return sorted(matches, key=version_key)[-1]


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


def main() -> int:
    if not MATRIX.exists():
        print(f"Missing matrix: {MATRIX}")
        return 1
    matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
    mc_versions = [e["minecraft_version"] for e in matrix if "minecraft_version" in e]

    forge_versions = []
    try:
        forge_versions = parse_maven_metadata(fetch_text(FORGE_META))
    except Exception as exc:
        print(f"[warn] forge metadata fetch failed: {exc}")

    neoforge_index = ""
    try:
        neoforge_index = fetch_text(NEOFORGE_INDEX)
    except Exception as exc:
        print(f"[warn] neoforge index fetch failed: {exc}")

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

    forge_map = parse_forge_versions(forge_versions)
    neoforge_map = parse_neoforge_versions(neoforge_index) if neoforge_index else {}

    neogradle_latest = latest_stable(neogradle_versions)
    quilt_loader_latest = latest_stable(quilt_loader_versions)
    quilt_loom_latest = latest_stable(quilt_loom_versions)

    out = {"forge": {}, "neoforge": {}, "quilt": {}}

    for mc in mc_versions:
        # Forge
        forge_ver = forge_map.get(mc)
        if forge_ver:
            out["forge"][mc] = {
                "forge_version": forge_ver,
                "forge_gradle_version": "",
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
        qm = choose_latest_with_suffix(quilt_mappings, f"+build.1") if quilt_mappings else None
        if qsl or quilt_loader_latest or quilt_loom_latest:
            out["quilt"][mc] = {
                "quilt_loader_version": quilt_loader_latest or "",
                "quilt_mappings": f"{mc}+build.1",
                "qsl_version": qsl or "",
                "quilt_loom_version": quilt_loom_latest or "",
            }

    OUT.write_text(json.dumps(out, indent=2), encoding="utf-8")
    print(f"Wrote {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
