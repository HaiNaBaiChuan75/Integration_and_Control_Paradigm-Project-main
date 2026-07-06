---
updated: 2026-07-06
status: current
maintainer: @项目协作者
---

# 核心标记方块 (SeatBlock) & IACPSeatEntity

## 旧 SeatBlock（初代原型）
半砖形状的标记方块，作为载具核心概念的**最早原型**。已被 `CockpitBlock`（通用驾驶舱）取代，保留作为技术参考模板。

| 特性 | SeatBlock（初代） | CockpitBlock（当前） |
|------|:-----------------:|:--------------------:|
| 形状 | 半砖（单方块） | 双方块结构（炼药锅+脚手架） |
| 结构完整性 | ❌ 无检查 | ✅ 下格+上格完整性检查 |
| 状态 | 保留为模板 | 被 BaseCabinBlock 取代 |

## IACPSeatEntity（当前座位实现）

自 2026-07-06 起，`BaseCabinBlock` 使用 `IACPSeatEntity` 实现座位功能，完整复刻 Create `SeatEntity` 并集成 Sable 物理。

### 功能清单

| 功能 | 说明 |
|------|------|
| **右键坐下** | 空手右键 BaseCabinBlock → `sitDown()` 创建座位实体 |
| **占座检测** | 已有 IACPSeatEntity → 弹射旧乘客换人 |
| **Shift 下车** | 原版 `stopRiding()`，下车位置 +Y 0.5 |
| **SubLevel 跟随** | `tick()` → `logicalPose().transformPosition(homePos)` |
| **朝向跟随** | `logicalPose().transformNormal(fwd)` → 计算 yaw |
| **偏航传递** | 帧间 yawDelta → `passenger.setYRot(yRot + delta)` |
| **碰撞箱** | 0.25×0.35，`noPhysics=true` |
| **不可见** | Render 返回 false |
| **不可推动** | `setDeltaMovement()` 空覆盖 |
| **生命周期** | 方块消失 → discard；有乘客 → 撑住 |
| **NBT 持久化** | homePos 写入/读取，重载世界恢复 |
| **轨道摄像机** | CameraMixin 检测 IACPSeatEntity → **跳过**（2026-07-06 起 seat 自身管位姿），F5 使用原版管线 |
| **载具控制集成** | 首个骑乘者注册到 `PlayerMountTracker` → WASD 映射、信息覆盖层恢复 |
| **player 位置** | `positionRider()` 通过 `riderWorldPos` 全量位姿变换（含旋转），非简单 Y 偏移 |

### 抖动修复（客户端位置同步）

去掉 `tick()` 中的 `isClientSide` 守卫，客户端直接通过本地 SubLevel `logicalPose()` 更新座位位置，避免依赖服务端 20Hz 实体包。

### 旋转跟随（身体朝向）

`followSubLevelPose()` 计算帧间偏航变化量：
- **服务端**：应用到所有骑乘者（权威同步）
- **客户端**：`ClientMountGameHandler.onClientTickPost` 读取 `lastYawDelta` 修正本地玩家
- 首次 tick 跳过（`firstTick` 标记），防止骑乘瞬间快照旋转

### 实现参考
- 源码：`entity/IACPSeatEntity.java`
- 注册：`index/ModEntities.java` — `IACP_SEAT`
- 渲染：`IACPSeatEntity.Render` — 不可见
- 客户端事件：`client/ClientMountGameHandler.java` — 本地玩家 yaw 修正
- Create 参考：`SeatEntity.java`（positionRider、onPassengerTurned、tick 生命周期）

### 与 Create SeatEntity 的差异（2026-07-06 修复）

| # | 差异 | 修复内容 |
|---|------|----------|
| ① | tick 时序：`super.tick()` → `positionRider` 在 `followSubLevelPose` 前用旧值 | 后置 followSubLevelPose 并重调 positionRider |
| ② | 缺少 `yBodyRot` 跟随 | 服务端/客户端均加 `yBodyRot += yawDelta` |
| ③ | 缺少 `setPos` 碰撞箱居中覆写 | 添加 Create 一致的 setPos → AABB 居中 |
| ④ | EntityType `updateInterval=3` | 改为 `Integer.MAX_VALUE`（不主动同步位置） |
| ⑤ | `trackingRange=10` | 改为 5 |

### 载具控制集成

首个骑乘 IACPSeatEntity 的玩家自动注册到 `PlayerMountTracker`：

- **服务端**：`tryRegisterMountControl()` → `PlayerMountTracker.mount()` + `MountedStateS2CPacket`
- **客户端**：`ClientMountHandler.isMounted = true` → `sendVehicleControlInput()` 扫描悬挂、打包按键、信息覆盖层恢复渲染
- **限制**：`isSubLevelOccupiedByOther()` 阻止同 SubLevel 多玩家控制
- **注销**：下车 / 断线 / 死亡 → `unregisterMountControl()` 清理 + `remove()` 兜底

### 载具摄像机模式（F6）

见 `3-轨道摄像机（Plan B1）.md` → "载具参照摄像机模式" 章节。

### 历史记录

| 日期 | 变更 |
|------|------|
| 2026-07-06 | IACPSeatEntity 全面对齐 Create SeatEntity（5项差异修复 + 全量位姿变换 + 载具控制集成 + VehicleCameraMixin） |
