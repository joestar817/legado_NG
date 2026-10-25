// @name 微软翻译
// @schema 1
// @version 1.0.1
// @uuid builtin_microsoft_translator
// @author Legado
// @url https://dev.microsofttranslator.com/apps/endpoint?api-version=1.0
// @enabled true
// @cookieJar false
// @maxConcurrency 1
// @audioType audio/mpeg
// @defaultSpeed 50
// @defaultVolume 50
// @defaultPitch 50
// @sampleText 前不见古人，后不见来者。念天地之悠悠，独怆然而涕下。
// @capabilities style_tags,emotion,synthesis_speed,synthesis_volume,synthesis_pitch
// @description 微软翻译中文语音，仅保留中文名称的普通话、方言和多语言发音人。

// 音色与风格来自 2026-10-19 官方 voices/list；只保留中文名称的普通 Neural 音色。
// 默认云希；多语言音色固定使用中文，方言保留目录语言。目录内置，加载列表不联网。
// 认证沿固定入口、默认 TLS 校验、无重定向、有界超时/响应体；不经过会记录裸 JWT 的通用 JS 网络日志。
// CLIENT_CONSTANT 是公开客户端协议常量，不是用户密钥。token 提前 60 秒刷新，失败不回退旧值。

var TRANSLATOR_AUTH_URL = "https://dev.microsofttranslator.com/apps/endpoint?api-version=1.0";
var TRANSLATOR_CLIENT_CONSTANT = "oik6PdDdMnOXemTbwvMn9de/h9lFnfBaCWbGMMZqqoSaQaqUOqjVGm5NqsmjcBI1x+sS9ugjB55HEJWRiFXYFw==";
var TRANSLATOR_TOKEN_KEY = "builtin_microsoft_translator_token_v1";
var TRANSLATOR_REFRESH_EARLY_MS = 60000;
var TRANSLATOR_DEFAULT_VOICE = "zh-CN-YunxiNeural";
var TRANSLATOR_OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3";
var TRANSLATOR_VOICES = [
  {
    "id": "zh-CN-YunxiNeural",
    "name": "云希",
    "language": "zh-CN",
    "gender": "male",
    "style": "12 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "青年男声-阳光叙事",
      "Chat",
      "Audiobooks",
      "Bright",
      "Animated",
      "Cheerful"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "青年男声-阳光叙事",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Chat",
        "Audiobooks"
      ],
      "personalities": [
        "Bright",
        "Animated",
        "Cheerful"
      ],
      "azure_styles": [
        "narration-relaxed",
        "embarrassed",
        "fearful",
        "cheerful",
        "disgruntled",
        "serious",
        "angry",
        "sad",
        "depressed",
        "chat",
        "assistant",
        "newscast"
      ],
      "roles": [
        "Narrator",
        "YoungAdultMale",
        "Boy"
      ],
      "role_hints": [
        "年轻男主",
        "少年",
        "旁白"
      ],
      "styles": [
        {
          "id": "narration-relaxed",
          "name": "舒缓旁白",
          "value": "narration-relaxed"
        },
        {
          "id": "embarrassed",
          "name": "尴尬",
          "value": "embarrassed"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "disgruntled",
          "name": "不满",
          "value": "disgruntled"
        },
        {
          "id": "serious",
          "name": "严肃",
          "value": "serious"
        },
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        },
        {
          "id": "depressed",
          "name": "沮丧",
          "value": "depressed"
        },
        {
          "id": "chat",
          "name": "聊天",
          "value": "chat"
        },
        {
          "id": "assistant",
          "name": "助手",
          "value": "assistant"
        },
        {
          "id": "newscast",
          "name": "新闻播报",
          "value": "newscast"
        }
      ]
    }
  },
  {
    "id": "wuu-CN-XiaotongNeural",
    "name": "晓彤",
    "language": "wuu-CN",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "wuu-CN",
      "Assistant",
      "Chat",
      "Warm",
      "Friendly",
      "Soothing"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "wuu-CN",
      "chinese_locales": [
        "wuu-CN"
      ],
      "categories": [
        "Assistant",
        "Chat"
      ],
      "personalities": [
        "Warm",
        "Friendly",
        "Soothing"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "wuu-CN-YunzheNeural",
    "name": "云哲",
    "language": "wuu-CN",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "wuu-CN",
      "Assistant",
      "Chat",
      "Calm",
      "Deep",
      "Gentle"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "wuu-CN",
      "chinese_locales": [
        "wuu-CN"
      ],
      "categories": [
        "Assistant",
        "Chat"
      ],
      "personalities": [
        "Calm",
        "Deep",
        "Gentle"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "yue-CN-XiaoMinNeural",
    "name": "晓敏",
    "language": "yue-CN",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "yue-CN",
      "Assistant",
      "News",
      "Bright",
      "Crisp",
      "Confident"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "yue-CN",
      "chinese_locales": [
        "yue-CN"
      ],
      "categories": [
        "Assistant",
        "News"
      ],
      "personalities": [
        "Bright",
        "Crisp",
        "Confident"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "yue-CN-YunSongNeural",
    "name": "云松",
    "language": "yue-CN",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "yue-CN",
      "News",
      "Assistant",
      "Deep",
      "Calm",
      "Formal"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "yue-CN",
      "chinese_locales": [
        "yue-CN"
      ],
      "categories": [
        "News",
        "Assistant"
      ],
      "personalities": [
        "Deep",
        "Calm",
        "Formal"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-CN-XiaoxiaoNeural",
    "name": "晓晓",
    "language": "zh-CN",
    "gender": "female",
    "style": "20 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "少女感-温柔旁白",
      "Chat",
      "Audiobooks",
      "Warm",
      "Well-Rounded",
      "Animated"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "少女感-温柔旁白",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Chat",
        "Audiobooks"
      ],
      "personalities": [
        "Warm",
        "Well-Rounded",
        "Animated"
      ],
      "azure_styles": [
        "assistant",
        "chat",
        "customerservice",
        "newscast",
        "affectionate",
        "angry",
        "calm",
        "cheerful",
        "disgruntled",
        "fearful",
        "gentle",
        "lyrical",
        "sad",
        "serious",
        "poetry-reading",
        "friendly",
        "chat-casual",
        "whispering",
        "sorry",
        "excited"
      ],
      "roles": [],
      "role_hints": [
        "温柔旁白",
        "年轻女主",
        "新闻播报"
      ],
      "styles": [
        {
          "id": "assistant",
          "name": "助手",
          "value": "assistant"
        },
        {
          "id": "chat",
          "name": "聊天",
          "value": "chat"
        },
        {
          "id": "customerservice",
          "name": "客户服务",
          "value": "customerservice"
        },
        {
          "id": "newscast",
          "name": "新闻播报",
          "value": "newscast"
        },
        {
          "id": "affectionate",
          "name": "深情",
          "value": "affectionate"
        },
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "calm",
          "name": "平静",
          "value": "calm"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "disgruntled",
          "name": "不满",
          "value": "disgruntled"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "gentle",
          "name": "温柔",
          "value": "gentle"
        },
        {
          "id": "lyrical",
          "name": "抒情",
          "value": "lyrical"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        },
        {
          "id": "serious",
          "name": "严肃",
          "value": "serious"
        },
        {
          "id": "poetry-reading",
          "name": "诗歌朗诵",
          "value": "poetry-reading"
        },
        {
          "id": "friendly",
          "name": "友好",
          "value": "friendly"
        },
        {
          "id": "chat-casual",
          "name": "随意聊天",
          "value": "chat-casual"
        },
        {
          "id": "whispering",
          "name": "耳语",
          "value": "whispering"
        },
        {
          "id": "sorry",
          "name": "歉意",
          "value": "sorry"
        },
        {
          "id": "excited",
          "name": "兴奋",
          "value": "excited"
        }
      ]
    }
  },
  {
    "id": "zh-CN-YunjianNeural",
    "name": "云健",
    "language": "zh-CN",
    "gender": "male",
    "style": "10 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "低沉男声-热血解说",
      "Narration",
      "Podcast",
      "Deep",
      "Casual",
      "Engaging"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "低沉男声-热血解说",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Narration",
        "Podcast"
      ],
      "personalities": [
        "Deep",
        "Casual",
        "Engaging"
      ],
      "azure_styles": [
        "narration-relaxed",
        "sports-commentary",
        "sports-commentary-excited",
        "angry",
        "disgruntled",
        "cheerful",
        "sad",
        "serious",
        "depressed",
        "documentary-narration"
      ],
      "roles": [],
      "role_hints": [
        "热血男配",
        "战斗旁白",
        "赛事解说"
      ],
      "styles": [
        {
          "id": "narration-relaxed",
          "name": "舒缓旁白",
          "value": "narration-relaxed"
        },
        {
          "id": "sports-commentary",
          "name": "体育解说",
          "value": "sports-commentary"
        },
        {
          "id": "sports-commentary-excited",
          "name": "兴奋体育解说",
          "value": "sports-commentary-excited"
        },
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "disgruntled",
          "name": "不满",
          "value": "disgruntled"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        },
        {
          "id": "serious",
          "name": "严肃",
          "value": "serious"
        },
        {
          "id": "depressed",
          "name": "沮丧",
          "value": "depressed"
        },
        {
          "id": "documentary-narration",
          "name": "纪录片旁白",
          "value": "documentary-narration"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaoyiNeural",
    "name": "晓伊",
    "language": "zh-CN",
    "gender": "female",
    "style": "9 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "少女-活泼甜亮",
      "Chat",
      "Audiobook",
      "Bright",
      "Emotional",
      "Engaging"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "少女-活泼甜亮",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Chat",
        "Audiobook"
      ],
      "personalities": [
        "Bright",
        "Emotional",
        "Engaging"
      ],
      "azure_styles": [
        "angry",
        "disgruntled",
        "affectionate",
        "cheerful",
        "fearful",
        "sad",
        "embarrassed",
        "serious",
        "gentle"
      ],
      "roles": [],
      "role_hints": [
        "少女",
        "校园女主",
        "活泼配角"
      ],
      "styles": [
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "disgruntled",
          "name": "不满",
          "value": "disgruntled"
        },
        {
          "id": "affectionate",
          "name": "深情",
          "value": "affectionate"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        },
        {
          "id": "embarrassed",
          "name": "尴尬",
          "value": "embarrassed"
        },
        {
          "id": "serious",
          "name": "严肃",
          "value": "serious"
        },
        {
          "id": "gentle",
          "name": "温柔",
          "value": "gentle"
        }
      ]
    }
  },
  {
    "id": "zh-CN-YunyangNeural",
    "name": "云扬",
    "language": "zh-CN",
    "gender": "male",
    "style": "3 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "青年男声-专业播报",
      "News",
      "Narration",
      "Formal",
      "Deep",
      "Calm"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "青年男声-专业播报",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "News",
        "Narration"
      ],
      "personalities": [
        "Formal",
        "Deep",
        "Calm"
      ],
      "azure_styles": [
        "customerservice",
        "narration-professional",
        "newscast-casual"
      ],
      "roles": [],
      "role_hints": [
        "新闻播报",
        "专业旁白",
        "官方说明"
      ],
      "styles": [
        {
          "id": "customerservice",
          "name": "客户服务",
          "value": "customerservice"
        },
        {
          "id": "narration-professional",
          "name": "专业旁白",
          "value": "narration-professional"
        },
        {
          "id": "newscast-casual",
          "name": "轻松新闻",
          "value": "newscast-casual"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaochenNeural",
    "name": "晓辰",
    "language": "zh-CN",
    "gender": "female",
    "style": "1 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "女青年-自然柔和",
      "Chat",
      "Podcast",
      "Friendly",
      "Casual",
      "Upbeat"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "女青年-自然柔和",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Friendly",
        "Casual",
        "Upbeat"
      ],
      "azure_styles": [
        "livecommercial"
      ],
      "roles": [],
      "role_hints": [
        "少女",
        "温柔女主",
        "情绪对白"
      ],
      "styles": [
        {
          "id": "livecommercial",
          "name": "直播带货",
          "value": "livecommercial"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaochenMultilingualNeural",
    "name": "晓辰 多语言",
    "language": "zh-CN",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN",
      "Chat",
      "Podcast",
      "Friendly",
      "Casual",
      "Upbeat"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN",
        "zh-CN",
        "zh-HK",
        "zh-TW"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Friendly",
        "Casual",
        "Upbeat"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-CN-XiaohanNeural",
    "name": "晓涵",
    "language": "zh-CN",
    "gender": "female",
    "style": "10 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "少女-高亮温柔",
      "Audiobook",
      "Narration",
      "Gentle",
      "Warm",
      "Emotional"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "少女-高亮温柔",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Audiobook",
        "Narration"
      ],
      "personalities": [
        "Gentle",
        "Warm",
        "Emotional"
      ],
      "azure_styles": [
        "calm",
        "fearful",
        "cheerful",
        "disgruntled",
        "serious",
        "angry",
        "sad",
        "gentle",
        "affectionate",
        "embarrassed"
      ],
      "roles": [],
      "role_hints": [
        "女主",
        "女青年",
        "温柔配角"
      ],
      "styles": [
        {
          "id": "calm",
          "name": "平静",
          "value": "calm"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "disgruntled",
          "name": "不满",
          "value": "disgruntled"
        },
        {
          "id": "serious",
          "name": "严肃",
          "value": "serious"
        },
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        },
        {
          "id": "gentle",
          "name": "温柔",
          "value": "gentle"
        },
        {
          "id": "affectionate",
          "name": "深情",
          "value": "affectionate"
        },
        {
          "id": "embarrassed",
          "name": "尴尬",
          "value": "embarrassed"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaomengNeural",
    "name": "晓梦",
    "language": "zh-CN",
    "gender": "female",
    "style": "1 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "少女-轻柔聊天",
      "News",
      "Chat",
      "Gentle",
      "Upbeat",
      "Friendly"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "少女-轻柔聊天",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "News",
        "Chat"
      ],
      "personalities": [
        "Gentle",
        "Upbeat",
        "Friendly"
      ],
      "azure_styles": [
        "chat"
      ],
      "roles": [],
      "role_hints": [
        "少女",
        "轻柔聊天",
        "自然对话"
      ],
      "styles": [
        {
          "id": "chat",
          "name": "聊天",
          "value": "chat"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaomoNeural",
    "name": "晓墨",
    "language": "zh-CN",
    "gender": "female",
    "style": "12 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "少女-多角色温柔",
      "Audiobook",
      "Narration",
      "Deep",
      "Casual",
      "Calm"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "少女-多角色温柔",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Audiobook",
        "Narration"
      ],
      "personalities": [
        "Deep",
        "Casual",
        "Calm"
      ],
      "azure_styles": [
        "embarrassed",
        "calm",
        "fearful",
        "cheerful",
        "disgruntled",
        "serious",
        "angry",
        "sad",
        "depressed",
        "affectionate",
        "gentle",
        "envious"
      ],
      "roles": [
        "YoungAdultFemale",
        "YoungAdultMale",
        "OlderAdultFemale",
        "OlderAdultMale",
        "SeniorFemale",
        "SeniorMale",
        "Girl",
        "Boy"
      ],
      "role_hints": [
        "多角色",
        "少女",
        "年轻女主"
      ],
      "styles": [
        {
          "id": "embarrassed",
          "name": "尴尬",
          "value": "embarrassed"
        },
        {
          "id": "calm",
          "name": "平静",
          "value": "calm"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "disgruntled",
          "name": "不满",
          "value": "disgruntled"
        },
        {
          "id": "serious",
          "name": "严肃",
          "value": "serious"
        },
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        },
        {
          "id": "depressed",
          "name": "沮丧",
          "value": "depressed"
        },
        {
          "id": "affectionate",
          "name": "深情",
          "value": "affectionate"
        },
        {
          "id": "gentle",
          "name": "温柔",
          "value": "gentle"
        },
        {
          "id": "envious",
          "name": "羡慕",
          "value": "envious"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaoqiuNeural",
    "name": "晓秋",
    "language": "zh-CN",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN",
      "成熟女声-高亮硬朗",
      "Audiobook",
      "Documentary",
      "Calm",
      "Engaging",
      "Soothing"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "成熟女声-高亮硬朗",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Audiobook",
        "Documentary"
      ],
      "personalities": [
        "Calm",
        "Engaging",
        "Soothing"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "母亲",
        "老师",
        "成熟女性",
        "旁白"
      ],
      "styles": []
    }
  },
  {
    "id": "zh-CN-XiaorouNeural",
    "name": "晓柔",
    "language": "zh-CN",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN",
      "少女-清甜",
      "Chat",
      "Podcast",
      "Cheerful",
      "Engaging",
      "Pleasant"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "少女-清甜",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Cheerful",
        "Engaging",
        "Pleasant"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "少女",
        "校园女主",
        "活泼配角"
      ],
      "styles": []
    }
  },
  {
    "id": "zh-CN-XiaoruiNeural",
    "name": "晓睿",
    "language": "zh-CN",
    "gender": "female",
    "style": "4 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "少女-冷亮情绪",
      "Audiobook",
      "Narration",
      "Confident",
      "Emotional",
      "Hoarse"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "少女-冷亮情绪",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Audiobook",
        "Narration"
      ],
      "personalities": [
        "Confident",
        "Emotional",
        "Hoarse"
      ],
      "azure_styles": [
        "calm",
        "fearful",
        "angry",
        "sad"
      ],
      "roles": [],
      "role_hints": [
        "少女",
        "冷静角色",
        "情绪对白"
      ],
      "styles": [
        {
          "id": "calm",
          "name": "平静",
          "value": "calm"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaoshuangMultilingualNeural",
    "name": "晓双 多语言",
    "language": "zh-CN",
    "gender": "female",
    "style": "1 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "Chat",
      "Story",
      "Crisp",
      "Cheerful",
      "Bright"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN",
        "zh-CN",
        "zh-HK",
        "zh-TW"
      ],
      "categories": [
        "Chat",
        "Story"
      ],
      "personalities": [
        "Crisp",
        "Cheerful",
        "Bright"
      ],
      "azure_styles": [
        "chat"
      ],
      "roles": [],
      "role_hints": [],
      "styles": [
        {
          "id": "chat",
          "name": "聊天",
          "value": "chat"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaoshuangNeural",
    "name": "晓双",
    "language": "zh-CN",
    "gender": "female",
    "style": "1 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "女童/少女-稚嫩清亮",
      "Chat",
      "Audiobook",
      "Crisp",
      "Cheerful",
      "Bright"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "女童/少女-稚嫩清亮",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Chat",
        "Audiobook"
      ],
      "personalities": [
        "Crisp",
        "Cheerful",
        "Bright"
      ],
      "azure_styles": [
        "chat"
      ],
      "roles": [],
      "role_hints": [
        "女童",
        "少女",
        "轻松聊天"
      ],
      "styles": [
        {
          "id": "chat",
          "name": "聊天",
          "value": "chat"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaoxiaoDialectsNeural",
    "name": "晓晓 方言",
    "language": "zh-CN",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN",
      "女青年-方言清亮",
      "Chat",
      "Podcast",
      "Warm",
      "Animated",
      "Bright"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "女青年-方言清亮",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN",
        "zh-CN-shaanxi",
        "zh-CN-sichuan",
        "zh-CN-shanxi",
        "zh-CN-anhui",
        "zh-CN-hunan",
        "zh-CN-gansu",
        "zh-CN-shandong",
        "zh-CN-henan",
        "zh-CN-liaoning",
        "zh-TW",
        "yue-CN",
        "wuu-CN"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Warm",
        "Animated",
        "Bright"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "方言",
        "女青年",
        "旁白"
      ],
      "styles": []
    }
  },
  {
    "id": "zh-CN-XiaoxiaoMultilingualNeural",
    "name": "晓晓 多语言",
    "language": "zh-CN",
    "gender": "female",
    "style": "7 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "女青年-温柔多语",
      "Assistant",
      "Narration",
      "Warm",
      "Animated",
      "Bright"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "女青年-温柔多语",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN",
        "zh-CN",
        "zh-HK",
        "zh-TW"
      ],
      "categories": [
        "Assistant",
        "Narration"
      ],
      "personalities": [
        "Warm",
        "Animated",
        "Bright"
      ],
      "azure_styles": [
        "affectionate",
        "cheerful",
        "empathetic",
        "excited",
        "poetry-reading",
        "sorry",
        "story"
      ],
      "roles": [],
      "role_hints": [
        "多语言",
        "温柔女主",
        "故事旁白"
      ],
      "styles": [
        {
          "id": "affectionate",
          "name": "深情",
          "value": "affectionate"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "empathetic",
          "name": "共情",
          "value": "empathetic"
        },
        {
          "id": "excited",
          "name": "兴奋",
          "value": "excited"
        },
        {
          "id": "poetry-reading",
          "name": "诗歌朗诵",
          "value": "poetry-reading"
        },
        {
          "id": "sorry",
          "name": "歉意",
          "value": "sorry"
        },
        {
          "id": "story",
          "name": "故事",
          "value": "story"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaoyanNeural",
    "name": "晓颜",
    "language": "zh-CN",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN",
      "女青年-自然柔和",
      "Assistant",
      "Chat",
      "Warm",
      "Gentle",
      "Empathetic"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "女青年-自然柔和",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Assistant",
        "Chat"
      ],
      "personalities": [
        "Warm",
        "Gentle",
        "Empathetic"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "女主",
        "女青年",
        "温柔配角"
      ],
      "styles": []
    }
  },
  {
    "id": "zh-CN-XiaoyouMultilingualNeural",
    "name": "晓悠 多语言",
    "language": "zh-CN",
    "gender": "female",
    "style": "7 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "Chat",
      "Story",
      "Learning",
      "Crisp",
      "Cheerful",
      "Bright"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN",
        "zh-CN",
        "zh-HK",
        "zh-TW"
      ],
      "categories": [
        "Chat",
        "Story",
        "Learning"
      ],
      "personalities": [
        "Crisp",
        "Cheerful",
        "Bright"
      ],
      "azure_styles": [
        "chat",
        "angry",
        "cheerful",
        "poetry-reading",
        "sad",
        "story",
        "cute"
      ],
      "roles": [],
      "role_hints": [],
      "styles": [
        {
          "id": "chat",
          "name": "聊天",
          "value": "chat"
        },
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "poetry-reading",
          "name": "诗歌朗诵",
          "value": "poetry-reading"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        },
        {
          "id": "story",
          "name": "故事",
          "value": "story"
        },
        {
          "id": "cute",
          "name": "可爱",
          "value": "cute"
        }
      ]
    }
  },
  {
    "id": "zh-CN-XiaoyouNeural",
    "name": "晓悠",
    "language": "zh-CN",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN",
      "少女-高亮稚嫩",
      "Audiobook",
      "Narration",
      "Crisp",
      "Cheerful",
      "Bright"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "少女-高亮稚嫩",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Audiobook",
        "Narration"
      ],
      "personalities": [
        "Crisp",
        "Cheerful",
        "Bright"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "少女",
        "活泼配角",
        "清亮旁白"
      ],
      "styles": []
    }
  },
  {
    "id": "zh-CN-XiaoyuMultilingualNeural",
    "name": "晓宇 多语言",
    "language": "zh-CN",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN",
      "少女-柔亮多语",
      "Chat",
      "Podcast",
      "Deep",
      "Confident",
      "Casual"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "少女-柔亮多语",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN",
        "zh-CN",
        "zh-HK",
        "zh-TW"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Deep",
        "Confident",
        "Casual"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "多语言",
        "少女",
        "配角"
      ],
      "styles": []
    }
  },
  {
    "id": "zh-CN-XiaozhenNeural",
    "name": "晓甄",
    "language": "zh-CN",
    "gender": "female",
    "style": "6 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "女青年-清亮情绪",
      "Chat",
      "Audiobook",
      "Calm",
      "Serious",
      "Confident"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "女青年-清亮情绪",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Chat",
        "Audiobook"
      ],
      "personalities": [
        "Calm",
        "Serious",
        "Confident"
      ],
      "azure_styles": [
        "angry",
        "disgruntled",
        "cheerful",
        "fearful",
        "sad",
        "serious"
      ],
      "roles": [],
      "role_hints": [
        "女青年",
        "情绪对白",
        "剧情配角"
      ],
      "styles": [
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "disgruntled",
          "name": "不满",
          "value": "disgruntled"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        },
        {
          "id": "serious",
          "name": "严肃",
          "value": "serious"
        }
      ]
    }
  },
  {
    "id": "zh-CN-YunfengNeural",
    "name": "云枫",
    "language": "zh-CN",
    "gender": "male",
    "style": "7 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "青年男声-清朗情绪",
      "Audiobook",
      "Podcast",
      "Confident",
      "Animated",
      "Emotional"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "青年男声-清朗情绪",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Audiobook",
        "Podcast"
      ],
      "personalities": [
        "Confident",
        "Animated",
        "Emotional"
      ],
      "azure_styles": [
        "angry",
        "disgruntled",
        "cheerful",
        "fearful",
        "sad",
        "serious",
        "depressed"
      ],
      "roles": [],
      "role_hints": [
        "男主",
        "书生",
        "清朗旁白"
      ],
      "styles": [
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "disgruntled",
          "name": "不满",
          "value": "disgruntled"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        },
        {
          "id": "serious",
          "name": "严肃",
          "value": "serious"
        },
        {
          "id": "depressed",
          "name": "沮丧",
          "value": "depressed"
        }
      ]
    }
  },
  {
    "id": "zh-CN-YunhaoNeural",
    "name": "云皓",
    "language": "zh-CN",
    "gender": "male",
    "style": "1 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "低沉男声-活力广告",
      "Advertisement",
      "Chat",
      "Warm",
      "Soft",
      "Upbeat"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "低沉男声-活力广告",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Advertisement",
        "Chat"
      ],
      "personalities": [
        "Warm",
        "Soft",
        "Upbeat"
      ],
      "azure_styles": [
        "advertisement-upbeat"
      ],
      "roles": [],
      "role_hints": [
        "成熟男声",
        "活力说明",
        "广告旁白"
      ],
      "styles": [
        {
          "id": "advertisement-upbeat",
          "name": "广告活力",
          "value": "advertisement-upbeat"
        }
      ]
    }
  },
  {
    "id": "zh-CN-YunjieNeural",
    "name": "云杰",
    "language": "zh-CN",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN",
      "少年男声-温和",
      "Chat",
      "Podcast",
      "Casual",
      "Confident",
      "Warm"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "少年男声-温和",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Casual",
        "Confident",
        "Warm"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "少年",
        "年轻男主",
        "同学"
      ],
      "styles": []
    }
  },
  {
    "id": "zh-CN-YunxiaNeural",
    "name": "云夏",
    "language": "zh-CN",
    "gender": "male",
    "style": "5 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "男童/少年-高亮稚嫩",
      "Audiobook",
      "Chat",
      "Cheerful",
      "Friendly",
      "Emotional"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "男童/少年-高亮稚嫩",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Audiobook",
        "Chat"
      ],
      "personalities": [
        "Cheerful",
        "Friendly",
        "Emotional"
      ],
      "azure_styles": [
        "calm",
        "fearful",
        "cheerful",
        "angry",
        "sad"
      ],
      "roles": [],
      "role_hints": [
        "男童",
        "少年",
        "活泼男配"
      ],
      "styles": [
        {
          "id": "calm",
          "name": "平静",
          "value": "calm"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        }
      ]
    }
  },
  {
    "id": "zh-CN-YunyeNeural",
    "name": "云野",
    "language": "zh-CN",
    "gender": "male",
    "style": "8 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "低沉大叔-多角色",
      "Audiobook",
      "Narration",
      "Casual",
      "Deep",
      "Calm"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "低沉大叔-多角色",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Audiobook",
        "Narration"
      ],
      "personalities": [
        "Casual",
        "Deep",
        "Calm"
      ],
      "azure_styles": [
        "embarrassed",
        "calm",
        "fearful",
        "cheerful",
        "disgruntled",
        "serious",
        "angry",
        "sad"
      ],
      "roles": [
        "YoungAdultFemale",
        "YoungAdultMale",
        "OlderAdultFemale",
        "OlderAdultMale",
        "SeniorFemale",
        "SeniorMale",
        "Girl",
        "Boy"
      ],
      "role_hints": [
        "大叔",
        "父亲",
        "反派",
        "厚重旁白"
      ],
      "styles": [
        {
          "id": "embarrassed",
          "name": "尴尬",
          "value": "embarrassed"
        },
        {
          "id": "calm",
          "name": "平静",
          "value": "calm"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "disgruntled",
          "name": "不满",
          "value": "disgruntled"
        },
        {
          "id": "serious",
          "name": "严肃",
          "value": "serious"
        },
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        }
      ]
    }
  },
  {
    "id": "zh-CN-YunyiMultilingualNeural",
    "name": "云逸 多语言",
    "language": "zh-CN",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN",
      "青年男声-清朗多语",
      "Chat",
      "Podcast",
      "Gentle",
      "Casual",
      "Friendly"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "青年男声-清朗多语",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN",
        "zh-CN",
        "zh-HK",
        "zh-TW"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Gentle",
        "Casual",
        "Friendly"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "多语言",
        "男青年",
        "配角"
      ],
      "styles": []
    }
  },
  {
    "id": "zh-CN-YunzeNeural",
    "name": "云泽",
    "language": "zh-CN",
    "gender": "male",
    "style": "9 种风格",
    "tags": [
      "微软翻译",
      "zh-CN",
      "低沉长辈-纪录片",
      "Documentary",
      "Narration",
      "Deep",
      "Confident",
      "Formal"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "低沉长辈-纪录片",
      "source_locale": "zh-CN",
      "chinese_locales": [
        "zh-CN"
      ],
      "categories": [
        "Documentary",
        "Narration"
      ],
      "personalities": [
        "Deep",
        "Confident",
        "Formal"
      ],
      "azure_styles": [
        "calm",
        "fearful",
        "cheerful",
        "disgruntled",
        "serious",
        "angry",
        "sad",
        "depressed",
        "documentary-narration"
      ],
      "roles": [
        "OlderAdultMale",
        "SeniorMale"
      ],
      "role_hints": [
        "成熟男性",
        "长辈",
        "纪录片旁白"
      ],
      "styles": [
        {
          "id": "calm",
          "name": "平静",
          "value": "calm"
        },
        {
          "id": "fearful",
          "name": "恐惧",
          "value": "fearful"
        },
        {
          "id": "cheerful",
          "name": "欢快",
          "value": "cheerful"
        },
        {
          "id": "disgruntled",
          "name": "不满",
          "value": "disgruntled"
        },
        {
          "id": "serious",
          "name": "严肃",
          "value": "serious"
        },
        {
          "id": "angry",
          "name": "愤怒",
          "value": "angry"
        },
        {
          "id": "sad",
          "name": "悲伤",
          "value": "sad"
        },
        {
          "id": "depressed",
          "name": "沮丧",
          "value": "depressed"
        },
        {
          "id": "documentary-narration",
          "name": "纪录片旁白",
          "value": "documentary-narration"
        }
      ]
    }
  },
  {
    "id": "zh-CN-guangxi-YunqiNeural",
    "name": "云奇 广西",
    "language": "zh-CN-guangxi",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN-guangxi",
      "Chat",
      "Podcast",
      "Engaging",
      "Casual",
      "Animated"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-CN-guangxi",
      "chinese_locales": [
        "zh-CN-guangxi"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Engaging",
        "Casual",
        "Animated"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-CN-henan-YundengNeural",
    "name": "云登",
    "language": "zh-CN-henan",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN-henan",
      "Chat",
      "Podcast",
      "Casual",
      "Friendly",
      "Animated"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-CN-henan",
      "chinese_locales": [
        "zh-CN-henan"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Casual",
        "Friendly",
        "Animated"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-CN-liaoning-XiaobeiNeural",
    "name": "晓北 辽宁",
    "language": "zh-CN-liaoning",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN-liaoning",
      "Chat",
      "Podcast",
      "Friendly",
      "Casual",
      "Gentle"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-CN-liaoning",
      "chinese_locales": [
        "zh-CN-liaoning"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Friendly",
        "Casual",
        "Gentle"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-CN-liaoning-YunbiaoNeural",
    "name": "云彪 辽宁",
    "language": "zh-CN-liaoning",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN-liaoning",
      "Chat",
      "Podcast",
      "Confident",
      "Casual",
      "Cheerful"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-CN-liaoning",
      "chinese_locales": [
        "zh-CN-liaoning"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Confident",
        "Casual",
        "Cheerful"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-CN-shaanxi-XiaoniNeural",
    "name": "晓妮",
    "language": "zh-CN-shaanxi",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN-shaanxi",
      "Chat",
      "Podcast",
      "Confident",
      "Engaging",
      "Casual"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-CN-shaanxi",
      "chinese_locales": [
        "zh-CN-shaanxi"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Confident",
        "Engaging",
        "Casual"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-CN-shandong-YunxiangNeural",
    "name": "云翔",
    "language": "zh-CN-shandong",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN-shandong",
      "Chat",
      "Podcast",
      "Casual",
      "Animated",
      "Strong"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-CN-shandong",
      "chinese_locales": [
        "zh-CN-shandong"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Casual",
        "Animated",
        "Strong"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-CN-sichuan-YunxiNeural",
    "name": "云希 四川",
    "language": "zh-CN-sichuan",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-CN-sichuan",
      "Chat",
      "Podcast",
      "Casual",
      "Animated",
      "Gentle"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-CN-sichuan",
      "chinese_locales": [
        "zh-CN-sichuan"
      ],
      "categories": [
        "Chat",
        "Podcast"
      ],
      "personalities": [
        "Casual",
        "Animated",
        "Gentle"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-HK-HiuMaanNeural",
    "name": "曉曼",
    "language": "zh-HK",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-HK",
      "News",
      "Narration",
      "Bright",
      "Upbeat"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-HK",
      "chinese_locales": [
        "zh-HK"
      ],
      "categories": [
        "News",
        "Narration"
      ],
      "personalities": [
        "Bright",
        "Upbeat"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-HK-WanLungNeural",
    "name": "雲龍",
    "language": "zh-HK",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-HK",
      "News",
      "Narration",
      "Calm",
      "Formal"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-HK",
      "chinese_locales": [
        "zh-HK"
      ],
      "categories": [
        "News",
        "Narration"
      ],
      "personalities": [
        "Calm",
        "Formal"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-HK-HiuGaaiNeural",
    "name": "曉佳",
    "language": "zh-HK",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-HK",
      "News",
      "E-learning",
      "Crisp",
      "Bright",
      "Clear"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "",
      "source_locale": "zh-HK",
      "chinese_locales": [
        "zh-HK"
      ],
      "categories": [
        "News",
        "E-learning"
      ],
      "personalities": [
        "Crisp",
        "Bright",
        "Clear"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [],
      "styles": []
    }
  },
  {
    "id": "zh-TW-HsiaoChenNeural",
    "name": "曉臻",
    "language": "zh-TW",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-TW",
      "台配女青年-自然亲和",
      "Narration",
      "News",
      "Soft",
      "Caring"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "台配女青年-自然亲和",
      "source_locale": "zh-TW",
      "chinese_locales": [
        "zh-TW"
      ],
      "categories": [
        "Narration",
        "News"
      ],
      "personalities": [
        "Soft",
        "Caring"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "繁中女声",
        "女青年",
        "旁白"
      ],
      "styles": []
    }
  },
  {
    "id": "zh-TW-YunJheNeural",
    "name": "雲哲",
    "language": "zh-TW",
    "gender": "male",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-TW",
      "台配男声-温和低沉",
      "News",
      "Narration",
      "Engaging",
      "Gentle"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "台配男声-温和低沉",
      "source_locale": "zh-TW",
      "chinese_locales": [
        "zh-TW"
      ],
      "categories": [
        "News",
        "Narration"
      ],
      "personalities": [
        "Engaging",
        "Gentle"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "繁中男声",
        "成熟男性",
        "旁白"
      ],
      "styles": []
    }
  },
  {
    "id": "zh-TW-HsiaoYuNeural",
    "name": "曉雨",
    "language": "zh-TW",
    "gender": "female",
    "style": "",
    "tags": [
      "微软翻译",
      "zh-TW",
      "台配成熟女声-明亮",
      "News",
      "E-learning",
      "Crisp",
      "Bright",
      "Clear"
    ],
    "extra": {
      "provider": "microsoft-translator",
      "profile": "台配成熟女声-明亮",
      "source_locale": "zh-TW",
      "chinese_locales": [
        "zh-TW"
      ],
      "categories": [
        "News",
        "E-learning"
      ],
      "personalities": [
        "Crisp",
        "Bright",
        "Clear"
      ],
      "azure_styles": [],
      "roles": [],
      "role_hints": [
        "繁中女声",
        "成熟女性",
        "旁白"
      ],
      "styles": []
    }
  }
];

var EXPRESSIVE_STYLE_ALIASES;

function expressiveStyleAliases() {
    if (EXPRESSIVE_STYLE_ALIASES) {
        return EXPRESSIVE_STYLE_ALIASES;
    }
    EXPRESSIVE_STYLE_ALIASES = [
    { style: "angry", terms: ["angry", "愤怒", "生气", "恼怒", "暴怒"] },
    { style: "anxious", terms: ["anxious", "焦虑", "紧张", "不安"] },
    { style: "calm", terms: ["calm", "平静", "克制", "冷静", "沉着"] },
    { style: "cheerful", terms: ["cheerful", "欢快", "开心", "喜悦", "活泼"] },
    { style: "comforting", terms: ["comforting", "安慰", "宽慰"] },
    { style: "curious", terms: ["curious", "好奇", "疑惑"] },
    { style: "depressed", terms: ["depressed", "沮丧", "低落", "压抑"] },
    { style: "disappointed", terms: ["disappointed", "失望"] },
    { style: "embarrassed", terms: ["embarrassed", "尴尬", "窘迫"] },
    { style: "empathetic", terms: ["empathetic", "共情", "同情"] },
    { style: "excited", terms: ["excited", "兴奋", "激动", "高昂"] },
    { style: "fearful", terms: ["fearful", "恐惧", "害怕", "惊恐"] },
    { style: "gentle", terms: ["gentle", "温柔", "柔和", "轻柔"] },
    { style: "sad", terms: ["sad", "悲伤", "难过", "哀伤"] },
    { style: "serious", terms: ["serious", "严肃", "郑重", "庄重"] },
    { style: "shy", terms: ["shy", "害羞", "羞涩"] },
    { style: "sorry", terms: ["sorry", "歉意", "抱歉", "愧疚"] },
    { style: "surprised", terms: ["surprised", "惊讶", "震惊", "意外"] },
    { style: "whispering", terms: ["whispering", "耳语", "低语", "悄声"] },
    { style: "newscast", terms: ["newscast", "新闻播报", "播报"] },
    { style: "poetry-reading", terms: ["poetry-reading", "诗歌朗诵", "朗诵"] },
    { style: "story", terms: ["story", "故事", "讲故事", "叙事"] },
    { style: "story-telling", terms: ["story-telling", "故事", "讲故事", "叙事"] }
    ];
    return EXPRESSIVE_STYLE_ALIASES;
}

function options() {
    return [{ key: "timeout", label: "超时秒数", type: "number", defaultValue: "30" }];
}

function voices(options, ctx) {
    return TRANSLATOR_VOICES;
}

function translatorTimeout(options) {
    var value = Number(options && options.timeout != null ? options.timeout : 30);
    if (!isFinite(value)) value = 30;
    return Math.max(5, Math.min(120, Math.round(value)));
}

function translatorVoice(voice) {
    var id = String(voice && voice.id || TRANSLATOR_DEFAULT_VOICE);
    for (var i = 0; i < TRANSLATOR_VOICES.length; i++) {
        if (TRANSLATOR_VOICES[i].id === id) return TRANSLATOR_VOICES[i];
    }
    throw new Error("微软翻译：音色不在内置目录中");
}

function translatorSelectedStyle(voice, catalogVoice) {
    var value = "";
    if (voice && voice.selected_style && voice.selected_style.value != null) {
        value = String(voice.selected_style.value);
    } else if (voice && voice.style_value != null) {
        value = String(voice.style_value);
    }
    if (!value || value.toLowerCase() === "default") return "";
    var styles = catalogVoice.extra.styles;
    for (var i = 0; i < styles.length; i++) {
        if (styles[i].value === value) return value;
    }
    throw new Error("微软翻译：当前音色不支持所选风格");
}

function translatorParam(params, key) {
    var value = Number(params && params[key] != null ? params[key] : 50);
    if (!isFinite(value)) value = 50;
    return Math.max(0, Math.min(100, value));
}

function translatorSignedPercent(value) {
    var rounded = Math.round(value);
    return (rounded > 0 ? "+" : "") + rounded + "%";
}

function translatorEscapeXml(value) {
    var input = String(value == null ? "" : value);
    var result = "";
    for (var i = 0; i < input.length; i++) {
        var code = input.charCodeAt(i);
        if (code >= 0xD800 && code <= 0xDBFF) {
            var next = input.charCodeAt(i + 1);
            if (next >= 0xDC00 && next <= 0xDFFF) {
                result += input.charAt(i) + input.charAt(++i);
            } else {
                result += "\uFFFD";
            }
        } else if (code === 9 || code === 10 || code === 13 ||
                   code >= 0x20 && code <= 0xD7FF || code >= 0xE000 && code <= 0xFFFD) {
            result += input.charAt(i);
        } else {
            result += "\uFFFD";
        }
    }
    return result.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&apos;")
        .replace(/\{/g, "&#123;").replace(/\}/g, "&#125;").replace(/@/g, "&#64;");
}

function buildTranslatorSsml(text, voice, params, ctx) {
    var catalogVoice = translatorVoice(voice);
    var style = translatorSelectedStyle(voice, catalogVoice) || automaticStyleValue(catalogVoice, ctx);
    // 50 精确表示服务默认值。语速 -50%..+100%，音调 -50%..+50%，相对音量 -100%..+100%。
    var speedDelta = translatorParam(params, "speed") - 50;
    var rate = translatorSignedPercent(speedDelta < 0 ? speedDelta : speedDelta * 2);
    var pitch = translatorSignedPercent(translatorParam(params, "pitch") - 50);
    var volume = translatorSignedPercent((translatorParam(params, "volume") - 50) * 2);
    var content = '<prosody rate="' + rate + '" pitch="' + pitch + '" volume="' + volume + '">' +
        translatorEscapeXml(text) + '</prosody>';
    if (catalogVoice.extra.source_locale !== catalogVoice.language) {
        content = '<lang xml:lang="' + translatorEscapeXml(catalogVoice.language) + '">' + content + '</lang>';
    }
    if (style) content = '<mstts:express-as style="' + translatorEscapeXml(style) + '">' + content + '</mstts:express-as>';
    return '<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" ' +
        'xmlns:mstts="https://www.w3.org/2001/mstts" xml:lang="' + translatorEscapeXml(catalogVoice.language) + '">' +
        '<voice name="' + translatorEscapeXml(catalogVoice.id) + '">' + content + '</voice></speak>';
}

function translatorUtcStamp(date) {
    var days = ["sun", "mon", "tue", "wed", "thu", "fri", "sat"];
    var months = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];
    function two(value) { return value < 10 ? "0" + value : String(value); }
    // 协议原值：秒数后直接拼 gmt，没有空格。
    return days[date.getUTCDay()] + ", " + two(date.getUTCDate()) + " " + months[date.getUTCMonth()] + " " +
        date.getUTCFullYear() + " " + two(date.getUTCHours()) + ":" + two(date.getUTCMinutes()) + ":" +
        two(date.getUTCSeconds()) + "gmt";
}

function translatorSignature(stamp, nonce) {
    var encodedUrl = encodeURIComponent(TRANSLATOR_AUTH_URL.split("://")[1]);
    var signed = ("MSTranslatorAndroidApp" + encodedUrl + stamp + nonce).toLowerCase();
    var digest = CryptoJS.HmacSHA256(signed, CryptoJS.enc.Base64.parse(TRANSLATOR_CLIENT_CONSTANT));
    return "MSTranslatorAndroidApp::" + CryptoJS.enc.Base64.stringify(digest) + "::" + stamp + "::" + nonce;
}

function translatorTokenExpiresAt(token) {
    try {
        var parts = String(token).split(".");
        if (parts.length !== 3) throw new Error("not JWT");
        var encoded = parts[1].replace(/-/g, "+").replace(/_/g, "/");
        while (encoded.length % 4) encoded += "=";
        var claims = JSON.parse(CryptoJS.enc.Utf8.stringify(CryptoJS.enc.Base64.parse(encoded)));
        if (typeof claims.exp !== "number" || !isFinite(claims.exp) || claims.exp <= 0) throw new Error("no exp");
        return Math.floor(claims.exp * 1000);
    } catch (ignored) {
        throw new Error("微软翻译：认证响应缺少有效的 token 到期时间");
    }
}

function translatorValidRegion(region) {
    return typeof region === "string" && /^[a-z][a-z0-9-]{1,31}$/.test(region);
}

function translatorClearToken() {
    source.put(TRANSLATOR_TOKEN_KEY, "");
}

function translatorCachedToken(now) {
    var raw = String(source.get(TRANSLATOR_TOKEN_KEY) || "");
    if (!raw) return null;
    try {
        var cached = JSON.parse(raw);
        if (!cached || typeof cached.token !== "string" || !translatorValidRegion(cached.region)) return null;
        var actualExpiry = translatorTokenExpiresAt(cached.token);
        if (cached.expiresAt !== actualExpiry || actualExpiry <= now + TRANSLATOR_REFRESH_EARLY_MS) return null;
        return cached;
    } catch (ignored) {
        return null;
    }
}

function translatorAuthErrorStatus(error) {
    try {
        if (error && error.javaException) return Number(error.javaException.getStatusCode()) || 0;
    } catch (ignored) {}
    var match = String(error).match(/(?:Status=|HTTP\s+|status[\s:=]+)([1-5][0-9]{2})/i);
    return match ? Number(match[1]) : 0;
}

function translatorAuthRequest(headers, timeout) {
    // 固定认证地址；不设置不安全 TLS，也不经过记录完整响应体的通用 JS 网络封装。
    var connection = Packages.org.jsoup.Jsoup.connect(TRANSLATOR_AUTH_URL)
        .method(Packages.org.jsoup.Connection.Method.POST)
        .ignoreContentType(true)
        .ignoreHttpErrors(true)
        .followRedirects(false)
        .timeout(timeout * 1000)
        .maxBodySize(65536)
        .requestBody("");
    for (var key in headers) {
        if (Object.prototype.hasOwnProperty.call(headers, key)) connection.header(key, String(headers[key]));
    }
    return connection.execute();
}

function translatorEndpoint(timeout) {
    var now = Date.now();
    var cached = translatorCachedToken(now);
    if (cached) return cached;
    // 清掉失效条目后再联网；失败时不回退旧 token。
    translatorClearToken();
    var stamp = translatorUtcStamp(new Date(now));
    var nonce = String(java.randomUUID()).replace(/-/g, "").toLowerCase();
    var headers = {
        "X-MT-Signature": translatorSignature(stamp, nonce),
        "X-ClientVersion": "4.0.530a 5fe1dc6c",
        "X-UserId": String(java.randomUUID()).replace(/-/g, "").substring(0, 16),
        "X-HomeGeographicRegion": "zh-Hans-CN",
        "X-ClientTraceId": String(java.randomUUID()),
        "Accept-Language": "zh-Hans",
        "User-Agent": "okhttp/4.5.0",
        "Content-Type": "application/json; charset=utf-8"
    };
    var response;
    try {
        response = translatorAuthRequest(headers, timeout);
    } catch (error) {
        var status = translatorAuthErrorStatus(error);
        throw new Error("微软翻译认证失败" + (status ? "（HTTP " + status + "）" : "（网络请求失败）"));
    }
    var statusCode = Number(response.statusCode());
    if (statusCode !== 200) throw new Error("微软翻译认证失败（HTTP " + statusCode + "）");
    var result;
    try {
        result = JSON.parse(String(response.body()));
    } catch (ignored) {
        throw new Error("微软翻译：认证响应不是有效 JSON");
    }
    if (!result || typeof result.t !== "string" || !result.t || !translatorValidRegion(result.r)) {
        throw new Error("微软翻译：认证响应缺少 token 或有效区域");
    }
    var expiresAt = translatorTokenExpiresAt(result.t);
    if (expiresAt <= Date.now() + TRANSLATOR_REFRESH_EARLY_MS) {
        throw new Error("微软翻译：认证返回的 token 已到期或即将到期");
    }
    var endpoint = { token: result.t, region: result.r, expiresAt: expiresAt };
    source.put(TRANSLATOR_TOKEN_KEY, JSON.stringify(endpoint));
    return endpoint;
}

function synthesize(text, voice, params, options, ctx) {
    var body = buildTranslatorSsml(text, voice, params, ctx);
    var timeout = translatorTimeout(options);
    var endpoint = translatorEndpoint(timeout);
    return {
        url: "https://" + endpoint.region + ".tts.speech.microsoft.com/cognitiveservices/v1",
        method: "POST",
        headers: {
            // t 已含上游要求的形式，不能再次盲加 Bearer。
            "Authorization": endpoint.token,
            "X-Microsoft-OutputFormat": TRANSLATOR_OUTPUT_FORMAT,
            "User-Agent": "okhttp/4.5.0"
        },
        body: body,
        requestContentType: "application/ssml+xml",
        audioContentType: "audio/mpeg",
        timeout: timeout,
        retry: 0
    };
}

function automaticStyleValue(voice, ctx) {
    var supported = voice && voice.extra && voice.extra.azure_styles
        ? voice.extra.azure_styles
        : [];
    if (!supported.length) {
        return "";
    }
    var expressive = ctx && ctx.synthesis && ctx.synthesis.expressive
        ? ctx.synthesis.expressive
        : null;
    if (!expressive) {
        return "";
    }
    var concepts = [];
    if (expressive.emotion) {
        concepts.push(String(expressive.emotion));
    }
    var styleConcepts = expressive.style_concepts || [];
    for (var i = 0; i < styleConcepts.length; i++) {
        concepts.push(String(styleConcepts[i]));
    }
    for (var j = 0; j < concepts.length; j++) {
        var matched = styleForConcept(concepts[j], supported);
        if (matched) {
            return matched;
        }
    }
    return "";
}

function styleForConcept(concept, supported) {
    var normalized = String(concept || "").toLowerCase().replace(/^\s+|\s+$/g, "");
    if (!normalized) {
        return "";
    }
    if (containsStyle(supported, normalized)) {
        return normalized;
    }
    var aliases = expressiveStyleAliases();
    for (var i = 0; i < aliases.length; i++) {
        var item = aliases[i];
        if (!containsStyle(supported, item.style)) {
            continue;
        }
        for (var j = 0; j < item.terms.length; j++) {
            if (normalized.indexOf(item.terms[j]) >= 0) {
                return item.style;
            }
        }
    }
    return "";
}

function containsStyle(styles, expected) {
    for (var i = 0; i < styles.length; i++) {
        if (String(styles[i]).toLowerCase() === expected) {
            return true;
        }
    }
    return false;
}
