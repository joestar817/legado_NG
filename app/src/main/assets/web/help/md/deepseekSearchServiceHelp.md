## DeepSeek 搜索服务帮助

### 提供商介绍

DeepSeek 提供独立的 Search 和 Fetch API。搜索返回网页链接和摘要，抓取按链接返回 Markdown 正文。

### 费用和免费额度

- **搜索和抓取**各为 **5 元／1,000 次成功请求**。搜索返回结果或抓取返回正文时计费。
- 官方暂未公布独立接口的固定免费额度。赠送余额和可用余额可在开放平台查看。
- 用 DeepSeek 模型的内置联网时，搜索和抓取工具本身免费，按模型 Token 用量计费。

[网页搜索与抓取计费](https://api-docs.deepseek.com/zh-cn/guides/search/#计费)

### 在哪里注册和获取 API Key

1. 注册或登录 [DeepSeek 开放平台](https://platform.deepseek.com/)。
2. 在 [API Keys](https://platform.deepseek.com/api_keys) 创建并复制密钥。已有 DeepSeek 官方 API Key 可以直接复用。

### 怎么在 NG 中使用

1. 把密钥粘贴到「API Key」，打开「启用服务」。
2. 在「测试服务」选「网页搜索」输入关键字，或选「网页抓取」粘贴链接，点箭头查看结果。
3. 在「AI 设置 → AI助理」将「联网方式」设为「指定外置服务」，并在「外置搜索服务」选 DeepSeek。选择支持工具调用的聊天模型，打开输入框的联网开关。
