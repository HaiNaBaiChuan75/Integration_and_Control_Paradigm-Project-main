# CLAUDE.md

(更新于 2026/7/6)

本文件为 Claude Code 在此仓库中的编码指导（编码约定、设计原则、注释规范）。
项目全局文档（架构设计、功能清单、技术参考、踩坑索引）见 `《中控载具工坊：范式》管理文档3.0/`。
项目依赖/框架文档(ECS,Sable)等见`docs/ai`

## 项目概述

IAC-P 是一个 **Minecraft 1.21.1 NeoForge 模组**，通过协调 [Sable](https://github.com/ryanhcode/sable)
物理引擎、[Create](https://github.com/Creators-of-Create/Create) 机械动力及其他第三方系统，提供载具操控、武器和 HUD。它是"
胶水层"——不实现自己的物理。

主要依赖：NeoForge、Create、Sable、Offroad、Simulated、Aeronautics、GeckoLib。所有版本号在 `gradle.properties` 中定义。

## 构建命令

```bash
./gradlew runClient       # 启动游戏客户端
./gradlew runClient_alt   # 启动游戏客户端（备用用户名）
./gradlew runServer       # 启动专用服务器
./gradlew compileJava     # 仅编译
./gradlew build           # 构建模组 JAR
```

- 需要 Java 21
- 首次启动会自动解压嵌套的 JAR 依赖
- IDE 运行配置："Client"、"Client 2"（备用用户名）、"Server"、"Data Generation"

## 需求分析与代码生成

在生成任何代码之前，先判断请求的性质，选择对应的处理方式。

### 请求的两种形态

**结构型请求** — 请求中已包含明确的类/方法/接口划分、职责边界说明、或对扩展性的考量。此类请求可以直接进入编码，但仍需对照设计原则做最终检查。

**场景型请求** — 请求以游戏内场景、空间关系、类比、或"想要什么效果"的方式呈现，而非以代码结构呈现。它准确描述了问题域，只是没有预设实现方案。场景型请求很常见，处理时需要额外的分解步骤：

1. **先分解、再编码** — 将场景描述拆解为可独立变化的关注点（渲染、碰撞、数据存储、交互逻辑等），给出结构方案后再动手。不要对方说"做一个 X"就直接写一个 X 类——先想清楚 X 涉及哪些独立职责。
2. **用游戏语言解释设计** — 用方块、实体、Tick、数据同步、模型加载等游戏中可见的概念解释设计决策，使用设计模式与设计原则要解释这是什么和为什么
3. **识别表述中的设计意图** — 日常语言描述的痛点或需求，可能等价于一个精心设计的抽象。AI 的工作是发现这种等价关系，而非照字面堆砌代码。例如对方说"这样做管理会变灾难"，可能指向注册表模式或数据驱动方案。
4. **先确认、再实现** — 给出组件划分和职责分配后，用一两句话向对方确认理解是否一致，再动手编码。

### 通用底线

- **设计原则检查**：生成的所有代码必须逐条对照设计原则（见下方），不符合的部分标注并说明原因。
- **替代方案优先于拒绝**：当对方的想法违反设计原则时，给出一个遵循原则的等价实现方案，并说明两者在职责分离、耦合度、可扩展性上的差异。不直接说"这样不行"。
- **为"不适合编码"的需求找替代路径**：当需求本质上更适合用配置、数据驱动、脚本或非代码方式解决时，指出替代路径而非强行用 Java 类实现。
- **解释"为什么"**：解释设计决策时使用方块、实体、Tick 等游戏内概念，使用设计模式与设计原则要解释这是什么和为什么。

## 设计原则

- SRP · OCP · LSP · ISP · DIP · 迪米特法则
- 组合优于继承 · DRY · KISS · YAGNI · 封装变化

## 注释与文档

代码和代码注释是比项目文档**更权威**的真相来源——它们与代码同步演进，项目文档天然滞后。信息冲突时以代码为准。

### 原则

- **写意图不写行为** — 注释解释*为什么*（设计意图、性能权衡、边界条件），不重述代码做了什么
- **公共 API 必须写** — `public`/`protected` 用 `/** ... */`，含 `@param`、`@return`、`@throws`
- **不写**：复读机注释 · 过时注释（改代码时同步改，否则是谎言） · 无关信息
- **需要注释**：复杂算法 · 反直觉决策 · 边界情况 · 核心领域模型 · 维护标记（`// TODO/FIXME/HACK`，需带上下文）
- **不需要注释**：代码已自解释 · 简单 getter/setter · 可读性差时优先重构而非加注释
- **注解即文档**：`@NotNull`/`@Nullable` 替代 null 相关 Javadoc（公开 API 强制使用）。`@Contract`（`"null -> false"`、`"null -> fail"`、`pure = true`）替代简单 `@throws` 和副作用描述。注解 + 签名足以表达契约时可省略对应 Javadoc 文字

### 语言策略

中文为主

## 编码约定

- **命名**：遵循标准 Java 约定。NBT 标签常量使用 `TAG_帕斯卡命名`。包 ID 使用 `蛇形命名`。
- **日志**：使用 `IACP.LOGGER`（SLF4J）。error 级别慎用；非关键边界情况用 warn；调试信息用 info。渲染代码使用宽泛的 try-catch
  守护，防止单个 BE 异常导致整个渲染流程崩溃。
- **错误处理**：渲染/叠加层代码静默吞异常（视觉瑕疵优于崩溃）。逻辑代码对无效输入抛出 `IllegalArgumentException`。无自定义受检异常。
- **优先使用 record / enum**：数据组件必须使用 `record`，有限状态使用 `enum`。新增数据通道必须走
  record 组件，不得新定义 getter/setter 接口（旧接口如 `EnginePart.getTorque()` 在 record 模式下不可模式匹配）。
- **JOML 对象约定**：`Vector3dc`/`Quaterniondc` 等 JOML 只读视图遵循「**入口拷贝、出口只读、计算不滥分配**」：
    - **入口即拷贝** — record compact constructor 中对所有 `Vector3dc`/`Quaterniondc` 参数防御性拷贝 `new Vector3d(src)`
      ，确保 record 持有独立副本不受外部修改
    - **出口只读不拷贝** — getter/accessor 返回 `Vector3dc`，调用方通过只读接口读 `.x()`/`.y()`/`.z()`，需要修改时自行
      `new Vector3d(src)` 再改
    - **计算不乱分配** — System 内临时计算复用局部 `Vector3d` 变量，用 `.set()` 修改而非每步 new
    - nullable 的 Vector 字段（如 `ControlState.aimTarget`、`WheelState.contactPointLocal`）：非 null 时同样拷贝

## System 编写规范

System 层是模组核心逻辑所在，写法必须清晰一致。以下为 `ecs/v2/system/` 下所有 System 类的强制约定。

### 组件访问：仅解构 View 容器，组件 record 走访问器

**仅对 `View2`/`View3`/`View4` 使用 Record Pattern 解构**（目的是为两个 View 绑定有意义的局部变量名）。
组件数据 record（`WheelDef`、`EngineState` 等）通过 `.get()` + 访问器按需取值：

```java
// ✅ 正确：仅 Views2 解构，组件 record 走访问器
for(var entry :View.

find(parts, WheelDef.KEY, WheelState.KEY)){
        if(!(entry instanceof

Views2(var defView, var stateView)))continue;
var wd = defView.get();

PartTransform tx = PartTransform.of(stateView.part());
Vector3dc localDir = tx.toRelativePos(aimTarget);
YawPitch raw = YawPitch.from(localDir);

double clampedYaw = Math.clamp(raw.yaw(), gd.minYaw(), gd.maxYaw());
double clampedPitch = Math.clamp(raw.pitch(), gd.minPitch(), gd.maxPitch());

    stateView.

set(new GimbalState(
        new YawPitch(clampedYaw, clampedPitch), 0,0));
        }
```

```java
// ❌ 错误：解构组件 record 全部字段（位置敏感，为用一个字段拆 11 个）
if(!(defView.get() instanceof

WheelDef(var radius, var mountDirection, var mountPoint, var suspensionDirection, var suspensionStiffness, var steeringAxis, var maxSteeringAngle, var driven, var gripForward, var gripLateral, var rollingResistance)))
        continue;
        if(driven)driveCount++;
```

### 为什么

| 方式                                     | 耦合                         | 可读性                            | 重构代价 |
|----------------------------------------|----------------------------|--------------------------------|------|
| `instanceof WheelDef(var radius, ...)` | **位置敏感**：加/删字段 → 所有解构处编译报错 | 一次列出全部 11 个名字，读者必须一一识别哪些用了哪些没用 | 高    |
| `defView.get().driven()`               | **名称敏感**：加字段不影响            | 只写实际需要的调用                      | 低    |

组件 record 的字段排序不是 API 契约——字段名才是。解构把记录的顺序耦合进每个读取处，违反信息隐藏原则。

### 单组件读取

单个组件的读取不需要 Views2 容器，直接用 `View.of()` 或 `ComponentKey` 访问器：

```java
// ✅ 单组件
View<ControlState> cv = View.findPrimary(parts, null, ControlState.KEY);
if(cv ==null)return;
double throttle = -Mth.clamp(cv.get().intent().z(), -1.0, 1.0);
```

### 多字段写入（Wither 链）

需要构造新 record 替换旧值时，从 `stateView.get()` 取原有字段，仅替换需要变更的部分：

```java
// ✅ 正确
var ws = stateView.get();
stateView.

set(new WheelState(ws.angularVelocity(),ws.

suspensionCompression(),smoothed,
        ws.

torque(),ws.

braking(),ws.

contactPointLocal()));
```

```java
// ❌ 错误：用 Record Pattern 解构后再重建（等于重新列出全部字段，只为传一个参数）
if(!(stateView.get() instanceof

WheelState(var angVel, var suspComp, var steerAng, var oldTorque, var brakeVal, var contactPt)))
        continue;
        stateView.

set(new WheelState(angVel, suspComp, steerAng, torquePerWheel, brakeVal ||braking, contactPt));
```

**例外**：如果确实需要读取/回写组件的全部字段，允许解构减少重复。但这种情况在 Sysem 层极少见（通常只需变 1-2 个字段）。

## 分包规范

**原则："按类型分层，按功能分块"**。重构和新增代码必须遵守本节规则。根包为 `com.hainabaichuan75.iac_p`，主模组类 `IACP.java`
置于根包下。

### 标准分包

| 包         | 职责       | 放什么                                                    |
|-----------|----------|--------------------------------------------------------|
| `block`   | 方块定义     | `Block` 子类、`BlockEntity` 子类                            |
| `item`    | 物品定义     | `Item` 子类（工具、武器、护甲等）                                   |
| `entity`  | 实体定义     | 生物实体                                                   |
| `client`  | 客户端专用    | `renderer/`（渲染器）、`screen/`（GUI）、模型、粒子。**服务端不可引用此包**    |
| `network` | 网络通信     | 数据包定义（`packets/`）、序列化、网络处理器注册                          |
| `data`    | 数据生成     | DataGen 代码，与运行时代码严格分离。输出目录 `src/generated/resources/`  |
| `index`   | 注册入口     | `DeferredRegister` 创建和注册调用（等同经典结构中的 `init`/`registry`） |
| `mixin`   | Mixin 注入 | 对原版或第三方模组的 Mixin 注入类                                   |
| `events`  | 事件处理     | 订阅 NeoForge 事件总线的处理器                                   |
| `util`    | 工具类      | 无状态的辅助方法、数学工具、常量。**不在此放业务逻辑**                          |

### 功能分包

| 包      | 职责                                                      |
|--------|---------------------------------------------------------|
| `ecs/` | ECS 架构。子包：`dispatch/`（调度）、`part/`（组件）、`system/`（ECS 系统） |

### ECS 组件命名

`ecs/v2/part/state/` 下的数据组件遵循以下命名体系。

**默认分类（适用于大多数部件）**：

| 后缀      | 角色    | 频率   | 例子                    |
|---------|-------|------|-----------------------|
| `Def`   | 定义/参数 | 相对不变 | `EngineDef.maxTorque` |
| `State` | 运行状态  | 动态调整 | `EngineState.torque`  |

约定：`Def` + `State` 是默认命名方案，两者**必须平铺放置为同级组件，各自持有独立 `ComponentKey`**。
`State` record **不得**持有 `Def` 引用，反之亦然。System 通过 View 查询拼合读取（详见「System 编写规范」）：
<pre>{@code
// 正确：Def/State 分离，各自独立查询（通过 View 批量获取）
for (var entry : View.find(parts, EngineDef.KEY, EngineState.KEY)) {
    if (!(entry instanceof Views2(var defView, var stateView))) continue;
    // defView.get().maxTorque(), stateView.get().torque()
}

// ❌ 错误：State 内嵌 Def
public record EngineState(EngineDef spec, double torque) { … }  // 禁止
}</pre>

**特殊命名**（不适用上述分类时使用描述性名字，无固定后缀）

### 关键规则

1. **客户端代码隔离**：`client/` 包下代码仅物理客户端执行，用 `@OnlyIn(Dist.CLIENT)` 或分发到客户端事件总线。**服务端代码绝不
   import `client/` 包**。网络包定义归 `network/` 而非 `client/`。

2. **DataGen 单独成包**：`data/` 中的 DataGen 代码不引用运行业务逻辑，运行时也不引用 `data/`。

3. **事件订阅** 一般放在events包，且使用`@EventBusSubscriber class`和`@SubscribeEvent public static`

4. **新增代码时**，必须按上述"标准分包"选择正确的包，优先改善而非恶化现有结构。**重构时**，逐步将代码朝目标方向迁移。

## 开发备注

- 无单元测试——通过启动客户端（`./gradlew runClient`）在游戏中验证
- 模组运行时需要所有依赖存在（Create、Sable、Offroad、Simulated、Aeronautics）
- `BlockEntity` 类型分散在多个索引类中而非单一注册表——按职责选择正确的索引类
- 数据生成输出目录：`src/generated/resources/`
- Access Transformer：无
