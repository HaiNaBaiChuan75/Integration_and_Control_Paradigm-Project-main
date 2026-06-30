# Sable Engine 对象参考

本文档说明 Sable 物理引擎（`sable-neoforge-1.21.1:1.1.3`）的核心对象及其关系，
帮助理解 IAC-P 如何利用 Sable 管理载具的物理行为。

关键版本：Sable sable-neoforge 1.1.3 + sable-companion 1.5.0 （Rapier 刚体仿真）

---

## 对象关系总览

```
Level  (Minecraft)
  └── SubLevelContainer (每 Level 一个)
        └── SubLevel (一个"物理实体" = 一组区块)
              └── LevelPlot (SubLevel 内的区块管理)
                    └── PlotChunkHolder[] (持有 LevelChunk)
                    └── BlockEntitySubLevelActor[] (物理参与块)
                    └── BlockSubLevelLiftProvider[] (升力/阻力块)
                    └── KinematicContraption[] (运动学机构)
  SubLevelPhysicsSystem (每 Level 一个)
        └── PhysicsPipeline (底层 Rapier 管线)
              │  └── 每个 ServerSubLevel / KinematicContraption = 一个 RigidBody
              │  └── 通过 RigidBodyHandle 暴露 API
              └── RigidBodyHandle (轻量句柄，指向 PhysicsPipelineBody)
```

---

## 核心层级

### 1. `SubLevel` — 物理实体的最小单位

**包**: `dev.ryanhcode.sable.sublevel.SubLevel`

一个 SubLevel 代表一个**运动的物体**：它包含一组区块（chunks），这些区块可以整体平移/旋转。
在 IAC-P 中，一辆载具就是一个 SubLevel。

**关键字段**:

| 字段             | 类型              | 说明                                 |
|----------------|-----------------|------------------------------------|
| `level`        | `Level`         | 所属的主世界                             |
| `pose`         | `Pose3d`        | **当前逻辑位姿**（位置+朝向+旋转点+缩放）           |
| `lastPose`     | `Pose3d`        | 上一 tick 的位姿                        |
| `globalBounds` | `BoundingBox3d` | **世界空间包围盒**（由 plot 边界 * pose 变换得到） |
| `plot`         | `LevelPlot`     | 该 SubLevel 的区块容器                   |
| `uniqueId`     | `UUID`          | 全局唯一 ID                            |
| `name`         | `String`        | 可读名称                               |

**子类**:

| 子类               | 所在包              | 用途      |
|------------------|------------------|---------|
| `ServerSubLevel` | `sable.sublevel` | 服务端物理实体 |
| `ClientSubLevel` | `sable.sublevel` | 客户端渲染实体 |

**核心方法**:

- `tick()` — 委托给 `plot.tick()`
- `updateBoundingBox()` — 根据 plot 边界和 pose 更新 world-space AABB
- `updateLastPose()` — 备份当前 pose 为 lastPose
- `onPlotBoundsChanged()` — 当 plot 内方块变化时触发

### 2. `ServerSubLevel` — 物理引擎的直接操作对象

**包**: `dev.ryanhcode.sable.sublevel.ServerSubLevel`

服务端运行的 SubLevel，实现了 `PhysicsPipelineBody` 接口（可被添加到物理管线中）。

**额外的关键字段**:

| 字段                      | 类型                                  | 说明                    |
|-------------------------|-------------------------------------|-----------------------|
| `latestLinearVelocity`  | `Vector3d`                          | 上一 tick 的线速度（通过位姿差计算） |
| `latestAngularVelocity` | `Vector3d`                          | 上一 tick 的角速度          |
| `trackingPlayers`       | `Set<UUID>`                         | 正在追踪此 SubLevel 的玩家    |
| `queuedForceGroups`     | `Map<ForceGroup, QueuedForceGroup>` | 排队等待施加的力              |
| `massTracker`           | `MergedMassTracker`                 | 合并质量数据                |
| `runtimeId`             | `int`                               | 管线内运行时 ID             |

**PhysicsPipelineBody 接口**要求实现:

- `getRuntimeId()` → 管线内标识
- `getMassTracker()` → 质量/质心/惯性张量
- `isRemoved()` → 是否已标记移除

**物理更新流程** (`SubLevelPhysicsSystem.tickPipelinePhysics()`):

1. `prePhysicsTickBegin()` — 重置 QueuedForceGroup
2. `updateMergedMassData()` — 更新质心/惯性
3. `prePhysicsTick()` — 调用所有 `BlockEntitySubLevelActor.sable$physicsTick()` + LiftProvider 计算升力/阻力
4. `applyQueuedForces()` — 施加累积的力
5. 管线执行 `physicsTick()` → Rapier 计算新位置
6. `updatePose()` — 从 Rapier 读出位姿并计算速度

### 3. `ClientSubLevel` — 客户端侧的渲染实体

**包**: `dev.ryanhcode.sable.subplot.ClientSubLevel`

- 通过 `SubLevelSnapshotInterpolator` 处理服务端位姿插值
- 计算天光缩放（SubLevel 位于地下时调暗）
- 管理渲染数据 (`SubLevelRenderData`)
- 提供 `renderPose()` — 用于渲染的插值位姿

---

## 容器与组织

### 4. `SubLevelContainer` — SubLevel 的二维网格容器

**包**: `dev.ryanhcode.sable.api.sublevel.SubLevelContainer`

每 Level 一个（主世界 = 一个 `SubLevelContainer`）。
SubLevel 按**地块图（plot grid）** 组织：二维 tile 系统，每个 tile 可以放置一个 SubLevel。

| 常量                        | 值     | 含义                      |
|---------------------------|-------|-------------------------|
| `DEFAULT_LOG_SIZE_LENGTH` | 7     | 网格边长 = 2^7 = 128        |
| `DEFAULT_LOG_PLOT_SIZE`   | 7     | 地块边长 = 2^7 = 128 chunks |
| `DEFAULT_ORIGIN`          | 10000 | 网格原点偏移                  |

**子类**:

- `ServerSubLevelContainer` — 服务端，额外持有 `SubLevelPhysicsSystem` + `SubLevelTrackingSystem`
- `ClientSubLevelContainer` — 客户端，额外持有插值状态

**关键方法**:

- `allocateNewSubLevel(Pose3d)` — 在空位创建新 SubLevel
- `allocateSubLevel(UUID, x, z, Pose3d)` — 指定位置创建
- `removeSubLevel(x, z, reason)` — 移除（REMOVED = 永久 / UNLOADED = 临时）
- `getPlot(ChunkPos)` — 根据全局 chunk 坐标定位所在的 SubLevel
- `queryIntersecting(BoundingBox3dc)` — 空间查询

### 5. `LevelPlot` — 地块内的区块管理

**包**: `dev.ryanhcode.sable.sublevel.plot.LevelPlot`

每个 `SubLevel` 含一个 Plot。Plot 管理一组 `PlotChunkHolder`（持有 Minecraft `LevelChunk`），
负责子世界的区块生命周期。

| 字段                            | 含义            |
|-------------------------------|---------------|
| `plotPos` (ChunkPos)          | 地块在网格中的位置     |
| `localBounds` (BoundingBox3i) | 本地空间中有内容的边界   |
| `blockEntityActors`           | 注册的物理参与方块的 BE |
| `blockEntityReactionWheels`   | 反作用飞轮（姿态控制）   |

**子类**:

- `ServerLevelPlot` — 服务端，管理光照引擎、序列化保存/加载、LiftProvider、KinematicContraption
- `ClientLevelPlot` — 客户端侧，简洁实现

---

## 物理引擎层

### 6. `SubLevelPhysicsSystem` — 每 Level 一个物理系统

**包**: `dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem`

每 Level 的核心调度器。实现了 `SubLevelObserver` 接口。

**职责**:

- 创建 `PhysicsPipeline`（底层 Rapier 管线）
- 每个 tick：为所有 ServerSubLevel 执行 `prePhysicsTick()` → `applyQueuedForces()` → 管线步进 → `updatePose()`
- 管理方块变化对质量的增量更新
- 管理脉冲冷却（防连击 `tryPunch()`）
- 管理 `ArbitraryPhysicsObject`（额外物理对象，如绳索/方块）

**物理步进** (`config.substepsPerTick` 次迭代）:

```
prePhysicsTicks()
for each substep:
  prePhysicsTickBegin()       // 重置全 SubLevel 的力队列
  updateMergedMassData()      // 更新质心/惯性
  prePhysicsTick()            // BlockEntitySubLevelActor + LiftProvider
  prePhysicsTick (SableEvent) // 事件总线
  applyQueuedForces()         // 排队力 → 脉冲
  pipeline.physicsTick(dt)    // Rapier 解算
  processSubLevelRemovals()   // 移除无效 SubLevel
  updateAllPoses()            // 从管线读出位姿
  postPhysicsTick (SableEvent)// 事件总线
```

### 7. `PhysicsPipeline` — Rapier 物理管线

**包**: `dev.ryanhcode.sable.api.physics.PhysicsPipeline`

底层刚体仿真的接口。添加/移除 `ServerSubLevel` 或 `KinematicContraption` 时，
管线会创建/销毁对应的 Rapier RigidBody。

**关键方法**:

| 方法                                                            | 说明                   |
|---------------------------------------------------------------|----------------------|
| `add(ServerSubLevel, Pose3dc)`                                | 将 SubLevel 注册为刚体     |
| `remove(ServerSubLevel)`                                      | 移除刚体                 |
| `physicsTick(double dt)`                                      | 步进仿真                 |
| `applyImpulse(body, point, force)`                            | 在指定点施加冲量             |
| `applyLinearAndAngularImpulse(body, impulse, torque, wakeUp)` | 施加线/角冲量              |
| `teleport(body, pos, orientation)`                            | 瞬移刚体                 |
| `readPose(body, dest)`                                        | 从管线读出位姿              |
| `addConstraint(...)`                                          | 添加约束（两个 SubLevel 之间） |
| `addRope(...)` / `addBox(...)`                                | 添加额外物理对象             |

### 8. `RigidBodyHandle` — 刚体操作句柄

**包**: `dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle`

对 `PhysicsPipelineBody`（通常是 `ServerSubLevel`）× `SubLevelPhysicsSystem` 的轻量封装。

**创建方式**:

```java
// 方式1：通过等级和对象
RigidBodyHandle.of(serverLevel, physicsPipelineBody);
// 方式2：直接通过 SubLevel
RigidBodyHandle.of(serverSubLevel);
// 方式3：从 SubLevelPhysicsSystem
physicsSystem.getPhysicsHandle(serverSubLevel);
```

**关键操作**:

- `applyImpulseAtPoint(position, force)` — 在**世界坐标点**施加冲量
- `applyLinearAndAngularImpulse(impulse, torque)` — 施加线/角冲量
- `applyLinearImpulse(impulse)` — 施加纯线冲量
- `applyAngularImpulse(torque)` — 施加纯角冲量（扭矩冲量）
- `teleport(position, orientation)` — 瞬移
- `getLinearVelocity(dest)` / `getAngularVelocity(dest)` — 读取当前速度
- `addLinearAndAngularVelocity(linear, angular)` — 直接加减速度（不经过队列）
- `isValid()` — 检查刚体是否还有效（未被移除）

> 注意：所有 `apply*` 方法最终都是通过管线执行的，不排队，**立即生效**。
> 如果需要收集力然后在管线步进前统一施加，请使用 `QueuedForceGroup` 系统。

---

## 质量和力学量

### 9. `MassData` — 质量数据接口

**包**: `dev.ryanhcode.sable.api.physics.mass.MassData`

| 方法                          | 返回          | 说明                           |
|-----------------------------|-------------|------------------------------|
| `getMass()`                 | `double`    | 总质量 (kg)                     |
| `getInverseMass()`          | `double`    | 1/质量                         |
| `getInertiaTensor()`        | `Matrix3dc` | 3×3 惯性张量                     |
| `getInverseInertiaTensor()` | `Matrix3dc` | 逆惯性张量                        |
| `getCenterOfMass()`         | `Vector3dc` | 质心（世界坐标）                     |
| `isInvalid()`               | `boolean`   | 质量 ≤ 0 → 无效（会触发 SubLevel 移除） |

### 10. `MassTracker` — MassData 的具体实现

**包**: `dev.ryanhcode.sable.api.physics.mass.MassTracker`

增量更新质量/质心/惯性张量。SubLevel 内的每个方块变化（`handleBlockChange`）
都会通过 `MassTracker.addBlockMass()` 更新总质量数据。

### 11. `MergedMassTracker` — 合并质量追踪器

`MergedMassTracker` 包装了 SubLevel 自身的质量追踪器 + 来自其他源的合并质量数据，
最终暴露给 `PhysicsPipelineBody.getMassTracker()`。

---

## 力系统

### 12. `ForceGroup` — 力的分类标签

**包**: `dev.ryanhcode.sable.api.physics.force.ForceGroup`

```java
public record ForceGroup(Component name, Component description, int color, boolean defaultDisplayed)
```

注册在 `ForceGroups.REGISTRY` 中的预定义组:

| ForceGroup       | 颜色       | 默认显示 | 用途        |
|------------------|----------|------|-----------|
| `GRAVITY`        | 0x216D95 | 否    | 重力        |
| `DRAG`           | 0x835AE1 | 否    | 阻力（通用/空气） |
| `LEVITATION`     | 0x734000 | 是    | 反重力       |
| `BALLOON_LIFT`   | 0xD2585E | 是    | 气球升力      |
| `PROPULSION`     | 0x5A7DDF | 是    | 推进力       |
| `LIFT`           | 0x8CB4C6 | 是    | 气动升力（翼面）  |
| `MAGNETIC_FORCE` | 0xE05543 | 否    | 磁力        |

### 13. `QueuedForceGroup` — 排队力容器

**包**: `dev.ryanhcode.sable.api.physics.force.QueuedForceGroup`

每个 `ServerSubLevel` 维护一个 `Map<ForceGroup, QueuedForceGroup>`。
力的收集发生在 `prePhysicsTick()` 阶段，统一施加在 `applyQueuedForces()` 阶段。

`ForceTotal` 是力的累加器：跟踪 `localForce`（线力总和）+ `localTorque`（扭矩总和）。

### 14. `BlockEntitySubLevelActor` — 物理参与方块的接口

**包**: `dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor`

任何想参与 SubLevel 物理的 BlockEntity 必须实现此接口：

```java
interface BlockEntitySubLevelActor {
    void sable$tick(ServerSubLevel subLevel);           // 游戏 tick（20Hz）
    void sable$physicsTick(ServerSubLevel, RigidBodyHandle, double dt); // 物理 tick（~100Hz）
    Iterable<SubLevel> sable$getConnectionDependencies(); // 加载依赖
}
```

IAC-P 的 `SuspensionTestBlockEntity`、`CockpitBlockEntity` 等都实现了此接口。

### 15. `BlockSubLevelLiftProvider` — 升力/阻力方块的接口

**包**: `dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider`

产生气动升力和阻力的方块（如机翼、气球）。提供：

| 方法                                   | 默认值    | 说明        |
|--------------------------------------|--------|-----------|
| `sable$getNormal(state)`             | 方向     | 升力面法线     |
| `sable$getLiftScalar()`              | 0.475  | 升力系数      |
| `sable$getParallelDragScalar()`      | 0.75   | 法向阻力系数    |
| `sable$getDirectionlessDragScalar()` | 0.0689 | 方向无关阻力系数  |
| `sable$contributeLiftAndDrag(...)`   | —      | 计算升力/阻力贡献 |

升力计算使用 `DimensionPhysicsData.getAirPressure()`（随高度变化的气压曲线）。

---

## 额外物理对象

### 16. `KinematicContraption` — 运动学机构

**包**: `dev.ryanhcode.sable.api.sublevel.KinematicContraption`

Create 等模组中的运动学机构（如传送带、机械臂），注册在 `ServerLevelPlot` 中。
有自己的位置/朝向插值函数、质量追踪器、升力面提供器。

### 17. `ArbitraryPhysicsObject` — 自由物理对象

**包**: `dev.ryanhcode.sable.api.physics.object`

管线中的额外物理对象（非 SubLevel）：

- `BoxPhysicsObject` / `BoxHandle` — 简单盒子
- `RopePhysicsObject` / `RopeHandle` — 绳索

### 18. `PhysicsConstraintHandle` / `PhysicsConstraintConfiguration` — 约束系统

两个 SubLevel 之间可以添加约束（铰链、滑块、固定等），用于实现炮塔（旋转约束）、
悬挂等复合结构。

---

## 辅助概念

### 19. `Pose3d` — 6-DOF 位姿

**包**: `dev.ryanhcode.sable.companion.math.Pose3d`

包含：

- `position` (Vector3d) — 世界坐标位置
- `orientation` (Quaterniond) — 朝向四元数
- `rotationPoint` (Vector3d) — 旋转中心（局部偏移）
- `scale` (Vector3d) — 缩放（默认为 (1,1,1)）

实现 `Pose3dc` 接口提供只读视图。

### 20. `BoundingBox3d` / `BoundingBox3i` — 包围盒

- `BoundingBox3d` — 浮点精度世界包围盒
- `BoundingBox3i` — 整数精度本地包围盒（以区块局部坐标表示）

### 21. `SubLevelLoadingTicket` / `SubLevelTicketInfo` — 加载票证

用于在服务端管理 SubLevel 的加载/卸载范围（chunk ticket 系统）。

### 22. `SubLevelTrackingSystem` — 追踪系统

管理哪些玩家应该接收哪些 SubLevel 的更新数据包。

### 23. `SubLevelRemovalReason` — 移除原因枚举

- `UNLOADED` — 临时卸载（玩家离开范围）
- `REMOVED` — 永久移除（SubLevel 消亡）

### 24. `SubLevelSnapshotInterpolator` — 客户端位姿插值

**包**: `dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator`

处理服务端快照的插值，使客户端渲染平滑。

---

## IAC-P 中使用示例

| IAC-P 类                             | Sable 对象                                | 关系                                                    |
|-------------------------------------|-----------------------------------------|-------------------------------------------------------|
| `SuspensionTestBlockEntity`         | `BlockEntitySubLevelActor`              | 实现接口，在 `sable$physicsTick()` 中通过 `RigidBodyHandle` 施力 |
| `CockpitBlockEntity`                | `BlockEntitySubLevelActor`              | 实现接口 + `sable$getConnectionDependencies()`            |
| `TurretBaseBlock`                   | 两个 SubLevel 通过约束链接                      | Grindstone SubLevel + LightningRod SubLevel           |
| `SubLevelUtil`                      | `SubLevel` / `SubLevelContainer`        | 辅助扫描/操作                                               |
| `ComponentRegistry`                 | `SubLevel`                              | 按 `SubLevel + ComponentRole` 索引功能组件                   |
| `SablePostPhysicsTickEvent`         | `SubLevelPhysicsSystem.postPhysicsTick` | 监听物理 tick 后的 Sync 事件                                  |
| `AffiliationRegistry`               | `SubLevel` UUID                         | 通过 SubLevel UUID 追踪归属                                 |
| `EngineModel` / `TransmissionModel` | `RigidBodyHandle`                       | 通过句柄施加推进力                                             |
| `TirePhysicsCalculator`             | `RigidBodyHandle`                       | 在接触点施加轮胎力                                             |
| `BulletTrailRenderer`               | `ClientSubLevel` 位置                     | 渲染弹道时参考 SubLevel 位姿                                   |
