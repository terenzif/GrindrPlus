#!/usr/bin/env python3
"""Fetch the latest Play-scraped Grindr version into latest_play.json.

This is telemetry / spoof input only — it is NOT the supported hook target.
Hook support is tracked in supported_target.json (and BuildConfig in app/build.gradle.kts).

Writes files only when versionName or versionCode actually change (anti-noise).
Never updates supported_target.json.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Union

import requests
from bs4 import BeautifulSoup

VersionPayload = Dict[str, Union[str, int]]


def get_latest_version() -> Tuple[str, str]:
    """
    Get the latest version & build of the app.

    Returns:
        Tuple[str, str]: A tuple containing (app_version, build_number)

    Raises:
        Exception: If there's an error fetching or parsing the version information
    """
    url: str = (
        'https://www.apkmirror.com/apk/grindr-llc/grindr-gay-chat-meet-date/'
    )
    base_url: str = url.split('/apk/')[0]

    headers = {
        'User-Agent': (
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
            'AppleWebKit/537.36 (KHTML, like Gecko) '
            'Chrome/114.0.0.0 Safari/537.36'
        )
    }

    response: requests.Response = requests.get(url, headers=headers, timeout=30)
    response.raise_for_status()

    soup: BeautifulSoup = BeautifulSoup(response.text, 'html.parser')
    latest_links = soup.select('.appRowTitle > a')

    if not latest_links:
        raise ValueError('Could not find latest version link')

    latest_href: str = latest_links[0]['href']
    version_url: str = f'{base_url}{latest_href}'

    response = requests.get(version_url, headers=headers, timeout=30)
    response.raise_for_status()

    soup = BeautifulSoup(response.text, 'html.parser')

    variant_tables = soup.select('.variants-table')
    if not variant_tables:
        raise ValueError('Could not find variants table')

    table_rows = variant_tables[0].select('.table-row')
    if not table_rows:
        raise ValueError('Could not find table rows')

    cells = table_rows[-1].select('.table-cell')
    if not cells:
        raise ValueError('Could not find table cells')

    app_data: List[str] = cells[0].text.strip().split('\n')
    app_data = [line.strip() for line in app_data]

    if not app_data:
        raise ValueError('Could not parse app data')

    app_version: str = app_data[0]

    digits = [int(s) for s in app_data if s.isdigit()]
    if not digits:
        raise ValueError('Could not find build number')

    build_number: int = digits[0]

    return app_version, str(build_number)


def load_version_json(path: Path) -> Optional[VersionPayload]:
    """Load an existing version JSON file, or None if missing/invalid."""
    if not path.is_file():
        return None
    try:
        with path.open(encoding='utf-8') as f:
            data = json.load(f)
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(data, dict):
        return None
    if 'versionName' not in data or 'versionCode' not in data:
        return None
    return {
        'versionName': str(data['versionName']),
        'versionCode': int(data['versionCode']),
    }


def same_version(a: Optional[VersionPayload], b: VersionPayload) -> bool:
    """True when both payloads describe the same Play version."""
    if a is None:
        return False
    return (
        str(a['versionName']) == str(b['versionName'])
        and int(a['versionCode']) == int(b['versionCode'])
    )


def save_version_to_json(payload: VersionPayload, output_file: Path) -> None:
    """Write version information with a trailing newline."""
    with output_file.open('w', encoding='utf-8') as f:
        json.dump(payload, f, indent=2)
        f.write('\n')
    print(f'Version information saved to {output_file}')


def parse_args() -> argparse.Namespace:
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description='Fetch latest app version and build number'
    )
    parser.add_argument(
        '-o',
        '--output',
        default='latest_play.json',
        help='Output JSON file path (default: latest_play.json)',
    )
    parser.add_argument(
        '--also-version-json',
        action='store_true',
        help=(
            'Also mirror to version.json when the Play scrape changes '
            '(deprecated consumers only; DisableUpdates uses latest_play.json)'
        ),
    )
    parser.add_argument(
        '--github-output',
        action='store_true',
        help='Append changed/versionName/versionCode lines to $GITHUB_OUTPUT',
    )
    return parser.parse_args()


def write_github_output(
    changed: bool, version_name: str, version_code: int
) -> None:
    """Emit workflow outputs when running under GitHub Actions."""
    raw = os.environ.get('GITHUB_OUTPUT')
    if not raw:
        return
    path = Path(raw)
    with path.open('a', encoding='utf-8') as f:
        f.write(f'changed={"true" if changed else "false"}\n')
        f.write(f'versionName={version_name}\n')
        f.write(f'versionCode={version_code}\n')


def main() -> int:
    """Fetch Play version; write files only on NEW versionCode/name."""
    args = parse_args()
    output_path = Path(args.output)

    version, build = get_latest_version()
    version_code = int(build)
    payload: VersionPayload = {
        'versionName': version,
        'versionCode': version_code,
    }
    print(f'App Version: {version}')
    print(f'Build Number: {build}')

    existing = load_version_json(output_path)
    changed = not same_version(existing, payload)

    if not changed:
        print(
            f'UNCHANGED: {version} ({version_code}) already in {output_path}'
        )
        print(
            'Note: supported_target.json is NOT updated by this script '
            '(mapping packs / human decision).'
        )
        if args.github_output:
            write_github_output(False, version, version_code)
        return 0

    print(f'NEW: {version} ({version_code}) — writing {output_path}')
    save_version_to_json(payload, output_path)

    if args.also_version_json:
        mirror = Path('version.json')
        # Keep deprecated mirror in sync only when Play scrape changes.
        save_version_to_json(payload, mirror)
        print(f'Mirrored deprecated {mirror}')

    print(
        'Note: supported_target.json is NOT updated by this script '
        '(mapping packs / human decision).'
    )
    if args.github_output:
        write_github_output(True, version, version_code)
    return 0


if __name__ == '__main__':
    sys.exit(main())
