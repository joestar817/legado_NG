## OpenCode 使用帮助

### 提供商介绍

OpenCode Console 汇集了多家厂商的模型。NG 预设接入 OpenCode Go。

### 提供哪些模型

- **Kimi、GLM、Qwen 等系列**。提供文本对话、推理和代码模型。
- **DeepSeek、MiMo 等系列**。Go 模型清单列出了各系列可用的型号。

**Go 采用订阅制。** 套餐内的模型共用额度。Go 面向编程客户端，第三方调用受平台规则限制。

[模型与计费](https://opencode.ai/docs/console/go/)

### 在哪里注册和获取 API Key

1. 登录 OpenCode Console，选择工作区并订阅 Go。部分型号需在控制台单独启用。[打开控制台](https://opencode.ai/console)
2. 创建并复制该工作区的 API Key。[获取 API Key](https://opencode.ai/docs/console/go/#how-it-works)

### 怎么在 NG 中使用

1. 把密钥粘贴到「API Key」，使用预填的 API 地址和路径。
2. 打开「启用 AI」，点击「测试连通性」，确认能读取模型列表。
3. 切到下方「模型」页，下拉刷新并启用文本对话模型。
4. 在「AI 设置 → AI助理 → 聊天模型」选择已启用的模型。
