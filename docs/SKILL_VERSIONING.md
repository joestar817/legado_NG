# 内置 Skill 版本维护

单文件资源 `app/src/main/assets/skills/*.md` 的 frontmatter 必须包含唯一的 `id` 和 `version`。
`version` 使用 `1..2147483647` 的递增整数。修改正文、提示词或其它 frontmatter 字段时，
版本必须大于目标分支中的同一 Skill；版本也不能回退。测试不固定具体版本号。

校验按 App 归一化后的 Skill ID 比对，重命名文件不能绕过检查。新 Skill 必须有合法版本；
文件换行格式和 App 会去除的首尾空白不算有效内容变化。目录包中的 `SKILL.md` 和引用资源
继续使用现有包哈希机制，不由这个单文件版本门禁判断升级。

不要直接改用 12／14 位时间戳：App 当前用 `toIntOrNull()` 读取版本，超出整数范围不会触发正常升级。
本规则不改变 App 对用户修改状态、数据库副本或系统工作流的处理。

## 本地检查

Windows PowerShell：

```powershell
python -X utf8 -m unittest discover -s scripts -p 'test_skill_versions.py' -v
python -X utf8 scripts/test_character_card_skill.py
python -X utf8 scripts/check_skill_versions.py --base-ref origin/main
```

WSL 使用同样的参数，将 `python` 换为 `python3`。默认检查工作区内容；检查已提交的候选时增加
`--head-ref HEAD`。比较前需同步目标分支；不要用最初分叉点代替最新目标分支。

## CI 与并发 PR

`Skill Versions` 工作流独立运行 Python 检查，不构建 APK，也不需要额外 Python 依赖。
PR 和合并队列检查 checkout 获取的最新目标分支，并要求候选提交已包含该基线；
`main` 的 push 检查本次推送前后两个提交。普通 PR 不按路径跳过，便于作为固定的 required check。

需要在仓库规则中把 **Skill version gate** 设为 required，并启用
**Require branches to be up to date before merging**，或使用配置了同一检查的合并队列。
工作流文件不会自行修改仓库规则：仅增加 CI，不能阻止维护者绕过检查，或使用目标分支变化前的旧通过结果。

例如两个 PR 都从版本 29 升到 30：第一个合并后，第二个需更新目标分支并重跑；
若合并结果改变了该 Skill 的内容，版本必须升到至少 31。
