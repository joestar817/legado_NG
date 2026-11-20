## Gemini 使用帮助

### 提供商介绍

Gemini API 由 Google 提供，可通过 Google AI Studio 获取密钥。NG 预设使用 Google 官方 Gemini 接口。

### 提供哪些模型

- **Gemini Pro 系列**。适合复杂文本分析和推理。
- **Gemini Flash、Flash-Lite 系列**。适合日常问答和高频文本处理，部分型号支持图片输入。

**部分型号有免费档。** Gemini 3.8 Flash 的标准调用提供免费档，受项目用量限制。

[模型与计费](https://aistudio.google.com/docs/pricing)

### 在哪里注册和获取 API Key

1. 用 Google 账号登录 Google AI Studio，进入要使用的项目。[打开控制台](https://aistudio.google.com/)
2. 在 API Keys 页面创建或复制该项目的 API Key。[获取 API Key](https://aistudio.google.com/api-keys)

### 怎么在 NG 中使用

1. 把密钥粘贴到「API Key」，使用预填的 API 地址和路径。
2. 打开「启用 AI」，点击「测试连通性」，确认能读取模型列表。
3. 切到下方「模型」页，下拉刷新并启用文本对话模型。
4. 在「AI 设置 → AI净化 → 净化模型」或「AI 设置 → AI听书 → 分镜模型」选择已启用的模型。AI助理目前使用 OpenAI 兼容接口。
