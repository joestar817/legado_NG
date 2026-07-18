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
- `@concurrentRate`：请求频率限制，沿用阅读现有的“间隔毫秒”或“次数/毫秒”格式。
- `@maxConcurrency`：单引擎最大在途合成数。`0` 或省略时跟随听书全局 Worker 数；多人 TTS 中绑定到同一引擎的不同角色和音色共享该上限。
- `@audioType`：默认音频 MIME 类型，旧 `@contentType` 仍兼容。
- `@sampleText`：该脚本的默认试听文本。发音人未提供 `sample_text` 时，试听优先使用这里的文本，再回落到 App 内置默认文本。

`options()` 支持 `text/password/number/select/boolean`。`select.values` 可以是字符串数组，也可以是 `{ label, value }` 数组，保存时只保存 `value`。

`voices(options, ctx)` 必须返回标准发音人数组。App 不解析服务商私有响应格式；如果远端接口返回 `catalog`、map、嵌套对象或其它结构，脚本需要先在 `voices()` 内转换成标准数组再返回。发音人必填字段是 `id/name`，可选字段是 `language/gender/style/tags/sample_text/extra`，其中 `extra` 会原样传回 `synthesize()`。

如果发音人支持风格，建议在 `extra.styles` 中放置数组：

```json
[
  { "id": "angry", "name": "愤怒", "value": "angry" },
  { "id": "calm", "name": "平静", "value": "calm" }
]
```

用户在试听时选择风格后，App 会在传给 `synthesize()` 的 `voice` 对象里附加 `style_id`、`style_value`、`style_tag` 和 `selected_style`。脚本需要合成风格参数时优先读取 `voice.style_value` 或 `voice.selected_style.value`；未选择时这些字段为空。

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

HTTP 分支接收直接音频响应，或 JSON 中的完整 Base64/音频 URL。普通 HTTP body 本身可以随网络读取；Chat Completions SSE 需要使用下方的 `transport: "sse"` 增量解码协议，不能只在请求体里设置 `stream: true`。

## SSE / Chat Completions PCM 分片

SSE 接口由 `stream` 描述事件格式。下面示例从 OpenAI-compatible Chat Completions 的 `choices[0].delta.audio.data` 提取 Base64 PCM16 分片，并在输出开头自动增加流式 WAV 头，使播放器可以在总长度未知时开始解码：

```json
{
  "transport": "sse",
  "url": "https://example.com/v1/chat/completions",
  "method": "POST",
  "headers": { "Authorization": "Bearer ..." },
  "requestContentType": "application/json",
  "body": "{\"stream\":true}",
  "audioContentType": "audio/pcm",
  "timeout": 60,
  "stream": {
    "dataPrefix": "data:",
    "doneData": "[DONE]",
    "finishOnEof": true,
    "maxAudioBytes": 33554432,
    "pcm": {
      "sampleRate": 24000,
      "channels": 1,
      "bitsPerSample": 16
    },
    "textRules": [
      {
        "matchPath": "choices[0].delta.audio.data",
        "audioPath": "choices[0].delta.audio.data",
        "audioEncoding": "base64"
      },
      {
        "matchPath": "error.message",
        "errorPath": "error.message"
      }
    ]
  }
}
```

`dataPrefix` 默认是标准 SSE 的 `data:`，`doneData` 默认 `[DONE]`。不返回裸 PCM 时可省略 `pcm`，并把 `audioContentType` 设置为实际分片格式，例如 `audio/mpeg`。

## WebSocket 音频

需要多阶段 WebSocket 会话的脚本，可让 `synthesize()` 返回 `transport: "websocket"`，并通过 `websocket` 描述会话：

```json
{
  "transport": "websocket",
  "url": "wss://example.com/tts",
  "headers": { "Origin": "https://example.com" },
  "audioContentType": "audio/mpeg",
  "timeout": 60,
  "websocket": {
    "openMessages": ["{\"event\":\"StartTask\"}"],
    "binaryAudio": true,
    "finishOnClose": false,
    "connectTimeout": 15,
    "firstAudioTimeout": 15,
    "idleTimeout": 15,
    "finishGrace": 300,
    "maxAudioBytes": 33554432,
    "textRules": [
      {
        "matchPath": "event",
        "equals": "TaskStarted",
        "sendMessages": ["{\"payload\":\"...\"}"]
      },
      {
        "matchPath": "type",
        "equals": "3",
        "audioPath": "buffer",
        "audioEncoding": "base64"
      },
      {
        "matchPath": "event",
        "equals": "TaskFinished",
        "finish": true
      },
      {
        "matchPath": "status_code",
        "notEquals": "20000000",
        "errorPath": "status_text"
      }
    ]
  }
}
```

`openMessages` 和 `sendMessages` 中的元素可以是字符串或 JSON 对象；对象会在发送前序列化。`binaryAudio` 表示二进制帧直接作为音频，文本帧中的 Base64 音频使用 `audioPath/audioEncoding` 提取。`finishGrace` 用于接收完成事件之后可能延迟到达的尾部音频帧。

SSE 和 WebSocket 音频分片可以通过 V2 的阻塞音频流增量交给调用方；需要完整结果的调用仍会汇总全部分片，以保持缓存、试听和旧调用方兼容。这里描述的是上游 TTS 传输与引擎输出，不决定播放器是否直接消费未完成音频。WebSocket 返回裸 PCM 时，也可以在 `websocket` 内使用与 SSE 相同的 `pcm` 配置。
