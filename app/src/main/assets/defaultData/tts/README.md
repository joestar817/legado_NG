# TTS JS 模板

默认朗读脚本以单个 `.js` 文件维护，头部 `// @...` 注释声明导入元信息。

App 主动调用的协议函数只有：

- `options()`
- `voices(options, ctx)`
- `synthesize(text, voice, params, options, ctx)`

头部建议字段：

- `@schema`：脚本协议版本，当前为 `1`。
- `@uuid`：稳定唯一 id。
- `@name`：引擎名称。
- `@enabled`：默认是否启用。
- `@cookieJar`：是否启用内部 CookieJar。
- `@audioType`：默认音频 MIME 类型，旧 `@contentType` 仍兼容。

`options()` 支持 `text/password/number/select/boolean`。`select.values` 可以是字符串数组，也可以是 `{ label, value }` 数组，保存时只保存 `value`。

`voices(options, ctx)` 必须返回标准发音人数组。App 不解析服务商私有响应格式；如果远端接口返回 `catalog`、map、嵌套对象或其它结构，脚本需要先在 `voices()` 内转换成标准数组再返回。发音人必填字段是 `id/name`，可选字段是 `language/gender/style/tags/sample_text/extra`，其中 `extra` 会原样传回 `synthesize()`。

注意：`java.ajax()` 等 Java 侧能力返回到 Rhino 后，不要依赖 `typeof value === "string"` 判断。需要解析 JSON 时建议先写 `JSON.parse(String(value || "{}"))`。

`synthesize()` 可以返回字符串 URL，也可以返回对象：

- `url`：请求地址。
- `method`：请求方法。
- `headers`：请求头对象。
- `body`：请求体。
- `requestContentType`：请求体 MIME 类型。
- `audioContentType`：返回音频 MIME 类型。
- `responseType`：`audio` 或 `json`。
- `audioExtract`：JSON 返回体里的音频字段路径，例如 `$.data.audio`。
- `audioEncoding`：`base64` 或 `url`。
- `timeout`：单次请求超时秒数。
- `retry`：简单重试次数。

其它认证、签名、测试、token 刷新等函数由脚本作者自由定义，并在协议函数内部自行调用。
