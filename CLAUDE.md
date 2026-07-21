# CLAUDE.md

## 项目简介

MateriaEngine 是 Paper/Folia 1.21.4 插件，用于承载 TeaStory 风格机器逻辑，并与 CraftEngine 内容包配合使用。

- 插件负责：机器交互、GUI、进度、配方逻辑、数据保存、Folia 安全调度。
- CraftEngine 负责：物品、方块、模型、贴图、GUI 字体图标。
- 当前 CraftEngine API 版本按服务器现状锁定为 `0.0.67`。
- 当前配套 CGAP-RESOURCE 资源版本：`0.13.3`。

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
- 单次加工需要的热值使用配方 `process-ticks`；配方未配置时使用 `processing.process-ticks`。
- GUI 使用 `image-token`，不要回退为 CE 自动分配的 `image-char`。
- GUI `title` 是布局控制字符串，放在 `config.yml`；`lang/*.yml` 只放可读名称和消息。
- 方块状态统一使用 `block.state.property/type/default/filled/running`，不保留旧字段 fallback。

## 当前机器

- `cgap:tea_drying_pan`：炒茶锅，按天气处理茶叶，右击加工 GUI，潜行右击内部存储。
- `cgap:teapan`：茶盘，简单加工机器。
- `cgap:barrel`：发酵桶，简单加工机器。
- `cgap:tea_stove`：茶炉，需要燃料，使用专用 GUI 与 `tea_stove_progress_0..5`。
- `cgap:cooking_pan`：平底锅，需要燃料。
- `cgap:tea_table`：茶桌 GUI 展示入口。

## 关键类

```text
src/main/java/com/github/cinnaio/materiaengine/MateriaEnginePlugin.java
src/main/java/com/github/cinnaio/materiaengine/feature/TeaDryingPanGui.java
src/main/java/com/github/cinnaio/materiaengine/feature/SimpleProcessingMachineGui.java
src/main/java/com/github/cinnaio/materiaengine/feature/TeaTableGui.java
src/main/java/com/github/cinnaio/materiaengine/config/BlockStateConfig.java
src/main/java/com/github/cinnaio/materiaengine/config/MachineGuiLayout.java
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

当前已使用：

```text
cgap:tea_drying_pan
cgap:teapan
cgap:barrel
cgap:tea_stove
cgap:cooking_pan
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
4. 放置机器方块
5. 右击打开 GUI，潜行右击检查内部存储入口
6. 放入允许输入；茶炉/平底锅同时放入燃料
7. 等待产出，确认进度条、方块状态、燃料余热和数据保存正常
