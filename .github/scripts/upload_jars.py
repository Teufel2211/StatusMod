#!/usr/bin/env python3
"""
Upload StatusMod JARs to Modrinth and CurseForge for each Minecraft version.
This script automatically maps JAR versions to the correct mod platform versions.
"""

import os
import json
import subprocess
from pathlib import Path

# Modrinth and CurseForge project IDs
MODRINTH_PROJECT_ID = os.getenv("MODRINTH_PROJECT_ID", "statusmod")
MODRINTH_TOKEN = os.getenv("MODRINTH_TOKEN")
CURSEFORGE_PROJECT_ID = os.getenv("CURSEFORGE_PROJECT_ID")
CURSEFORGE_TOKEN = os.getenv("CURSEFORGE_TOKEN")

# Version mappings for Modrinth/CurseForge
# These should match the versions created on these platforms
VERSION_MAPPING = {
    "1.21.10": {"modrinth": "1.21.10", "curseforge": "1.21.10"},
    "1.21.9": {"modrinth": "1.21.9", "curseforge": "1.21.9"},
    "1.21.8": {"modrinth": "1.21.8", "curseforge": "1.21.8"},
    "1.21.7": {"modrinth": "1.21.7", "curseforge": "1.21.7"},
    "1.21.6": {"modrinth": "1.21.6", "curseforge": "1.21.6"},
    "1.21.5": {"modrinth": "1.21.5", "curseforge": "1.21.5"},
    "1.21.4": {"modrinth": "1.21.4", "curseforge": "1.21.4"},
    "1.21.3": {"modrinth": "1.21.3", "curseforge": "1.21.3"},
    "1.21.2": {"modrinth": "1.21.2", "curseforge": "1.21.2"},
    "1.21.1": {"modrinth": "1.21.1", "curseforge": "1.21.1"},
    "1.21": {"modrinth": "1.21", "curseforge": "1.21"},
    "1.20.6": {"modrinth": "1.20.6", "curseforge": "1.20.6"},
    "1.20.5": {"modrinth": "1.20.5", "curseforge": "1.20.5"},
    "1.20.4": {"modrinth": "1.20.4", "curseforge": "1.20.4"},
    "1.20.3": {"modrinth": "1.20.3", "curseforge": "1.20.3"},
    "1.20.2": {"modrinth": "1.20.2", "curseforge": "1.20.2"},
    "1.20.1": {"modrinth": "1.20.1", "curseforge": "1.20.1"},
    "1.19.4": {"modrinth": "1.19.4", "curseforge": "1.19.4"},
    "1.19.3": {"modrinth": "1.19.3", "curseforge": "1.19.3"},
    "1.19.2": {"modrinth": "1.19.2", "curseforge": "1.19.2"},
    "1.19": {"modrinth": "1.19", "curseforge": "1.19"},
}

def upload_to_modrinth(jar_path, mc_version):
    """Upload JAR to Modrinth for specific Minecraft version."""
    if not MODRINTH_TOKEN:
        print(f"  ⊘ Modrinth: Token not set (MODRINTH_TOKEN)")
        return False
    
    modrinth_version = VERSION_MAPPING.get(mc_version, {}).get("modrinth")
    if not modrinth_version:
        print(f"  ⊘ Modrinth: No version mapping for {mc_version}")
        return False
    
    print(f"  → Uploading to Modrinth ({modrinth_version})...", end="", flush=True)
    
    try:
        # Using Modrinth API v2
        url = f"https://api.modrinth.com/v2/project/{MODRINTH_PROJECT_ID}/version"
        
        with open(jar_path, 'rb') as f:
            files = {'file': (os.path.basename(jar_path), f)}
            data = {
                'version_number': modrinth_version,
                'version_title': f'StatusMod {modrinth_version}',
                'game_versions': json.dumps([modrinth_version]),
                'loaders': json.dumps(['fabric']),
                'featured': 'false',
            }
            
            response = subprocess.run([
                'curl', '-X', 'POST', url,
                '-H', f'Authorization: {MODRINTH_TOKEN}',
                '-F', f'file=@{jar_path}',
                '-F', f'data={json.dumps(data)}'
            ], capture_output=True, text=True)
            
            if response.returncode == 0:
                print(" ✓")
                return True
            else:
                print(f" ✗ ({response.stderr})")
                return False
    except Exception as e:
        print(f" ✗ ({e})")
        return False

def upload_to_curseforge(jar_path, mc_version):
    """Upload JAR to CurseForge for specific Minecraft version."""
    if not CURSEFORGE_TOKEN:
        print(f"  ⊘ CurseForge: Token not set (CURSEFORGE_TOKEN)")
        return False
    
    curseforge_version = VERSION_MAPPING.get(mc_version, {}).get("curseforge")
    if not curseforge_version:
        print(f"  ⊘ CurseForge: No version mapping for {mc_version}")
        return False
    
    print(f"  → Uploading to CurseForge ({curseforge_version})...", end="", flush=True)
    
    try:
        url = f"https://minecraft.curseforge.com/api/projects/{CURSEFORGE_PROJECT_ID}/upload-file"
        
        with open(jar_path, 'rb') as f:
            metadata = {
                'changelog': f'StatusMod release for Minecraft {curseforge_version}',
                'gameVersions': [curseforge_version],
                'releaseType': 'release',
            }
            
            files = {'file': (os.path.basename(jar_path), f)}
            
            response = subprocess.run([
                'curl', '-X', 'POST', url,
                '-H', f'X-Api-Token: {CURSEFORGE_TOKEN}',
                '-F', f'file=@{jar_path}',
                '-F', f'metadata={json.dumps(metadata)}'
            ], capture_output=True, text=True)
            
            if response.returncode == 0:
                print(" ✓")
                return True
            else:
                print(f" ✗ ({response.stderr})")
                return False
    except Exception as e:
        print(f" ✗ ({e})")
        return False

def main():
    build_dir = Path("build/libs")
    
    if not build_dir.exists():
        print("Error: build/libs directory not found. Run build_all_versions_extended.ps1 first.")
        return 1
    
    # Find all statusmod JARs
    jars = sorted(
        [f for f in build_dir.glob("statusmod-*.jar") if "-sources" not in str(f)],
        key=lambda x: x.name,
        reverse=True
    )
    
    if not jars:
        print("Error: No JARs found in build/libs")
        return 1
    
    print(f"\n{'='*50}")
    print("  STATUSMOD JAR UPLOAD")
    print(f"{'='*50}\n")
    
    modrinth_success = 0
    modrinth_fail = 0
    curseforge_success = 0
    curseforge_fail = 0
    
    for jar_path in jars:
        # Extract MC version from filename
        mc_version = jar_path.stem.replace("statusmod-", "")
        
        print(f"Version: {mc_version}")
        
        # Upload to Modrinth
        if upload_to_modrinth(str(jar_path), mc_version):
            modrinth_success += 1
        else:
            modrinth_fail += 1
        
        # Upload to CurseForge
        if upload_to_curseforge(str(jar_path), mc_version):
            curseforge_success += 1
        else:
            curseforge_fail += 1
        
        print()
    
    print(f"{'='*50}")
    print("UPLOAD SUMMARY")
    print(f"{'='*50}")
    print(f"Modrinth:   {modrinth_success} success, {modrinth_fail} failed")
    print(f"CurseForge: {curseforge_success} success, {curseforge_fail} failed")
    print(f"{'='*50}\n")
    
    return 1 if (modrinth_fail + curseforge_fail) > 0 else 0

if __name__ == '__main__':
    exit(main())
