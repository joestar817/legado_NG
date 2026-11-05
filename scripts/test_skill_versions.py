from contextlib import redirect_stderr, redirect_stdout
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from check_skill_versions import (
    MAX_VERSION,
    SKILL_DIR,
    check_versions,
    collect_skills,
    main,
    parse_skill,
    read_snapshot,
    read_worktree,
)


def document(version="1", body="原有规则", skill_id="test_skill"):
    return f"---\nid: {skill_id}\nversion: {version}\n---\n{body}\n"


def snapshot(content, path=f"{SKILL_DIR}/test.md"):
    return collect_skills([(path, content)])


class SkillVersionPolicyTest(unittest.TestCase):
    def test_version_accepts_positive_int_and_double_quotes(self):
        for version in ("1", '"29"', str(MAX_VERSION)):
            with self.subTest(version=version):
                self.assertGreater(parse_skill("test.md", document(version)).version, 0)

    def test_invalid_or_overflowing_versions_are_rejected(self):
        for version in ("", "0", "-1", "+1", "1.5", "1_000", "latest", "２",
                        "'29'", "29 # comment", str(MAX_VERSION + 1), "202611060100"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                parse_skill("test.md", document(version))

    def test_body_version_cannot_replace_frontmatter_version(self):
        with self.assertRaisesRegex(ValueError, "version 必须"):
            parse_skill("test.md", "---\nid: test\n---\nversion: 2\n")

    def test_missing_and_unclosed_frontmatter_are_rejected(self):
        for content in ("version: 1", "---\nid: test\nversion: 1"):
            with self.subTest(content=content), self.assertRaises(ValueError):
                parse_skill("test.md", content)

    def test_duplicate_identity_or_version_is_rejected(self):
        for field in ("id: other", "version: 2"):
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "重复"):
                parse_skill("test.md", document().replace("version: 1", "version: 1\n" + field))

    def test_missing_identity_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "缺少 id"):
            parse_skill("test.md", "---\nversion: 1\n---\nbody")

    def test_duplicate_normalized_skill_ids_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "id.*重复"):
            collect_skills([
                ("first.md", document(skill_id="Test Skill")),
                ("second.md", document(skill_id="test_skill")),
            ])

    def test_content_change_requires_strictly_higher_version(self):
        errors = check_versions(snapshot(document("2")), snapshot(document("2", "新规则")))
        self.assertEqual(1, len(errors))
        self.assertIn("必须大于目标分支的 2", errors[0])

    def test_frontmatter_change_also_requires_higher_version(self):
        changed = document().replace("version: 1", "name: 新标题\nversion: 1")
        self.assertEqual(1, len(check_versions(snapshot(document()), snapshot(changed))))

    def test_content_change_with_higher_version_passes(self):
        self.assertEqual([], check_versions(snapshot(document("1")), snapshot(document("2", "新规则"))))

    def test_version_must_not_decrease_even_when_payload_is_unchanged(self):
        errors = check_versions(snapshot(document("2")), snapshot(document("1")))
        self.assertEqual(1, len(errors))
        self.assertIn("不得回退", errors[0])

    def test_version_only_bump_passes(self):
        self.assertEqual([], check_versions(snapshot(document("1")), snapshot(document("2"))))

    def test_line_endings_and_trimmed_outer_whitespace_do_not_change_payload(self):
        current = "\n" + document().replace("\n", "\r\n") + " \n"
        self.assertEqual([], check_versions(snapshot(document()), snapshot(current)))

    def test_rename_tracks_skill_identity_instead_of_filename(self):
        previous = snapshot(document())
        renamed = snapshot(document(), f"{SKILL_DIR}/renamed.md")
        changed = snapshot(document(body="新规则"), f"{SKILL_DIR}/renamed.md")
        self.assertEqual([], check_versions(previous, renamed))
        self.assertEqual(1, len(check_versions(previous, changed)))

    def test_new_skill_is_validated_and_removal_does_not_require_a_bump(self):
        self.assertEqual([], check_versions({}, snapshot(document())))
        self.assertEqual([], check_versions(snapshot(document()), {}))


class SkillVersionGitTest(unittest.TestCase):
    def setUp(self):
        workspace = Path(__file__).resolve().parents[1]
        scratch = workspace / ".agent" / "test-tmp" / "skill-versions"
        scratch.mkdir(parents=True, exist_ok=True)
        # Windows 上的 Git 测试工作树及其递归清理都限定在本仓库内。
        if not scratch.resolve().is_relative_to(workspace.resolve()):
            raise RuntimeError("测试临时目录超出工作区")
        temporary = tempfile.TemporaryDirectory(prefix="case-", dir=scratch)
        self.addCleanup(temporary.cleanup)
        self.repo = Path(temporary.name)
        self.env = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "Skill Version Tests")
        self.git("config", "user.email", "skill-tests@example.invalid")
        self.git("config", "core.autocrlf", "false")
        self.write("test.md", document())
        self.base = self.commit()

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.repo, env=self.env).decode("utf-8").strip()

    def write(self, name, content):
        path = self.repo / SKILL_DIR / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def commit(self):
        self.git("add", ".")
        self.git("commit", "-qm", "测试样本")
        return self.git("rev-parse", "HEAD")

    def check(self, *args):
        return self.invoke("--base-ref", self.base, *args)

    def invoke(self, *args):
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            return main(["--repo", str(self.repo), *args])

    def test_worktree_default_catches_uncommitted_content_changes(self):
        self.write("test.md", document(body="未提交新规则"))
        self.assertEqual(1, self.check())
        self.assertEqual(0, self.check("--head-ref", "HEAD"))

    def test_git_snapshots_check_bumps_without_touching_the_worktree(self):
        self.write("test.md", document("2", "新规则"))
        head = self.commit()
        self.assertEqual(0, self.check("--head-ref", head, "--require-base-ancestor"))
        self.assertEqual("", self.git("status", "--porcelain"))

    def test_latest_target_catches_two_prs_with_the_same_increment(self):
        self.write("test.md", document("2", "第一个 PR"))
        target = self.commit()
        self.git("checkout", "-qb", "second-pr", self.base)
        self.write("test.md", document("2", "第二个 PR"))
        candidate = self.commit()
        # 相同候选对旧分叉点会通过，对已合并第一个 PR 的最新基线会失败。
        self.assertEqual(0, self.check("--head-ref", candidate))
        self.assertEqual(1, self.invoke("--base-ref", target, "--head-ref", candidate))
        self.assertEqual(1, self.invoke(
            "--base-ref", target, "--head-ref", candidate,
            "--require-base-ancestor",
        ))

    def test_updated_merge_candidate_still_requires_bump_over_latest_target(self):
        self.write("test.md", document("2", "已合并规则"))
        target = self.commit()
        self.write("test.md", document("2", "候选规则"))
        candidate = self.commit()
        self.assertEqual(1, self.invoke(
            "--base-ref", target, "--head-ref", candidate,
            "--require-base-ancestor",
        ))

    def test_directory_packages_are_outside_the_single_file_gate(self):
        self.write("workflow/SKILL.md", "version: not-an-integer\n")
        self.write("workflow/references/rules.md", "包内规则\n")
        self.commit()
        self.assertEqual(1, len(read_snapshot(self.repo, "HEAD")))
        self.assertEqual(1, len(read_worktree(self.repo)))
        self.assertEqual(0, self.check("--head-ref", "HEAD"))

    def test_invalid_new_skill_and_duplicate_id_fail(self):
        self.write("new.md", document("0", skill_id="new"))
        self.assertEqual(1, self.check())
        self.write("new.md", document("1"))
        self.assertEqual(1, self.check())

    def test_missing_git_ref_fails_closed(self):
        self.assertEqual(1, self.check("--head-ref", "missing-ref"))

    def test_removing_the_last_single_file_skill_is_allowed(self):
        path = self.repo / SKILL_DIR / "test.md"
        path.unlink()
        path.parent.rmdir()
        self.assertEqual(0, self.check())


if __name__ == "__main__":
    unittest.main()
