# Sable 物理引擎使用教程

> 基于 Sable v2.0.3 + NeoForge 1.21.1，源码来自本地 Maven 缓存
> 编写日期：2026-06-24

---

## 目录

1. [概述](#1-概述)
2. [核心架构](#2-核心架构)
3. [SubLevel——基础物理单元](#3-sublevel基础物理单元)
4. [物理管线 (PhysicsPipeline)](#4-物理管线-physicspipeline)
5. [RigidBodyHandle——施力接口](#5-rigidbodyhandle施力接口)
6. [约束系统 (Constraints)](#6-约束系统-constraints)
7. [BlockEntitySubLevelActor——物理 Tick 回调](#7-blockentitysublevelactor物理-tick-回调)
8. [力系统与 ForceGroup](#8-力系统与-forcegroup)
9. [KinematicContraption——运动学物体](#9-kinematiccontraption运动学物体)
10. [物理配置](#10-物理配置)
11. [完整的实战示例](#11-完整的实战示例)
12. [常见问题与最佳实践](#12-常见问题与最佳实践)

---

## 1. 概述

### 什么是 Sable

Sable 是一个为 Minecraft 设计的物理引擎——更准确地说，它是一个 **Minecraft ↔ Rapier 的胶合层**。它使用 Rust
编写的 [Rapier](https://rapier.rs/) 物理引擎作为后端（通过 JNI 调用原生 `.dll`/`.so`/`.dylib`），为 Minecraft
世界方块结构提供刚体物理模拟。

### 它能做什么

- **将方块结构转为刚体**——任何由方块组成的结构都可以被赋予质量、惯性和碰撞体
- **模拟重力、碰撞、摩擦**——基于 Rapier 的精确物理求解
- **约束系统**——SubLevel 之间可以通过旋转副、固定副、自由副、通用副连接
- **升力和阻力**——内置空气动力学系统（升力/阻力提供者接口）
- **网络同步**——自动将物理状态同步到客户端（支持 TCP + UDP 双通道）
- **嵌入 (SubLevel)**——物理物体以"嵌入层" (SubLevel) 的形式存在于 Minecraft 世界中，对玩家可见

### 它不是

- Sable **不是**一个独立运行的物理引擎——它完全依赖于 Rapier 的 JNI 原生库
- Sable **不**提供车辆控制逻辑、武器系统等——那是使用它的模组（如 IAC-P）的工作

### 坐标约定

- Sable 大量使用 JOML (`org.joml`) 库的 `Vector3d`、`Quaterniond`、`Matrix3d` 等类型
- 所有 API 偏向使用 `Vector3dc`（不可变向量接口）作为参数，内部可变为 `Vector3d`

---

## 2. 核心架构

### 依赖关系图

```
Minecraft Server Level
    └── SubLevelContainer (管理所有 SubLevel)
            ├── SubLevelPhysicsSystem (驱动的核心)
            │       └── PhysicsPipeline (Rapier 桥接)
            │               └── Rapier3D (JNI 原生调用)
            └── SubLevel[] (物理物体)
```

### 关键类职责

| 类                       | 角色                                      |
|-------------------------|-----------------------------------------|
| `Sable`                 | 主入口，`createPhysicsPipeline()` 工厂方法      |
| `SubLevel`              | 抽象的"嵌入层"，包含位置 (Pose)、包围盒 (BoundingBox)  |
| `ServerSubLevel`        | 服务端 SubLevel，同时实现 `PhysicsPipelineBody` |
| `SubLevelContainer`     | 管理容器，维护 SubLevel 的分配/释放/查询              |
| `SubLevelPhysicsSystem` | 物理系统的核心调度器——驱动 Pipeline 的每个 tick        |
| `PhysicsPipeline`       | 物理管线接口，连接 Minecraft 和 Rapier            |
| `RapierPhysicsPipeline` | Rapier 后端实现，通过 JNI 调用原生库                |
| `RigidBodyHandle`       | 方便的施力/调速/传送句柄                           |
| `ForceTotal`            | 力的累加器，自动计算力矩                            |

### 初始化流程

Sable 引擎对每个世界自动初始化：

```java
// Sable.java (内部实现)
public static void defaultSubLevelContainerInitializer(Level level, SubLevelContainer container) {
    if (container instanceof ServerSubLevelContainer serverContainer) {
        ServerLevel serverLevel = serverContainer.getLevel();
        
        // 1. 创建物理系统
        SubLevelPhysicsSystem physicsSystem = new SubLevelPhysicsSystem(serverLevel);
        physicsSystem.initialize();
        serverContainer.takePhysicsSystem(physicsSystem);
        
        // 2. 创建追踪系统 (网络同步)
        SubLevelTrackingSystem trackingSystem = new SubLevelTrackingSystem(serverLevel);
        serverContainer.takeTrackingSystem(trackingSystem);
        
        // 3. 注册观察者
        serverContainer.addObserver(physicsSystem);
        serverContainer.addObserver(trackingSystem);
        
        // 4. 加载方块物理属性定义
        PhysicsBlockPropertiesDefinitionLoader.INSTANCE.applyAll();
    }
}
```

**使用方不需要手动调用初始化**——Sable 通过 Mixin 在 ServerLevel 创建时自动完成。

---

## 3. SubLevel——基础物理单元

### 核心概念

SubLevel（子世界/嵌入层）是 Sable 中**最小的物理物体单元**。它本质上是一个"微缩维度"——持有自己的方块数据、Pose（位置+朝向+缩放）、物理属性。

物理世界中，一个 SubLevel 相当于 Rapier 的一个**刚体**。

### 类层次

```
SubLevel (abstract)
    ├── ServerSubLevel (服务端，实现 PhysicsPipelineBody)
    └── ClientSubLevel (客户端，渲染和插值)
```

### ServerSubLevel 关键字段

```java
public class ServerSubLevel extends SubLevel implements PhysicsPipelineBody {
    public final Vector3d latestLinearVelocity;   // 最新线速度
    public final Vector3d latestAngularVelocity;  // 最新角速度
    private final int runtimeId;                  // Rapier 中的唯一 ID
    private final FloatingBlockController floatingBlockController; // 浮空方块控制器
    private final ReactionWheelManager reactionWheelManager;       // 反作用轮
    private MergedMassTracker massTracker;        // 质量追踪器
    // ...
}
```

### 创建 SubLevel

通过 `SubLevelContainer.allocateNewSubLevel(Pose3d pose)` 创建：

```java
// 获取容器
ServerSubLevelContainer container = 
    (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);

// 创建 Pose
Pose3d pose = new Pose3d();
pose.position().set(x, y, z);          // 位置
pose.orientation().set(quaternion);     // 朝向 (Quaterniond)

// 分配新的 SubLevel
ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);

// 在 SubLevel 中放置方块
initSingleBlockSubLevel(subLevel, Blocks.GRINDSTONE.defaultBlockState());

// 告诉物理管线：这个 SubLevel 要 teleport 到目标位置
pipeline.teleport(subLevel, spawnVec, orientation);

// 更新上一帧位姿（避免首帧速度突变）
subLevel.updateLastPose();
```

### SubLevel 的物理属性

SubLevel 的质量和惯性由其中包含的方块**自动计算**。Sable 读取每个方块的 `PhysicsBlockPropertyHelper` 来获取质量和惯性值。

服务器端全自动处理：

```java
// SubLevelPhysicsSystem 内部监听方块变化：
public void handleBlockChange(...) {
    // 更新 SubLevel 的质量数据
    this.updateMassDataFromBlockChange(subLevel, globalBlockPos, oldState, newState, true);
    // 通知物理管线
    this.pipeline.handleBlockChange(...);
}
```

### 查询 SubLevel

```java
// 通过坐标获取 SubLevel
SubLevel subLevel = container.getSubLevel(plotX, plotZ);

// 通过 UUID 获取
SubLevel subLevel = container.getSubLevel(uuid);

// 获取所有 SubLevel
List<ServerSubLevel> allSubLevels = container.getAllSubLevels();

// AABB 相交查询
Iterable<SubLevel> intersecting = container.queryIntersecting(boundingBox);
```

---

## 4. 物理管线 (PhysicsPipeline)

### 管线接口

`PhysicsPipeline` 是连接 Minecraft 和 Rapier 的桥梁：

```java
public interface PhysicsPipeline {
    void init(Vector3dc gravity, double universalDrag);  // 初始化
    void dispose();                                       // 销毁
    void prePhysicsTicks();                               // 物理 tick 前
    void physicsTick(double timeStep);                    // 物理 tick
    void postPhysicsTicks();                              // 物理 tick 后
    void tick();                                          // 游戏 tick
    
    // SubLevel 管理
    void add(ServerSubLevel subLevel, Pose3dc pose);
    void remove(ServerSubLevel subLevel);
    Pose3d readPose(ServerSubLevel subLevel, Pose3d dest);
    
    // 物体操作
    void teleport(PhysicsPipelineBody body, Vector3dc pos, Quaterniondc orient);
    void applyImpulse(PhysicsPipelineBody body, Vector3dc pos, Vector3dc force);
    void applyLinearAndAngularImpulse(PhysicsPipelineBody body, Vector3dc force, Vector3dc torque, boolean wakeUp);
    void addLinearAndAngularVelocity(PhysicsPipelineBody body, Vector3dc linVel, Vector3dc angVel);
    Vector3d getLinearVelocity(PhysicsPipelineBody body, Vector3d dest);
    Vector3d getAngularVelocity(PhysicsPipelineBody body, Vector3d dest);
    void wakeUp(PhysicsPipelineBody body);
    
    // 约束
    <T extends PhysicsConstraintHandle> T addConstraint(
        ServerSubLevel subLevelA, ServerSubLevel subLevelB,
        PhysicsConstraintConfiguration<T> config);
    
    // 特殊物体
    BoxHandle addBox(BoxPhysicsObject box);
    RopeHandle addRope(RopePhysicsObject rope);
}
```

### 获取物理管线

```java
// 方式一：通过 SubLevelPhysicsSystem
SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
SubLevelPhysicsSystem physicsSystem = ((ServerSubLevelContainer)container).physicsSystem();
PhysicsPipeline pipeline = physicsSystem.getPipeline();

// 方式二：通过 RigidBodyHandle (见下一节)
```

### Tick 流程 (在 SubLevelPhysicsSystem 中)

```java
private void tickPipelinePhysics(ServerSubLevelContainer container) {
    pipeline.prePhysicsTicks();
    
    for (int substep = 0; substep < config.substepsPerTick; substep++) {
        double dt = 0.05 / config.substepsPerTick;
        
        // 1. 重置力队列
        for (ServerSubLevel subLevel : container.getAllSubLevels())
            subLevel.prePhysicsTickBegin();
        
        // 2. 更新质量数据
        for (ServerSubLevel subLevel : container.getAllSubLevels())
            subLevel.updateMergedMassData(partialPhysicsTick);
        
        // 3. 运行预物理计算 (升力/阻力/BE 回调)
        for (ServerSubLevel subLevel : container.getAllSubLevels())
            subLevel.prePhysicsTick(this, handle, dt);
        
        // 4. 发布 prePhysicsTick 事件
        SableEventPublishPlatform.INSTANCE.prePhysicsTick(this, dt);
        
        // 5. 应用排队力
        for (ServerSubLevel subLevel : container.getAllSubLevels())
            subLevel.applyQueuedForces(this, handle, dt);
        
        // 6. 执行物理步进 (Rapier)
        pipeline.physicsTick(dt);
        
        // 7. 从 Rapier 读取结果位姿
        updateAllPoses(container);
        
        // 8. 发布 postPhysicsTick 事件
        SableEventPublishPlatform.INSTANCE.postPhysicsTick(this, dt);
    }
    
    pipeline.postPhysicsTicks();
}
```

**关键点**：一个游戏 tick (50ms) 内默认包含多个物理子步 (substep)。`ForgeSablePostPhysicsTickEvent` 在每个子步后都触发。

---

## 5. RigidBodyHandle——施力接口

`RigidBodyHandle` 是操作物理物体的便捷包装——不直接操作 Pipeline，而是通过它来施力。

### 获取句柄

```java
// 方式一：通过 SubLevel
RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);

// 方式二：通过 PipelineBody + Level
RigidBodyHandle handle = RigidBodyHandle.of(serverLevel, pipelineBody);
```

### 施力方法

```java
// 在指定点施力（产生力和力矩）
handle.applyImpulseAtPoint(position, force);

// 分别施加线性和角冲量
handle.applyLinearAndAngularImpulse(linearImpulse, torque);

// 仅线性
handle.applyLinearImpulse(impulse);

// 仅角（扭矩）
handle.applyAngularImpulse(torque);
handle.applyTorqueImpulse(torque);

// 直接加减速度
handle.addLinearAndAngularVelocity(linearVelocity, angularVelocity);

// 传送
handle.teleport(position, orientation);

// 读取速度
Vector3d linearVel = handle.getLinearVelocity(new Vector3d());
Vector3d angularVel = handle.getAngularVelocity(new Vector3d());

// 应用 ForceTotal（累加的力）
handle.applyForcesAndReset(forceTotal);
```

### ForceTotal——力累加器

`ForceTotal` 自动将作用在物体上的力分解为线力和力矩：

```java
ForceTotal forceTotal = new ForceTotal();

// 记录在质心位置的力
forceTotal.applyLinearImpulse(force);

// 记录在指定点的力（自动计算力矩）
forceTotal.applyImpulseAtPoint(massTracker, position, force);

// 应用并重置
handle.applyForcesAndReset(forceTotal);
```

---

## 6. 约束系统 (Constraints)

### 约束类型

Sable 支持四种约束，都是通过 `pipeline.addConstraint()` 创建：

| 类型                               | 说明                | 自由度        |
|----------------------------------|-------------------|------------|
| `RotaryConstraintConfiguration`  | 旋转副——两个物体绕指定轴相对旋转 | 1 个旋转自由度   |
| `FixedConstraintConfiguration`   | 固定副——完全锁定相对位姿     | 0 自由度      |
| `FreeConstraintConfiguration`    | 自由副——6 自由度全自由     | 6 自由度      |
| `GenericConstraintConfiguration` | 通用副——可锁定任意轴       | 0~6 自由度可配置 |

### 约束句柄

所有约束都返回 `PhysicsConstraintHandle`，提供以下操作：

```java
// 获取关节冲量（用于调试反作用力）
void getJointImpulses(Vector3d linear, Vector3d angular);

// 启用/禁用碰撞（重要！两个 SubLevel 如果不希望互相碰撞，设 false）
void setContactsEnabled(boolean enabled);

// 设置马达：
// axis: 哪个轴
// targetPos: 目标位置
// targetVel: 目标速度
// stiffness: 刚度 (越大越硬)
// damping: 阻尼
void setMotor(ConstraintJointAxis axis, double targetPos, double targetVel,
              double stiffness, boolean forceLimitsEnabled, double damping);

// 移除约束
void remove();

// 是否有效
boolean isValid();
```

### RotaryConstraint（旋转副）示例

```java
RotaryConstraintConfiguration config = new RotaryConstraintConfiguration(
    pos1,           // 物体 A 上的连接点 (局部坐标)
    pos2,           // 物体 B 上的连接点 (局部坐标)
    new Vector3d(0, 1, 0),  // normal1: 物体 A 上的旋转轴方向
    new Vector3d(0, 1, 0)   // normal2: 物体 B 上的旋转轴方向
);

RotaryConstraintHandle handle = pipeline.addConstraint(subLevelA, subLevelB, config);

// 禁用碰撞（通常需要，否则约束物体会物理碰撞）
handle.setContactsEnabled(false);

// 设置马达驱动（实现自动瞄准/转向）
handle.setMotor(ConstraintJointAxis.ANGULAR_Y, 
    0,              // targetPos
    desiredSpeed,   // targetVelocity
    stiffness,      // 刚度
    true,           // 力限制
    damping         // 阻尼
);
```

### GenericConstraint（通用副）示例

```java
// 锁定全部线性轴 + 保留 ANGULAR_X 自由（像门铰链一样）
GenericConstraintConfiguration config = new GenericConstraintConfiguration(
    pos1, pos2,                    // 连接点
    new Quaterniond(),             // orientation1
    new Quaterniond(),             // orientation2
    EnumSet.of(                    // 锁定的轴
        ConstraintJointAxis.LINEAR_X,
        ConstraintJointAxis.LINEAR_Y,
        ConstraintJointAxis.LINEAR_Z,
        ConstraintJointAxis.ANGULAR_Y,
        ConstraintJointAxis.ANGULAR_Z
    ) // ANGULAR_X 未锁定 = 可绕 X 轴旋转（俯仰）
);

GenericConstraintHandle handle = pipeline.addConstraint(subLevelA, subLevelB, config);
handle.setContactsEnabled(false);
```

### 约束坐标注意事项

约束连接点的坐标使用的是 SubLevel **底层的局部坐标**。Sable 会在内部通过每个 SubLevel 的 `logicalPose()` 将它们变换到世界空间：

```java
// 获取 SubLevel 中"中心方块"的局部坐标
BlockPos centerBlock = subLevel.getPlot().getCenterBlock();
Vector3d localPos = new Vector3d(
    centerBlock.getX() + 0.5,   // 方块中心
    centerBlock.getY() + 0.5,
    centerBlock.getZ() + 0.5
);

// 可以加上偏移量来微调连接点
localPos.add(offsetX, offsetY, offsetZ);
```

---

## 7. BlockEntitySubLevelActor——物理 Tick 回调

如果想在**每个物理子步**中对 SubLevel 施加自定义力，实现 `BlockEntitySubLevelActor` 接口：

```java
public interface BlockEntitySubLevelActor {
    // 游戏 tick 回调 (20Hz)
    default void sable$tick(ServerSubLevel subLevel) { }
    
    // 物理 tick 回调 (~100Hz，每个子步触发一次)
    default void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) { }
    
    // 加载依赖（用于约束场景）
    default Iterable<SubLevel> sable$getLoadingDependencies() { return null; }
    
    // 连接依赖
    default Iterable<SubLevel> sable$getConnectionDependencies() { return null; }
}
```

### 使用示例——悬架测试 BlockEntity

```java
@BlockEntitySubLevelActor  // 或者你的 BlockEntity 直接实现该接口
public class SuspensionTestBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        // 获取当前速度
        Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        
        // 计算阻力
        double dragCoeff = 0.5;
        Vector3d dragForce = new Vector3d(velocity)
            .mul(-dragCoeff * velocity.length());
        
        // 施加阻力
        handle.applyLinearImpulse(dragForce.mul(timeStep));
    }
}
```

---

## 8. 力系统与 ForceGroup

### ForceGroup

Sable 有一个注册表式的力分组系统，用于组织和可视化不同来源的力：

```java
public record ForceGroup(Component name, Component description, int color, boolean defaultDisplayed) { }
```

内置的 ForceGroup（在 `ForceGroups` 中注册）：

| 名称             | 颜色 | 说明   |
|----------------|----|------|
| GRAVITY        | 深灰 | 重力   |
| DRAG           | 浅蓝 | 空气阻力 |
| LEVITATION     | 紫色 | 漂浮力  |
| BALLOON_LIFT   | 橙色 | 气球升力 |
| PROPULSION     | 绿色 | 推进力  |
| LIFT           | 青绿 | 升力   |
| MAGNETIC_FORCE | 深紫 | 磁力   |

### QueuedForceGroup——排队力系统

`QueuedForceGroup` 允许你记录力到 SubLevel 上，然后由物理系统在每个子步统一应用：

```java
// 获取或创建排队力组
QueuedForceGroup forceGroup = serverSubLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());

// 在指定点施力（自动计算力矩）
forceGroup.applyAndRecordPointForce(point, forceVector);

// 物理系统自动在 prePhysicsTick 中应用：
// subLevel.applyQueuedForces(physicsSystem, handle, timeStep);
```

---

## 9. KinematicContraption——运动学物体

`KinematicContraption` 接口代表**运动学驱动的物体**——它不通过物理力来移动，而是由外部系统（如 Create 模组的动力网络）指定位置：

```java
public interface KinematicContraption {
    void sable$getLocalBounds(BoundingBox3i dest);        // 局部包围盒
    BlockGetter sable$blockGetter();                       // 方块获取器
    MassTracker sable$getMassTracker();                    // 质量数据
    Vector3dc sable$getPosition(double partialTick);       // 当前插值位置
    Quaterniond sable$getOrientation(double partialTick);  // 当前插值朝向
    Map<BlockPos, LiftProviderContext> sable$liftProviders(); // 升力提供者
    boolean sable$shouldCollide();                         // 是否碰撞
    boolean sable$isValid();                               // 是否有效
}
```

运动学物体通过 `pipeline.add(contraption)` 和 `pipeline.remove(contraption)` 管理。

---

## 10. 物理配置

### 运行时配置 (PhysicsConfigData)

```java
// 通过 SubLevelPhysicsSystem 获取/修改配置
SubLevelPhysicsSystem system = container.physicsSystem();
PhysicsConfigData config = system.getConfig();
// config.substepsPerTick    // 每游戏 tick 的物理子步数
// config.solverIterations   // 求解器迭代次数
// config.pgsIterations      // PGS 迭代次数
```

### SableConfig (配置文件)

Sable 提供 NeoForge ModConfigSpec 配置项：

```java
// 在 sable-server.toml 中配置
SableConfig.SUB_LEVEL_SPLITTING             // SubLevel 是否可分裂 (默认 true)
SableConfig.SUB_LEVEL_TRACKING_RANGE        // 物理网络同步范围 (默认 320)
SableConfig.SUB_LEVEL_REMOVE_MIN            // SubLevel 可存在的最低 Y (默认 -10000)
SableConfig.SUB_LEVEL_REMOVE_MAX            // SubLevel 可存在的最高 Y (默认 100000)
SableConfig.VELOCITY_RETAINED_ON_LOAD       // 加载时保留的速度比例 (默认 0.9)
SableConfig.SUB_LEVEL_PUNCH_STRENGTH_MULTIPLIER    // 击打冲量倍率 (默认 2.1)
```

---

## 11. 完整的实战示例

### 示例：创建一块可物理坠落的方块

```java
// ServerSide - 将一块沙子变为物理物体

ServerLevel level = (ServerLevel) event.getLevel();
ServerSubLevelContainer container = 
    (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
PhysicsPipeline pipeline = physicsSystem.getPipeline();

// 1. 创建 SubLevel
Pose3d pose = new Pose3d();
pose.position().set(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);
pose.orientation().set(new Quaterniond());  // 无旋转

ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);

// 2. 在 SubLevel 中放置沙子
fillSubLevelWithBlock(subLevel, Blocks.SAND.defaultBlockState(),
    new BoundingBox3i(0, 0, 0, 1, 1, 1));

// 3. 注册到物理系统（自动添加质量、碰撞体）
// 这一步由 SubLevelPhysicsSystem.onSubLevelAdded() 自动完成

// 4. 可选：赋予初速度
RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
if (handle != null && handle.isValid()) {
    handle.addLinearAndAngularVelocity(
        new Vector3d(0, -5, 0),  // 向下初速度
        new Vector3d(0, 0, 0)    // 无旋转
    );
}
```

### 示例：两段可旋转的机械臂

```java
// 创建两个 SubLevel
ServerSubLevel arm1 = (ServerSubLevel) container.allocateNewSubLevel(pose1);
ServerSubLevel arm2 = (ServerSubLevel) container.allocateNewSubLevel(pose2);

fillSubLevelWithBlock(arm1, Blocks.IRON_BLOCK.defaultBlockState(), arm1Bounds);
fillSubLevelWithBlock(arm2, Blocks.IRON_BLOCK.defaultBlockState(), arm2Bounds);

// 用 RotaryConstraint 连接（绕 Y 轴旋转）
BlockPos center1 = arm1.getPlot().getCenterBlock();
BlockPos center2 = arm2.getPlot().getCenterBlock();

RotaryConstraintConfiguration config = new RotaryConstraintConfiguration(
    new Vector3d(center1.getX() + 0.5, center1.getY() + 0.5, center1.getZ() + 0.5),
    new Vector3d(center2.getX() + 0.5, center2.getY() + 0.5, center2.getZ() + 0.5),
    new Vector3d(0, 1, 0),  // 旋转轴 Y
    new Vector3d(0, 1, 0)
);

var joint = pipeline.addConstraint(arm1, arm2, config);
joint.setContactsEnabled(false);  // 避免两个臂互相碰撞

// 用马达驱动第二个臂
joint.setMotor(ConstraintJointAxis.ANGULAR_Y,
    0,              // 目标位置
    0.5,            // 目标角速度 (rad/s)
    1000,           // 刚度
    true,           // 启用力矩限制
    10              // 阻尼
);
```

### 示例：使用 Sable PostPhysicsTick 事件

此事件是 `ForgeSablePostPhysicsTickEvent`，在 NeoForge 总线上发布：

```java
@SubscribeEvent
public static void onPostPhysicsTick(ForgeSablePostPhysicsTickEvent event) {
    SubLevelPhysicsSystem physicsSystem = event.getPhysicsSystem();
    ServerLevel level = physicsSystem.getLevel();
    
    // 在这里做物理同步后立即需要执行的操作
    // 如：同步骑乘玩家位置、更新 HUD 数据等
}
```

---

## 12. 常见问题与最佳实践

### 坐标转换

SubLevel 使用局部坐标和 `Pose3d`（位姿）与世界空间做变换：

```java
// SubLevel 局部→世界
Vector3d worldPos = new Vector3d(localX, localY, localZ);
subLevel.logicalPose().transformPosition(worldPos);

// 世界→SubLevel 局部（用在 SubLevel 容器内）
Vector3d localPos = new Vector3d(worldX, worldY, worldZ);
subLevel.logicalPose().transformInversePosition(localPos);
```

### 碰撞禁用

当两个 SubLevel 需要重叠时（如炮管穿透车体），**必须**用约束禁用碰撞：

```java
var freeJoint = pipeline.addConstraint(subLevelA, subLevelB, 
    new FreeConstraintConfiguration(posA, posB, new Quaterniond()));
freeJoint.setContactsEnabled(false);  // ← 关键！
```

### 速度读取时机

在 `sable$physicsTick` 回调中读取的速度是**上一个子步**的结果。如果需要最新的速度，请在物理 tick 之后（通过
`ForgeSablePostPhysicsTickEvent`）读取。

### 避免 NaN 崩溃

如果 SubLevel 的位姿出现 NaN，Sable 会自动尝试恢复。可在日志中看到：

```
Invalid position or orientation received for sub-level X from pipeline.
Attempting to recover...
```

### 性能建议

- 减少每个 SubLevel 的方块数量——Rapier 对含大量方块的 SubLevel 碰撞检测开销更大
- `substepsPerTick` 默认值影响精度和性能，子步越多物理越精确但越吃性能
- 合理使用 `pipeline.wakeUp()`——静止的物体不消耗 CPU 资源

### 常见陷阱

1. **忘记 `setContactsEnabled(false)`** → 两个相连的 SubLevel 会互相弹开
2. **约束坐标使用世界坐标而非局部坐标** → 约束飞到奇怪的位置
3. **没有调用 `subLevel.updateLastPose()`** → 第一帧出现巨大速度突变
4. **在客户端调用服务端 API** → SubLevel API 大部分只在服务端可用

---

> 本教程基于 Sable v2.0.3 源码编写。API 可能在不同版本之间有所变化。
> Sable 项目地址：https://github.com/ryanhcode/sable
> IAC-P 整合示例：本项目的 `src/main/java/` 目录下有大量实战用法。
