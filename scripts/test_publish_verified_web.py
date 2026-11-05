from unittest.mock import Mock, patch
import json
import subprocess
import unittest

import publish_verified_web as publisher


BRANCH = "codex/web-assets-123-1"
COMMIT = "a" * 40
REF = "refs/heads/" + BRANCH


def run(status="completed", conclusion="success", **changes):
    return dict(id=17, head_branch=BRANCH, head_sha=COMMIT, event="workflow_dispatch",
                status=status, conclusion=conclusion, html_url="https://github.com/example/run/17", **changes)


class VerifiedWebPublishTest(unittest.TestCase):
    def setUp(self):
        self.api = Mock()
        self.api.request.side_effect = [None, {"workflow_runs": [run()]}]
        self.commands = []
        self.patch = patch.object(publisher, "git", side_effect=self.git)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def git(self, *args):
        self.commands.append(args)
        if args[0] == "rev-parse":
            return COMMIT
        if args[0] == "ls-remote":
            return f"{COMMIT}\t{REF}"
        return ""

    def assert_not_published(self):
        self.assertNotIn(("push", "origin", f"{COMMIT}:refs/heads/main"), self.commands)

    def assert_cleaned(self):
        self.assertEqual(("push", f"--force-with-lease={REF}:{COMMIT}", "origin", f":{REF}"),
                         self.commands[-1])

    def test_only_a_successful_matching_run_can_update_main(self):
        publisher.publish(self.api, BRANCH, COMMIT)
        self.assertIn(("push", "origin", f"{COMMIT}:refs/heads/main"), self.commands)
        self.assert_cleaned()

    def test_failed_check_does_not_publish_and_cleans_own_branch(self):
        self.api.request.side_effect = [None, {"workflow_runs": [run(conclusion="failure")]}]
        with self.assertRaisesRegex(RuntimeError, "版本校验未通过"):
            publisher.publish(self.api, BRANCH, COMMIT)
        self.assert_not_published()
        self.assert_cleaned()

    def test_other_commit_green_result_is_not_reused(self):
        wrong = run()
        wrong["head_sha"] = "b" * 40
        self.api.request.side_effect = [None, {"workflow_runs": [wrong]}, {"workflow_runs": [run()]}]
        with patch.object(publisher.time, "sleep"):
            publisher.publish(self.api, BRANCH, COMMIT)
        self.assertEqual(3, self.api.request.call_count)
        self.assert_cleaned()

    def test_other_branch_or_event_green_result_is_not_reused(self):
        for field, value in (("head_branch", "main"), ("event", "push")):
            with self.subTest(field=field):
                wrong = run()
                wrong[field] = value
                self.api.request.reset_mock()
                self.api.request.side_effect = [None, {"workflow_runs": [wrong]},
                                                {"workflow_runs": [run()]}]
                with patch.object(publisher.time, "sleep"):
                    publisher.publish(self.api, BRANCH, COMMIT)
                self.assertEqual(3, self.api.request.call_count)

    def test_timeout_cancels_own_run_without_publishing(self):
        self.api.request.side_effect = [None, {"workflow_runs": [run(status="in_progress", conclusion=None)]}, None]
        with patch.object(publisher.time, "monotonic", side_effect=[0, 0, 2]), \
                patch.object(publisher.time, "sleep"), self.assertRaisesRegex(RuntimeError, "超时"):
            publisher.publish(self.api, BRANCH, COMMIT, timeout=1)
        self.api.request.assert_called_with("/actions/runs/17/cancel", {})
        self.assert_not_published()
        self.assert_cleaned()

    def test_changed_remote_branch_is_not_deleted(self):
        with patch.object(publisher, "git", side_effect=[COMMIT, "b" * 40 + "\t" + REF]):
            with self.assertRaisesRegex(ValueError, "临时分支已变化"):
                publisher.publish(self.api, BRANCH, COMMIT)
        self.api.request.assert_not_called()
        self.assertEqual([], self.commands)

    def test_foreign_branch_name_is_rejected(self):
        with self.assertRaises(ValueError):
            publisher.publish(self.api, "main", COMMIT)
        self.assertEqual([], self.commands)

    def test_invalid_commit_is_rejected_before_git_or_api(self):
        with self.assertRaises(ValueError):
            publisher.publish(self.api, BRANCH, "HEAD:main")
        self.assertEqual([], self.commands)
        self.api.request.assert_not_called()

    def test_dispatch_sends_json_to_the_repository_api(self):
        response = Mock()
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        response.read.return_value = b""
        with patch.object(publisher, "urlopen", return_value=response) as request:
            api = publisher.GitHub("owner/repo", "test-token")
            api.request("/actions/workflows/skill-versions.yml/dispatches", {"ref": BRANCH})
        sent = request.call_args.args[0]
        self.assertEqual("application/json", sent.get_header("Content-type"))
        self.assertEqual("POST", sent.get_method())
        self.assertEqual({"ref": BRANCH}, json.loads(sent.data))
        self.assertEqual("https://api.github.com/repos/owner/repo/actions/workflows/skill-versions.yml/dispatches",
                         sent.full_url)

    def test_main_advance_rejects_publish_and_cleans_candidate(self):
        original = self.git
        def changed_main(*args):
            if args == ("push", "origin", f"{COMMIT}:refs/heads/main"):
                raise subprocess.CalledProcessError(1, args)
            return original(*args)
        with patch.object(publisher, "git", side_effect=changed_main):
            with self.assertRaises(subprocess.CalledProcessError):
                publisher.publish(self.api, BRANCH, COMMIT)
        self.assert_cleaned()


if __name__ == "__main__":
    unittest.main()
