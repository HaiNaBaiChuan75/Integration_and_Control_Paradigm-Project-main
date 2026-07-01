# ECS 架构 — 载具 System/Part 协作范式

## 哲学：ECS 风格的 System / Part 架构

`ecs` 包的核心思路是**将载具逻辑从 BlockEntity 中剥离，放入独立的 System 类**。BlockEntity（Part）只保留**状态数据**、*
*自洽的平滑插值**与**非车辆逻辑**（如动画触发、渲染状态）。

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
- System **持有全部车辆逻辑**——瞄准解算、引擎模型、扭矩分配均在 System 中
- Part **不保存车辆逻辑**——只暴露纯数据 getter/setter（如 `setTargetYaw(double)`、`getCurrentRpm()`）；动画、骨骼平滑、粒子等自洽逻辑除外
- System 通过 `instanceof` 找到需要的 Part，读取其数据、计算、写回结果
- Part 不直接调用 System，不持有 System 引用
- 注册在 `VehicleSystemRegistry.registerAll()` 中完成（服务端），客户端专用 System 在 `IACPClient` 中注册

> **架构定位**：本实现是 Minecraft 环境下的**务实变体**，借鉴 ECS 的"System 驱动逻辑、Part 持有状态"哲学，但并非纯 ECS。Part
> 保留行为接口仅限于**非车辆逻辑**（如动画状态机、GeckoLib 骨骼更新），所有车辆级算法（瞄准、动力、悬挂）必须在 System 中实现。在 ~
> 20 个活跃部件的轻量场景下，这种混合模式比引入独立 Component 层更贴合 MC 的原生生命周期。

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

---

## 三个 Tick 阶段

| 阶段       | 接口                                     | 触发事件                            | 频率       | 典型用途               |
|----------|----------------------------------------|---------------------------------|----------|--------------------|
| 逻辑 Tick  | `VehicleTickSystem.onTick()`           | `ServerTickEvent.Pre` (server)  | 20Hz     | 瞄准解算、控制输入、变速箱、状态更新 |
| 物理 Tick  | `VehiclePhysicsSystem.onPhysicsTick()` | `ForgeSablePrePhysicsTickEvent` | Sable 步进 | 悬挂力、弹簧阻尼、推力        |
| 客户端 Tick | `VehicleClientSystem.onTick()`         | `LevelTickEvent.Pre` (client)   | 20Hz     | HUD、覆盖层、调试粒子       |

注册表位置：`VehicleSystemRegistry` 中的三个 `static final List`。

---

## 如何添加一个新的 System

### 例 1：逻辑 Tick System

```java
public class WeaponAimSystem implements VehicleTickSystem {
    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {
        // 1. 找到主控输入源（如玩家正在交互的控制器）
        Controller ctrl = findPrimaryController(parts);
        if (ctrl == null) return;

        // 2. 遍历武器挂载点，System 做全部瞄准计算
        for (PartBlockEntity part : parts) {
            if (part instanceof WeaponMount mount) {
                // 坐标转换、角度解算 —— 这是车辆逻辑，必须在 System 中
                Vector3d targetLocal = mount.partLogicalPose().transformPositionInverse(new Vector3d(ctrl.getAimTarget()));
                double yaw = Math.toDegrees(Math.atan2(targetLocal.x(), targetLocal.z()));
                double pitch = Math.toDegrees(Math.atan2(-targetLocal.y(), Math.sqrt(targetLocal.x() * targetLocal.x() + targetLocal.z() * targetLocal.z())));

                // 3. 只写入纯数据目标值，Part 内部只做平滑插值
                mount.setTargetYaw(yaw);
                mount.setTargetPitch(pitch);
            }
        }
    }
}
```

### 例 2：物理 Tick System

```java
public class SuspensionPhysicsSystem implements VehiclePhysicsSystem {
    @Override
    public void onPhysicsTick(@NotNull ServerSubLevel subLevel, @NotNull List<PartBlockEntity> parts,
                              @NotNull RigidBodyHandle handle, double timeStepSeconds) {
        // 读取引擎 System 上一逻辑 tick 写入的轮上扭矩
        for (PartBlockEntity part : parts) {
            if (part instanceof WheelPart wheel) {
                double torque = wheel.getTorqueInput();  // 纯数据，WheelPart 不解释其含义
                // 弹簧/阻尼/摩擦力计算 —— 车辆逻辑在 System
                applyForces(handle, wheel, torque, timeStepSeconds);
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
        // 如果只在 F3 调试界面显示
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen()) return;
        // 读取 Part 数据渲染 HUD
    }
}
```

### System 执行顺序

`VehicleTickSystem` 的多个实例按 `VehicleSystemRegistry` 的 `TICK_SYSTEMS` 列表顺序**串行执行**。如果 System 之间存在数据依赖（如
`EnginePowerSystem` 输出 `torquePerWheel` 供 `SuspensionPhysicsSystem` 读取），注册顺序即执行顺序：

```java
// 在 VehicleSystemRegistry.registerAll() 中按依赖顺序添加：
TICK_SYSTEMS.add(new SteeringSystem());       // 处理转向输入
        TICK_SYSTEMS.

add(new EnginePowerSystem());    // 计算引擎/扭矩分配
        TICK_SYSTEMS.

add(new WeaponAimSystem());      // 独立系统，顺序无关
        TICK_SYSTEMS.

add(new ClientSyncSystem());     // 最后同步完整状态
```

> **设计约束**：当前不引入显式优先级接口，依赖注册顺序保证时序。若未来 System 数量增长，可升级为 `priority()` 排序，但现阶段
> YAGNI。

---

## 如何添加一个新的载具方块（Part）

1. **继承 `PartBlockEntity`**（取代直接继承 `BlockEntity`）

```java
public class MyPartBlockEntity extends PartBlockEntity implements WeaponMount, Controller {
    // orientation() 定义了方块在 SubLevel 中的朝向（四元数）
    @Override
    public Quaterniondc orientation() {
        return ORIENTATIONS.get(facingIndex);
    }

    // sable$tick(): Part 级别的平滑和动画，不做跨 Part 逻辑
    // ⚠ 错误模式：在此处做瞄准计算、目标追踪、伤害判定。
    // 这些属于车辆逻辑，必须在 VehicleTickSystem 中实现。
    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        prevYaw = yaw;
        prevPitch = pitch;
        // 只做：位置平滑插值（yaw/pitch 逼近 targetYaw/targetPitch）、动画触发器
        // 不做：目标计算、动力分配、换挡策略（这些放进 VehicleTickSystem）
        updateSmoothRotation();
    }

    // ===== 纯数据接口（WeaponMount）=====
    @Override
    public void setTargetYaw(double yaw) {this.targetYaw = yaw;}

    @Override
    public void setTargetPitch(double pitch) {this.targetPitch = pitch;}

    @Override
    public double getCurrentYaw() {return yaw;}

    // ===== 纯数据接口（Controller ）=====
    @Override
    public float getThrottleForward() {return throttleForward;}
}
```

2. **定义纯数据接口**（Part 只暴露状态，不暴露算法）

```java
// 武器挂载点 — 纯数据，无瞄准逻辑
public interface WeaponMount {
    void setTargetYaw(double yaw);

    void setTargetPitch(double pitch);

    double getCurrentYaw();

    double getCurrentPitch();
}

// 控制器 — 提供玩家输入状态
public interface Controller {
    float getThrottleForward();

    float getThrottleBackward();

    float getBrakeInput();

    float getTargetSteeringYaw();
    // 注意：没有 "applyControl()" 或 "computeEngineOutput()" 等方法
}
```

3. **注册到 `IACPBlockEntities`** 和 `IACPBlocks`

4. **注册渲染器** 在 `IACPClient` 中：

```java
event.registerBlockEntityRenderer(IACPBlockEntities.MY_PART.get(),
    ctx -> new PartRenderer<>(new MyPartModel()));
```

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

- 模型朝北（North）放置，朝前的方向是 Z-
- 旋转骨骼时：Yaw 绕 Y 轴(上)，Pitch 绕 X 轴(右)
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

## 控制器抽象与多主控选举

载具需要输入源（油门、刹车、转向）。**不**使用具体的 `BaseCabinBlockEntity` 作为枢纽，而是定义 `Controller ` 接口——任何 Part
都可以是控制器（驾驶舱、遥控终端、甚至 AI 核心）。

**多控制器场景**：MC 建造自由度允许玩家放置多个控制器，也可能因物理断裂导致 0 个控制器。处理规则：

| 场景         | 行为                                     |
|------------|----------------------------------------|
| **0 控制器**  | System 静默跳过，本 tick 不执行载具逻辑             |
| **1 控制器**  | 正常作为主控输入源                              |
| **>1 控制器** | 按方块坐标**字典序最小**（x→y→z）选举主控，其余忽略 也可以其他方案 |

> **分类型的控制器** 如果有需要，可以把移动、开火等逻辑拆分到不同控制器，每个控制器独立按照上面的的逻辑寻找主控，但是主控不一定是同一个。

> **数据流**：System 从 `Controller ` 读取输入，计算后直接将结果写入各 Part 的纯数据字段（如 `WheelPart.setTorqueInput()`
> ）。Part 之间不中转数据，System 之间不直接通信。

---

## 关键约定

- **System 本身不创建 Part**，只遍历 `parts` 参数
- **Part 不保存车辆逻辑**——瞄准、引擎、变速箱、悬挂等算法必须在 System 中；Part 只保存自身状态数据（yaw、pitch、rpm、torqueInput
  等）和自洽的非车辆逻辑（动画、平滑插值）
- **System 直接读写 Part 数据**——计算结果直接写入目标 Part 的 setter，不通过"枢纽 Part"中转
- **不要在 Part 中直接触发 System**，System 永远是从 Dispatcher 单向流向 Part
- **EC 对等**：Part（C）和 System（S）在架构中地位对等，不存在谁服务于谁——Part 定义"是什么"，System 定义"做什么"
- **轻量约束**：活跃 Part 通常不超过 20 个（结构部件），其余为平凡方块。`collectParts()` 遍历 Sable 内部 actor 列表，O(actors)
  开销可忽略，无需预过滤或索引
- **调度器无状态**：`VehicleSystemDispatcher` 每 tick 重新收集 Part，不缓存、不持有 Part 引用。SubLevel 由 Sable
  管理，本框架只读/只调用
- **网络同步原生**：Part 继承 `BlockEntity`，状态变更与同步走 MC 原生机制（`setChanged()` + `getUpdatePacket`）。System
  不介入网络层
- **System 无状态边界**：System 不持有运行时业务状态（如当前速度、装填进度），但可持有**配置常量**（如 PID 参数、最大转向角）。跨
  Tick 的连续计算状态若不属于单个 Part，可存放于 SubLevel 级（若 Sable 提供扩展点）或通过 Part 数据字段间接传递


