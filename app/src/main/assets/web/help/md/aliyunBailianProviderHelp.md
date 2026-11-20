## 阿里云百炼使用帮助

### 提供商介绍

百炼是阿里云的大模型服务平台，提供通义千问和第三方模型。NG 预设使用华北2（北京）地域的 OpenAI 兼容接口。

### 提供哪些模型

- **通义千问（Qwen）系列**。提供文本对话、推理和视觉型号。
- **DeepSeek 等第三方模型**。可在百炼模型目录查看具体型号和可用地域。

**部分模型有新用户试用额度。** 额度限华北2（北京）地域，并有有效期。用完后继续调用按量计费，可在控制台开启「免费额度用完即停」。

[模型目录](https://help.aliyun.com/zh/model-studio/models) · [试用额度](https://help.aliyun.com/zh/model-studio/new-free-quota)

### 在哪里注册和获取 API Key

1. 注册或登录阿里云，完成实名认证并开通百炼。[打开控制台](https://bailian.console.aliyun.com/)
2. 在华北2（北京）地域选择业务空间，创建并复制 API Key。[获取 API Key](https://bailian.console.aliyun.com/cn-beijing/model/settings/api-key)

### 怎么在 NG 中使用

1. 把密钥粘贴到「API Key」，使用预填的 API 地址和路径。
2. 打开「启用 AI」，点击「测试连通性」，确认能读取模型列表。
3. 切到下方「模型」页，下拉刷新并启用文本对话模型。
4. 在「AI 设置 → AI助理 → 聊天模型」选择已启用的模型。
