## Xiaomi MiMo 使用帮助

### 提供商介绍

Xiaomi MiMo 是小米的大模型开放平台，提供文本、视觉和语音模型。NG 预设使用按量计费的接口。

### 提供哪些模型

- **MiMo V2.6 Flash**（`mimo-v2.6-flash`）。适合日常问答和高频调用。
- **MiMo V2.6 Pro**（`mimo-v2.6-pro`）。适合复杂推理和长文本处理。

**按用量计费。** 套餐用户可在 Token Plan 页面查看专用地址和密钥。

[模型目录](https://mimo.mi.com/docs/zh-CN/quick-start/summary/model) · [计费说明](https://mimo.mi.com/docs/zh-CN/price/pay-as-you-go)

### 在哪里注册和获取 API Key

1. 用小米账号注册或登录 Xiaomi MiMo API 开放平台。[打开控制台](https://platform.xiaomimimo.com/)
2. 在 API Keys 页面创建并复制按量计费使用的普通 API Key。[获取 API Key](https://platform.xiaomimimo.com/#/console/api-keys)

### 怎么在 NG 中使用

1. 把密钥粘贴到「API Key」，使用预填的 API 地址和路径。
2. 打开「启用 AI」，点击「测试连通性」，确认能读取模型列表。
3. 切到下方「模型」页，下拉刷新并启用文本对话模型。
4. 在「AI 设置 → AI助理 → 聊天模型」选择已启用的模型。
