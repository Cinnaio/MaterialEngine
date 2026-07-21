# MateriaEngine

MateriaEngine 是 Paper/Folia 1.21.4 插件，用于承载 TeaStory 风格机器逻辑，并与 CraftEngine 内容包配合使用。

## 职责拆分

- 插件：机器交互、GUI、进度、配方逻辑、数据保存、Folia 安全调度。
- CraftEngine：物品、方块、模型、贴图、GUI 字体图标。
- 当前 CraftEngine API 版本锁定为 `0.0.67`。

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
| 平底锅 | `cgap:cooking_pan` | 需要燃料的简单加工机器。 |
| 茶桌 | `cgap:tea_table` | GUI 展示入口。 |

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
E:\Developments\Projects\CGAP-RESOURCE
```

当前配套资源版本：

```text
0.13.3
```

已使用的主要资源：

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
4. 放置对应机器方块
5. 右击打开 GUI
6. 放入允许输入；需要燃料的机器同时放入燃料
7. 等待产出并确认进度条、方块状态和存储保存正常
