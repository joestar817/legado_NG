## 豆包搜索服务帮助

### 提供商介绍

豆包搜索是火山引擎的网页检索服务。**Custom** 是 NG 默认模式，侧重较低的搜索延迟。**Global** 覆盖全球站点。两种模式均可配合 Fetch 读取网页正文。

### 费用和免费额度

- **每个火山引擎账号每月 500 次免费调用**，每月 1 日重置。Custom、Global 和 Fetch 共用这 500 次。
- 超出额度后，已开通按量后付费的 **Custom 和 Global 网页搜索均为 0.020 元／次**，**Fetch 为 0.003 元／次**。如需付费抓取，在控制台开通 Fetch 按量后付费。

[计费详情](https://docs.volcengine.com/docs/networked-search/online-search-product-billing?lang=zh) · [Fetch 免费额度](https://docs.volcengine.com/docs/networked-search/doubao-search-web-analysis-fetch?lang=zh)

### 在哪里注册和获取 API Key

1. 注册或登录火山引擎，进入 [豆包搜索的 API Key 管理](https://console.volcengine.com/search-infinity/api-key?tab=post_paid)。
2. 在「按量后付费」页创建并复制密钥。Global 和网页抓取都使用这个类型的密钥。

### 怎么在 NG 中使用

1. 把密钥粘贴到「API Key（按量后付费）」，选择搜索模式并打开「启用服务」。
2. 在「测试服务」选「网页搜索」输入关键字，或选「网页抓取」粘贴链接，点箭头查看结果。
3. 在「AI 设置 → AI助理」将「联网方式」设为「指定外置服务」，并在「外置搜索服务」选豆包搜索。选择支持工具调用的聊天模型，打开输入框的联网开关。
