# 作品成分与可学习偏好

格式：`tag_id｜显示名｜检测性｜提问策略｜定义｜排除边界｜旧称/别名`。提问策略只有 `selection_impact|never_ask`：只有中性、可复用且会影响选书的作品特征使用 `selection_impact`；缺失策略、负面人物评价、质量判断和确定风险一律按 `never_ask` 处理。`adaptive` 只表示可以记录为作品事实，绝不再等同于“可以拿来问用户”。

## 基础中性路线（5）

这些 ID 与基础档案轴使用同一稳定值。快速定位可直接由 `relationship_profile.structure` 确认感情路线；用户已在 `dimension_answers` 明确回答同一轴时不得重复询问，本卡只保存本次显式三态，不同时写两份偏好。

- `route.no_romance`｜无感情线／无女主｜high｜selection_impact｜作品没有持续核心恋爱线或核心感情对象｜开篇尚未出现感情线不等于全书无感情线｜无女主
- `route.single_partner`｜单女主／一对一｜high｜selection_impact｜作品只有一名稳定核心感情对象｜单一样本只出现一名女性不够｜单女主
- `route.multi_partner`｜后宫／多女主路线｜high｜selection_impact｜主角与多名角色建立明确感情或归宿关系｜多名女性出场、单向好感或多 POV 不够｜后宫、多女主
- `center.single_male`｜单男主中心｜medium｜selection_impact｜一名男性承担主要叙事与因果中心｜高价值配角多不等于群像｜单男主
- `center.multi_male_ensemble`｜多男主真群像｜medium｜selection_impact｜多名男性主角各有不可替代的独立目标和主线｜频繁切视角不自动等于真群像｜群像

## 通用 `adaptive` 标签（27）

### 设定与叙事形式（14）

- `setting.system_present`｜有系统｜high｜selection_impact｜存在发任务、发奖励或提供交互规则的系统机制｜普通功法面板不自动算系统｜系统流
- `setting.sign_in_reward_loop`｜签到领奖｜high｜selection_impact｜签到、打卡、领取奖励是持续成长机制｜一次性领奖不算｜签到流
- `setting.numeric_panel_heavy`｜数值面板较重｜medium｜selection_impact｜属性、战力和技能数字反复占据正文并替代成长描写｜正常境界名不算｜属性面板流
- `format.national_destiny_competition`｜国运竞赛｜high｜selection_impact｜角色与国家绑定，胜负直接影响国运｜普通国家战争不算｜国运流、国运擂台
- `format.public_broadcast_exposure`｜全民围观／回忆曝光｜high｜selection_impact｜主角经历被全球直播、天道播放、榜单曝光或死后公开｜职业主播文不自动算｜天道直播、回忆曝光
- `format.audience_reaction_heavy`｜群众反应较多｜medium｜selection_impact｜弹幕、观众震惊和各国反应占显著篇幅并承担主要爽点｜少量旁观反应不算｜弹幕过多
- `power.invincible_from_start`｜开局无敌｜high｜selection_impact｜主角从开局起长期缺少可信威胁｜后期修炼所得的无敌不算｜无敌流
- `strategy.cautious_survival`｜苟道发育｜medium｜selection_impact｜核心乐趣是隐藏、避险和稳健积累｜短期谨慎不算｜苟道流
- `identity.gender_transformation`｜变身／性转｜high｜selection_impact｜主角性别发生实质转换｜易容、女装不算｜变身文、性转
- `structure.infinite_instances`｜无限副本｜high｜selection_impact｜反复进入相对独立的任务世界或副本并返回｜单个秘境不算｜无限流
- `setting.apocalypse_survival`｜末世生存｜high｜selection_impact｜文明崩溃、生存资源和环境威胁构成主要矛盾｜普通灾难篇章不算｜末世流
- `tone.dark_cruel`｜黑暗残酷｜medium｜selection_impact｜样本持续以残酷、压迫和低缓解为主要体验｜单个悲剧事件不够｜黑暗文
- `aesthetic.cthulhu_weird`｜克苏鲁／诡异风｜high｜selection_impact｜污染、疯狂、仪式和不可理解存在构成核心审美｜普通妖魔不算｜诡异流
- `style.meta_anti_trope`｜反套路写法｜medium｜selection_impact｜作品持续拆解、反转或嘲弄常见套路｜偶尔反转不算｜反套路文

### 叙事中心与主角体验（9）

- `narrative.protagonist_absent_long`｜主角长期掉线｜low｜never_ask｜单主角作品中配角线长期压过主角｜真正多男主群像不算｜群像戏过重
- `narrative.frequent_pov_switch`｜频繁切换视角｜medium｜selection_impact｜视角切换高频且明显打断单一代入｜偶尔插叙不算｜多视角切换
- `protagonist.system_driven`｜主角受系统支配｜medium｜never_ask｜关键目标和选择长期由系统任务决定，自主目标明显不足｜仅“有系统”不够｜系统傀儡
- `protagonist.mercy_causes_harm`｜对敌人反复心软｜low｜never_ask｜已知敌人会继续害人仍多次放过，并造成可预见损失｜正常善良不算｜圣母主角
- `protagonist.indecisive`｜优柔寡断｜low｜never_ask｜关键决策无充分理由地反复拖延、撤回或摇摆｜谨慎权衡不算｜优柔寡断
- `protagonist.passive_humiliation`｜长期窝囊受气｜medium｜never_ask｜角色具备现实可行的拒绝或反抗选择，却长期无合理策略依据地接受羞辱和压制｜囚禁、重伤、酷刑、药物／术法控制、无逃脱能力等受害处境不算；有明确收益的策略忍耐也不算｜窝囊主角
- `protagonist.romantic_doormat`｜感情中无底线低位｜medium｜never_ask｜明知对方伤害自己仍持续跪舔、服从或放弃尊严｜正常付出不算｜舔狗、惧女
- `protagonist.moral_bottomless`｜主角价值观无底线｜medium｜never_ask｜为便利或利益伤害无辜且缺少作品内追责｜杀敌和杀伐果断不算｜毫无人性
- `protagonist.brotherhood_over_self`｜兄弟义气压过自身利益｜low｜never_ask｜为兄弟反复牺牲自己、感情对象或核心利益｜正常互助不算｜兄弟情节

### 感情与后宫体验（4）

- `relationship.harem_rivalry`｜后宫争风吃醋｜medium｜selection_impact｜多名明确感情对象反复争斗｜一次玩笑不算｜修罗场
- `relationship.relationship_limbo`｜感情长期悬而不决｜low｜never_ask｜多个对象间反复横跳，或明确感情长期不确认｜正常慢热不算｜暧昧、炒股文
- `relationship.collection_without_development`｜收女多、铺垫少｜low｜never_ask｜大量角色迅速进入后宫且缺少独立关系发展｜多女主本身不算｜推土机
- `relationship.unresolved_love_interest`｜重要感情角色没收尾｜low｜never_ask｜充分铺垫的感情角色最终未收、消失或结局未交代｜普通配角不算｜漏女

## 条件式 `conditional` 标签（11）

- `harem.exclusive_love_history`｜后宫成员无他人感情或亲密经历｜low｜selection_impact｜精神与亲密经历均排他，包含明确前世｜只证明无性关系不够｜全初
- `harem.all_partners_sexually_inexperienced`｜后宫成员无既往性关系｜low｜selection_impact｜所有已确认后宫成员无既往性关系｜不代表无他人情感经历｜全处
- `harem.all_notable_love_interests_join`｜重要感情角色全部进入后宫｜low｜selection_impact｜有充分戏份与双向交集的重要女性全部收束｜仅出场或单向好感不够｜全收、QCQS 部分条件
- `relationship.partner_prior_marriage`｜女方有既往婚姻｜high｜selection_impact｜核心感情对象在当前或过去有明确婚姻｜传闻和假身份不算｜人妻
- `relationship.protagonist_intrudes_existing_relationship`｜主角介入他人既有关系｜medium｜never_ask｜主角主动破坏第三方稳定亲密关系并成为受益者｜双方已实质结束不算｜NTL
- `relationship.rejects_interested_love_interest`｜主角拒绝明确追求者｜medium｜selection_impact｜对方明确表达感情且主角明确拒绝｜未察觉或暂缓不算｜拒女
- `relationship.large_age_gap_older_partner`｜女方年龄明显更大｜high｜selection_impact｜女方与男主存在显著代际年龄差｜普通姐弟恋不算｜小马拉大车
- `relationship.close_kin_romance`｜近亲恋爱｜high｜never_ask｜直系或近亲之间形成恋爱关系｜无血缘的称谓关系不算｜骨科
- `narrative.second_generation_focus`｜主角子代戏份过重｜low｜selection_impact｜子代长期挤占原主角资源、感情或主线位置｜正常后日谈不算｜主二代
- `narrative.alternate_identity_romance`｜用另一个身份发展感情｜medium｜never_ask｜主角以伪装身份让感情对象对虚假身份产生持续感情｜短暂潜伏不算｜面具流
- `narrative.conscious_clone`｜有独立意识的分身｜medium｜selection_impact｜主角分身拥有自主意识和不可完全控制的选择｜纯傀儡身体不算｜分身流第二类

“逆推、阿黑颜、RBQ、WRQ、全家桶、母女、皇叔／刘备”等只描述成人内容或特定性癖，不进入通用标签库。未来若单独建设成人内容包，必须使用中性显示名和独立适用边界。
