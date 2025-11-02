#!/usr/bin/env python3
"""
Simple release uploader for Modrinth and CurseForge.

Notes:
- This is a minimal, best-effort script to be run from CI (GitHub Actions in this repo).
- You must set the following repository secrets in GitHub for the workflow to actually upload:
  - MODRINTH_TOKEN
  - MODRINTH_PROJECT_ID
  - CURSEFORGE_API_KEY
  - CURSEFORGE_PROJECT_ID

Use: python upload_release.py path/to/mod.jar

This script attempts to call the public APIs. API surface may change; adjust fields or add extra metadata as needed.
"""
import sys
import os
import requests
import glob

def upload_modrinth(jar_path, project_id, token, version):
    if not project_id or not token:
        print("Skipping Modrinth upload: MODRINTH_PROJECT_ID or MODRINTH_TOKEN not provided")
        return
    url = f"https://api.modrinth.com/v2/project/{project_id}/version"
    headers = {
        'Authorization': token
    }
    # Minimal fields: version_number, game_versions[], loaders[] and the file multipart.
    # Modrinth expects multipart/form-data. This may need adjusting depending on Modrinth API changes.
    files = {'file': open(jar_path, 'rb')}
    # infer version from provided value or from jar filename
    if not version:
        basename = os.path.basename(jar_path)
        if '-' in basename:
            inferred = basename.rsplit('-', 1)[-1]
            inferred = inferred.rsplit('.', 1)[0]
            version = inferred
        else:
            version = 'unspecified'
    data = {
        'version_number': version.lstrip('v'),
        'game_versions[]': '1.21.10',
        'loaders[]': 'fabric',
        'name': f"StatusMod {version.lstrip('v')}",
        'changelog': 'Automated upload from GitHub Actions'
    }
    print(f"Uploading to Modrinth: {jar_path} -> project {project_id}")
    try:
        resp = requests.post(url, headers=headers, files=files, data=data, timeout=120)
        print('Modrinth response:', resp.status_code)
        print(resp.text)
    finally:
        files['file'].close()

def upload_curseforge(jar_path, project_id, api_key, version):
    if not project_id or not api_key:
        print("Skipping CurseForge upload: CURSEFORGE_PROJECT_ID or CURSEFORGE_API_KEY not provided")
        return
    url = f"https://api.curseforge.com/v1/mods/{project_id}/files"
    headers = {'x-api-key': api_key}
    files = {'file': open(jar_path, 'rb')}
    # CurseForge requires game version(s) and it's helpful to include java version & description
    # Optionally read JAVA version and environment from env vars
    java_version = os.environ.get('CURSEFORGE_JAVA_VERSION', '')
    environment = os.environ.get('CURSEFORGE_ENVIRONMENT', '')
    # infer version if not provided
    if not version:
        basename = os.path.basename(jar_path)
        if '-' in basename:
            inferred = basename.rsplit('-', 1)[-1]
            inferred = inferred.rsplit('.', 1)[0]
            version = inferred
        else:
            version = 'unspecified'
    data = {
        'changelog': 'Automated upload from GitHub Actions',
        'displayName': f"StatusMod {version.lstrip('v')}",
        'releaseType': 'release',
        # gameVersions[] is the accepted parameter for API to indicate Minecraft versions
        'gameVersions[]': '1.21.10'
    }
    if java_version:
        data['javaVersion'] = java_version
    if environment:
        data['environment'] = environment
    print(f"Uploading to CurseForge: {jar_path} -> project {project_id}")
    try:
        resp = requests.post(url, headers=headers, files=files, data=data, timeout=120)
        print('CurseForge response:', resp.status_code)
        print(resp.text)
    finally:
        files['file'].close()

def main():
    if len(sys.argv) < 2:
        print('Usage: upload_release.py path/to/mod.jar')
        sys.exit(2)
    # find first jar matching pattern(s)
    arg = sys.argv[1]
    matches = glob.glob(arg)
    if not matches:
        print('No file matched', arg)
        sys.exit(1)
    jar_path = matches[0]

    # Environment secrets (set in GitHub Actions as repo secrets)
    modrinth_token = os.environ.get('MODRINTH_TOKEN')
    modrinth_project = os.environ.get('MODRINTH_PROJECT_ID')
    curse_key = os.environ.get('CURSEFORGE_API_KEY')
    curse_project = os.environ.get('CURSEFORGE_PROJECT_ID')
    version = os.environ.get('VERSION', '')

    upload_modrinth(jar_path, modrinth_project, modrinth_token, version)
    upload_curseforge(jar_path, curse_project, curse_key, version)

if __name__ == '__main__':
    main()
