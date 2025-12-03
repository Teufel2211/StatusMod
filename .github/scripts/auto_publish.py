#!/usr/bin/env python3
"""
Auto publisher with analysis and retries for Modrinth and CurseForge.

Behavior:
- Attempts to upload the provided mod JAR to Modrinth and CurseForge using the public APIs.
- On recoverable errors (e.g. duplicate version), it will bump the local `version.txt`, rebuild the mod, and retry.
- On transient errors it retries with exponential backoff.
- On authentication errors it creates a GitHub issue (if `GITHUB_TOKEN` is present) with logs and exits non-zero.

Configuration (via environment variables):
- MODRINTH_TOKEN, MODRINTH_PROJECT_ID
- CURSEFORGE_API_KEY, CURSEFORGE_PROJECT_ID
- GITHUB_TOKEN (optional, used to file an issue when human attention is required)
- MAX_ATTEMPTS (default: 10)
- BASE_BACKOFF_SECONDS (default: 5)

Notes:
- This script runs inside the workflow workspace and will modify `version.txt` locally and run the Gradle build again when bumping versions.
- It does NOT modify repo history or push commits. The bumped version is only used for the artifact built in the running workflow.
"""
import os
import sys
import time
import glob
import json
import re
import subprocess
from typing import Tuple

import requests


def run(cmd, check=True, env=None):
    print(f">>> {' '.join(cmd)}")
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)
    out = res.stdout.decode(errors='replace')
    print(out)
    if check and res.returncode != 0:
        raise RuntimeError(f"Command {' '.join(cmd)} failed (code={res.returncode})")
    return out


def read_version_file(path='version.txt') -> str:
    with open(path, 'r', encoding='utf-8') as f:
        return f.read().strip()


def write_version_file(version: str, path='version.txt') -> None:
    with open(path, 'w', encoding='utf-8') as f:
        f.write(version + '\n')
    print(f"Updated {path} -> {version}")


def bump_patch_version(v: str) -> str:
    # simple semver-like bump for last numeric part
    parts = v.strip().split('.')
    if not parts[-1].isdigit():
        # append .1 if last part not numeric
        parts.append('1')
    else:
        parts[-1] = str(int(parts[-1]) + 1)
    return '.'.join(parts)


def gradle_build(java_home_env_var='JAVA_HOME') -> None:
    # run gradle wrapper to rebuild artifact and update build/version.txt
    env = os.environ.copy()
    # ensure JAVA_HOME is present (setup-java in workflow sets it)
    if java_home_env_var in env:
        env['ORG_GRADLE_JAVA_HOME'] = env.get(java_home_env_var, env.get('JAVA_HOME', ''))
    run(['chmod', '+x', './gradlew'], check=False)
    run(['./gradlew', 'clean', 'build', 'writeVersion', '--no-daemon', f"-Dorg.gradle.java.home={env.get('JAVA_HOME','')}"] , env=env)


def select_runtime_jar(version: str) -> str:
    # prefer explicit names, else pick first non-sources jar
    candidates = [f'build/libs/statusmod-{version}.jar', f'build/libs/status-mod-{version}.jar']
    for c in candidates:
        if os.path.isfile(c):
            return c
    # fallback: first non-sources jar
    jars = [p for p in glob.glob('build/libs/*.jar') if not p.endswith('-sources.jar')]
    if not jars:
        raise FileNotFoundError('No runtime jar found in build/libs')
    return jars[0]


def upload_modrinth(jar_path: str, project_id: str, token: str, version: str) -> Tuple[bool, int, str]:
    if not project_id or not token:
        return False, 0, 'missing-credentials'
    url = f'https://api.modrinth.com/v2/project/{project_id}/version'
    headers = {'Authorization': token}
    files = {'file': open(jar_path, 'rb')}
    data = {
        'version_number': version.lstrip('v'),
        'game_versions[]': '1.21.10',
        'loaders[]': 'fabric',
        'name': f'StatusMod {version.lstrip("v")}',
        'changelog': 'Automated upload from GitHub Actions'
    }
    try:
        resp = requests.post(url, headers=headers, files=files, data=data, timeout=120)
        text = resp.text
        code = resp.status_code
        success = (200 <= code < 300)
        return success, code, text
    except Exception as e:
        return False, 0, str(e)
    finally:
        try:
            files['file'].close()
        except Exception:
            pass


def upload_curseforge(jar_path: str, project_id: str, api_key: str, version: str) -> Tuple[bool, int, str]:
    if not project_id or not api_key:
        return False, 0, 'missing-credentials'
    url = f'https://api.curseforge.com/v1/mods/{project_id}/files'
    headers = {'x-api-key': api_key}
    files = {'file': open(jar_path, 'rb')}
    data = [
        ('changelog', 'Automated upload from GitHub Actions'),
        ('displayName', f'StatusMod {version.lstrip("v")}'),
        ('releaseType', 'release'),
        ('gameVersions[]', '1.21.10'),
    ]
    try:
        resp = requests.post(url, headers=headers, files=files, data=data, timeout=120)
        code = resp.status_code
        try:
            text = json.dumps(resp.json())
        except Exception:
            text = resp.text
        success = (200 <= code < 300)
        return success, code, text
    except Exception as e:
        return False, 0, str(e)
    finally:
        try:
            files['file'].close()
        except Exception:
            pass


def create_github_issue(repo: str, token: str, title: str, body: str) -> None:
    if not token:
        print('No GITHUB_TOKEN provided; cannot create issue')
        return
    url = f'https://api.github.com/repos/{repo}/issues'
    headers = {'Authorization': f'token {token}', 'Accept': 'application/vnd.github.v3+json'}
    payload = {'title': title, 'body': body}
    try:
        resp = requests.post(url, headers=headers, json=payload, timeout=30)
        print('Created GitHub issue:', resp.status_code)
        print(resp.text)
    except Exception as e:
        print('Failed to create GitHub issue:', e)


def analyze_modrinth_failure(code: int, text: str) -> str:
    # return action: 'retry', 'bump_version', 'auth', 'fatal'
    if code in (401, 403):
        return 'auth'
    if code == 409 or 'already exists' in text.lower() or 'duplicate' in text.lower():
        return 'bump_version'
    if code == 0:
        return 'retry'
    if 500 <= code < 600:
        return 'retry'
    return 'fatal'


def analyze_curseforge_failure(code: int, text: str) -> str:
    if code in (401, 403):
        return 'auth'
    if code == 409 or 'already exists' in text.lower() or 'duplicate' in text.lower():
        return 'bump_version'
    if code == 0 or (500 <= code < 600):
        return 'retry'
    return 'fatal'


def main():
    repo = os.environ.get('GITHUB_REPOSITORY', '')
    github_token = os.environ.get('GITHUB_TOKEN', '')
    modrinth_token = os.environ.get('MODRINTH_TOKEN', '')
    modrinth_project = os.environ.get('MODRINTH_PROJECT_ID', '')
    curse_key = os.environ.get('CURSEFORGE_API_KEY', os.environ.get('CURSEFORGE_TOKEN', ''))
    curse_project = os.environ.get('CURSEFORGE_PROJECT_ID', '')
    max_attempts = int(os.environ.get('MAX_ATTEMPTS', '10'))
    base_backoff = int(os.environ.get('BASE_BACKOFF_SECONDS', '5'))

    # initial build should already have run; ensure build/version.txt exists
    if not os.path.isfile('build/version.txt'):
        print('No build/version.txt found — running initial build')
        gradle_build()

    version = read_version_file('build/version.txt') if os.path.isfile('build/version.txt') else read_version_file('version.txt')

    attempt = 0
    mod_ok = False
    cf_ok = False

    while attempt < max_attempts and not (mod_ok and cf_ok):
        attempt += 1
        print(f'=== Attempt {attempt} / {max_attempts} (version={version}) ===')

        try:
            jar = select_runtime_jar(version)
        except Exception as e:
            print('Jar selection failed:', e)
            # try a rebuild and retry
            gradle_build()
            version = read_version_file('build/version.txt')
            time.sleep(base_backoff)
            continue

        # Modrinth
        if not mod_ok:
            ok, code, text = upload_modrinth(jar, modrinth_project, modrinth_token, version)
            print('Modrinth:', ok, code, text[:1000])
            if ok:
                mod_ok = True
            else:
                action = analyze_modrinth_failure(code, text)
                print('Modrinth action:', action)
                if action == 'bump_version':
                    # bump project version.txt and rebuild locally
                    v = read_version_file('version.txt')
                    newv = bump_patch_version(v)
                    write_version_file(newv, 'version.txt')
                    gradle_build()
                    version = read_version_file('build/version.txt')
                    print('Bumped version ->', version)
                    # immediate retry
                    continue
                if action == 'auth':
                    body = f"Automated publish failed: Modrinth authentication error (code {code}).\nLogs:\n{text[:10000]}"
                    create_github_issue(repo, github_token, 'Publish failed: Modrinth auth', body)
                    sys.exit(2)
                if action == 'retry':
                    backoff = base_backoff * (2 ** (attempt - 1))
                    print(f'Modrinth transient error, sleeping {backoff}s')
                    time.sleep(backoff)
                    continue
                # fatal
                body = f"Automated publish failed: Modrinth fatal error (code {code}).\nLogs:\n{text[:10000]}"
                create_github_issue(repo, github_token, 'Publish failed: Modrinth fatal', body)
                sys.exit(3)

        # CurseForge
        if not cf_ok:
            ok, code, text = upload_curseforge(jar, curse_project, curse_key, version)
            print('CurseForge:', ok, code, text[:1000])
            if ok:
                cf_ok = True
            else:
                action = analyze_curseforge_failure(code, text)
                print('CurseForge action:', action)
                if action == 'bump_version':
                    v = read_version_file('version.txt')
                    newv = bump_patch_version(v)
                    write_version_file(newv, 'version.txt')
                    gradle_build()
                    version = read_version_file('build/version.txt')
                    print('Bumped version ->', version)
                    continue
                if action == 'auth':
                    body = f"Automated publish failed: CurseForge authentication error (code {code}).\nLogs:\n{text[:10000]}"
                    create_github_issue(repo, github_token, 'Publish failed: CurseForge auth', body)
                    sys.exit(2)
                if action == 'retry':
                    backoff = base_backoff * (2 ** (attempt - 1))
                    print(f'CurseForge transient error, sleeping {backoff}s')
                    time.sleep(backoff)
                    continue
                body = f"Automated publish failed: CurseForge fatal error (code {code}).\nLogs:\n{text[:10000]}"
                create_github_issue(repo, github_token, 'Publish failed: CurseForge fatal', body)
                sys.exit(4)

    if mod_ok and cf_ok:
        print('Both uploads succeeded')
        sys.exit(0)
    else:
        print('Reached attempt limit without success')
        body = f"Automated publish failed after {attempt} attempts. Last version: {version}. See logs above."
        create_github_issue(repo, github_token, 'Publish failed: retries exhausted', body)
        sys.exit(1)


if __name__ == '__main__':
    main()
