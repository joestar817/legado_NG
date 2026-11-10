# 阅读 NG 内容主题

这里存放独立分发的普通主题源文件，不参与 APK 打包。夏日童趣、秋山书意、绘本书屋均使用原有素材字节，保留完整日夜配色、背景、栏配置和抽屉配置。

APP 的离线默认外观为内置“多色光场”；柔光渐变、墨水仍作为基础显示模式保留。未导入任何内容主题时，APP 也可以正常使用。

## 生成 ZIP

在仓库根目录运行（WSL 或已配置 Python 的 PowerShell 均可）：

```sh
python scripts/package_content_themes.py
```

产物位于 `build/outputs/content-themes/`，同时生成包含大小与 SHA-256 的 `catalog.json`。脚本使用 Python 标准库，固定 ZIP 元数据；相同源文件可重复生成相同字节的 ZIP，不重新编码或压缩画质。

在“外观设置 → 主题管理 → 菜单 → 导入主题”选择 ZIP，即可通过现有本地主题安装流程导入、应用、编辑、导出和删除。资源中心使用相同完整 ZIP 发布；发布前将 catalog.json 的文件大小及哈希同步到资源目录。

普通主题采用 `reading-ng-theme` v1，主题清单与资源放在同一个 ZIP 中，不引用 APP 内的图片。主题版本和作者来自真实发布清单；本地编辑会遵循现有主题保存语义。特效主题仍使用 `plugins/EFFECT_THEMES.md` 中的 v2 代码插件协议。
