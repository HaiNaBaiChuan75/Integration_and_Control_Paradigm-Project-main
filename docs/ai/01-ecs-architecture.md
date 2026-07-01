# ECS 架构 — 载具 System/Part 协作范式

## 哲学：ECS 风格的 System / Part 架构

`ecs` 包的核心思路是**将载具逻辑从 BlockEntity 中剥离，放入独立的 System 类**。BlockEntity（Part）只保留状态和渲染。

```
                    ┌─ VehicleTickSystem       (20Hz — 逻辑)
 Minecraft Tick ────┤─ VehiclePhysicsSystem    (物理步进后 — 力)
                    └─ VehicleClientSystem     (20Hz — 客户端)
                              │
                              ▼
                    VehicleSystemRegistry
                    (注册 + collectParts)
                              │
                              ▼
                    PartBlockEntity (instanceof 分发)
```

**核心规则：**

- System **无状态**——所有运行时状态保留在 Part 或 SubLevel 上
- System 通过 `instanceof` 找到需要的 Part，再调用其方法
- Part 不直接调用 System，不持有 System 引用
- 注册在 `VehicleSystemRegistry.registerAll()` 中完成（服务端），客户端专用 System 在 `IACPClient` 中注册

---

## 包结构

```
ecs/
  part/                                  ← C：部件契约
    PartBlockEntity.java                所有载具方块的抽象基类
    PartRenderer.java                   GeckoLib 渲染基类（客户端）
    PartQuery.java                      @Deprecated 旧版部件查询工具

  system/                                ← S：系统契约
    VehicleTickSystem.java              20Hz 逻辑 Tick 接口
    VehiclePhysicsSystem.java           物理步进后 Tick 接口
    VehicleClientSystem.java            客户端 Tick 接口
    VehicleSystemRegistry.java          注册表 + collectParts() + registerAll()

  dispatch/                              ← 调度：NeoForge ↔ System
    VehicleSystemDispatcher.java        NeoForge 事件 → System 调用的桥梁
                                        一个 @EventBusSubscriber，监听三种事件
```

> **注意**：`AxisRenderSystem`（调试坐标轴粒子）已移至 `iac_p/system/` 包——它是 ECS 框架的消费者，不是框架本身。`AssemblyUtil` 和 `SubLevelUtil` 也已从 `ecs/` 移出至独立包。

---

## 三个 Tick 阶段

| 阶段       | 接口                                     | 触发事件                            | 频率       | 典型用途             |
|----------|----------------------------------------|---------------------------------|----------|------------------|
| 逻辑 Tick  | `VehicleTickSystem.onTick()`           | `ServerTickEvent.Post` (server)   | 20Hz     | 瞄准、控制输入、变速箱、状态更新 |
| 物理 Tick  | `VehiclePhysicsSystem.onPhysicsTick()` | `ForgeSablePostPhysicsTickEvent` | Sable 步进 | 悬挂力、弹簧阻尼、推力      |
| 客户端 Tick | `VehicleClientSystem.onTick()`         | `LevelTickEvent.Pre` (client)   | 20Hz     | HUD、覆盖层、调试粒子     |

注册表位置：`VehicleSystemRegistry` 中的三个 `static final List`。

---

## 如何添加一个新的 System

### 例 1：逻辑 Tick System

```java
// 1. 实现接口
public class WeaponAimSystem implements VehicleTickSystem {
    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {
        // 用 instanceof 找到需要的部件
        for (PartBlockEntity part : parts) {
            if (part instanceof Aimable aimable) {
                aimable.aimAt(someTarget);
            }
        }
    }
}

// 2. 注册
// 在 VehicleSystemRegistry.registerAll() 中添加：
TICK_SYSTEMS.add(new WeaponAimSystem());
```

### 例 2：物理 Tick System

```java
public class SuspensionSystem implements VehiclePhysicsSystem {
    @Override
    public void onPhysicsTick(@NotNull ServerSubLevel subLevel, @NotNull List<PartBlockEntity> parts,
                              @NotNull RigidBodyHandle handle, double timeStepSeconds) {
        for (PartBlockEntity part : parts) {
            if (part instanceof WheelPart wheel) {
                // 施加力
            }
        }
    }
}
```

### 例 3：客户端 System

```java
public class SpeedHudSystem implements VehicleClientSystem {
    @Override
    public void onTick(@NotNull ClientSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {
        // 只在 F3 调试界面显示
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen()) return;
    }
}
```

---

## 如何添加一个新的载具方块（Part）

1. **继承 `PartBlockEntity`**（取代直接继承 `BlockEntity`）

```java
public class MyPartBlockEntity extends PartBlockEntity implements Aimable {
    // orientation() 定义了方块在 SubLevel 中的朝向（四元数）
    @Override
    public Quaterniondc orientation() {
        return ORIENTATIONS.get(facingIndex);
    }

    // sable$tick(): Part 级别的平滑和动画，不做跨 Part 逻辑
    // ⚠ ShotGunBlockEntity 当前在 sable$tick 中做瞄准计算是错误模式。
    // System 在整个 SubLevel 的 parts 列表上运行（协调多个 Part），
    // 而 sable$tick 是 Sable 为每个 PartBlockEntity 单独调用的，
    // 不是 System 调度的一部分，不适合做跨 Part 的协调逻辑。
    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        prevYaw = yaw;
        prevPitch = pitch;
        // 只做：位置平滑插值（yaw/pitch 逼近目标值）、动画触发器
        // 不做：目标计算、伤害判定、动力分配（这些放进 VehicleTickSystem）
        updateSmoothRotation();
    }
}
```

2. **实现 `Aimable`**（如果可以被瞄准系统驱动）

```java
public interface Aimable {
    void aimAt(Vector3dc targetAbsPoint);  // 设置绝对世界坐标中的目标点
}
```

3. **注册到 `IACPBlockEntities`** 和 `IACPBlocks`

4. **注册渲染器** 在 `IACPClient` 中：

```java
event.registerBlockEntityRenderer(IACPBlockEntities.MY_PART.get(),
    ctx -> new PartRenderer<>(new MyPartModel()));
```

---

## 现有 System 参考

| System                   | 阶段     | 位置              | 说明                           |
|--------------------------|--------|-----------------|------------------------------|
| `RandomAimVehicleSystem` | Tick   | `test_system/`  | 测试：让所有 Aimable 绕 Y 轴画圈跟踪目标   |
| `AxisRenderSystem`       | Client | `iac_p/system/` | 调试：F3 下显示每个 Part 的 XYZ 坐标轴粒子 |

---

## 现有 PartBlockEntity 参考

| 类                      | 位置                  | 说明                                                               |
|------------------------|---------------------|------------------------------------------------------------------|
| `ShotGunBlockEntity`   | `block/shotgun/`    | 功能完备的参考实现，但注意：**sable$tick 中做瞄准计算是错误模式**（←此处应抽到 WeaponAimSystem） |
| `BaseCabinBlockEntity` | `block/base_cabin/` | 驾驶舱骨架，待填充                                                        |

---

## Part 的坐标系

Part 涉及三层坐标空间：

```
方块局部坐标 (myPartLocalPose / orientation)
  │  orientation() 四元数定义 Part 自身的朝向偏移
  ▼
SubLevel 逻辑坐标 (subLevel.logicalPose)
  │  包含 SubLevel 整体的位置、旋转、缩放
  ▼
世界绝对坐标
```

**约定：**

- **z- = 前方，x+ = 右方**（Minecraft 世界标准，F3 调试界面显示的朝向）
- `orientation()` 返回的四元数定义 Part 相对于 SubLevel 的朝向。默认返回 `IDENTITY_QUAT`（无旋转）
- `partLogicalPose()`（旧名 `worldPose()`）返回完整的局部→世界变换，包含 orientation + SubLevel 姿态的组合
- `getCenterInWorld()` 返回 Part 方块中心的世界坐标，常用于瞄具目标计算
- 在瞄准计算中，用 `partLogicalPose().transformPositionInverse(target)` 将世界坐标转换到 Part 局部空间，再计算 yaw/pitch

```java
// 将世界目标转换到 Part 局部空间 → 计算角度
Vector3d target = partLogicalPose().transformPositionInverse(new Vector3d(targetAbsPoint));
double yaw = Math.toDegrees(Math.atan2(target.x(), target.z()));  // Minecraft ∠(x,z)
double pitch = Math.toDegrees(Math.atan2(-target.y(), Math.sqrt(target.x() * target.x() + target.z() * target.z())));
```

---

## 模型和骨骼绑定

### 坐标系对齐

GeckoLib geo 模型的坐标系必须与 Minecraft 世界坐标系一致：

| 轴  | 方向 |
|----|----|
| X+ | 右  |
| Y+ | 上  |
| Z+ | 后  |
| Z- | 前  |

**在 BlockBench 中建模时：**

- 模型朝北（North）放置，朝下的方向是 Z-
- 旋转骨骼时：Yaw 绕 Y 轴（正值顺时针，即向右转），Pitch 绕 X 轴（正值向下）
- 如果模型默认朝向与上述不符，需在 `orientation()` 中补偿旋转

### PartRenderer 的行为

`PartRenderer.actuallyRender()` 在渲染前对 `PoseStack` 应用了 `animatable.orientation()`：

```java
REUSE_QUAT.set(animatable.orientation());
poseStack.mulPose(REUSE_QUAT);
```

这意味着：

- 模型文件（geo.json）中的朝向应定义为 **orientation() 为 identity 时的默认姿态，一般而言为面向北方**
- 如果 Part 有 `facingIndex`（东西南北），则在 `orientation()` 返回对应旋转，渲染器自动应用
- 骨骼动画（idle / firing）在 orientation 之上叠加，不会干扰朝向

---

## 关键约定

- **System 本身不创建 Part**，只遍历 `parts` 参数
- **Part 之间的数据共享**通过 PartBlockEntity 上的 public getter/setter 完成
- **不要在 Part 中直接触发 System**，System 永远是从 Dispatcher 单向流向 Part
- **EC 对等**：Part（C）和 System（S）在架构中地位对等，不存在谁服务于谁——Part 定义"是什么"，System 定义"做什么"
