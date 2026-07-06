---
updated: 2026-07-06
status: current
maintainer: @项目协作者
---

# 3. 轨道摄像机

## 设计思路

利用原版 F5 第三人称摄像机行为——`Camera.setup()` 已通过 `entity.getViewYRot()/getViewXRot()` 设置 `yRot/xRot` 来自玩家鼠标。本系统**仅将焦点从玩家实体重定向到 SubLevel 结构中心**，并应用配置距离/高度偏移。

**相比旧方案（独立轨道状态 + 低通滤波器）：**
- ✅ 无需独立维护 yaw/pitch 状态——直接复用原版 Camera 的 yRot/xRot
- ✅ 无需低通滤波器——mouse → entity → Camera 链路不存在服务端覆盖
- ✅ F5 切换自然工作，第三人称前/后自动处理
- ✅ 第一人称保持原版行为

## 实现

### CameraMixin

`@Inject(method = "setup", at = @At("TAIL"))` — 仅 `thirdPerson == true` 时激活。

```java
// CameraMixin 注入逻辑
if (!thirdPerson) return;                // 第一人称跳过
ClientSubLevel subLevel = resolveSubLevel(entity); // 旧mount / IACPSeatEntity

// 计算 SubLevel 焦点位置（结构中心 + 配置偏移）
focusX/Y/Z = renderPose.position() + bbox half + config offset

// 哨兵模式（V 键冻结位置）
if stationary → setPosition(frozenPos) + 旋转指向焦点

// 第三人称轨道（使用原版 Camera.yRot/xRot）
float yaw = this.yRot;
float pitch = this.xRot;
if (inverseView) { yaw += 180; pitch = -pitch; } // 正面翻转

// 球坐标计算轨道位置
dx = sin(yaw) * cos(pitch) * configDistance
dy = ±sin(pitch) * configDistance          // CAMERA_INVERT_Y
dz = -cos(yaw) * cos(pitch) * configDistance

cameraPos = focusPos + (dx, dy, dz)
setPosition(cameraPos)
setRotation(lookYaw, lookPitch)            // 始终看向焦点
```

### SubLevel 解析

支持两种骑乘系统：

1. **旧 mount 系统**：`ClientMountHandler.isMounted()` → `getMountedClientSubLevel()`
2. **IACPSeatEntity 骑乘**：~~搜索 SubLevel 做轨道覆盖~~ → **2026-07-06 起跳过**，seat 自身管理位姿跟随。F5 使用原版管线围绕骑乘者实体做轨道。

> 注意：IACPSeatEntity 骑乘时 CameraMixin 返回 null（不干涉），F5 三模式全部由原版 `Camera.setup()` 处理。

### 自适应参数

| 配置项 | 功能 | 默认值 |
|--------|------|--------|
| `cameraDistance` | 基础距离（格） | 4.0 |
| `cameraHeightOffset` | 高度偏移（格，正=上） | 0.0 |
| `cameraAdaptiveDistance` | 自适应距离 = 基础 + 结构最长边/2 | true |
| `cameraAdaptiveHeight` | 自适应高度 = 结构半高 + 1 + 偏移 | true |
| `cameraInvertY` | 反转垂直方向 | false |

## F5 行为

| 模式 | 行为 |
|------|------|
| 第一人称 | CameraMixin 跳过 → 原版第一人称 |
| 第三人称后（默认） | 摄像机在 SubLevel 后方，鼠标控制环绕 |
| 第三人称前 | 摄像机在 SubLevel 前方（yaw+180, pitch=-pitch） |
| F5 循环 | 三个模式自然切换 |

## 哨兵摄像机（V 键）

仅旧 mount 系统可用。进入时冻结摄像机世界坐标，持续锁定 SubLevel 焦点。退出时根据车辆速度方向恢复视角。

## 载具参照摄像机模式（F6 键，IACPSeatEntity 专属）

新增于 2026-07-06。骑乘 IACPSeatEntity 时通过 **F6** 循环切换：关 → 结构固定 → 方向稳定 → 关。

实现于独立 Mixin `VehicleCameraMixin`（同样注入 `Camera.setup @TAIL`），仅在 `ClientMountHandler.getVehicleCameraMode() != null` 时激活。

### 模式说明

| 模式 | 位置计算 | 画面效果 |
|------|----------|----------|
| **结构固定** (`STRUCTURE_FIXED`) | 通过 `Pose3d.transformPosition(localOffset)` 全量变换，含俯仰/侧倾 | 画面随车体晃动，路感强 |
| **方向稳定** (`DIRECTION_STABILIZED`) | 仅提取偏航旋转 + 位移，移除俯仰/侧倾 | 画面始终水平，适合长时间驾驶 |

### 技术实现

```java
// VehicleCameraMixin 核心逻辑
Pose3dc pose = subLevel.renderPose(partialTick);
Vector3d localOffset = sphericalOffset(yaw, pitch, distance, height);

switch (mode) {
    case STRUCTURE_FIXED -> pose.transformPosition(localOffset, worldCamPos);
    case DIRECTION_STABILIZED -> {
        Quaterniond yawOnly = new Quaterniond().rotateY(vehicleYaw);
        worldCamPos = pose.position() + yawOnly.transform(localOffset);
    }
}
setPosition(worldCamPos);
setLookAt(worldCamPos, vehicleCenter);  // 始终看向载具中心
```

### 设计决策

- **不替代 F5**：作为额外按键（F6）切换，F5 三模式继续有效
- **参考系**：以 SubLevel 结构中心为参考点，而非玩家实体
- **鼠标控制**：yaw/pitch 控制摄像机轨道位置（始终 lookAt 载具中心）
- **文件**：`mixin/VehicleCameraMixin.java` + `client/VehicleCameraMode.java`

## 极点奇点修复

`cos(pitch) ≈ 0`（俯仰接近 ±90°）时 `horizontalDist → 0`，`atan2(0,0) → 0`。

```java
float lookYaw = horizontalDist < 1e-4 ? entity.getYRot()
    : (float) Mth.atan2(lookZ, lookX) * Mth.RAD_TO_DEG - 90.0F;
```

## 相关文档
- 自适应摄像机系统详见 `4. 自适应摄像机.md`
- 极点奇点技术分析详见 `5-技术参考/5.1-关键技术要点/11. 摄像机极点奇点（Gimbal Lock）.md`

## 历史记录

| 日期 | 变更 |
|------|------|
| 2026-06-06 | 初版：独立轨道状态 + 低通滤波器 |
| 2026-07-06 | **重构**：移除独立 yaw/pitch 追踪，使用原版 F5 第三人称 + Camera.yRot/xRot。仅 thirdPerson 模式激活。 |
| 2026-07-06 | **CameraMixin 跳过 IACPSeatEntity**：seat 自身管位姿。**新增 VehicleCameraMixin**：F6 切换结构固定/方向稳定模式。 |
