---
updated: 2026-06-27
status: current
maintainer: @项目协作者
---

# GeckoLib 与 SubLevel 渲染兼容性

## 背景

需要验证 GeckoLib 的 `GeoBlockRenderer`（`ENTITYBLOCK_ANIMATED` 渲染模式）是否能在 Sable SubLevel 内部正常工作。SubLevel 使用 `logicalPose`/`renderPose` 变换坐标系，而 GeckoLib 的模型加载和渲染管线有自己的矩阵管理。

## 根因

无根本兼容性问题——GeckoLib 使用标准的 `BlockEntityRenderDispatcher` 接口，Sable 已有 mixin 处理 BE 渲染变换：

- `SublevelRenderOffsetHelper` — 处理 BE 渲染偏移
- `VanillaSubLevelRenderDispatcher.renderBlockEntities()` — 调度 SubLevel 内 BE 渲染
- `BeltRendererMixin` — Create 传送带在 SubLevel 内的渲染适配

GeckoLib 的 `GeoBlockRenderer` 通过标准 `BlockEntityRenderer` 接口工作，自动继承上述 mixin 的变换处理。

## 解决方案

### 测试方案

创建了 `TestController` 测试结构：

1. **Java 文件**（4 个）：
   - `TestControllerBlock.java` — `RenderShape.ENTITYBLOCK_ANIMATED`，实现 `EntityBlock`
   - `TestControllerBlockEntity.java` — 实现 `GeoBlockEntity`，播放旋转动画
   - `TestControllerRenderer.java` — `GeoBlockRenderer`，6×6×6 渲染包围盒
   - `TestControllerModel.java` — `DefaultedBlockGeoModel` 子类

2. **资源文件**（`assets/iac_p/`）：
   - `geo/block/test_controller.geo.json` — 32×32×32 立方体模型
   - `textures/block/test_controller.png` — 橙色纹理
   - `animations/block/test_controller.animation.json` — Y 轴 4 秒旋转动画

3. **注册链**：
   - `ModBlocks.TEST_CONTROLLER` → `ModItems.TEST_CONTROLLER` → `ModTestControllerBlockEntityTypes.TEST_CONTROLLER`
   - `IACP.java` 注册 BE 类型 → `IACPClient.java` 注册渲染器

### DefaultedBlockGeoModel 路径规则

| 资源类型 | 路径模板 | 示例 |
|---------|---------|------|
| 模型 | `geo/block/{path}.geo.json` | `geo/block/test_controller.geo.json` |
| 纹理 | `textures/block/{path}.png` | `textures/block/test_controller.png` |
| 动画 | `animations/block/{path}.animation.json` | `animations/block/test_controller.animation.json` |

传入 `ResourceLocation("iac_p", "test_controller")` 后，`DefaultedBlockGeoModel` 自动在 `geo/` 后插入 `block/`，所以文件必须放在 `geo/block/` 下而非 `geo/`。

### 踩坑：GeoModel 路径找不到崩溃

- **症状**：放置方块时崩溃，日志报 `iac_p:geo/block/test_controller.geo.json: Unable to find model`
- **根因**：`DefaultedBlockGeoModel` 构造路径为 `geo/block/{name}.geo.json`，但文件放在了 `geo/{name}.geo.json`
- **修复**：将 `.geo.json` 文件移至 `geo/block/` 子目录，纹理和动画同理

### 排除 GeckoLib 渲染器注册

`GeoBlockRenderer` 不能使用 `BlockEntityRenderers.register()`（仅限 datafixer 引导阶段），必须在 `EntityRenderersEvent.RegisterRenderers` 事件中注册：

```java
// IACPClient.java
modEventBus.addListener(this::registerRenderers);

private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(
        ModTestControllerBlockEntityTypes.TEST_CONTROLLER.get(),
        TestControllerRenderer::new
    );
}
```

## 验证结果

- ✅ 普通世界放置：橙色旋转立方体正常渲染
- ✅ SubLevel 内放置：渲染正常，旋转动画正常，跟随 SubLevel 变换
- ❌ **无**渲染兼容性问题——GeckoLib 与 Sable SubLevel 完全兼容

## 参考

- `content/blocks/test_controller/` — 全部测试代码
- `build.gradle` — GeckoLib 4.6.6 依赖声明
- `gradle.properties` — `geckolib_version=4.6.6`
- Sable 渲染 mixin：`SublevelRenderOffsetHelper`、`VanillaSubLevelRenderDispatcher`
