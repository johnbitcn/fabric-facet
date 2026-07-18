---
title: Facet 项目记忆
updated: 2026-07-18
---

# Facet 项目记忆

## 2026-07-18：客户端“涂鸦”功能

### 变更概述

Facet 新增客户端本地涂鸦：玩家将准星对准方块面后按 `G`，可添加或移除固定贴图。涂鸦通过 Fabric Model Loading/Renderer API 追加带轻微表面偏移的透明 Quad，不改写方块纹理或服务器世界。

### 决策与约束

- 延续 Facet 的纯客户端定位；不发送网络包，其他玩家不可见。
- 涂鸦按服务器或单人世界、维度、方块坐标和方向保存到客户端 `config/facet-graffiti.properties`。
- 同一面已有涂鸦时，快捷键优先移除，不再执行新增资格检查。
- 新增资格要求命中面在最外层平面上的形状投影并集面积严格大于 `0.90`。
- 流体、非模型方块、带 Block Entity 的方块，以及明确列出的功能方块类别不允许新增；完整叶块允许新增。
- 运行时资源使用附件图片的 `256×256` RGBA 副本；`doc/image/graffiti-source.png` 保留未经修改的原图。

### 相关文件

- `src/shared/java/com/facet/client/FacetClient.java`
- `src/shared/java/com/facet/client/GraffitiEligibility.java`
- `src/shared/java/com/facet/client/GraffitiStore.java`
- `src/shared/resources/assets/facet/textures/block/graffiti.png`
- `doc/image/graffiti-source.png`

### 验证

- `./gradlew --no-daemon clean build --console=plain` 在 Minecraft 26.1 与 26.2 两个子项目均通过。
- 两个版本的 JAR 都包含 `assets/facet/textures/block/graffiti.png` 和四种语言新增键；运行时纹理为 `256×256` RGBA，并与对附件原图执行 `sips -z 256 256` 的输出逐字节一致。
- Minecraft 26.2 测试客户端已启动到主界面，Fabric/Indigo 注册、Facet 资源重载和方块图集烘焙均完成，未出现涂鸦相关异常；尚未完成进入世界后的按键与六面视觉人工验收。

### 已知限制与重访条件

- 当前只保存“坐标上的面”，不追踪被活塞移动或被破坏后重新放置的具体方块实例。
- 如果未来需要多人共享，应迁移为服务端权威数据并增加同步、权限、方块破坏和活塞移动处理；不要复用当前客户端 properties 文件作为服务器数据源。
