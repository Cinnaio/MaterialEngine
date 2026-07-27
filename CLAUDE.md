# CLAUDE.md

## 项目简介

MateriaEngine 是 Paper/Folia 1.21.4 插件，用于承载 TeaStory 风格机器逻辑，并与 CraftEngine 内容包配合使用。

- 插件负责：机器交互、GUI、进度、配方逻辑、数据保存、Folia 安全调度。
- CraftEngine 负责：物品、方块、模型、贴图、GUI 字体图标。
- 当前 CraftEngine API 版本按服务器现状锁定为 `0.0.67`。
- 当前配套 CGAP-RESOURCE 资源版本：`0.17.0`（依赖其中的 `cgap:lemon` 与紫砂壶 `max-damage: 8`）。

## 开发规则

- 每实现一个功能就提交一次 Git commit。
- 每次完成代码/配置/文档修改都要同步版本管理。
- 提交信息使用中文 Conventional Commits。
- 不要把 TeaStory/CraftEngine 内容硬编码进大框架；优先配置驱动。
- CraftEngine 对接集中放在 `CraftEngineHook`，其他逻辑不要直接调用 CraftEngine API。
- 先做最小可运行闭环，再扩展机器。
- 仓库外文件需要用户明确授权到具体路径和版本变更后再修改。

## 构建

```bash
./gradlew build
```

## 配置结构

主配置位于：

```text
src/main/resources/config.yml
```

机器配置按功能分组：

- `block`：CraftEngine 方块 ID 与方块状态映射。
- `processing`：机器级默认加工时间。
- `inventory`：GUI 输入、燃料、输出槽位。
- `gui`：GUI 背景 token、标题布局、进度条配置。
- `recipes`：配方输入、条件、加工时间、输出。

示例结构：

```yaml
machines:
  tea-stove:
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
      fuel-slot: 13
      output-slot: 14
    gui:
      image-token: <image:cgap:tea_stove_gui>
      title: "<white><shift:-9>{image}<shift:-105>{progress}<shift:-94><reset>{name}"
      title-update-ticks: 5
      progress-image-width: 5
      progress-char-start: 59776
    recipes:
      broken-to-green:
        input:
          id: cgap:broken_tea_leaf
          amount: 1
        output:
          id: cgap:green_tea_leaf
          amount: 1
```

配置约定：

- `input.amount` / `output.amount` 保留在默认配置中便于阅读，但缺省时按 `1` 处理。
- `inventory.fuel-slot` 是可选项；没有该字段的机器不支持也不要求燃料。
- 有 `fuel-slot` 时，燃料物品提供热值，热值随时间流逝逐 tick 递减。
- 机器级 `fuel-items`（物品 id → 热值 tick）非空时是**独占燃料表**（原版 `isFuel` 失效，如发酵桶只吃 `cgap:baking_powder`）；为空时回退原版燃料判定。
- 单次加工需要的热值使用配方 `process-ticks`；配方未配置时使用 `processing.process-ticks`。
- 简单加工机配方支持 `conditions.weather: clear|rain|thunder|any`（按机器所在世界天气匹配）。
- 物品 id 同时接受 CraftEngine id 与 `minecraft:` 原版 id（输入匹配与输出/返还创建都支持）。
- GUI 使用 `image-token`，不要回退为 CE 自动分配的 `image-char`。
- GUI `title` 是布局控制字符串，放在 `config.yml`；`lang/*.yml` 只放可读名称和消息。
- 方块状态统一使用 `block.state.property/type/default/filled/running`，不保留旧字段 fallback。
- 茶桌配方输入支持一槽多候选：`inputs.<槽名>.any` 列表，每个候选可带自己的 `consume-replacement`（按实际消耗的物品返还空容器）；`id` 也可写成字符串列表，共享同一个 `consume-replacement`。开水壶候选集用 YAML 锚点 `&boiled_water` 复用（Paper 的 YamlConfiguration 别名数无上限）。
- 茶桌输入带 `damage: N` 时不扣数量，改为对 Damageable meta 累加 N 耐久；耐久耗尽按候选的 `consume-replacement` 替换（如满壶倒空换空壶、茶筅打断消失）。用于茶筅损耗与壶装倒茶。
- 茶桌按数量消耗且带 `consume-replacement` 的输入：返还数量 = 消耗数量（紫砂壶 2 茶包返 2 茶渣）；槽内有剩余时返还物放入茶桌储物格，放不下则掉落在方块上方。
- 茶桌配方按声明槽位做**首匹配**（未声明的槽不参与匹配）：声明 tool/sugar 的特化配方（奶茶/柠檬茶/抹茶）必须排在同茶底的通用配方之前，否则会被通用配方抢先且不消耗奶糖。
- 机器音效在 `effects.sounds` 下配置，全部走原版声音事件（零 ogg）：`open/close/start/finish/ambient/fuel-consume`，每项 `{ key, volume, pitch }`，`ambient` 额外支持 `interval`（tick 节流，默认 40）；缺项不播。茶桌无燃料槽故无 fuel-consume，声音在机器方块位置以 BLOCKS 类别播放。
- 开工失败提示（no-recipe/not-enough-input/output-blocked）只发给触发交互的玩家（点击/拖拽/Shift 投料路径），tick 自动续做保持静默；简单机器要求输入槽非空、茶桌要求某条配方声明的槽位全部已放物（nearMiss）才发 no-recipe，避免摆料阶段刷屏。储物 GUI 标题走 lang 根键 `storage-title`。

## 当前机器

工艺链唯一正道（对齐原版 TeaStory 全机器工艺）：

```text
茶树收获 5 种分级鲜叶
  → 茶盘(晴天)萎凋成同级萎凋叶 / (雨·雷)淋成湿叶(失分级) / 湿叶晴天挽救成无分级萎凋叶
  → 炒茶锅杀青成青叶(芽/一叶 ×2) / 湿叶误炒成焦叶
  → 炒茶锅炒青=绿茶；茶炉烘青=白茶、蒸青(绿茶)=抹茶；茶盘闷黄(绿茶)=黄茶
  → 研钵碎茶 → 发酵桶(发酵粉驱动)三级发酵 → 茶炉烘焙成乌龙/红茶/普洱
  → 茶桌冲泡(杯/壶) + 倒茶；茶渣×2 → 发酵粉(回收闭环)
```

- `cgap:tea_drying_pan`：炒茶锅，原版燃料，杀青/炒青/误炒陷阱（8 条，不看天）。
- `cgap:teapan`：茶盘，露天无燃料，看天萎凋/淋湿/挽救/闷黄（17 条）。
- `cgap:barrel`：发酵桶，`fuel-items` 独占燃料（发酵粉 800 tick），碎茶三级发酵（3 条），复用茶炉 GUI 布局。
- `cgap:tea_stove`：茶炉，原版燃料，烘焙三种发酵叶 + 烘青白茶 + 蒸青抹茶（5 条）。
- `cgap:tea_table`：茶桌，六槽多输入冲泡机器（tool/sugar/cup/water/leaf → drink），内置 108 条配方：15 特化杯装（奶/柠/抹）+ 6 特化壶装 + 30 纯茶杯装 + 12 纯茶壶装 + 45 倒茶（满壶按耐久倒杯，瓷壶 4 杯/紫砂壶 8 杯）。

## 关键类

```text
src/main/java/com/github/cinnaio/materiaengine/MateriaEnginePlugin.java
src/main/java/com/github/cinnaio/materiaengine/feature/SimpleProcessingMachineGui.java
src/main/java/com/github/cinnaio/materiaengine/feature/TeaTableGui.java
src/main/java/com/github/cinnaio/materiaengine/config/BlockStateConfig.java
src/main/java/com/github/cinnaio/materiaengine/config/MachineGuiLayout.java
src/main/java/com/github/cinnaio/materiaengine/config/MachineSounds.java
src/main/java/com/github/cinnaio/materiaengine/data/MachineDataStore.java
src/main/java/com/github/cinnaio/materiaengine/data/StoredMachine.java
src/main/java/com/github/cinnaio/materiaengine/util/CraftEngineHook.java
src/main/java/com/github/cinnaio/materiaengine/util/MachineItems.java
```

## CGAP-RESOURCE 配合项

CraftEngine 内容包在：

```text
E:\Developments\Projects\CGAP-RESOURCE
```

当前已使用（详见 `config.yml`，共 123 个 cgap id + 原版 `minecraft:sugar/milk_bucket/bucket`）：

```text
机器方块: cgap:tea_drying_pan / teapan / barrel / tea_stove / tea_table
茶叶链: fresh_tea_leaf_{bud,bud_leaf1,bud_leaf2,bud_leaf3,old_leaf} → withered_* 同后缀
        wet_tea_leaf / withered_tea_leaf / failed_fixation_tea_leaf / tea_leaf / broken_tea_leaf
        green/yellow/white/matcha/oolong/black/puer_tea_leaf + semi/fully/deep_fermented_tea_leaf
辅料: cgap:baking_powder（发酵桶燃料）/ cgap:lemon（0.17.0 新增）/ cgap:tea_whisk（耐久 120）
容器: cup_{glass,stone,wood,porcelain,zisha} / empty_{porcelain,zisha}_kettle
      pot_* 与 boiled_water_pot_*（stone/porcelain/iron/zisha）
成品: {black,green,oolong,puer,white,yellow}_tea_<杯材质> / matcha_drink_* / milk_tea_* / lemon_tea_*
      九味 × 瓷壶/紫砂壶 的 *_kettle 满壶 + 六味 *_tea_bag 与 *_tea_residue
GUI: cgap:tea_drying_pan_gui / tea_stove_gui / tea_table_gui
     cgap:tea_progress_0..108 / tea_stove_progress_0..5
```

## 验证

1. `./gradlew build`
2. 将 jar 放入服务端
3. 确认 CraftEngine 已加载 CGAP-RESOURCE
4. 放置机器方块
5. 右击打开 GUI，潜行右击检查内部存储入口
6. 放入允许输入；茶炉同时放入燃料
7. 等待产出，确认进度条、方块状态、燃料余热和数据保存正常
