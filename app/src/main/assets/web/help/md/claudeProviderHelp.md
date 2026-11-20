## Claude 使用帮助

### 提供商介绍

Claude API 由 Anthropic 提供，可用于文本总结、写作和推理。NG 预设使用 Anthropic 官方接口。

### 提供哪些模型

- **Opus**。适合复杂推理和长文档分析。
- **Sonnet**。兼顾处理能力和响应速度，适合日常文本任务。
- **Haiku**。适合短文本和需要较快响应的任务。

**按用量计费。** Claude API 使用独立的余额和账单，可在控制台查看用量及额度。

[模型目录](https://platform.claude.com/docs/en/models/overview) · [计费说明](https://platform.claude.com/docs/en/about-claude/pricing)

### 在哪里注册和获取 API Key

1. 注册或登录 Claude Platform，选择要使用的工作区。[打开控制台](https://platform.claude.com/)
2. 创建并复制该工作区的 API Key，按提示配置 API 账单。[获取 API Key](https://platform.claude.com/settings/keys)

### 怎么在 NG 中使用

1. 把密钥粘贴到「API Key」，使用预填的 API 地址和路径。
2. 打开「启用 AI」，点击「测试连通性」，确认能读取模型列表。
3. 切到下方「模型」页，下拉刷新并启用文本对话模型。
4. 在「AI 设置 → AI净化 → 净化模型」或「AI 设置 → AI听书 → 分镜模型」选择已启用的模型。AI助理目前使用 OpenAI 兼容接口。
