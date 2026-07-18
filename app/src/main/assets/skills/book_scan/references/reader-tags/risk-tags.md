# 内置严重风险

以下 11 项均为 `layer=always_warn`。它们不是正向爱好选项；用户防御再高也要看见已确认事实，只允许调整推荐权重和措辞。

格式：`tag_id｜显示名｜检测性｜定义｜排除边界｜旧称/别名`。

- `risk.committed_partner_betrayal`｜核心伴侣自愿背叛｜high｜建立排他承诺后，自愿发生情感或身体背叛｜被强迫必须另记，不能说成角色主动背叛｜背叛、绿帽的一类
- `risk.third_party_sexual_violation`｜核心角色遭第三方性侵害｜high｜非自愿性侵害或严重身体边界侵犯｜轻度言语骚扰不能升级到本项｜强迫型绿帽风险
- `risk.developed_love_interest_paired_elsewhere`｜重要感情角色最终归他人｜high｜与主角已有充分感情投入的角色永久和第三方配对｜普通女性配角不算｜送女、神雕的结果型风险
- `risk.forced_third_party_marriage`｜核心感情角色被迫与第三方成婚｜high｜公开婚姻名分或名义婚姻已经成立｜未圆房、第三方无性能力都不能抵消名分和原关系影响｜强迫婚配
- `risk.core_love_interest_death`｜核心感情角色不可逆死亡｜high｜正文确认死亡且结局无复活可能｜疑似死亡用 `outcome_unknown`，复活后保留事件并用 `reversed`｜死女
- `risk.core_love_interest_severe_abuse`｜核心感情角色遭严重虐待｜medium｜长期囚禁、酷刑、公开凌辱或造成永久伤害｜普通冲突、一次受伤或轻度骚扰不够｜重度亵女、虐女
- `risk.extreme_protagonist_abuse`｜主角长期遭极端虐待｜medium｜长期受辱、永久伤残、囚禁或持续失去主动权｜普通挫折和短期低谷不算｜重度／极度虐主
- `risk.abuser_reunited_without_accountability`｜严重虐待后强行复合｜low｜施害者造成严重或不可逆伤害，却缺少有意义的追责与修复，靠中毒、失忆、控制等免责并在受害者缺少自由选择时复合｜已明确展示长期追责／赎罪、实质修复且受害者自由选择时排除；结局已满足排除条件时不得再以 `threatened` 命中｜虐恋强行复合
- `risk.protagonist_death`｜主角结局死亡｜high｜正文确认主角不可逆死亡｜假死或结果未明使用证据状态｜诡雷示例之一
- `risk.unexpected_protagonist_gender_change`｜非预期永久性转｜high｜作品未明示变身题材，却在后期永久改变主角性别｜开篇已明示的变身题材是普通成分标签｜诡雷示例之一
- `status.abandoned_or_hiatus`｜长期断更／未完结停更｜high｜元数据和更新时间表明作品长期停止且未完结｜正常连载间隔不算｜太监

## 关系风险必须拆事实

“NTR／绿帽”只能作为用户可见概括或输入别名，底层必须区分：

- 自愿背叛：`risk.committed_partner_betrayal`
- 被强迫侵害：`risk.third_party_sexual_violation`
- 被迫公开婚姻：`risk.forced_third_party_marriage`
- 已充分铺垫的感情角色最终归他人：`risk.developed_love_interest_paired_elsewhere`

同一事件可以命中多个独立事实，但报告必须合并同一根因，不能拆成四张近义警告。`threatened|outcome_unknown` 只说明风险出现，不能写成结果已经发生。
