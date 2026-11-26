## OpenAI 使用帮助

### 提供商介绍

OpenAI API 是 OpenAI 的开发平台，提供 GPT 系列模型。NG 预设使用 Chat Completions 接口。

### 提供哪些模型

- **GPT 通用和推理模型**。适合长文本整理、复杂问答和分析。
- **mini、nano 等轻量型号**。适合日常问答和短文本处理。

**按用量计费。** API 使用独立的账单。账户获赠的 API 额度可在开发平台查看。

[模型目录](https://developers.openai.com/api/docs/models) · [计费说明](https://openai.com/api/pricing/)

### 在哪里注册和获取 API Key

1. 注册或登录 OpenAI 开发平台，选择要使用的项目。[打开控制台](https://platform.openai.com/)
2. 创建并复制该项目的 API Key，按提示配置 API 账单。[获取 API Key](https://platform.openai.com/api-keys)

### 怎么在 NG 中使用

1. 把密钥粘贴到「API Key」，使用预填的 API 地址和路径。
2. 打开「启用 AI」，点击「测试连通性」，确认能读取模型列表。
3. 切到下方「模型」页，下拉刷新并启用文本对话模型。
4. 在「AI 设置 → AI助理 → 聊天模型」选择已启用的模型。

### 查看 API 额度

在 OpenAI 提供商配置中点击「查看额度」，登录开发平台后选择领取额度的 API 组织，查看剩余额度和到期时间。 [OpenAI 额度明细](https://platform.openai.com/settings/organization/billing/credit-grants)
