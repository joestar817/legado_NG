## ChatGPT 使用帮助

### Sign in with ChatGPT 是什么

这是 OpenAI 提供的账号登录方式。登录后，你可以单独授权阅读 NG 使用你的 ChatGPT 套餐。OpenAI 的开源接入指南面向符合资格的 Plus 和 Pro 账号，可用权限取决于你选择的账号和工作区。[官方接入说明](https://developers.openai.com/siwc/token-sharing-open-source)

这项授权不会让阅读 NG 读到你在 ChatGPT 网页或 App 中的聊天记录和记忆。使用套餐无需填写 API Key，OpenAI API 的开发者账单独立计算。[官方授权说明](https://help.openai.com/en/articles/20001542-using-your-chatgpt-plan-in-other-apps-and-sites) · [API 账单说明](https://help.openai.com/en/articles/9039756-managing-billing-for-chatgpt-and-the-api-platform)

### 怎么在 NG 中使用

1. 点击「使用 ChatGPT 登录」，在浏览器中选择账号并同意使用套餐，然后返回阅读 NG。若已经登录但尚未同意，点击「授权使用套餐」。
2. 打开「启用 AI」，切到下方「模型」页，刷新列表并启用想用的模型。
3. 在「AI 设置 → AI助理 → 聊天模型」选择已启用的模型。

### 能用什么，有哪些限制

- 模型列表随账号和工作区变化。阅读 NG 支持文字对话，选用支持图像输入的模型时可以看图。需要搜索网页时，在 AI 助理开启联网，并选用支持内置联网的模型。[模型和接口说明](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- 套餐接入处于官方预览阶段，回复通过流式接口返回。目前不支持音频和视频输入，也不开放 OpenAI 托管的生图、文件搜索、代码解释器、MCP 和电脑操作工具。NG 内置 MCP 可在支持工具调用的模型上使用。[官方预览限制](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)

### 查看用量和恢复授权

- 点击「查看用量」进入 ChatGPT 官方网页，查看账号的套餐限额、重置时间和 Credits 余额。应用也可能有自己的限额。Credits 是否可用取决于账号、余额和你对该应用的授权。[官方用量说明](https://help.openai.com/en/articles/20001542-using-your-chatgpt-plan-in-other-apps-and-sites)
- 如果页面显示「重新授权」，按提示在浏览器完成授权。[官方异常说明](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
