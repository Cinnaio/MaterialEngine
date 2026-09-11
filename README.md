# TeaStory

TeaStory 是 Paper/Folia 26.1.x 插件（已用 26.1.2 验证），用于承载 TeaStory 机器逻辑，并与 CraftEngine 内容包配合使用。

主分支使用 Java 25 与 CraftEngine `26.8`；旧版 1.21.4 构建保留在 `legacy/1.21.4` 分支。

## 职责拆分

- 插件：机器交互、GUI、进度、配方逻辑、数据保存、Folia 安全调度。
- CraftEngine：物品、方块、模型、贴图、GUI 字体图标。
- 当前 CraftEngine API 版本锁定为 `26.8`。

## 构建

```bash
./gradlew build
```

构建产物位于：

```text
build/libs/
```

## 配置

主配置与定义文件：

```text
src/main/resources/config.yml
src/main/resources/definitions/machines/<machine>.yml
src/main/resources/definitions/tools/<tool>.yml
```

`config.yml` 只保存全局运行参数；每台机器和每个采收工具各自使用一个 YAML 文件。运行时默认文件会复制到插件数据目录：

```text
plugins/TeaStory/config.yml
plugins/TeaStory/definitions/machines/<machine>.yml
plugins/TeaStory/definitions/tools/<tool>.yml
```

例如 `definitions/machines/tea-stove.yml` 直接从机器字段开始，不再包在 `machines.<name>` 下：

```yaml
block:
  id: cgap:tea_stove
  state:
    property: lit
    type: boolean
    default: 0
    filled: 0
    running: 1
processing:
  process-ticks: 200
inventory:
  input-slot: 12
  fuel-slot: 13 # 可选；省略则不需要燃料
  output-slot: 14
gui:
  image-token: <image:cgap:tea_stove_gui>
  title: "<white><shift:-9>{image}<shift:-105>{progress}<shift:-94><reset>{name}"
  title-update-ticks: 5
  progress-image-width: 5
recipes:
  broken-to-green:
    input:
      id: cgap:broken_tea_leaf
      amount: 1
    output:
      id: cgap:green_tea_leaf
      amount: 1
```

说明：

- `input.amount` / `output.amount` 可省略，默认 `1`。
- `inventory.fuel-slot` 可省略；省略时机器不支持也不需要燃料。
- 存在 `fuel-slot` 时，配方 `process-ticks` 同时作为单次加工燃料消耗依据。
- 配方未写 `process-ticks` 时，使用机器级 `processing.process-ticks`。
- GUI 背景使用稳定的 `image-token`，不依赖 CE 自动分配的字符。
- GUI `title` 属于对应的 `definitions/machines/<machine>.yml`；语言文件只保存可读名称和提示文本。

旧版 `config.yml` 中的 `machines`、`sickle` 和 `harvest-tools.tools` 会在首次启动时迁移到对应定义文件；旧的 `plugins/MateriaEngine` 数据目录也会复制到 `plugins/TeaStory`，不会删除旧文件。

## 当前机器

| 机器 | 方块 ID | 说明 |
| --- | --- | --- |
| 炒茶锅 | `cgap:tea_drying_pan` | 按天气处理茶叶，右击打开加工 GUI，潜行右击打开内部存储。 |
| 茶盘 | `cgap:teapan` | 简单加工机器。 |
| 发酵桶 | `cgap:barrel` | 简单加工机器。 |
| 茶炉 | `cgap:tea_stove` | 需要燃料的简单加工机器，使用专用 GUI。 |
| 茶桌 | `cgap:tea_table` | GUI 展示入口。 |

## 采收工具

`2.6.0-SNAPSHOT` 对接 CGAP-RESOURCE `0.18.47` 的精制采茶剪、花草采收剪、根茎采掘铲、长柄采果杆与竹编采收篓，并将镰刀覆盖补齐到 14 种自定义作物阶段及 5 种原版作物。

- `definitions/tools/sickle.yml` 保存镰刀物品、半径、种子与成熟阶段；其他工具分别位于 `definitions/tools/<tool>.yml`，全局开关、通用冷却、耐久、六种果树和重新结果时间仍在 `config.yml` 的 `harvest-tools` 下。旧文件缺失的新字段从随包默认定义合并，已有配置优先；`seed: ''` 可禁用某种作物。
- 茶剪把较低品质鲜叶以 35% 概率转为单芽或一芽一叶，数量不变；花草剪与根茎铲分别有 20% / 25% 概率额外产出一份对应原料。先结算原掉落表中的时运，再应用一种主工具增益，种子不参与额外奖励。
- 作物采后若背包已有对应种子则消耗一份原位续种，没有种子则移除；不使用本次新掉落的种子。副手采收篓只收取本次产物，溢出落地；实际入包数量沿用 BeaconEngine 获取统计接口。
- 采果杆从眼睛位置沿视线定点采果，默认 5 格，遇到第一个方块即停止，不穿墙或树叶。先清除 `fruiting` 再发放一个果实；自然树叶默认 20 分钟后重新结果，计时存储在区块 PDC 中，可跨重启恢复，加载后每分钟检查一次，不强制加载区块。
- 只处理主手生存/创造模式交互，逐目标检查 CraftEngine 的交互、破坏及续种放置权限，并遵守自定义破坏事件、原版破坏事件和 `PlayerHarvestBlockEvent` 的取消结果。每株扣一次主手耐久，破损后立即停止。采收和射线路径仅访问已加载且当前线程拥有的区域，果实计时使用区域调度器。
- CraftEngine 对接集中在 `CraftEngineHook`，兼容 `26.8` 的 Builder 掉落上下文和 `26.9-SNAPSHOT` 的 ContextHolder / 破坏事件签名。真实保护插件、客户端射线、物品附魔和果实恢复仍需实机测试；此处区域检查不代表其他现有机器模块已完成 Folia 运行验证。

验证：`./gradlew test build`。指定 `-PcraftEngineRuntimeJar=<服务端 CraftEngine jar>` 可额外核对该 JAR 的采收 API 签名。资源包侧校验脚本仍按旧的 `--materia-config` 布局读取，需在资源包仓库单独适配到 `definitions/`；本次未修改仓库外脚本。

### 采收反馈与统计（2.2.0-SNAPSHOT）

`2.2.1-SNAPSHOT` 在采收汇总中显示续种株数、缺种未续种株数及采收篓的背包溢出数量；未携带采收篓的正常落地不会显示满包提示，采果不显示续种信息。

沿用 CGAP-RESOURCE `0.18.46`，无需添加声音文件或更新工具模型。成功采收在目标位置向操作者播放声音和粒子，ActionBar 显示采收株数、直接入包和落地数量；品质提升、额外产量和采果使用各自的反馈。一次范围收割只汇总反馈一次，未成熟、未结果、服务不可用、取消或保护阻止时显示对应提示，失败与冷却提示默认至少间隔 20 tick。工具仍拦截原版锄地/铲路行为，同时允许箱子及机器等无关方块的正常交互。

全局参数放在 `config.yml`，机器和工具定义放在各自的 `definitions/` 文件中，通过 `/teastory reload`（兼容 `/ts`）重载；缺少的新字段自动继承随包默认值，已有自定义语言文件也继承新增消息。无效粒子配置会拒绝整份采收设置并保留上次有效值，命令明确报告采收配置未生效。

| 配置路径 | 默认值与边界 |
| --- | --- |
| `harvest-tools.cooldown-ticks` / `durability-cost` | 每次动作冷却 4 tick，每株耐久消耗 1；分别限制在 0–200 / 0–100，0 表示不施加冷却/不损耗。 |
| `definitions/tools/<工具>.yml: cooldown-ticks` / `durability-cost` | 可覆盖通用值；镰刀单独写在 `definitions/tools/sickle.yml`。 |
| `definitions/tools/<工具>.yml: chance` / `target-chances.<作物ID>` | 主工具概率及单作物覆盖，范围 0–1；品质转换仍使用 `quality-sources` 和 `quality-targets`。 |
| `definitions/tools/<工具>.yml: bonus-amount` | 额外产量模式默认增加 1 件，可设 0–64；不改变种子掉落。 |
| `definitions/tools/sickle.yml: radius` / `definitions/tools/fruit-picker.yml: reach` | 镰刀半径默认 1（3×3），范围 0–2；采果射程默认 5 格，范围 1–6。 |
| `harvest-tools.regrow-seconds` / `regrow-overrides.<树叶ID>` | 默认 1200 秒，可按树叶种类覆盖，范围 60–604800 秒；只影响新采收记录，已有区块到期时间不变。 |
| `harvest-tools.feedback` | 总开关、ActionBar、声音和粒子可分别关闭；`success/bonus/quality/fruit/failure/cooldown` 可配声音、音量、音调、粒子、数量和范围。粒子仅接受无需额外数据的 Bukkit 名称，空名称表示关闭。 |
| `harvest-tools.feedback.failure-interval-ticks` | 默认 20，范围 0–200；失败/冷却提示也不会立即覆盖最近的成功反馈。 |
| `harvest-tools.stats.enabled` / `include-creative` / `flush-seconds` | 默认开启统计、排除创造模式，每 30 秒保存；保存间隔范围 5–3600 秒。关闭统计仅暂停新增记录。 |

统计按玩家 UUID、主工具 ID、作物/树叶 ID 保存采收次数，并按具体产物 ID 保存数量，因此单芽、一芽一叶等品质可分别查看。统计只在成功改变目标并完成掉落处理后累计，包含原掉落表的种子；入包数按采收篓实际接收计算，落地数按成功生成的物品实体计算。品质提升与额外产量按事件处理后仍可确认的增量记录，取消的采收或被移除的奖励不增加这些计数。

落地表示本次产出，不表示已被玩家捡起；BeaconEngine 仍只在直接入包时由 TeaStory 调用物品获得接口，落地物品由 BeaconEngine 原有拾取监听统计，避免重复上报。

```text
/teastory harvest stats                    查看自己的统计，控制台默认查看全服
/teastory harvest stats all                查看全服汇总
/teastory harvest stats <在线玩家名或UUID>   查看指定玩家；离线玩家使用 UUID
/teastory reload                           重载参数、定义文件与中英文消息
```

个人查询使用默认开放的 `teastory.harvest` 权限，他人/全服查询需要 `teastory.harvest.others` 或 `teastory.admin`；导出与重载需要 `teastory.admin`。旧的 `materiaengine.*` 权限仍兼容。文本查询包含总计、采收次数最多的五个工具/作物组合，以及产量最多的五种产物；完整明细保存在 `plugins/TeaStory/harvest_stats.db` 的 `harvest_stats` 和 `harvest_outputs` 表。

数据库只在启动时加载，采收路径更新内存；Paper/Folia 异步定时器以事务批量保存变更行，写入失败后保留待写记录供下个周期重试，正常停服保存最后一批。强制终止进程可能丢失尚未保存的数据，最多约一个保存周期。数据库加载失败会明确记录错误并阻止覆盖已有文件，需要修复原因后重启。自动化测试覆盖 SQLite 重开、重复写入、并发更新、事务回滚与重试；声音、粒子和保护插件的真实客户端表现仍需服务端验收。

### 日周统计与导出（2.3.0-SNAPSHOT）

`harvest-tools.stats.timezone` 默认 `Asia/Shanghai`，今日按当地零点划分，本周从周一开始。累计与每日记录在同一个 SQLite 事务内保存，新增 `harvest_daily_stats` 和 `harvest_daily_outputs` 两张表。旧版本的累计统计完整保留，日周统计只从升级后的实际采收开始；修改时区不会重新划分已经保存的日期。

```text
/teastory harvest stats today
/teastory harvest stats week
/teastory harvest stats all today
/teastory harvest stats <在线玩家名或UUID> week
/teastory harvest export [all|在线玩家名|UUID] [all|today|week]
```

CSV 异步保存到 `plugins/TeaStory/exports/`，采用带 BOM 的 UTF-8 编码并正确转义逗号、引号及公式前缀。`record_type=harvest` 是工具/作物的采收汇总，`record_type=output` 是具体产物明细，汇总行与明细行应分别筛选后求和；累计导出的 `date` 为空，日周导出带实际记录日期。一次只运行一个导出，导出不会清零或修改统计。

### 玩家采收面板（2.4.0-SNAPSHOT）

玩家使用 `/teastory`、`/teastory harvest` 或 `/teastory harvest menu` 打开采收手册。面板以实际物品图标、物品名称和中英文作物名称展示总览、产物与品质、工具、作物与果树四个分页视图；每页 21 条，可切换累计/今日/本周、翻页和刷新。所有内容都是只读展示，Shift、数字键、丢弃和拖拽等操作不会取走图标。

`/teastory harvest menu <在线玩家名或UUID>` 和 `/teastory harvest menu all` 提供管理员查询入口，面板每次交互都重新检查查看权限。关闭面板及停用插件会清空虚拟图标，统计本身不受影响。未安装 CraftEngine 或某个内容 ID 无法解析时，用纸张及原 ID 显示回退条目。

### 种子袋（2.6.0-SNAPSHOT）

CGAP-RESOURCE `0.18.47` 新增 `cgap:seed_pouch`，由 3 皮革、1 线和 1 蓝色染料合成。手持右键或 `/teastory harvest pouch` 打开种子袋；点击背包种子存入，点击袋中种子取出，右键每次转移 1 个，底栏可一键存入或取出。背包放不下的种子留在袋中。

`harvest-tools.seed-pouch` 配置开关、物品 ID 与 9/18/27 格容量，每格保留种子的原始堆叠上限和完整元数据。只接收 `definitions/tools/sickle.yml` 的 `crops` 声明的种子，禁止套袋。内容保存在实体物品的 PDC 中，随物品转移和保存；缩小容量不会裁掉已有物品。每次操作立即保存，GUI 仅作展示，损坏或堆叠的袋子拒绝修改；旧 `materiaengine` 命名空间的袋子数据会在读写时兼容迁移。

镰刀和采收工具续种时优先使用背包散装种子，再使用背包或副手种子袋；仅在保护检查通过且作物成功重置后消耗 1 个种子，不使用本次新掉落的种子。自动化测试覆盖存取守恒、部分入包、元数据、过期快照、配置缩容、损坏数据、GUI 转移限制及受保护作物不扣种；真实客户端操作仍需服务端验收。

每次采收还会向已安装的 BeaconEngine 发送一次采收、产物、品质、额外产量和果树事件；BeaconEngine 以持久化触发进度计算采收成就，缺少 BeaconEngine 时不影响本插件运行。

## 关键类

```text
src/main/java/com/github/cinnaio/teastory/TeaStoryPlugin.java
src/main/java/com/github/cinnaio/teastory/config/DefinitionFiles.java
src/main/java/com/github/cinnaio/teastory/feature/TeaDryingPanGui.java
src/main/java/com/github/cinnaio/teastory/feature/SimpleProcessingMachineGui.java
src/main/java/com/github/cinnaio/teastory/config/BlockStateConfig.java
src/main/java/com/github/cinnaio/teastory/config/MachineGuiLayout.java
src/main/java/com/github/cinnaio/teastory/data/MachineDataStore.java
src/main/java/com/github/cinnaio/teastory/util/CraftEngineHook.java
```

## CGAP-RESOURCE

CraftEngine 内容包在：

```text
E:\Games\Minecraft Server\purpur-1.21.4-2416-server\plugins\CraftEngine\resources
```

当前配套资源版本：

```text
0.18.47
```

已使用的主要资源：

```text
cgap:tea_drying_pan
cgap:teapan
cgap:barrel
cgap:tea_stove
cgap:tea_table
cgap:fresh_tea_leaf
cgap:withered_tea_leaf
cgap:tea_leaf
cgap:green_tea_leaf
cgap:broken_tea_leaf
cgap:semi_fermented_tea_leaf
cgap:fully_fermented_tea_leaf
cgap:deep_fermented_tea_leaf
cgap:oolong_tea_leaf
cgap:black_tea_leaf
cgap:puer_tea_leaf
cgap:tea_drying_pan_gui
cgap:tea_stove_gui
cgap:tea_table_gui
cgap:tea_progress_0..108
cgap:tea_stove_progress_0..5
```

## 验证

1. `./gradlew build`
2. 将 jar 放入服务端
3. 确认 CraftEngine 已加载 CGAP-RESOURCE
4. 放置对应机器方块
5. 右击打开 GUI
6. 放入允许输入；需要燃料的机器同时放入燃料
7. 等待产出并确认进度条、方块状态和存储保存正常
