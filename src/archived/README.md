# 封存代码存档

> 封存日期：2026-06-26
> 封存原因：战略简化 — 自研轮胎摩擦/动力系统参数耦合严重，测试黑箱化
> 封存方式：完整副本，Git 可追溯。原始文件仍在 `src/main/` 中保持编译

## 封存内容

### 动力系统（驾驶舱）— `content/blocks/cockpit/`
| 文件 | 行数 | 职责 |
|------|:----:|------|
| `CockpitBlock.java` | — | 驾驶舱下格方块（炼药锅形状） |
| `CockpitBlockEntity.java` | 651 | 发动机/变速箱编排、NBT持久化 |
| `CockpitUpperBlock.java` | — | 驾驶舱上格方块（脚手架形状） |
| `EngineModel.java` | — | 发动机物理：油门直控RPM、扭矩曲线、内部摩擦 |
| `PowertrainConstants.java` | — | 动力系统编译时常量（齿比/怠速/红线/换挡参数） |
| `TransmissionModel.java` | — | 变速箱模型：5速+R+N、换挡真空期、比率变换 |

### 悬挂/轮胎系统 — `content/blocks/suspension_test/`
| 文件 | 行数 | 职责 |
|------|:----:|------|
| `SuspensionTestBlock.java` | — | 悬挂测试方块 |
| `SuspensionTestBlockEntity.java` | 1,254 | 弹簧-阻尼器/BinaryGrip/手刹/智能按键 |
| `SuspensionConstants.java` | 184 | 悬挂物理编译时常量 |
| `TirePhysicsCalculator.java` | — | 轮胎物理：滚动阻力/爆胎/胎压/形变 |
| `BrushTireModel.java` | — | [禁用] Brush轮胎侧偏模型 |
| `SuspensionTestRenderer.java` | — | 客户端渲染器 |

### 辅助事件 — `events/`
| 文件 | 职责 |
|------|------|
| `SableBlockHelper.java` | SubLevel 方块查询工具（射线追踪/坐标变换） |
| `SablePostPhysicsTickEvent.java` | 物理tick后处理（位置同步提升到~100Hz） |

### 配置
| 文件 | 职责 |
|------|------|
| `IACPConfig.java` | SubLevel 缩放编译时常量 |

## 状态说明

这些文件的原始版本仍在 `src/main/java/com/hainabaichuan75/iac_p/` 中。
封存副本仅供参考和历史追溯，不参与编译。

未来计划：
1. 将悬挂行为重置为上游 Offroad WheelMount 的简洁模式
2. 对接 Create RPM 网络替代自研发动机/变速箱
3. 视需要选择性复活部分物理模型（如扭矩曲线）
