import contextlib
import io
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import textwrap
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest import mock
import urllib.error

import select_release_base as selector


SCRIPT = Path(selector.__file__).resolve()
WORKFLOW = SCRIPT.parent.parent / "workflows" / "test.yml"


def release(tag, prerelease=False, **overrides):
    result = {
        "tag_name": tag,
        "draft": False,
        "prerelease": prerelease,
        "published_at": "2026-10-01T00:00:00Z",
        "target_commitish": "main",
    }
    result.update(overrides)
    return result


class ReleaseBaseTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="legado-release-base-")
        self.original_cwd = Path.cwd()
        os.chdir(self.directory.name)
        self.addCleanup(self.directory.cleanup)
        self.addCleanup(os.chdir, self.original_cwd)
        self.git("init", "-q", "--initial-branch=main")
        self.git("config", "user.name", "Release Test")
        self.git("config", "user.email", "release-test@example.invalid")
        self.commit("initial")
        self.formal_sha = self.commit("formal A", "3.26.100100")

    def git(self, *args):
        return subprocess.check_output(
            ["git", "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgSign=false",
             "-c", "tag.gpgSign=false", *args],
            text=True,
        ).strip()

    def commit(self, message, tag=None):
        self.git("commit", "-q", "--allow-empty", "-m", message)
        sha = self.git("rev-parse", "HEAD")
        if tag:
            self.git("tag", tag)
        return sha

    def select(self, releases, channel):
        with contextlib.redirect_stderr(io.StringIO()):
            return selector.select_base(releases, channel)

    def test_formal_ignores_newer_beta(self):
        self.commit("beta changes", "beta")
        self.commit("fix")
        self.assertEqual(self.formal_sha, self.select([
            release("beta", True), release("3.26.100100")
        ], "release"))

    def test_deleted_formal_release_leaves_tag_but_not_base(self):
        self.commit("withdrawn changes", "3.26.100200")
        self.commit("repair")
        base = self.select([release("3.26.100100")], "release")
        self.assertEqual(self.formal_sha, base)
        self.assertEqual("withdrawn changes\nrepair", self.git(
            "log", f"{base}..HEAD", "--format=%s", "--reverse"
        ))

    def test_beta_uses_formal_newer_than_old_fixed_beta_tag(self):
        self.git("tag", "beta", "HEAD~1")
        self.commit("new beta change")
        self.assertEqual(self.formal_sha, self.select([
            release("beta", True), release("3.26.100100")
        ], "beta"))

    def test_beta_uses_newer_fixed_beta_tag(self):
        beta_sha = self.commit("beta changes", "beta")
        self.commit("new beta change")
        self.assertEqual(beta_sha, self.select([
            release("3.26.100100"), release("beta", True)
        ], "beta"))

    def test_deleted_beta_release_does_not_use_remaining_beta_tag(self):
        self.commit("withdrawn beta", "beta")
        self.commit("repair")
        self.assertEqual(self.formal_sha, self.select([
            release("3.26.100100")
        ], "beta"))

    def test_unmerged_and_missing_tags_are_not_bases(self):
        self.git("checkout", "-q", "-b", "other-beta")
        self.commit("other branch change", "beta")
        self.git("checkout", "-q", "main")
        self.commit("main change")
        releases = [release("beta", True), release("missing"), release("3.26.100100")]
        for channel in ("release", "beta"):
            with self.subTest(channel=channel):
                self.assertEqual(self.formal_sha, self.select(releases, channel))

    def test_drafts_and_unpublished_releases_are_not_bases(self):
        self.commit("draft", "draft")
        self.commit("unpublished", "unpublished")
        self.assertEqual(self.formal_sha, self.select([
            release("draft", draft=True), release("unpublished", published_at=None),
            release("3.26.100100")
        ], "beta"))

    def test_annotated_tag_is_peeled_and_target_branch_is_ignored(self):
        self.git("tag", "-a", "annotated", "-m", "release", self.formal_sha)
        self.commit("later")
        self.assertEqual(self.formal_sha, self.select([
            release("annotated", target_commitish="missing-branch")
        ], "release"))

    def test_commit_history_takes_precedence_over_publication_date_and_tag_name(self):
        self.git("tag", "3.99.999999", "HEAD~1")
        beta_sha = self.commit("recent beta", "beta")
        self.commit("later")
        self.assertEqual(beta_sha, self.select([
            release("3.99.999999", published_at="2099-01-01T00:00:00Z"),
            release("beta", True)
        ], "beta"))

    def test_merged_release_commit_is_reachable(self):
        self.git("checkout", "-q", "-b", "beta-merged")
        beta_sha = self.commit("merged beta", "beta")
        self.git("checkout", "-q", "main")
        self.commit("main change")
        self.git("merge", "-q", "--no-ff", "beta-merged", "-m", "Merge beta")
        self.assertEqual(beta_sha, self.select([
            release("3.26.100100"), release("beta", True)
        ], "beta"))

    def test_no_published_release_has_no_base_even_with_existing_tags(self):
        self.commit("orphan beta", "beta")
        for channel in ("release", "beta"):
            with self.subTest(channel=channel):
                self.assertIsNone(self.select([], channel))

    def run_workflow(self, releases, branch="beta-test", status=200):
        block = WORKFLOW.read_text(encoding="utf-8").split("      - id: release-info\n", 1)[1]
        block = block.split("      - id: set-matrix\n", 1)[0].split("        run: |\n", 1)[1]
        shell = textwrap.dedent(block).replace(
            "${{ steps.set-ver.outputs.version }}", "3.26.101000"
        ).replace("python3 .github/scripts/select_release_base.py", shlex.join([sys.executable, str(SCRIPT)]))

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps(releases).encode())

            def log_message(self, *args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        output = Path(self.directory.name) / "github-output"
        env = dict(os.environ, GITHUB_REPOSITORY="test/repo", GH_TOKEN="test-token",
                   GITHUB_API_URL=f"http://127.0.0.1:{server.server_port}",
                   GITHUB_REF_NAME=branch, GITHUB_OUTPUT=str(output))
        try:
            result = subprocess.run(["bash", "-e", "-o", "pipefail", "-c", shell],
                                    env=env, capture_output=True, text=True, timeout=10)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
        values = dict(line.split("=", 1) for line in output.read_text().splitlines()) if output.exists() else {}
        return result, values

    def test_workflow_formal_keeps_beta_changes_and_filters_merge_subjects(self):
        self.commit("feature one", "beta")
        self.commit("Merge helper")
        self.commit("feature two")
        result, values = self.run_workflow([
            release("beta", True), release("3.26.100100")
        ], "release")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("* feature one\n* feature two\n", json.loads(values["body"]))
        self.assertEqual("false", values["prerelease"])
        self.assertEqual("3.26.101000", values["tag_name"])

    def test_workflow_beta_starts_after_latest_reachable_formal(self):
        self.git("tag", "beta", "HEAD~1")
        self.commit("new beta change")
        result, values = self.run_workflow([
            release("beta", True), release("3.26.100100")
        ])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("* new beta change\n", json.loads(values["body"]))
        self.assertEqual("beta", values["tag_name"])
        self.assertEqual("true", values["prerelease"])

    def test_workflow_without_releases_includes_short_history(self):
        result, values = self.run_workflow([])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("* initial\n* formal A\n", json.loads(values["body"]))

    def test_workflow_without_releases_limits_history_to_20_commits(self):
        for index in range(25):
            self.commit(f"change {index}")
        result, values = self.run_workflow([])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([f"* change {index}" for index in range(5, 25)],
                         json.loads(values["body"]).splitlines())

    def test_workflow_api_failure_stops_instead_of_using_an_orphan_tag(self):
        self.commit("orphan beta", "beta")
        result, values = self.run_workflow({"message": "unavailable"}, status=503)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("HTTP 503", result.stderr)
        self.assertEqual({}, values)


class ReleaseApiTests(unittest.TestCase):
    def test_all_pages_are_read_with_authentication(self):
        pages = [[release("draft", draft=True)] * 100, [release("older-formal")]]
        with mock.patch.object(selector.urllib.request, "urlopen") as urlopen:
            urlopen.side_effect = [io.BytesIO(json.dumps(page).encode()) for page in pages]
            releases = selector.list_releases("test/repo", "test-token", "https://api.example.invalid")
        self.assertEqual(101, len(releases))
        self.assertEqual("older-formal", releases[-1]["tag_name"])
        requests = [call.args[0] for call in urlopen.call_args_list]
        self.assertTrue(requests[0].full_url.endswith("per_page=100&page=1"))
        self.assertTrue(requests[1].full_url.endswith("per_page=100&page=2"))
        self.assertEqual("Bearer test-token", requests[0].get_header("Authorization"))

    def test_invalid_api_data_is_an_error(self):
        for payload in (b'{"message":"failure"}', b'not JSON', b'[null]'):
            with self.subTest(payload=payload), mock.patch.object(selector.urllib.request, "urlopen") as urlopen:
                urlopen.return_value = io.BytesIO(payload)
                with self.assertRaises(selector.ReleaseBaseError):
                    selector.list_releases("test/repo", "", "https://api.example.invalid")

    def test_network_failure_is_an_error(self):
        with mock.patch.object(selector.urllib.request, "urlopen", side_effect=urllib.error.URLError("offline")):
            with self.assertRaises(selector.ReleaseBaseError):
                selector.list_releases("test/repo", "", "https://api.example.invalid")


if __name__ == "__main__":
    unittest.main()
