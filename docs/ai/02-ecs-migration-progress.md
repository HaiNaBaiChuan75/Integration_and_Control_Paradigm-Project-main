# ECS 迁移重构笔记

> 实况伴档，记录从"BE 内聚逻辑"到"ECS System 分离"的迁移进度。
> 架构蓝图见 [01-ecs-architecture.md](01-ecs-architecture.md)。

---

## 总览

### 迁移目标

将载具逻辑从 `BlockEntity.tick()` / `sable$physicsTick()` 中剥离，移入独立的 System 类。
BlockEntity（Part）只保留**状态数据**、**自洽的平滑插值**与**非车辆逻辑**（如动画、渲染状态）。

```
Before                              After
──────                              ─────
CockpitBlockEntity.tick()    →      EnginePowerSystem        (VehicleTickSystem)
SuspensionTestBlockEntity
├── .tick()                →      SteeringSystem           (VehicleTickSystem)
└── .sable$physicsTick()   →      SuspensionPhysicsSystem  (VehiclePhysicsSystem)
TurretTestBlockEntity.tick() →      TurretAutoRotateSystem   (VehicleTickSystem)
ShotgunAimController         →      WeaponAimSystem          (VehicleTickSystem)
CockpitBlockEntity HUD sync  →      ClientSyncSystem         (VehicleTickSystem)
SuspensionTestBlockEntity
client visual update       →      WheelVisualSystem        (VehicleClientSystem)
```

### 当前状态（2026-07-02）

| 层         | 状态                                                 |
|-----------|----------------------------------------------------|
| 基础设施      | ✅ Part 接口 / PartBlockEntity 便利基类 / PartRenderer 就绪 |
| System 接口 | ✅ 3 个 @FunctionalInterface 定义完毕                    |
| 调度器       | ✅ VehicleSystemDispatcher 事件路由就绪                   |
| 注册表       | ✅ 框架就绪，但 `TICK_SYSTEMS` 和 `PHYSICS_SYSTEMS` 为空     |
| Part 角色接口 | ❌ 纯数据接口未定义（Controller / AimingMount / WheelPart 等） |
| System 实现 | ⚠️ 仅 AxisRenderSystem（调试用），其余 0 个                  |

---

## 迁移计划

### 阶段 1：定义 Part 纯数据接口（纯新增，零风险）

System 用 `instanceof` 找到需要的 Part，读取/写入其状态字段。接口**只暴露数据 getter/setter**，不暴露算法。

| 接口                 | 关键方法（纯数据）                                                                                                 | 实现者                                                                                          |
|--------------------|-----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| `Controller`       | `getThrottleForward()`, `getThrottleBackward()`, `getBrake()`, `getTargetSteeringYaw()`, `getAimTarget()` | CockpitBlockEntity, BaseCabinBlockEntity                                                     |
| `AimingMount`      | `setYaw(double)`, `setPitch(double)`, `getYaw()`, `getPitch()`                                            | TurretTestBlockEntity, ShotGunBlockEntity, ShotgunBaseBlockEntity, MachineGunBaseBlockEntity |
| `WheelPart`        | `getCurrentWheelRpm()`, `setTorqueInput(double)`, `getSteeringAngle()`, `setSteeringAngle(double)`        | SuspensionTestBlockEntity                                                                    |
| `EnginePart`       | `getRpm()`, `setRpm(double)`, `getTorque()`, `setTorque(double)`                                          | CockpitBlockEntity                                                                           |
| `TransmissionPart` | `getGear()`, `setGear(int)`, `getOutputRpm()`, `setOutputRpm(double)`                                     | CockpitBlockEntity                                                                           |

> **原则**：Part 接口不定义任何计算行为。例如 `AimingMount` 没有 `aimAt()`，`EnginePart` 没有
`computeThrottleControlledRun()`，这些算法全部上提到 System。

> Controller目前阶段只设置一个，后续过于复杂时再拆分

### 阶段 2：提取简单 Systems（快速验证流程）

#### 2a. TurretAutoRotateSystem (VehicleTickSystem)

- **来源：** `TurretTestBlockEntity.tick()` 中 2 行逻辑
- **做法：** 遍历 parts，`instanceof AimingMount` → 如果是 AUTO_ROTATE 模式，System 内部计算 yaw 增量 →
  `mount.setYaw(newYaw)`
- **接口需要：** `AimingMount`（纯数据）

#### 2b. ClientSyncSystem (VehicleTickSystem)

注意此系统可能被方块实体通过NBT同步取代，如果可能的话请避免这个系统

- **来源：** `CockpitBlockEntity.trySyncStateToClient()`
- **做法：** 遍历 parts，`instanceof Controller` → 读取状态 → 每 2 tick 发 `VehicleStateS2CPacket`
- **接口需要：** `Controller`（纯数据）

### 阶段 3：提取核心 Systems

#### 3a. WeaponAimSystem (VehicleTickSystem)

- **来源：** `ShotgunAimController.driveAnglesImmediate()`, `MachineGunAimController.driveAnglesImmediate()`
- **做法：**
    1. 遍历 parts，找到 `Controller` 读取瞄准目标点（`getAimTarget()`）
  2. 遍历 `AimingMount`，System 内部做坐标转换（`partLogicalPose().transformPositionInverse()`）和角度解算
  3. 计算结果写入 `mount.setYaw()` / `setPitch()`
- **接口需要：** `Controller`, `AimingMount`
- **注意：** 原 `VehicleControlC2SPacket` 将目标点写入 Controller Part 的字段即可，System 只读 Part 状态，不直接消费网络包

#### 3b. SteeringSystem (VehicleTickSystem)

- **来源：** `SuspensionTestBlockEntity.tick()` 中速度自适应转向、chasingYaw 插值
- **做法：**
    1. 遍历 parts，找到 `Controller` 读取 `getTargetSteeringYaw()`
    2. System 内部做速度自适应和 chasingYaw 插值计算
    3. 结果写入 `WheelPart.setSteeringAngle()`
- **接口需要：** `Controller`, `WheelPart`

#### 3c. EnginePowerSystem (VehicleTickSystem)

- **来源：** `CockpitBlockEntity.tick()` 中引擎/变速箱/扭矩分配（~80 行）
- **做法：**
    1. 遍历 parts，找到 `Controller` 读取油门/刹车输入
    2. 找到 `WheelPart` 读取 `getCurrentWheelRpm()`（用于负载/换挡决策）
    3. **System 内部持有引擎模型和变速箱模型**，计算 RPM、扭矩、档位
    4. 找到 `EnginePart` / `TransmissionPart` 写回状态（`setRpm`, `setGear` 等）
    5. 计算轮上扭矩分配，写入 `WheelPart.setTorqueInput()`
- **接口需要：** `Controller`, `EnginePart`, `TransmissionPart`, `WheelPart`
- **关键变化**：引擎/变速箱算法不再属于 CockpitBlockEntity，而是 EnginePowerSystem 的内部逻辑

#### 3d. SuspensionPhysicsSystem (VehiclePhysicsSystem)

- **来源：** `SuspensionTestBlockEntity.sable$physicsTick()`（~400 行）
- **做法：** 遍历 parts，`instanceof WheelPart` → 读取 `getTorqueInput()`、`getSteeringAngle()` → System
  内部做弹簧/阻尼/摩擦力计算 → 施加到 RigidBody
- **接口需要：** `WheelPart`
- **关键变化**：WheelPart 只提供 `getTorqueInput()`（由 EnginePowerSystem 上一逻辑 tick 写入），不再直接耦合
  CockpitBlockEntity

### 阶段 4：客户端 Systems

#### 4a. WheelVisualSystem (VehicleClientSystem)

- **来源：** `SuspensionTestBlockEntity.tick()` 客户端侧
- **做法：** 遍历 parts，`instanceof WheelPart` → 读取 `getCurrentWheelRpm()` 等 → System 内部做视觉插值 → 写入
  `WheelPart` 的视觉字段（`angle`, `angVel`）
- **接口需要：** `WheelPart`

### 阶段 5：注册与清理

1. 在 `VehicleSystemRegistry.registerAll()` 中按依赖顺序注册所有新 System
2. 从 BE 中删除已被 System 替代的车辆逻辑
3. BE 的 `sable$tick()` 仅保留：位置平滑插值（yaw/pitch 逼近 target 值）、动画触发器、非车辆逻辑

---

## 现有 Part 实现（BlockEntity）职责清单

### 当前逻辑分布

| BE 类                      | tick() 职责                   | sable$physicsTick() 职责 | 应归属的 System                                                |
|---------------------------|-----------------------------|------------------------|------------------------------------------------------------|
| CockpitBlockEntity        | 引擎/变速箱/扭矩分配/状态同步            | 无                      | EnginePowerSystem, ClientSyncSystem                        |
| BaseCabinBlockEntity      | 继承 CockpitBlockEntity（同上）   | 无                      | 同上                                                         |
| SuspensionTestBlockEntity | 速度自适应转向 + 轮子视觉更新            | 悬架力/弹簧/阻尼/摩擦力（~400 行）  | SteeringSystem, WheelVisualSystem, SuspensionPhysicsSystem |
| TurretTestBlockEntity     | AUTO_ROTATE 自动旋转（2 行）       | 无                      | TurretAutoRotateSystem                                     |
| ShotgunBaseBlockEntity    | 延时约束重建 + yaw/pitch servo 更新 | 无                      | WeaponAimSystem (瞄准解算)                                     |
| MachineGunBaseBlockEntity | 同 ShotgunBaseBlockEntity    | 无                      | WeaponAimSystem (瞄准解算)                                     |
| ShotGunBlockEntity        | 开火动画冷却 + 测试开火               | 无                      | 动画/冷却逻辑可保留或视情况提取                                           |

### 迁移后 BE 剩余职责

- `orientation()` — 定义朝向
- `sable$tick()` — 仅自洽平滑（yaw/pitch 逼近 target 值）、动画触发器、非车辆逻辑
- **纯数据字段 + getter/setter** — 由 System 读写，Part 内部不解释数据语义
- 开火/动画冷却 — ShotGunBlockEntity 的本地动画状态机

---

## 测试策略

- 无单元测试（项目惯例），通过 `./gradlew runClient` 在游戏中验证
- 每个 System 提取后：
    1. 编译通过 → 2. 启动客户端 → 3. 载具正常操控/物理/瞄准
- 回归要点：
    - 引擎 RPM 和变速箱换挡行为不变
    - 悬架支撑力和车辆姿态不变
    - 转向响应和速度自适应性不变
    - 武器瞄准跟踪正确
    - HUD 数据显示正确

---

## 已完成工单

| 日期         | 内容                                       |
|------------|------------------------------------------|
| 2026-07-02 | 基础设施就位：PartBlockEntity/PartRenderer 迁移完成 |
| 2026-07-02 | 创建本重构笔记，规划提取计划                           |
| 2026-07-02 | Part 重构为接口，PartBlockEntity 改为便利抽象基类      |

---

---

## 数据流分析（2026-07-02）

> 迁移前必须搞清数据如何在 BE 之间流动，才能正确设计 System 的输入输出边界。
> 分析基于 `block/cockpit/CockpitBlockEntity.java`、`block/suspension_test/SuspensionTestBlockEntity.java` 等源文件。

### 核心发现：Cockpit ↔ Suspension 双向耦合

这是整个项目中**最紧密的跨 BE 依赖**，存在隐式的时序契约：

```
游戏 Tick(20Hz)                 物理 Tick(~100Hz)
──────────────                  ─────────────────
CockpitBE.tick()                SuspensionBE.sable$physicsTick()
│                                   │
├─ 读取 Suspension:                 ├─ 读取 Cockpit:
│   └─ getCurrentWheelRpm() ────┐   │   └─ getTorquePerWheel() ←──┐
│   └─ hasThrottle()            │   │   └─ getTargetWheelRpm() ←──┤
│   └─ getTargetSteeringYaw()   │   │                              │
│                               │   │  反馈环: Cockpit 读轮速      │
├─ 计算扭矩 ─────────────────────┼───┼──→ 算扭矩 → Suspension      │
│   torquePerWheel = ...        │   │      读扭矩 → 施加力         │
│                               │   │      → 更新轮速 → ...       │
└─ trySyncStateToClient()       │   └──────────────────────────────┘
→ VehicleStateS2CPacket    │
```

**这意味着：** Cockpit 的 `tick()` 和 Suspension 的 `sable$physicsTick()` 之间存在一游戏 tick 的延迟——EnginePowerSystem
先运行产生扭矩，SuspensionPhysicsSystem 后运行消耗扭矩。

### 三类跨 BE 发现机制

| 机制                               | 开销                   | 使用者                                                    | ECS 迁移建议                                              |
|----------------------------------|----------------------|--------------------------------------------------------|-------------------------------------------------------|
| `PartQuery.findParts()`          | 低，单次遍历所有 BE          | CockpitBE, SuspensionBE, MachineGunTargetC2SPacket     | ✅ System 可直接使用 `VehicleSystemRegistry.collectParts()` |
| `SubLevelScanner.forEachBlock()` | **高**（三重嵌套 chunk 循环） | CockpitBE（回退）、SuspensionBE（回退）、VehicleControlC2SPacket | ❌ 消除，统一走 PartQuery/collectParts                       |
| 直接 `level.getBlockEntity(pos)`   | 最低                   | VehicleControlC2SPacket（写入控制输入）                        | ⚠️ 控制输入写入可保留，但读取/发现应统一                                |
| 静态 `HashMap<UUID, BlockPos>`     | O(1) 反向查找            | MachineGunBaseBE, ShotgunBaseBE 的装配映射                  | ⚠️ 需要评估是否保留或纳入 System 元数据                             |

### 完整跨 BE 数据流矩阵

```
CockpitBlockEntity.tick()
── 读取 ──
FROM SuspensionTestBlockEntity:
getCurrentWheelRpm()                 → 引擎负载/换挡决策
hasThrottle()                        → stall 检测
getTargetSteeringYaw()               → 转向输入判断
发现路径: PartQuery.findParts() → O(actors)
回退路径: SubLevelScanner.forEachBlock() → O(chunks×blocks)

── 写入 ──
TO 自己: torquePerWheel, effectiveTorque, engineRpm, currentGear, stalled
TO 网络: VehicleStateS2CPacket (engineRpm, throttle, gear, torque, speed, accel)

SuspensionTestBlockEntity.sable$physicsTick()
── 读取 ──
FROM CockpitBlockEntity:
getTorquePerWheel()                  → 推进力/差速器分配
getTargetWheelRpm()                  → 运动学限速
isStalled() + getTargetWheelRpm()    → getVisualRpm()（仅客户端视觉）
发现路径: cachedCockpit（字段级缓存）→ PartQuery.findPartsByUUID()
→ SubLevelScanner.forEachBlock() 回退

FROM Sable API:
Sable.HELPER.getVelocity(level, lp)  → 速度/摩擦力
subLevel.logicalPose()               → 姿态变换
subLevel.getMassTracker()            → 簧上质量

── 写入 ──
TO 自己: currentWheelRpm, pControllerDemand, frictionDemandRatio,
gripStatus, extension, lifted, chasingYaw, angle
TO RigidBody: 弹簧/阻尼/摩擦力（通过 Sable 句柄）

SuspensionTestBlockEntity.tick()（客户端侧）
── 读取 ──
FROM 自己: chasingYaw, targetSteeringYaw, throttleForward/Backward
FROM Cockpit: isStalled(), getTargetWheelRpm() → getVisualRpm()

── 写入 ──
TO 自己: chasingYaw（速度自适应插值）+ angle/angVel（轮子旋转视觉）

MachineGunTargetC2SPacket.handle()
⚠️ 这实质上已经是一个 System（状态计算 + 遍历写入）
── 读取 ──
SubLevel.logicalPose()                 → 坐标变换
PartQuery.findPartsByUUID():           → 遍历三类武器
.isAssembled(), .getGrindstoneSubLevelId()

── 写入 ──
TO MachineGunBaseBE:   driveImmediate(yaw, pitch)  → 伺服马达
TO TurretTestBE:       driveImmediate(yaw, pitch)  → 骨骼旋转
TO ShotgunBaseBE:      driveImmediate(yaw, pitch)  → 伺服马达
（经由 AimController 代理）

VehicleControlC2SPacket.handle()
── 写入 ──
TO SuspensionBE:   applyControlInput(fwd, bwd, left, right, brake)
→ this.throttleForward/Backward/braking/targetSteeringYaw
TO CockpitBE:      setRawThrottleDirection(direction)
发现: level.getBlockEntity(pos) + SubLevelScanner.forEachBlock()
```

### 数据流对 System 设计的约束

```
EnginePowerSystem (VehicleTickSystem)
输入: Controller.{throttleForward, throttleBackward, brake}
WheelPart.{getCurrentWheelRpm()}
输出: EnginePart.{setRpm, setTorque}
TransmissionPart.{setGear, setOutputRpm}
WheelPart.{setTorqueInput()}  ← 供物理 Tick 读取
⚠️ 依赖 SteeringSystem 先运行（读了转向输入但 EnginePowerSystem 不需要）

SteeringSystem (VehicleTickSystem)
输入: Controller.{targetSteeringYaw}
WheelPart.{getCurrentWheelRpm()}（用于速度自适应）
输出: WheelPart.{setSteeringAngle()}
⚠️ 应在 EnginePowerSystem**之前**运行（EnginePowerSystem 用到 chasingYaw？不——引擎不需要转向值）
✅ 可以在 EnginePowerSystem 之前或之后运行（独立）

SuspensionPhysicsSystem (VehiclePhysicsSystem)
输入: WheelPart.{getTorqueInput, getSteeringAngle, currentWheelRpm}
EnginePart.{isStalled}（或 Controller 状态）
输出: WheelPart.{setCurrentWheelRpm, gripStatus, extension, lifted}
⚠️ 读取的 getTorqueInput 由 EnginePowerSystem 上一逻辑 tick 写入

WeaponAimSystem (VehicleTickSystem)
输入: Controller.{getAimTarget()}
AimingMount.{getYaw, getPitch}
输出: AimingMount.{setYaw, setPitch}
✅ 独立于动力/悬挂系统

ClientSyncSystem (VehicleTickSystem)
输入: Controller.{engineRpm, throttle, gear, speed, accel}
输出: VehicleStateS2CPacket
✅ 应在 EnginePowerSystem**之后**运行（读完整引擎状态再发包）

WheelVisualSystem (VehicleClientSystem)
输入: WheelPart.{currentWheelRpm, isStalled, targetWheelRpm}
输出: WheelPart.{angle, lastAngle, angVel}
✅ 客户端专用，独立
```

### System 执行顺序约束

```
VehicleTickSystem (优先序):
1. SteeringSystem        ← 独立，转向输入无需等引擎
2. EnginePowerSystem     ← 需要轮速（SteeringSystem 不影响这个）
3. WeaponAimSystem       ← 独立
4. ClientSyncSystem      ← 需要引擎状态（EnginePowerSystem 之后）

VehiclePhysicsSystem:
1. SuspensionPhysicsSystem  ← 唯一物理 System

VehicleClientSystem:
1. WheelVisualSystem        ← 唯一客户端 System
```

> **注意：** 当前 `VehicleTickSystem` 接口只有一个 `onTick()` 方法，所有 Tick System 并行执行。
> 如果需要保序，有两种方案：
> 1. 保持单接口，按优先序依次调用（`VehicleSystemRegistry` 中列表顺序）
> 2. 拆分为多个阶段接口（但 YAGNI——当前四个 System 的顺序依赖很弱）
>
> **推荐方案 1**：在 `VehicleSystemDispatcher` 中按 `TICK_SYSTEMS` 列表顺序串行调用即可，
> 注册时按上述顺序添加。无需新增接口。`,

- 架构蓝图: `docs/ai/01-ecs-architecture.md`
- System 接口定义: `ecs/system/`
- 注册表: `ecs/system/VehicleSystemRegistry.java`
- 调度器: `ecs/dispatch/VehicleSystemDispatcher.java`
- Part 接口与基类: `ecs/part/Part.java`（接口）, `ecs/part/PartBlockEntity.java`（便利基类）, `ecs/part/PartRenderer.java`
  （渲染基类）
- BE 子类: `block/cockpit/`, `block/suspension_test/`, `block/turret/`, `block/shotgun/`, `block/machine_gun/`,
  `block/base_cabin/`
