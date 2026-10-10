#!/usr/bin/env python3
"""Select a changelog base from published Releases reachable from HEAD."""

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request


class ReleaseBaseError(Exception):
    pass


def list_releases(repository, token, api_url):
    """Read every page; orphan tags are intentionally not Release candidates."""
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "legado-release-notes",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    repository = urllib.parse.quote(repository, safe="/")
    releases = []
    page = 1
    while True:
        url = f"{api_url.rstrip('/')}/repos/{repository}/releases?per_page=100&page={page}"
        request = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                batch = json.load(response)
        except urllib.error.HTTPError as error:
            raise ReleaseBaseError(f"Release API returned HTTP {error.code}") from error
        except (urllib.error.URLError, TimeoutError, ValueError) as error:
            raise ReleaseBaseError("Failed to read the Release API response") from error
        if not isinstance(batch, list) or any(not isinstance(item, dict) for item in batch):
            raise ReleaseBaseError("Release API did not return a list of Releases")
        releases.extend(batch)
        if len(batch) < 100:
            return releases
        page += 1


def git(*args):
    return subprocess.run(
        ["git", *args], capture_output=True, text=True, check=False
    )


def select_base(releases, channel, head="HEAD"):
    head_result = git("rev-parse", "--verify", f"{head}^{{commit}}")
    if head_result.returncode:
        raise ReleaseBaseError("Cannot resolve the current build commit")
    head_sha = head_result.stdout.strip()
    candidates = []
    distances = {}
    for release in releases:
        if release.get("draft") is not False or not release.get("published_at"):
            continue
        if channel == "release" and release.get("prerelease") is not False:
            continue
        tag = release.get("tag_name")
        if not isinstance(tag, str) or not tag:
            continue
        # Resolve the actual tag, not target_commitish (which may be a moving branch).
        tag_result = git("rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}")
        if tag_result.returncode:
            continue
        sha = tag_result.stdout.strip()
        if sha not in distances:
            reachable = git("merge-base", "--is-ancestor", sha, head_sha)
            if reachable.returncode == 1:
                distances[sha] = None
            elif reachable.returncode:
                raise ReleaseBaseError("Failed to check Release commit ancestry")
            else:
                count = git("rev-list", "--count", f"{sha}..{head_sha}")
                if count.returncode:
                    raise ReleaseBaseError("Failed to count commits since a Release")
                distances[sha] = int(count.stdout.strip())
        distance = distances[sha]
        if distance is not None:
            candidates.append((distance, release["published_at"], tag, sha))
    if not candidates:
        return None
    # Prefer the nearest commit in this history. Publication time only breaks ties.
    distance, published_at, tag, sha = max(
        candidates, key=lambda item: (-item[0], item[1], item[2])
    )
    print(f"DEBUG: RELEASE_BASE_TAG={tag} RELEASE_BASE_SHA={sha} COMMITS_SINCE={distance}", file=sys.stderr)
    return sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--channel", choices=("release", "beta"), required=True)
    args = parser.parse_args()
    repository = os.environ.get("GITHUB_REPOSITORY")
    if not repository:
        parser.error("GITHUB_REPOSITORY is required")
    try:
        releases = list_releases(
            repository,
            os.environ.get("GH_TOKEN", ""),
            os.environ.get("GITHUB_API_URL", "https://api.github.com"),
        )
        base = select_base(releases, args.channel)
    except ReleaseBaseError as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    if base:
        print(base)
    else:
        print("DEBUG: no reachable published Release; using the latest 20 commits", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
