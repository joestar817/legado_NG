## Tavily 搜索服务帮助

### 提供商介绍

Tavily 提供面向 AI 助理的网页搜索和正文提取。NG 可将搜索深度设为 **Basic** 或 **Advanced**，默认使用 Basic。网页抓取使用 Basic Extract。

### 费用和免费额度

- **每月 1,000 免费积分**，注册免费方案无需绑定信用卡。
- Basic 搜索每次 **1 积分**，Advanced 搜索每次 **2 积分**。Basic Extract 每成功提取 5 个网址计 **1 积分**。
- 按量付费价格为 **0.008 美元／积分**。

[积分与计费详情](https://docs.tavily.com/documentation/api-credits)

### 在哪里注册和获取 API Key

1. 注册或登录 [Tavily 控制台](https://app.tavily.com/home)。
2. 在控制台的 API Keys 区域创建并复制密钥。

### 怎么在 NG 中使用

1. 把密钥粘贴到「API Key」，按需调整搜索深度，打开「启用服务」。
2. 在「测试服务」选「网页搜索」输入关键字，或选「网页抓取」粘贴链接，点箭头查看结果。
3. 在「AI 设置 → AI助理」将「联网方式」设为「指定外置服务」，并在「外置搜索服务」选 Tavily。选择支持工具调用的聊天模型，打开输入框的联网开关。
