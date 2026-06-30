# Sable `dev.ryanhcode.sable.api.block` 包参考

本文档说明 `sable-neoforge-1.21.1:1.1.3` 中 `dev.ryanhcode.sable.api.block` 包及子包
下的所有接口及其用法。

这个包定义了"方块如何参与 SubLevel 物理"的全部契约。
在 IAC-P 中，悬挂、座舱、引擎等系统都是通过实现这些接口来接入 Sable 物理管线的。

---

## 包结构

```
api.block
├── BlockEntitySubLevelActor          ← BE 参与物理的核心接口
├── BlockEntitySubLevelReactionWheel  ← 反作用飞轮（姿态控制）
├── BlockSubLevelAssemblyListener     ← 组装（方块转移）生命周期
├── BlockSubLevelCollisionShape       ← 自定义物理碰撞体形状
├── BlockSubLevelCustomCenterOfMass   ← 自定义方块质心
├── BlockSubLevelDynamicCollider      ← 动态碰撞体构建
├── BlockSubLevelLiftProvider         ← 升力/阻力面（机翼、气球）
├── BlockWithSubLevelCollisionCallback ← 碰撞回调入口
│
└── propeller/
    ├── BlockEntityPropeller               ← 螺旋桨属性
    └── BlockEntitySubLevelPropellerActor  ← 螺旋桨物理实现
```

---

## 一、核心物理参与接口

### 1. `BlockEntitySubLevelActor` — BE 参与物理管线的入口

**任何**想要参与 SubLevel 物理的 `BlockEntity` 都必须实现此接口。

```java
public interface BlockEntitySubLevelActor {
    // 游戏 tick（20Hz）— 非物理更新，用于状态逻辑
    default void sable$tick(ServerSubLevel subLevel) {}

    // 物理 tick（~100Hz = 20Hz × substepsPerTick）— 在此施力
    default void sable$physicsTick(ServerSubLevel subLevel,
                                   RigidBodyHandle handle,
                                   double timeStep) {}

    // 返回此 BE 依赖的其他 SubLevel（用于加载顺序）
    default @Nullable Iterable<@NotNull SubLevel> sable$getLoadingDependencies() {
        return sable$getConnectionDependencies();
    }

    // 返回此 BE 连接的其他 SubLevel（如炮塔的基座与炮管）
    default @Nullable Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        return null;
    }
}
```

**三个回调的调用时机**（以 `SubLevelPhysicsSystem.tickPipelinePhysics()` 为准）：

```
SubLevelContainer.tick()
  └─ SubLevel.tick()
       └─ BlockEntitySubLevelActor.sable$tick()    ← 所有 BE 先执行游戏逻辑

SubLevelPhysicsSystem.tick() → tickPipelinePhysics()
  for each substep:
    prePhysicsTick()
      └─ BlockEntitySubLevelActor.sable$physicsTick()  ← 在此施力
    applyQueuedForces()                                  ← 排队力统一施加
    pipeline.physicsTick(dt)                             ← Rapier 解算
    updatePose()                                         ← 从管线读回新位姿
```

**IAC-P 中的实现类**：

| IAC-P 类                     | 关键行为                                                                      |
|-----------------------------|---------------------------------------------------------------------------|
| `SuspensionTestBlockEntity` | 在 `sable$physicsTick()` 中通过 `RigidBodyHandle.applyImpulseAtPoint()` 施加轮胎力 |
| `CockpitBlockEntity`        | 在 `sable$physicsTick()` 中应用引擎推力/扭矩                                        |
| `TurretBaseBlockEntity`     | 通过 `sable$getConnectionDependencies()` 声明炮塔依赖的另一个 SubLevel                |

---

### 2. `BlockEntitySubLevelReactionWheel` — 反作用飞轮

姿态控制组件。每个实现此接口的 BlockEntity 会自动注册到
`ServerSubLevel.getReactionWheelManager()`。

```java
public interface BlockEntitySubLevelReactionWheel {
    // 输出当前飞轮的角速度（用于计算反作用扭矩）
    void sable$getAngularVelocity(Vector3d dest);
}
```

**注册方式**：当方块变化时，`LevelPlot.onBlockChange()` 自动检测 BlockEntity
是否 instanceof `BlockEntitySubLevelReactionWheel`，并将其加入 `ReactionWheelManager`。

**工作原理**：`ReactionWheelManager.physicsTick(handle)` 在 `prePhysicsTick()`
中由 `ServerSubLevel` 调用，将飞轮角动量变化转化为施加在 SubLevel 刚体上的扭矩，
从而实现姿态调整（类似航天器的反作用飞轮）。

---

### 3. `BlockEntityPropeller` (子包 `propeller`) — 螺旋桨接口

定义螺旋桨的推力属性。

```java
public interface BlockEntityPropeller {
    Direction getBlockDirection();        // 推力方向（方块朝向）
    double getAirflow();                  // 气流速度（用于计算气流缩放）
    double getThrust();                   // 基础推力值
    boolean isActive();                   // 是否运转
    Level getLevel();
    BlockPos getBlockPos();

    // 最终缩放后的推力 = -thrust × airflowScaling × airPressure
    default double getScaledThrust() {
        return -this.getThrust()
             * this.getAirflowScaling()
             * this.getCurrentAirPressure();
    }

    // 当前高度气压
    default double getCurrentAirPressure() {
        return DimensionPhysicsData.getAirPressure(level, 投影到主世界的位置);
    }

    // 气流速度缩放：考虑载具自身速度对推力的影响
    // 0%（顺风/同向速度达到 airflow）~ 100%（静止/逆风）
    default double getAirflowScaling() {
        double airflow = this.getAirflow();
        if (Math.abs(airflow) <= 0.001) return 1.0;
        Vector3d velocity = Sable.HELPER.getVelocity(level, subLevel, pos, new Vector3d());
        Vector3d thrustDir = subLevel.logicalPose().transformNormal(blockDirection);
        return clamp((airflow + velocity.dot(thrustDir)) / airflow, 0.0, 1.0);
    }
}
```

**关键点**：

- `getThrust()` 返回基础推力值
- `getScaledThrust()` 返回真正使用的推力值（考虑了气流缩放 + 气压）
- 推力会随载具速度变化：载具朝推力方向移动时推力减小（气流相对速度下降），反之增大

### 4. `BlockEntitySubLevelPropellerActor` (子包 `propeller`) — 螺旋桨物理实现

继承 `BlockEntitySubLevelActor`，将螺旋桨推力施加到物理引擎。

```java
public interface BlockEntitySubLevelPropellerActor
        extends BlockEntitySubLevelActor {

    BlockEntityPropeller getPropeller();  // 获取关联的螺旋桨属性

    @Override
    default void sable$physicsTick(ServerSubLevel subLevel,
                                   RigidBodyHandle handle,
                                   double timeStep) {
        BlockEntityPropeller prop = this.getPropeller();
        if (prop.isActive()) {
            Vec3 thrustDirection = Vec3.atLowerCornerOf(prop.getBlockDirection().getNormal());
            this.applyForces(subLevel, thrustDirection, timeStep);
        }
    }

    default void applyForces(ServerSubLevel subLevel,
                             Vec3 thrustDirection,
                             double timeStep) {
        BlockEntityPropeller prop = this.getPropeller();
        // 推力 = 缩放后推力 × timeStep（转换为冲量）
        Vec3 thrust = thrustDirection.scale(prop.getScaledThrust() * timeStep);

        THRUST_POSITION.set(prop.getBlockPos() 的中心);
        THRUST_VECTOR.set(thrust.x, thrust.y, thrust.z);

        QueuedForceGroup forceGroup =
            subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());
        forceGroup.applyAndRecordPointForce(
            new Vector3d(THRUST_POSITION),
            new Vector3d(THRUST_VECTOR));
    }
}
```

**设计模式**：螺旋桨将属性接口 (`BlockEntityPropeller`) 与物理施力接口
(`BlockEntitySubLevelPropellerActor`) 分离。BE 可以同时实现两者，也可以
分别实现（如代理模式）。

---

## 二、碰撞相关接口

### 5. `BlockSubLevelCollisionShape` — 自定义碰撞体形状

方块级别的接口，用于指定 SubLevel 碰撞使用什么形状（默认为方块的 `getCollisionShape()`）。

```java
public interface BlockSubLevelCollisionShape {
    VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state);
}
```

**用途**：当你想让方块在 SubLevel 物理中拥有与视觉/常规碰撞不同的碰撞体时实现。
例如：斜坡、半砖等复杂形状需要简化为凸多边形。

**默认行为**：如果方块未实现此接口，Sable 使用 `state.getCollisionShape(level, pos)`。

### 6. `BlockSubLevelDynamicCollider` — 动态碰撞体构建

更高级的碰撞体定义方式，允许构建多个子碰撞体（compound collider）。

```java
public interface BlockSubLevelDynamicCollider {
    void buildBoxes(VoxelColliderData data);
}
```

**用途**：用于创建由多个凸包组成的复合碰撞体。例如车辆底盘的多个碰撞盒。

### 7. `BlockSubLevelCollisionCallback` — 碰撞回调结果

**包**: `dev.ryanhcode.sable.api.physics.callback`（关联接口）

定义碰撞发生时的响应行为。

```java
public interface BlockSubLevelCollisionCallback {
    // internal: 桥接底层碰撞检测到上层 API
    @ApiStatus.Internal
    default double[] onCollision(int x, int y, int z,
                                 double x1, double y1, double z1,
                                 double impactVelocity) { ... }

    // 实现此方法：给定碰撞位置、法线、冲击速度 → 返回切向运动和是否移除碰撞
    CollisionResult sable$onCollision(BlockPos pos, Vector3d normal, double impactVelocity);

    record CollisionResult(Vector3dc tangentMotion, boolean removeCollision) {
        static final CollisionResult NONE = new CollisionResult(ZERO, false);
    }
}
```

**用途**：

- 脆性方块（`FragileBlockCallback`）：冲击速度超过阈值时破坏方块
- IAC-P 中的武器伤害判定：碰撞时触发伤害计算

### 8. `BlockWithSubLevelCollisionCallback` — 碰撞回调注册器

方块级别的接口，将 `Block` 与 `BlockSubLevelCollisionCallback` 关联。

```java
public interface BlockWithSubLevelCollisionCallback {
    BlockSubLevelCollisionCallback sable$getCallback();

    // 静态方法：从 BlockState 获取回调（自动处理脆性方块逻辑）
    static BlockSubLevelCollisionCallback sable$getCallback(BlockState state) { ... }
    static boolean hasCallback(BlockState state) { ... }
}
```

**注意**：实现此接口的必须是 `Block`（方块），不是 `BlockEntity`。
sable 的底层碰撞系统会调用 `sable$getCallback(state)` 来获取碰撞回调。

---

## 三、力学特性接口

### 9. `BlockSubLevelCustomCenterOfMass` — 自定义方块质心

对方块级别的质心进行定制（替代默认的包围盒中心）。

```java
public interface BlockSubLevelCustomCenterOfMass {
    // 返回此方块在自身 1×1×1 局部空间中的质心位置（单位：方块）
    Vector3dc getCenterOfMass(BlockGetter level, BlockState state);
}
```

**默认值**：`Block.getCollisionShape()` 的包围盒中心；若碰撞形状为空则用 `(0.5, 0.5, 0.5)`。

**用途**：使不规则方块的质心偏离几何中心。在 `MassTracker.build()` 和
`MassTracker.addBlockMass()` 中被调用。

### 10. `BlockSubLevelLiftProvider` — 升力/阻力面

产生空气动力学的升力和阻力。

**详细文档已在 `sable-object-reference.md#15` 中，此处为重点摘要**。

```java
public interface BlockSubLevelLiftProvider {
    Direction sable$getNormal(BlockState state);        // 升力面法线方向
    float sable$getLiftScalar();                        // 升力系数（默认 0.475）
    float sable$getParallelDragScalar();                // 法向阻力系数（默认 0.75）
    float sable$getDirectionlessDragScalar();           // 方向无关阻力系数（默认 0.0689）
    void sable$contributeLiftAndDrag(ctx, subLevel, localPose, timeStep,
                                     linearVelocity, angularVelocity,
                                     linearImpulse, angularImpulse, group);
}
```

**升力面分组**：连续相邻的 `LiftProvider` 方块被分组（`LiftProviderGroup`），
组内的升力/阻力中心被合并计算，用于调试可视化。

---

## 四、生命周期接口

### 11. `BlockSubLevelAssemblyListener` — 方块组装监听器

当一个方块从主世界**移动/复制**到 SubLevel 子世界时（组装过程），回调此接口。

```java
public interface BlockSubLevelAssemblyListener {
    // 移动前调用（源世界 → 目标世界的瞬间）
    default void beforeMove(ServerLevel originLevel,
                            ServerLevel resultingLevel,
                            BlockState newState,
                            BlockPos oldPos, BlockPos newPos) {}

    // 移动后调用
    void afterMove(ServerLevel originLevel,
                   ServerLevel resultingLevel,
                   BlockState newState,
                   BlockPos oldPos, BlockPos newPos);
}
```

**用途**：方块从主世界变成 SubLevel 的一部分时，可能需要进行状态调整
（如清除旧世界中的残留数据、设置 SubLevel 专属属性）。

> 此接口由 **方块（Block）** 实现，不是 BlockEntity。

---

## 五、关系图

```
                        Block (方块)
                        ├── BlockSubLevelCollisionShape       ← 碰撞体形状
                        ├── BlockSubLevelDynamicCollider      ← 复合碰撞体
                        ├── BlockSubLevelCustomCenterOfMass   ← 质心偏移
                        ├── BlockSubLevelAssemblyListener     ← 组装生命周期
                        ├── BlockWithSubLevelCollisionCallback ← 碰撞回调注册
                        └── BlockSubLevelLiftProvider         ← 升力/阻力

                        BlockEntity (方块实体)
                        ├── BlockEntitySubLevelActor          ← 物理参与入口
                        │     └── sable$tick()       (20Hz)
                        │     └── sable$physicsTick() (100Hz)  ← 施力点
                        │     └── sable$getConnectionDependencies()
                        ├── BlockEntitySubLevelReactionWheel  ← 姿态控制
                        └── BlockEntitySubLevelPropellerActor ← 螺旋桨施力
                              └── (extends BlockEntitySubLevelActor)
                              └── 委托 BlockEntityPropeller 读取推力属性
```

---

## 六、IAC-P 中的实现对照

| IAC-P 类                             | 实现的 Sable 接口                    | 作用                      |
|-------------------------------------|---------------------------------|-------------------------|
| `SuspensionTestBlockEntity`         | `BlockEntitySubLevelActor`      | 在 `physicsTick` 中施加轮胎力  |
| `CockpitBlockEntity`                | `BlockEntitySubLevelActor`      | 在 `physicsTick` 中应用引擎扭矩 |
| `TurretBaseBlock`                   | `BlockSubLevelAssemblyListener` | 炮塔组装/拆解时的 SubLevel 连接   |
| `EngineModel` / `TransmissionModel` | （内部调用）`RigidBodyHandle`         | 通过 handle 施加驱动力         |
| `TirePhysicsCalculator`             | （内部调用）`RigidBodyHandle`         | 轮胎与地面接触力                |

---

## 七、实现一个自定义物理 BE 的步骤

1. **BlockEntity 实现 `BlockEntitySubLevelActor`**

```java
public class MyThrusterBlockEntity extends BlockEntity
        implements BlockEntitySubLevelActor {

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel,
                                  RigidBodyHandle handle,
                                  double timeStep) {
        // 在质心施加推进力
        Vector3d thrust = new Vector3d(0, 0, -100 * timeStep);
        handle.applyLinearImpulse(thrust);
    }
}
```

2. **在 `LevelPlot` 中注册**（自动）：当方块放置/加载时，Sable 检测 BE 是否
   instanceof `BlockEntitySubLevelActor`，自动加入 `blockEntityActors` 列表。

3. **选择力施加方式**：
    - `handle.applyImpulseAtPoint(pos, force)` → 非线性力（产生旋转）
    - `handle.applyLinearImpulse(impulse)` → 质心施加，纯平移
    - `subLevel.getOrCreateQueuedForceGroup(ForceGroups.X)` → 排队力（归类调试）
