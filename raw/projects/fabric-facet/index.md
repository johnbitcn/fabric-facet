---
title: Facet 项目记忆
updated: 2026-07-19
---

# Facet 项目记忆

## 2026-07-19：四图涂鸦轮盘

### 变更概述

单图 `G` 键切换升级为非暂停的四图涂鸦轮盘。按一下 `G` 锁定准星命中的方块面；键盘上方 `1`–`4` 或鼠标可立即应用、替换图案，轮盘中心、`Backspace` 或 `Delete` 仅清除锁定面，`G` 或 `Esc` 取消。轮盘作为 Screen 捕获数字键，不会切换快捷栏。

### 决策与约束

- 图案顺序固定为：`1` 黄色方框 `square`、`2` 红色圆圈 `circle`、`3` 蓝色叉号 `cross`、`4` 绿色三角形 `triangle`；不增加设置项或额外全局快捷键。
- 打开轮盘时锁定世界、维度、坐标、方向和原方块 ID；应用前再次验证。目标变化时关闭并提示，不向过期坐标写入。已有但不再合格的记录仍可打开轮盘清除，图案选项禁用。
- 同一面选择当前图案仅关闭，不标记 dirty、不写盘、不触发 section 重建；新增、替换和清除只重建目标 `16×16×16` render section。
- properties key 不变；value 使用 `v2|<block-id>|<type-id>`。旧 `true` 在对应 chunk 首次加载时绑定当前合格方块，旧 `true` 和旧方块 ID 记录均迁移为 `square`。迁移只标记 dirty，沿用每 tick 最多一次的正常 flush。
- 四张 `128×128` sRGB Alpha PNG 同时用于方块 Quad 与 GUI 缩略图，运行时稳定路径为 `assets/facet/textures/block/graffiti/<type>.png`。旧单图运行时资源和旧“标”图源文件已删除。
- Minecraft 26.1 的 Mod Menu 开发依赖固定为官方 `18.0.0` 文件，26.2 继续使用 `20.0.0-alpha.1`；26.1 lifecycle-events 与 Fabric API 声明对齐为 `4.0.6+a9b9c17647`。

### 256×256 原始附件校验

| 文件 | SHA-256 |
| --- | --- |
| `square.png` | `0465037998f8db2b509d73f2c0b5763c71b96ad1b94017c68dc57a32ac6d3a1a` |
| `circle.png` | `60b2683bf985b3a19e44695b94abcfc8f83add44d3ec9b08c72d9822d454071e` |
| `cross.png` | `f4238ecedacb44cc523537ff7e7c9cad4718c9be20692715a3d5605f8bc13778` |
| `triangle.png` | `477e54d9efe3e7f35b2bface036688c07ae36ce310af012b247a4050124cc9fc` |

`/Users/john/Downloads/facet-icons-256/` 中的原始附件保持不变；运行时文件始终由这些原件确定性缩放，避免从低分辨率版本二次放大。

| 历史 64×64 运行时文件 | SHA-256 |
| --- | --- |
| `square.png` | `cbc4db7057c905fa25efd5b6fd26390ea579822b14b6234dbaabe96aa40e31c8` |
| `circle.png` | `8c5976a9250ee9584b6abbd63825afc802a3e40e2190d37f5bf513fc2b1cf093` |
| `cross.png` | `d0cee8d6c85cb9bcf6247441275325bc34fb6484414bba5f80c0a9e3515f96a7` |
| `triangle.png` | `1aaff8b13c30a50d1db63e6cf431561040300bf751ad11fcb4fc3e040fe61535` |

| 当前 128×128 运行时文件 | SHA-256 |
| --- | --- |
| `square.png` | `1e2b7b28d93e4322f6b779eb21e3bf529c0fe065461a9df74bd48406be17c97b` |
| `circle.png` | `e439f78844d380c49610d83bbc1f1d8a4ace0d62c5b22e378d83ac27401a696d` |
| `cross.png` | `7184fef6ee594812a5bcb5a409a027e08b39a2fe16393cbea6cd506d6407ffaa` |
| `triangle.png` | `b6114f6a9ec51afd402952a64ea405796b9aada1d1008a88a3c860460a2cfaa8` |

### 相关文件

- `src/shared/java/com/facet/client/GraffitiType.java`
- `src/shared/java/com/facet/client/GraffitiWheelScreen.java`
- `src/shared/java/com/facet/client/GraffitiStore.java`
- `src/shared/java/com/facet/client/FacetClient.java`
- `src/shared/resources/assets/facet/textures/block/graffiti/`
- `src/shared/resources/assets/facet/lang/`
- `build.gradle`
- `gradle.properties`

### 验证

- `./gradlew --no-daemon clean build --console=plain` 已在 Minecraft 26.1 与 26.2 两个子项目通过。
- 两个开发客户端都完成 Fabric/Mixin 初始化、Facet 资源重载、Indigo 注册以及 `blocks.png`、`gui.png` 图集创建；26.1 实际加载 Mod Menu `18.0.0` 和 lifecycle-events `4.0.6+a9b9c17647`。
- 两个正式 JAR 的 ZIP 完整性检查通过；每个 JAR 恰好包含四张涂鸦纹理、新轮盘与数据类型 class、客户端 Mixin 配置和四套语言文件，不包含旧单图纹理。
- 四套语言文件各有 32 个且键集合完全相同。四张运行时 PNG 当前均为 `128×128`、8-bit RGBA、带 sRGB 色彩配置，并由 256 原件确定性缩放生成。
- 仍需在实际世界中人工验收轮盘键鼠交互、快捷栏不切换、单面清除、旧存档迁移、目标变化保护和仅目标 section 重建；启动检查不能替代这些交互测试。

### 2026-07-19 轮盘缩略图缩放修复

首次游戏内验收发现，GUI `blit` 的简化重载把 `56×56` 同时作为目标尺寸和源采样尺寸，导致每张 `256×256` 图案只显示左上角区域。现改用可分别指定尺寸的重载：目标仍为边框内的 `56×56`，源区域为完整 `256×256`，不改变轮盘布局、方块 Quad 或运行时纹理。

修复后 26.1、26.2 `clean build` 和两个正式 JAR 的 ZIP 完整性检查均通过；两个版本的生成字节码都调用包含独立目标与源尺寸参数的 GUI `blit` 重载。

### 2026-07-19 运行时纹理降至 64×64

为降低资源分辨率和加载体积，四张运行时纹理由 `256×256` 确定性缩放为 `64×64`，下载目录中的 256 原件不变。GUI 仍将完整源纹理绘制到边框内的 `56×56` 区域；方块 Quad 自动使用同一组较小纹理，不改变图案类型、轮盘布局或持久化格式。

缩放后 26.1、26.2 `clean build` 通过；从两个正式 JAR 反向提取的四张纹理均为 `64×64`、sRGB、带 Alpha，SHA-256 与源资源目录逐项一致，两个 JAR 的 ZIP 完整性检查通过。

### 2026-07-19 轮盘整体透明度

轮盘所有可见元素统一应用 85% Alpha：图标、边框、标题、说明文字、数字标签和清除按钮一起降低 15% 不透明度；原本已半透明的底板、悬停层和禁用遮罩在自身 Alpha 上继续乘以 0.85。此调整只影响轮盘 GUI，不影响方块上的涂鸦 Quad 或 PNG 文件本身。

调整后 26.1、26.2 `clean build`、两个 JAR 的 ZIP 完整性和 `git diff --check` 均通过；两个版本的生成字节码都确认图标绘制调用使用带 ARGB 参数的完整纹理缩放重载。

### 2026-07-19 扇形布局与进出场动画

- GUI 图标显示区域由 `56×56` 调整为 `32×32`，外层点击与预览框为 `40×40`；运行时 PNG 当前为 `128×128`，完整缩放显示。
- 轮盘第一次渲染时锁定鼠标 GUI 坐标作为圆心。四个图标按 `1`–`4` 从左到右排列在圆心上方的 120° 扇面，角度依次为 `-150°`、`-110°`、`-70°`、`-30°`，最终半径为 80 GUI 像素；清除按钮保留在圆心。
- 入场时四个图标从圆心向最终扇形位置辐射，轮盘全部元素的动画 Alpha 从 0 增至既有的 85%；鼠标移动只改变悬停项，不移动已锁定的扇形圆心。
- `1`–`4`、鼠标选择、清除、`G` 和 `Esc` 都会触发 300 ms 出场；出场沿相反方向收回圆心并淡出，结束后才关闭 Screen。动画期间屏蔽重复输入，数据新增、替换或清除仍只发生一次。
- 入场和出场均使用 `cubic-bezier(0.1, 0.8, 0.2, 1)`。出场对位移方向和 Alpha 做反向变化，同时保留同一条“快速启动、缓慢收尾”时间曲线；若用户在入场尚未结束时选择，会从当前动画进度连续反向，不跳到最终位置。

曲线和几何机械核对结果：20% 时间点完成约 75.4% 位移；最终相邻图标中心距为 54–54.7 GUI 像素，40 像素点击框互不重叠。

实现后 26.1、26.2 `clean build`、客户端 Fabric/Mixin 初始化、Facet 资源重载和方块/GUI 图集创建均通过，两个正式 JAR 的 ZIP 完整性与 `git diff --check` 通过。仍需在实际世界中人工感受 300 ms 动画节奏和扇形视觉位置。

### 2026-07-19 成功喷涂本地音效

- 新增或替换涂鸦并产生真实数据变更时，立即播放一次原版 `SoundEvents.BRUSH_GENERIC`；选择当前图案、资格失败、目标变化和清除涂鸦均不播放。
- 使用 `Level.playLocalSound` 保持纯客户端行为，不发送网络包，其他玩家不会听见。声音坐标位于方块中心沿命中方向偏移 `0.501` 的表面位置，分类为 `SoundSource.BLOCKS`，遵循玩家的“方块”音量设置。
- 音量固定为 `0.8`，音高为 `0.95 + random × 0.10`，即每次在 `0.95–1.05` 间轻微变化；关闭距离延迟播放。
- 音效在数据成功变更时与 section 重建和成功提示一起触发，不等待 300 ms 出场动画结束；出场期间既有输入锁防止重复播放。不增加 OGG、`sounds.json`、设置项或服务端代码。

实现后 26.1、26.2 `clean build`、客户端声音引擎与资源图集初始化、两个正式 JAR 的 ZIP 完整性和 `git diff --check` 均通过。仍需进入实际世界人工确认刷子音色、空间方向、音量滑块以及新增/替换各只响一次。

### 2026-07-19 128×128 清晰度试验

为改善方块 Quad 和轮盘缩略图的清晰度，四张运行时纹理由 64 调整为 `128×128`。新文件直接从 `/Users/john/Downloads/facet-icons-256/` 的 256 原件确定性缩放生成，而非放大 64 版本；GUI 仍显示为 `32×32`，只提高采样源分辨率，不改变轮盘布局、动画、透明度、涂鸦数据或交互逻辑。四张资源总磁盘占用约 44 KiB。

调整后 26.1、26.2 `clean build`、客户端声音引擎与方块/GUI 图集初始化通过；从两个正式 JAR 反向提取的四张资源均为 `128×128`、sRGB、带 Alpha，SHA-256 与源码资源逐项一致。实际清晰度提升仍需在相同 GUI 缩放、视距和观察距离下与 64 版本进行人工对比。

## 2026-07-18：客户端“涂鸦”功能

### 变更概述

Facet 新增客户端本地涂鸦。涂鸦通过 Fabric Model Loading/Renderer API 追加带轻微表面偏移的透明 Quad，不改写方块纹理或服务器世界。

### 决策与约束

- 延续 Facet 的纯客户端定位；不发送网络包，其他玩家不可见。
- 涂鸦按服务器或单人世界、维度、方块坐标和方向保存到客户端 `config/facet-graffiti.properties`。
- 涂鸦记录包含原方块 ID；客户端收到服务器确认的方块状态后，若原方块被摧毁、被流体或其他方块替换，会定点清除该坐标六个面的涂鸦。该流程不注册服务端事件、不发送自定义网络包，也不主动触发渲染重建。
- 完整 chunk 加载时仅核对按世界、维度和 chunk 建立的涂鸦位置索引，不执行每 tick 全量遍历；多个清理操作在同一 tick 最多写盘一次。
- 按 `G` 添加或移除涂鸦时只标记目标方块所在的 `16×16×16` render section，不再使全部已编译区块几何失效。
- 新增资格要求命中面在最外层平面上的形状投影并集面积严格大于 `0.90`。
- 流体、非模型方块、带 Block Entity 的方块，以及明确列出的功能方块类别不允许新增；完整叶块允许新增。
- 运行时资源由附件图片生成；当前 `128×128` 尺寸、哈希与迁移规则见 2026-07-19 记录。

### 相关文件

- `src/shared/java/com/facet/client/FacetClient.java`
- `src/shared/java/com/facet/client/GraffitiEligibility.java`
- `src/shared/java/com/facet/client/GraffitiStore.java`
- `src/shared/java/com/facet/client/mixin/ClientLevelMixin.java`
- `src/shared/resources/facet.client.mixins.json`
- `versions/v26_1/src/client/java/com/facet/client/FacetMcBridge.java`
- `versions/v26_2/src/client/java/com/facet/client/FacetMcBridge.java`
- `src/shared/resources/assets/facet/textures/block/graffiti/`

### 验证

- `./gradlew --no-daemon clean build --console=plain` 在 Minecraft 26.1 与 26.2 两个子项目均通过；两个正式 JAR 的 ZIP 完整性检查通过。
- 两个版本的 JAR 都包含客户端 Mixin 配置、`ClientLevelMixin.class`、四种语言新增键和涂鸦纹理；四图资源的当前校验结果见 2026-07-19 记录。
- Minecraft 26.2 测试客户端已在本次事件清理改动后完成 Mixin 初始化、Fabric/Indigo 注册、Facet 资源重载和方块图集烘焙，未出现涂鸦相关异常。
- Minecraft 26.1 的 Mod Menu 开发依赖问题已在 2026-07-19 的四图轮盘升级中按版本拆分。
- 尚未完成进入世界后的 `G` 键、六面清理、服务器回滚、爆炸、流体、活塞与 section 失效范围的人工验收。

### 已知限制与重访条件

- 当前只保存“坐标上的面”，不会让涂鸦随活塞移动；活塞移动后原位置的涂鸦会被清除。
- 如果未来需要多人共享，应迁移为服务端权威数据并增加同步、权限、方块破坏和活塞移动处理；不要复用当前客户端 properties 文件作为服务器数据源。
