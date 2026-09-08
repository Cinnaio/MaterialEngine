# MateriaEngine

MateriaEngine 是 Paper/Folia 26.1.x 插件（已用 26.1.2 验证），用于承载 TeaStory 风格机器逻辑，并与 CraftEngine 内容包配合使用。

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

主配置：

```text
src/main/resources/config.yml
```

机器配置按功能分组：

```yaml
machines:
  example-machine:
    block:
      id: cgap:example_machine
      state:
        property: stage
        type: int
        default: 0
        filled: 1
        running: 1
    processing:
      process-ticks: 100
    inventory:
      input-slot: 12
      fuel-slot: 13 # 可选；省略则不需要燃料
      output-slot: 14
    gui:
      image-token: <image:cgap:tea_drying_pan_gui>
      title: "<white><shift:-9>{image}<shift:-143>{progress}<shift:-138><reset>{name}"
      title-update-ticks: 5
      progress-image-width: 108
    recipes:
      recipe-id:
        input:
          id: cgap:tea_leaf
          amount: 1
        process-ticks: 100
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
- GUI `title` 属于布局配置；语言文件只保存可读名称和提示文本。

## 当前机器

| 机器 | 方块 ID | 说明 |
| --- | --- | --- |
| 炒茶锅 | `cgap:tea_drying_pan` | 按天气处理茶叶，右击打开加工 GUI，潜行右击打开内部存储。 |
| 茶盘 | `cgap:teapan` | 简单加工机器。 |
| 发酵桶 | `cgap:barrel` | 简单加工机器。 |
| 茶炉 | `cgap:tea_stove` | 需要燃料的简单加工机器，使用专用 GUI。 |
| 茶桌 | `cgap:tea_table` | GUI 展示入口。 |

## 采收工具

`2.1.0-SNAPSHOT` 对接 CGAP-RESOURCE `0.18.46` 的精制采茶剪、花草采收剪、根茎采掘铲、长柄采果杆与竹编采收篓，并将镰刀覆盖补齐到 14 种自定义作物阶段及 5 种原版作物。

- `sickle` 保留既有物品、半径、种子与成熟阶段配置；`harvest-tools` 配置各工具、奖励概率、茶叶品质转换、六种果树和重新结果时间。旧文件缺失的新字段从随包默认配置合并，已有配置优先；`seed: ''` 可禁用某种作物。
- 茶剪把较低品质鲜叶以 35% 概率转为单芽或一芽一叶，数量不变；花草剪与根茎铲分别有 20% / 25% 概率额外产出一份对应原料。先结算原掉落表中的时运，再应用一种主工具增益，种子不参与额外奖励。
- 作物采后若背包已有对应种子则消耗一份原位续种，没有种子则移除；不使用本次新掉落的种子。副手采收篓只收取本次产物，溢出落地；实际入包数量沿用 BeaconEngine 获取统计接口。
- 采果杆从眼睛位置沿视线定点采果，默认 5 格，遇到第一个方块即停止，不穿墙或树叶。先清除 `fruiting` 再发放一个果实；自然树叶默认 20 分钟后重新结果，计时存储在区块 PDC 中，可跨重启恢复，加载后每分钟检查一次，不强制加载区块。
- 只处理主手生存/创造模式交互，逐目标检查 CraftEngine 的交互、破坏及续种放置权限，并遵守自定义破坏事件、原版破坏事件和 `PlayerHarvestBlockEvent` 的取消结果。每株扣一次主手耐久，破损后立即停止。采收和射线路径仅访问已加载且当前线程拥有的区域，果实计时使用区域调度器。
- CraftEngine 对接集中在 `CraftEngineHook`，兼容 `26.8` 的 Builder 掉落上下文和 `26.9-SNAPSHOT` 的 ContextHolder / 破坏事件签名。真实保护插件、客户端射线、物品附魔和果实恢复仍需实机测试；此处区域检查不代表其他现有机器模块已完成 Folia 运行验证。

验证：`./gradlew test build`。指定 `-PcraftEngineRuntimeJar=<服务端 CraftEngine jar>` 可额外核对该 JAR 的采收 API 签名。资源包侧使用 `_tools/validate_teastory_harvest_tools.py --materia-config <本仓库 src/main/resources/config.yml>` 验证工具 ID、种子、成熟阶段、果树、模型、材质、配方与翻译。

## 关键类

```text
src/main/java/com/github/cinnaio/materiaengine/MateriaEnginePlugin.java
src/main/java/com/github/cinnaio/materiaengine/feature/TeaDryingPanGui.java
src/main/java/com/github/cinnaio/materiaengine/feature/SimpleProcessingMachineGui.java
src/main/java/com/github/cinnaio/materiaengine/config/BlockStateConfig.java
src/main/java/com/github/cinnaio/materiaengine/config/MachineGuiLayout.java
src/main/java/com/github/cinnaio/materiaengine/data/MachineDataStore.java
src/main/java/com/github/cinnaio/materiaengine/util/CraftEngineHook.java
```

## CGAP-RESOURCE

CraftEngine 内容包在：

```text
E:\Games\Minecraft Server\purpur-1.21.4-2416-server\plugins\CraftEngine\resources
```

当前配套资源版本：

```text
0.18.46
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
