# 模型系列版本核对与内部支持清单

核对日：2026-09-25；新版本窗口：2026-06-25～2026-09-25。基线是 `AiModelRegistry.ALL_MODELS` 中的**全部命名系列和通用类型规则**，不是只挑新发布的热门聊天模型。逐系列检索厂商官方模型目录、更新记录或开源仓库；没有查到可确认的新版本时写明“未确认”，不把搜索不到当作不存在。

**状态含义**：下表的“旧规则/缺口”记录本轮改动前的基线；本轮已补能力规则见后文“实施状态”。Registry 标签、厂商 API 能力和 Android 实际往返是三层证据。不同平台转售同一模型时，ID 和参数以实际 Provider 为准。未使用用户 API Key 进行付费请求。

## 全系列核对

| Registry 基线系列 | 窗口内核实的新版本或升级 | 当前规则与接入结论 | 官方依据 |
| --- | --- | --- | --- |
| OpenAI GPT-4o/4.1、o 系列、GPT-OSS | 在本窗口未从官方 API 模型目录确认这些支线的新代际；保留现有规则 | 逐支线已核对；不要把 GPT-6 归入 GPT-5 规则 | [OpenAI API 模型目录](https://developers.openai.com/api/docs/models) |
| OpenAI GPT-5 系列 | 07-09 `gpt-5.6-sol` / `terra` / `luna`；09-03 `gpt-6-astra`；09-22 `gpt-6-sol` / `luna` | 5.6 被旧规则命中；6 系列无规则。GPT-6 Astra 的工具调用要求 Responses；Sol/Luna 在 Chat Completions 下仅 `reasoning_effort=none` 可调用函数，不能直接打开现有工具+推理组合 | [API 更新](https://developers.openai.com/api/docs/changelog)、[GPT-6 指导](https://developers.openai.com/api/docs/guides/latest-model) |
| Google Gemini 文本/视觉 | 07-21 `gemini-3.6-flash`、`gemini-3.5-flash-lite`；08-13 `gemini-3.7-flash`；09-02 `gemini-3.8-flash` | `gemini-3-*-flash` 会被旧 `GEMINI_3_FLASH` 子序列规则命中，3.5 也可命中本身规则；非“完全未识别”。原生 Gemini Provider 尚未进入现有 AI 聊天工具循环，思考签名需单独往返验证 | [Gemini 更新](https://ai.google.dev/gemini-api/docs/changelog)、[函数调用](https://ai.google.dev/gemini-api/docs/function-calling) |
| Google Gemini 图像 | 本窗口未核实新的 Gemini 图像生成主型号；现有 `gemini-3.1-flash-image` 于 05-28 GA | 现有图像规则保留；不能把 3.8 Flash 文本模型当作图像生成模型 | [Gemini 更新](https://ai.google.dev/gemini-api/docs/changelog) |
| Anthropic Claude | 06-30 `claude-sonnet-5`；07-24 `claude-opus-5`；09-01 `claude-fable-5-1`；09-22 `claude-opus-5-5` | 新 5 系列无规则。当前 AI 聊天只接受 OpenAI 兼容 Provider；原生 Claude 的自适应思考、强制工具限制和温度限制需独立适配 | [Claude 更新](https://platform.claude.com/docs/en/release-notes/overview)、[当前模型](https://platform.claude.com/docs/en/models/overview) |
| DeepSeek V/R 与官方 API | 07-31 V4 Flash 升级；08-13 V4 Pro GA；08-21 V4 Flash Vision Exp；09-10 V4.1 Flash，官方新 ID `deepseek-flash` | `DeepSeek-V4.1-Flash` 可被旧 V4 Flash 子序列规则命中；官方 `deepseek-flash` **不命中**。旧 `deepseek-v4-flash` 临时转发到 V4.1；V4 Pro 官方继续提供。截图缺 T/R 仍需核对保存条目 | [DeepSeek 更新](https://api-docs.deepseek.com/updates/)、[思考与工具](https://api-docs.deepseek.com/guides/thinking_mode/) |
| DeepSeek OCR | `DeepSeek-OCR-2` 发布于 2026-01，本窗口未确认更高版本 | 现有通用 OCR 规则仍只做视觉文本分类；OCR 开源权重不等于 DeepSeek 对话 API 可选 ID | [官方模型库](https://huggingface.co/deepseek-ai/DeepSeek-OCR-2) |
| PaddleOCR-VL | 现行官方版本 `PaddleOCR-VL-1.6` 于 05-28 发布；本窗口未核实后继代际 | 现有 `PADDLE_OCR_MODEL` 可按名称归类视觉文本，但文档解析流水线不能据此视为 App 已接入 | [PaddleOCR 官方版本记录](https://github.com/PaddlePaddle/PaddleOCR/releases) |
| Qwen 通用文本、VL、Omni | 07-21 `qwen3.7-flash`；08-02 `qwen3.8-max`，08-26 `qwen3.8-flash`，09-02 `qwen3.8-max-0902`；09-21 `qwen3.8-omni-flash-realtime` | 3.7 被旧规则命中；3.8 被泛化 `QWEN_3` 命中工具/推理但**漏视觉**；Omni Realtime 是独立实时协议，不能当普通聊天模型 | [百炼模型上架](https://help.aliyun.com/zh/model-studio/newly-released-models)、[文本能力表](https://help.aliyun.com/zh/model-studio/text-generation-model) |
| Qwen Embedding/Reranker/MT | 09-01 `qwen3.7-text-embedding-flash`、`qwen3.7-text-rerank`；08-28 `qwen-mt-image-2.0`。文本 `qwen-mt` 未确认窗口内新版本 | embedding 新 ID 会同时命中 `QWEN_3_7` 和 embedding 规则，得出向量类型却可能混入 T/R；`qwen3.7-text-rerank` 用 `rerank` 而非 `reranker`，可能被错分为聊天；`qwen-mt-image-2.0` 也不能归入文本聊天 | [百炼模型上架](https://help.aliyun.com/zh/model-studio/newly-released-models) |
| 豆包 Doubao / Seed OSS | 06-23 Seed 2.1 系列发布；09-15 快照 `doubao-seed-2-1-pro-260915`、`lite-260915` | Registry 仅有 Doubao 1.6/1.8 与 `seed-oss`，2.1 无专属规则；Seed2.1 的工具、思考、视觉须按火山方舟具体 ID/接口核对 | [Seed 官方发布](https://seed.bytedance.com/en/blog/seed2-1-officially-released-advancing-ai-productivity)、[方舟模型公告](https://docs.volcengine.com/docs/ark/model-release-announcement?lang=zh) |
| xAI Grok | 07-08 Grok 4.5、08-12 4.6、09-21 `grok-4.7` | 4.5～4.7 会命中旧 `GROK_4` 的 T/R/V 标签；需验证所选 xAI 网关的 Chat Completions/Responses 与推理参数，不能据标签认定运行完成 | [xAI 更新](https://docs.x.ai/developers/release-notes) |
| Kimi | 07-16 `kimi-k3`；09-11 K2.8 Preview 更新 `kimi-for-coding`（Kimi Code 渠道，ID 不变）；06-12 K2.7 Code 在窗口外 | K3、`kimi-for-coding` 均无专属规则；K2.8 在部分转售平台用不同 ID，不能把渠道别名直接当月之暗面通用 API ID | [K3 官方发布](https://www.kimi.com/en/blog/kimi-k3)、[Kimi Code 模型目录](https://www.kimi.com/code/docs/en/kimi-code/models.html) |
| Step | 官方开放平台现列 Step 5 Preview；百炼 09-21 上架 `stepfun/step-5-preview` | 旧规则只到 Step 3/3.7 Flash；新模型无专属规则。原厂 API ID 与百炼转售 ID 要分开核实 | [Step 官方平台](https://platform.stepfun.com/)、[百炼上架](https://help.aliyun.com/zh/model-studio/newly-released-models) |
| Intern-S | 07 月 Intern-S2-Preview-397B；09 月正式 `Intern-S2-397B` | 旧规则只认 Intern-S1；S2 无规则。开源权重已核实，所用 API 服务、工具协议另核对 | [上海人工智能实验室](https://www.shlab.org.cn/news/5444298)、[官方仓库](https://github.com/InternLM/Intern-S1) |
| GLM / Z.ai | 08 月 GLM-5.3；09 月 `glm-5.3-flash` | 泛化 `GLM_5` 能给工具/推理，但 Flash **漏图像输入**；不同 Provider 的 `Flash` / `FlashX` ID 不等价 | [智谱模型说明](https://autoclaw.z.ai/models/)、[Flash 官方说明](https://autoclaw.z.ai/blog/model/glm-5.3-flash/) |
| Nex-N | 09 月 Nex-N2.5 mini / Pro / Max | 泛化 `NEX_N2` 命中但未区分 mini/Pro 的视觉与 Max 的纯文本；需按实际托管 API ID 核对 | [Nex 官方仓库](https://github.com/nex-agi/Nex-N2.5)、[模型卡](https://huggingface.co/nex-agi/Nex-N2.5-Max) |
| MiniMax 文本 | M3 于 06-01 发布，已在 Registry；本窗口未从官方目录确认 M4 等更新聊天型号 | `MINIMAX_M3` 规则现有；勿把 07-31 H3 视频生成模型标为聊天模型 | [MiniMax 研究目录](https://www.minimax.io/blog)、[M3 API](https://www.minimax.io/models/text/m3) |
| Ling | 07～09 月出现 `Ling-3.0-flash`、`Ling-3.0-flash-VL` | 旧规则只认 Ling 2 的 flash/mini；3.0 无规则，VL 另需视觉输入和接口验证 | [Ling 官方模型目录](https://developer.ant-ling.com/en/docs/models/ling)、[官方快速接入](https://developer.ant-ling.com/en/docs/getting-started/quickstart/) |
| 腾讯混元 Hunyuan/Hy | 08-28 `hy4-preview`；TokenHub 另列 `Hy-MT2-Pro`、`Hy-Image-3.5-preview` | Registry 仅 `hunyuan-a13b` / `hunyuan-mt`；`hy4-preview` 和 `Hy-MT2-Pro` 不命中。旧腾讯云平台正迁至 TokenHub，须核对新 Base URL/ID | [腾讯 Hy4 仓库](https://github.com/Tencent-Hunyuan/Hy4-preview)、[TokenHub](https://cloud.tencent.com/product/tokenhub)、[旧平台迁移公告](https://cloud.tencent.com/document/product/1729/131925) |
| 商汤 SenseNova | 08-11 SenseNova 6.8 Flash Lite Preview；现官方页列 `sensenova-6.8-flash-lite`，另有 U1.5 Lite 图像模型 | 默认 Provider 和专属规则仍为 6.7 Flash Lite；6.8、U1.5 Lite 不应复用 6.7 的参数/聊天标签，模型正式可用性以 `/models` 为准 | [商汤发布](https://www.sensetime.com/cn/news/sensenova-6.8-flash-lite-preview)、[官方模型页](https://www.sensenova.cn/models)、[官方 ID 示例](https://www.sensetime.com/cn/news/sensenova-u1-5-lite-token-plan-20260911-1741) |
| 小米 MiMo | **09-22** `mimo-v2.6-pro`、`mimo-v2.6-flash`、`mimo-v2.6-pro-ultraspeed`；V2 系列 06-30 已停止 | 默认仍是 2.5 Pro。2.6 会被泛化 `XIAOMI_MIMO_V2` 误当旧款，得到 T/R 但**漏全模态视觉**；新三 ID 无专属规则，思考/工具参数和历史 `reasoning_content` 应按新版本核对 | [小米模型更新](https://mimo.mi.com/docs/zh-CN/updates/model)、[V2.6 官方发布](https://mimo.mi.com/docs/en-US/news/latest/v2-6) |
| BGE Embedding/Reranker | 本窗口未从官方 FlagEmbedding 目录确认新的 BGE 主型号 | 现有 BGE 类型规则保留；不能据此推出所有新向量模型均可走现有应用接口 | [FlagEmbedding 官方仓库](https://github.com/FlagOpen/FlagEmbedding) |
| Kolors 图像 | 本窗口未从官方 Kolors 仓库确认新的同系列型号 | 保留 `KOLORS_IMAGE`，不把其它厂商生图模型混为 Kolors | [Kolors 官方仓库](https://github.com/Kwai-Kolors/Kolors) |
| Wan 视频 | 08-06 `wan3.0-video`、08-20 `wan3.0-video-prime` | 现有 `Wan2.2-T2V/I2V` 名称规则不覆盖 3.0；3.0 是 All-in-One 异步视频生成接口，不能只补 Registry 视觉标签 | [百炼上架](https://help.aliyun.com/zh/model-studio/newly-released-models)、[Wan3.0 接口](https://help.aliyun.com/zh/model-studio/wan3-video-generation-guide) |
| TeleSpeech ASR、SenseVoice | 本窗口未从两家的官方仓库确认后继命名型号；现有基线分别是 TeleSpeech-ASR 1.0 和 SenseVoiceSmall | 保留现有 ASR 分类规则；其它厂商新的转写服务不能沿用这些权重名称判断 | [TeleSpeech 官方仓库](https://github.com/Tele-AI/TeleSpeech-ASR)、[SenseVoice 官方模型卡](https://huggingface.co/FunAudioLLM/SenseVoiceSmall) |
| CosyVoice、MOSS-TTSD | CosyVoice3 在 2025-12 已发布；MOSS-TTSD v1.0 于 2026-02 发布，本窗口未核实这两条线的新代际。另有 MOSS-TTS v1.5 于 05-26 发布 | 现有 TTS 通用规则只是分类，不包含音频生成接口和音色协议支持 | [CosyVoice 官方仓库](https://github.com/QwenAudio/CosyVoice)、[MOSS-TTSD 官方仓库](https://github.com/OpenMOSS/MOSS-TTSD)、[MOSS-TTS 官方仓库](https://github.com/OpenMOSS/MOSS-TTS) |

`ASR_MODEL`、`TTS_MODEL`、`IMAGE_*`、`PADDLE_OCR_MODEL` 等是**跨厂商名称规则**，没有单一“最新版本”。但现有 Provider 家族确有同期专用新模型：OpenAI `gpt-realtime-2.1` / `gpt-transcribe`，Google `gemini-3.5-transcribe` / `gemini-3.8-flash-tts`，xAI `grok-voice-transcribe-2.0`，阿里云 `qwen-audio-3.1-realtime-plus`，MiniMax `H3` 视频，商汤 U1.5 Lite 生图。它们应进各自的 Realtime、转写、TTS、图像或视频调用链，不能按聊天 T/R 图标接入。[OpenAI API 更新](https://developers.openai.com/api/docs/changelog)、[Gemini 更新](https://ai.google.dev/gemini-api/docs/changelog)、[xAI 更新](https://docs.x.ai/developers/release-notes)、[百炼上架](https://help.aliyun.com/zh/model-studio/newly-released-models)、[MiniMax H3](https://www.minimax.io/blog/minimax-h3)。

## 后续验证与协议事项

1. **DeepSeek**：读取截图对应的设备保存模型条目；验证 `deepseek-flash` 带工具的思考请求和 `role=tool` 第二轮。当前版本会为能力全空的已保存模型补上有证据的 Registry 分类，无需仅为更新标签重新请求厂商列表。
2. **MiMo**：分别验证三款 2.6 的模型列表、思考参数、工具调用回填和图片输入；静态能力标记不能代替 API 往返。
3. **其它 OpenAI 兼容提供商**：Qwen3.8、Doubao2.1、Grok4.7、Kimi K3、Step5、GLM5.3 Flash、Nex-N2.5、Ling3.0、Hy4、SenseNova6.8 仍需逐 Provider 验证请求和返回。
4. **需要新调用链的系列**：GPT-6 Responses 工具条件、原生 Claude/Gemini 聊天工具、Wan3 视频及各家实时语音/图像模型，完成协议适配后才能标为 App 功能可用。

## 实施状态（2026-09-25）

- 已把官方新 ID 和可由现有 `AiModelType` 表达的能力补入 `AiModelRegistry`：DeepSeek Flash/V4.1、MiMo 2.6 三款、GPT-6、Gemini 3.6～3.8、Claude 5、Qwen 3.8 与 3.7 向量/重排、豆包 2.1、Grok 4.7、Kimi K3、Step 5、Intern-S2、GLM-5.3 Flash、Nex-N2.5、Ling 3、Hy4、SenseNova 6.8，以及清单中可明确归类的图像、视频、转写和 TTS 型号。匹配器优先级已有回归测试源码覆盖。
- GPT-6 在当前 Chat Completions 聊天链下只标推理与视觉，不误标可同时使用的工具能力。官方 API 的工具支持仍须按 Responses 或其条件约束接入。
- Qwen 3.8 Omni Realtime、StepAudio Realtime 等需要独立实时协议的型号仍只在清单中记录；当前 `AiModelType` 与请求链不能完整表达其协议，未标为普通聊天已支持。云端模型功能如内置搜索、电脑操作、图片生成也不等于 App 具有对应调用链。
- `git diff --check` 通过。首次串行安装成功后，差异复核发现并修正 `stepfun` 与 `FlashX` 两种完整 token 的匹配遗漏；在前一任务明确退出后串行复装最终源码，流式安装成功，`BUILD SUCCESSFUL in 32s`。新增单元测试源码未按仓库约定另起 Gradle 任务运行；未做付费 API 往返。该批在用户验收后以 `baa7e9f4a` 提交，未推送；用户原有 `docs/READING_NG_UI_STYLE.md` 改动保持原样。

## 设备 MCP 缓存复核（2026-09-25）

通过只读 `ai_model_cache_list` 分页读取设备里 12 个内置提供商保存的全部 **525** 条模型（提供商 ID + 模型 ID 均唯一）。DeepSeek、硅基流动、MiMo、商汤、百炼、火山、智谱共 7 家有缓存；OpenAI、Claude、Gemini、OpenRouter、月之暗面共 5 家缓存为空。完整、脱敏的前后快照在 `.agent/reviews/2026-09-25-ai-model-capabilities/`，不含 API Key 或连接配置。MCP 此次读取的是**已保存列表**，不触发厂商 `/models`、不证明模型仍可调用。

首次读取有 **163** 条模型的输入模态、输出模态和能力均为空。依据官方模型目录补全旧 Qwen/QVQ/QwQ、MiniMax Speech、星辰 ASR、豆包旧聊天/Seed 2.0、Seedream/Seedance/Embedding 等明确系列，并在读取已有缓存时定向补全空字段；最终 **146 条从全空变为有能力标签，17 条仍保持未知**。另纠正了 11 条原有错误标签，包括 Qwen ASR 被通用 Qwen3 规则标成聊天，以及 Qwen 向量/重排、商汤生图误带工具/思考标签；复核过程中发现的 Seedance 1.5 误命中也已修正。最终类型分布：聊天 431、图像 33、视频 14、向量 25、ASR 12、TTS 10。[百炼文本能力](https://help.aliyun.com/zh/model-studio/text-generation-model)、[视觉推理](https://help.aliyun.com/zh/model-studio/visual-reasoning)、[语音识别](https://help.aliyun.com/zh/model-studio/asr-model)、[MiniMax 语音](https://solutions.minimaxi.com/debug/speech)、[硅基流动模型](https://siliconflow.cn/models)、[火山方舟模型](https://docs.volcengine.com/docs/ark/model-list?lang=zh)、[方舟视频接口](https://docs.volcengine.com/docs/ark/create-video-generation-task-api?lang=zh&redirect=1)、[方舟图片接口](https://docs.volcengine.com/docs/ark/image-generation-api?lang=zh&redirect=1)。

仍未知的 17 条：

| 提供商 | 模型 ID | 保留未知的原因 |
| --- | --- | --- |
| 硅基流动 | `diffusiongemma`、`Kev-4B`、`SemIf`、`XingChenAGI/XingChenGSR-V1.0` | 未找到足以确定当前接口输入/输出及工具/推理字段的官方说明 |
| 商汤 | `sensenova-u1-fast` | 现有特殊规则明确清除不可靠的厂商声明；未做实际接口验证 |
| 阿里云百炼 | `qwen-deep-research-2025-12-15`、`qwen-deep-search-planning`、`sre-gpu-auto-handle`、`test-sre-gpu-auto-handle`、`tongyi-xiaomi-analysis-flash`、`tongyi-xiaomi-analysis-pro`、`unisound/unisound-u2` | 专用工作流、内部或转售 ID 的普通聊天协议与能力未核实 |
| 火山引擎 | `doubao-seaweed-241128`、`doubao-seed3d-1-0-250928`、`doubao-smart-router-250928`、`hitem3d-2-0-251223`、`hyper3d-gen2-260112` | 路由或 3D 生成等类型不能由现有模型类型可靠表达；Seaweed 用途待核实 |

标签仅表示模型名称/官方文档可确认的能力；视频生成、图像生成、向量、ASR/TTS 和实时协议仍需各自的应用调用链，聊天模型的工具与思考仍需按 Provider 做真实往返验证。
