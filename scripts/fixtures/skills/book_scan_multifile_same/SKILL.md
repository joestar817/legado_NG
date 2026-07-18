---
name: book-scan
description: 对当前书籍进行快速定位、连续正文扫描和基于人物关系与读者代入的风险分析；用户要求打开 AI 扫书、继续扫书或分析现有扫书档案时使用。
metadata:
  id: book_scan
  version: pilot-b
  display_name: AI 扫书
  output_contract: output.contract.json
---

# AI 扫书

这是一个按需加载的多文件 Skill。只读取完成当前用户动作所需的资源，不要预读其它文件。

## 路由

- 用户首次打开扫书、当前书没有 `domain=book_scan` 的 manifest 记忆：必须读取 [首次快速定位](quick_scan.md)，然后完全按该文件执行。
- 用户选择继续扫描：读取 [连续完整扫描](full_scan.md)。
- 用户要求核对详细术语：读取 [风险术语](risk_terms.md)。
- 用户选择感情分析：读取 [感情分析](analysis/relationship.md)。
- 用户选择结局分析：读取 [结局分析](analysis/ending.md)。

当前请求命中某一路由后，不要加载其它资源。资源路径只能复制上面的 Markdown 链接，不得猜测。
