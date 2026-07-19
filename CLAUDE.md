# CLAUDE.md

## 项目简介

MateriaEngine 是 Paper/Folia 1.21.4 插件，用于承载 TeaStory 风格机器逻辑，并与 CraftEngine 内容包配合使用。

- 插件负责：机器交互、GUI、进度、配方逻辑、数据保存、Folia 安全调度。
- CraftEngine 负责：物品、方块、模型、贴图、GUI 图标。
- 当前 CraftEngine 版本按服务器现状锁定为 `0.0.67`。

## 开发规则

- 每实现一个功能就提交一次 Git commit。
- 提交信息使用中文 Conventional Commits。
- 不要把 TeaStory/CraftEngine 内容硬编码进大框架；优先配置驱动。
- CraftEngine 对接集中放在 `CraftEngineHook`，其他逻辑不要直接调用 CraftEngine API。
- 先做最小可运行闭环，再扩展机器。

## 构建

```bash
./gradlew build
```

## 当前机器原型

`cgap:tea_drying_pan`：右击打开炒茶 GUI。

配置位于：

```text
src/main/resources/config.yml
```

关键类：

```text
src/main/java/com/github/cinnaio/materiaengine/MateriaEnginePlugin.java
src/main/java/com/github/cinnaio/materiaengine/TeaDryingPanGui.java
src/main/java/com/github/cinnaio/materiaengine/CraftEngineHook.java
```

## CGAP-RESOURCE 配合项

CraftEngine 内容包在：

```text
E:\Developments\Projects\CGAP-RESOURCE
```

当前已使用：

```text
cgap:tea_drying_pan
cgap:fresh_tea_leaf
cgap:withered_tea_leaf
cgap:tea_leaf
cgap:green_tea_leaf
cgap:tea_drying_pan_progress_0..5
```

## 验证

1. `./gradlew build`
2. 将 jar 放入服务端
3. 确认 CraftEngine 已加载 CGAP-RESOURCE
4. 放置 `cgap:tea_drying_pan`
5. 右击打开 GUI
6. 放入允许输入，点击开始，等待产出
