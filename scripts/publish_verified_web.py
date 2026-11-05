"""Web 自动提交先通过 Skill CI，再快进 main；不需要管理员令牌。"""

import json
import os
import re
import subprocess
import sys
import time
from urllib.parse import urlencode
from urllib.request import Request, urlopen


WORKFLOW = "skill-versions.yml"


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


class GitHub:
    def __init__(self, repository, token):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
            raise ValueError("非法仓库名称")
        if not token:
            raise ValueError("缺少 GH_TOKEN")
        self.base = "https://api.github.com/repos/" + repository
        self.token = token

    def request(self, path, body=None):
        request = Request(
            self.base + path,
            data=json.dumps(body).encode() if body is not None else None,
            headers={"Authorization": "Bearer " + self.token,
                     "Accept": "application/vnd.github+json",
                     "Content-Type": "application/json",
                     "X-GitHub-Api-Version": "2026-03-10",
                     "User-Agent": "legado-verified-web-publish"},
        )
        with urlopen(request, timeout=30) as response:
            content = response.read()
            return json.loads(content) if content else None


def publish(api, branch, commit, timeout=600):
    if not re.fullmatch(r"codex/web-assets-[0-9]+-[0-9]+", branch):
        raise ValueError("只允许本工作流的临时 Web 分支")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("非法提交 SHA")
    if git("rev-parse", "HEAD") != commit:
        raise ValueError("本地 HEAD 与待发布提交不一致")
    ref = "refs/heads/" + branch
    remote = git("ls-remote", "origin", ref).split()
    if not remote or remote[0] != commit:
        raise ValueError("临时分支已变化，停止发布且不删除该分支")
    run = None
    completed = False
    try:
        api.request(f"/actions/workflows/{WORKFLOW}/dispatches", {"ref": branch})
        query = urlencode({"branch": branch, "event": "workflow_dispatch", "per_page": 20})
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            runs = api.request(f"/actions/workflows/{WORKFLOW}/runs?{query}")["workflow_runs"]
            run = next((item for item in runs if item["head_sha"] == commit
                        and item["head_branch"] == branch
                        and item["event"] == "workflow_dispatch"), None)
            if run and run["status"] == "completed":
                completed = True
                if run["conclusion"] != "success":
                    raise RuntimeError(f"版本校验未通过，main 未更新：{run['html_url']}")
                # 普通快进推送；校验后若 main 又变化，Git 会拒绝，需在最新 main 重跑。
                git("push", "origin", f"{commit}:refs/heads/main")
                print(f"Web 资源已通过校验并发布：{run['html_url']}")
                return
            time.sleep(5)
        raise RuntimeError("等待版本校验超时，main 未更新")
    finally:
        if run and not completed:
            try:
                api.request(f"/actions/runs/{run['id']}/cancel", {})
            except Exception as error:
                print(f"无法取消本次校验：{error}", file=sys.stderr)
        # 仅删除仍指向本次提交的临时分支，不能覆盖其它人后来推入的内容。
        try:
            git("push", f"--force-with-lease={ref}:{commit}", "origin", f":{ref}")
        except subprocess.CalledProcessError:
            print(f"临时分支清理未完成，请核对 {branch}", file=sys.stderr)


if __name__ == "__main__":
    publish(
        GitHub(os.environ["GITHUB_REPOSITORY"], os.environ.get("GH_TOKEN", "")),
        os.environ["WEB_PUBLISH_BRANCH"], os.environ["WEB_PUBLISH_COMMIT"],
    )
