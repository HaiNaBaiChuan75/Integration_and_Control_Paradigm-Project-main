# 常用物理量查询指南

本文档说明在 IAC-P / Sable 系统中查找常见物理量的位置和方法。

---

## 一、位姿与变换 (Pose & Transform)

### 位置 — `position`

| 访问路径                                  | 类型          | 说明                      |
|---------------------------------------|-------------|-------------------------|
| `subLevel.logicalPose().position()`   | `Vector3d`  | 当前逻辑位置（SubLevel 世界坐标中心） |
| `subLevel.lastPose().position()`      | `Vector3dc` | 上一 tick 的位置             |
| `subLevel.getPlot().getCenterBlock()` | `BlockPos`  | SubLevel 地块中心方块位置       |

在 IAC-P 中通过 `SubLevelUtil` 获取：

```java
SubLevelUtil.getSubLevelPos(serverSubLevel) // BlockPos
```

### 朝向 — `orientation`

| 访问路径                                   | 类型             | 说明          |
|----------------------------------------|----------------|-------------|
| `subLevel.logicalPose().orientation()` | `Quaterniond`  | 当前旋转四元数     |
| `subLevel.lastPose().orientation()`    | `Quaterniondc` | 上一 tick 的旋转 |

### 完整位姿

| 访问路径                                            | 类型                          | 说明          |
|-------------------------------------------------|-----------------------------|-------------|
| `subLevel.logicalPose()`                        | `Pose3d`                    | 可写的位姿对象     |
| `subLevel.getPlot().getEmbeddedLevelAccessor()` | `EmbeddedPlotLevelAccessor` | 地块世界内的方块访问器 |

---

## 二、速度 (Velocity)

### 线速度 — `linearVelocity`

| 访问路径                                      | 类型          | 帧率     | 说明                                                   |
|-------------------------------------------|-------------|--------|------------------------------------------------------|
| `serverSubLevel.latestLinearVelocity`     | `Vector3d`  | ~100Hz | **上一物理 tick 的平均线速度**（通过位姿差计算：`(pos - lastPos) * 20`） |
| `handle.getLinearVelocity(dest)`          | `Vector3d`  | ~100Hz | **从 Rapier 管线直接读取的瞬时线速度**                            |
| `handle.getLinearVelocity()` (deprecated) | `Vector3dc` | ~100Hz | 同上，但分配新对象                                            |

> ⚠️ `latestLinearVelocity` 是**帧间差分估算**（`Δpos × 20`），`getLinearVelocity()` 是 Rapier 内部保存的**瞬时真实速度**
> ，后者更准确。

### 角速度 — `angularVelocity`

| 访问路径                                   | 类型         | 帧率     | 说明                                                                  |
|----------------------------------------|------------|--------|---------------------------------------------------------------------|
| `serverSubLevel.latestAngularVelocity` | `Vector3d` | ~100Hz | 通过朝向四元数差分估算：`2 * acos(dot(q_last, q_curr)) * normalized(diff) * 20` |
| `handle.getAngularVelocity(dest)`      | `Vector3d` | ~100Hz | 从 Rapier 读取的瞬时角速度                                                   |

### 客户端侧

| 访问路径                                            | 类型         | 说明           |
|-------------------------------------------------|------------|--------------|
| `clientSubLevel.latestNetworkedVelocity`        | `Vector3d` | 最后收到的网络同步速度  |
| `clientSubLevel.latestNetworkedAngularVelocity` | `Vector3d` | 最后收到的网络同步角速度 |

---

## 三、质量 (Mass)

### 总质量

| 访问路径                                          | 说明                        |
|-----------------------------------------------|---------------------------|
| `serverSubLevel.getMassTracker().getMass()`   | 当前总质量（包含所有方块的累加值）         |
| `serverSubLevel.getMassTracker().isInvalid()` | 质量是否无效（≤0 → SubLevel 会自毁） |

### 质心 (Center of Mass)

| 访问路径                                                | 说明                   |
|-----------------------------------------------------|----------------------|
| `serverSubLevel.getMassTracker().getCenterOfMass()` | `Vector3dc` — 世界坐标质心 |

### 惯性张量

| 访问路径                                                        | 说明                     |
|-------------------------------------------------------------|------------------------|
| `serverSubLevel.getMassTracker().getInertiaTensor()`        | `Matrix3dc` — 3×3 惯性张量 |
| `serverSubLevel.getMassTracker().getInverseInertiaTensor()` | `Matrix3dc` — 用于冲量计算   |

### 内部质量追踪器

| 访问路径                                  | 说明                                    |
|---------------------------------------|---------------------------------------|
| `serverSubLevel.getSelfMassTracker()` | `MassTracker` — SubLevel 自身的增量质量追踪器   |
| `serverSubLevel.getMassTracker()`     | `MergedMassTracker` — 合并后的质量数据（暴露给管线） |

### 方块级的质量

| 访问路径                                                             | 说明               |
|------------------------------------------------------------------|------------------|
| `PhysicsBlockPropertyHelper.getMass(blockGetter, pos, state)`    | 单个方块的质量          |
| `PhysicsBlockPropertyHelper.getInertia(blockGetter, pos, state)` | 单个方块的局部惯性 (Vec3) |

默认方块质量为 1.0（可通过 datapack `sable/block_properties` 配置）。

---

## 四、力与冲量 (Force & Impulse)

### 通过 RigidBodyHandle 施加

| 方法                                                             | 效果              | 使用场景     |
|----------------------------------------------------------------|-----------------|----------|
| `handle.applyLinearAndAngularImpulse(impulse, torque, wakeUp)` | 施加线冲量 + 扭矩冲量    | 通用       |
| `handle.applyLinearImpulse(impulse)`                           | 施加线冲量           | 推进器      |
| `handle.applyAngularImpulse(torque)`                           | 施加角冲量（扭矩）       | 姿态控制     |
| `handle.applyImpulseAtPoint(position, force)`                  | 在世界坐标点施力        | 轮胎力、碰撞响应 |
| `handle.addLinearAndAngularVelocity(linear, angular)`          | **直接加减速度**（力等效） | 简单控制     |

> 以上方法**立即生效**（不排队）。

### 排队力系统

```java
// 获取 SubLevel 的排队力组
QueuedForceGroup liftGroup = subLevel.getOrCreateQueuedForceGroup(ForceGroups.LIFT.get());
liftGroup.applyAndRecordPointForce(position, force); // 施加并记录
liftGroup.recordPointForce(position, force);          // 仅记录（力在别处已施加）
// 排队力在 applyQueuedForces() 阶段一次性施加
```

预定义的 ForceGroups:

- `ForceGroups.GRAVITY` — 重力
- `ForceGroups.DRAG` — 阻力
- `ForceGroups.LIFT` — 气动升力
- `ForceGroups.PROPULSION` — 推进力
- `ForceGroups.LEVITATION` — 反重力
- `ForceGroups.BALLOON_LIFT` — 气球升力
- `ForceGroups.MAGNETIC_FORCE` — 磁力

### ForceTotal 工具

`ForceTotal` 提供冲量累加器，通过 `applyForces(RigidBodyHandle)` 一次性施加：

```java
ForceTotal total = new ForceTotal();
total.applyImpulseAtPoint(massTracker, position, force);
total.applyLinearAndAngularImpulse(impulse, torque);
handle.applyForcesAndReset(total);  // 施加并清零
```

---

## 五、环境物理量

### 重力 (Gravity)

| 访问路径                                          | 说明                           |
|-----------------------------------------------|------------------------------|
| `DimensionPhysicsData.getGravity(level)`      | 当前维度的重力矢量 (默认 `(0, -11, 0)`) |
| `DimensionPhysicsData.getGravity(level, pos)` | 指定位置的重力                      |
| `DimensionPhysics.DEFAULT_GRAVITY`            | `(0, -11, 0)` — 默认重力常数       |

### 空气阻力/阻尼

| 访问路径                                                 | 说明                 |
|------------------------------------------------------|--------------------|
| `DimensionPhysicsData.getUniversalDrag(serverLevel)` | 通用空气阻力系数 (默认 0.09) |
| `DimensionPhysicsData.getAirPressure(level, pos)`    | 指定位置的气压值 (随高度变化)   |

### 气压曲线

`DimensionPhysics` 中的默认气压函数：

```
P(y) = exp(-0.004 * (y - 海平面))  且 P ∈ [0, 1.5]
```

通过 `BezierResourceFunction` 定义，可通过 datapack 配置。

### 维度物理配置

配置在 `sable/dimension_physics` 资源路径下：

| 字段                  | 类型         | 默认值           | 说明       |
|---------------------|------------|---------------|----------|
| `base_gravity`      | `Vector3f` | `(0, -11, 0)` | 基础重力矢量   |
| `universal_drag`    | `float`    | `0.09`        | 通用阻力系数   |
| `base_pressure`     | `double`   | `1.0`         | 参考气压     |
| `pressure_function` | Bezier 曲线  | 指数衰减          | 气压随高度的变化 |
| `magnetic_north`    | `Vector3f` | `(0, 0, 0)`   | 磁北方向     |

---

## 六、物理常量

### 主世界维度默认值

| 量           | 值                              | 来源                                                             |
|-------------|--------------------------------|----------------------------------------------------------------|
| 重力加速度       | `11.0 m/s²` (垂直向下)             | `DimensionPhysics.DEFAULT_GRAVITY`                             |
| 通用阻力        | `0.09`                         | `DimensionPhysics` record                                      |
| 默认方块质量      | `1.0`                          | `block_properties` 数据包                                         |
| 物理步进频率      | `20 ticks/s × substepsPerTick` | `SubLevelPhysicsSystem`                                        |
| 默认 substeps | 取决于 config                     | `PhysicsConfigData.substepsPerTick`                            |
| 物理步长        | `0.05s / substepsPerTick`      | `tickPipelinePhysics()`                                        |
| 气动升力系数      | `0.475`                        | `BlockSubLevelLiftProvider.sable$getLiftScalar()`              |
| 法向阻力系数      | `0.75`                         | `BlockSubLevelLiftProvider.sable$getParallelDragScalar()`      |
| 方向无关阻力系数    | `0.068882026`                  | `BlockSubLevelLiftProvider.sable$getDirectionlessDragScalar()` |

### 冲量 vs 力

Sable 的物理接口使用**冲量而非持续力**：

- `applyLinearAndAngularImpulse(impulse, torque)` — N·s（冲量）
- `applyImpulseAtPoint(pos, force)` — 方法名含 force 但 API 实际应用 **impulse**（冲量）
- 每一 tick 步进计算 `impulse = force × dt`

### 碰撞和接触

| 操作    | API                                                                  |
|-------|----------------------------------------------------------------------|
| 施加接触力 | `BlockEntitySubLevelActor.sable$physicsTick()` 中通过 `RigidBodyHandle` |
| 升力/阻力 | `BlockSubLevelLiftProvider.sable$contributeLiftAndDrag()`            |
| 反作用飞轮 | `ReactionWheelManager`（通过 `BlockEntitySubLevelReactionWheel` 注册）     |
| 浮空方块  | `FloatingBlockController`                                            |

---

## 七、IAC-P 中的常见访问模式

### 获取 SubLevel 的速度

```java
// 在 physicsTick 中
ServerSubLevel subLevel = ...;
RigidBodyHandle handle = RigidBodyHandle.of(subLevel);

// 方式1：从位姿差（上一 tick 平均值）
Vector3d vel = subLevel.latestLinearVelocity;

// 方式2：从 Rapier（瞬时值，更准）
Vector3d vel = handle.getLinearVelocity(new Vector3d());
```

### 对 SubLevel 施加推进力

```java
// 在 BlockEntitySubLevelActor.sable$physicsTick 中
RigidBodyHandle handle = ...;
Vector3d thrust = new Vector3d(0, 0, -100); // N
Vector3d applyPos = new Vector3d(x, y, z);  // 世界坐标作用点
handle.applyImpulseAtPoint(applyPos, thrust);
// 注：此处 thrust 实际是冲量 = 力 × dt，需要自行乘 dt
```

### 对 SubLevel 施加纯推力（不产生旋转）

```java
// 在质心施加纯线冲量
Vector3d impulse = new Vector3d(0, 0, -5);
handle.applyLinearImpulse(impulse);
```

### 获取 SubLevel 的质量/质心

```java
ServerSubLevel subLevel = ...;
MassData mass = subLevel.getMassTracker();
double totalMass = mass.getMass();
Vector3dc com = mass.getCenterOfMass();
```
