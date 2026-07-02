package com.hainabaichuan75.iac_p.block.cockpit;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionTestBlock;
import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionTestBlockEntity;
import com.hainabaichuan75.iac_p.ecs.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.ecs.part.PartQuery;
import com.hainabaichuan75.iac_p.events.SubLevelScanner;
import com.hainabaichuan75.iac_p.index.ModCockpitBlockEntityTypes;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.List;

import static com.hainabaichuan75.iac_p.block.cockpit.PowertrainConstants.ENGINE_IDLE_RPM;
import static com.hainabaichuan75.iac_p.block.cockpit.PowertrainConstants.ENGINE_MAX_RPM;

/**
 * 驾驶舱方块实体 —— 载具动力系统的状态管理和编排。
 * <p>
 * 编译时常量见 {@link PowertrainConstants}，发动机计算见 {@link EngineModel}，变速箱见
 * {@link TransmissionModel}。
 *
 * <h3>动力系统架构</h3>
 * <pre>
 * 玩家输入 (油门)
 *   ↓
 * EngineModel.computeThrottleControlledRun()  ← 发动机始终独立运行
 *   RPM = IDLE + throttle × (MAX - IDLE)             油门直控
 *   扭矩 = TORQUE_MIN + throttle × (TORQUE_MAX - TORQUE_MIN)  油门线性，与RPM解耦
 *   ↓
 * 空档：torquePerWheel = 0（变速箱断开）
 * 在档：TransmissionModel.computeOutput() 纯数学变换
 *   扭矩b = 扭矩a × 齿比  转速b = RPM / 齿比
 *   换挡真空期 6 tick → 扭矩b = 0
 *   ↓
 * 各 Suspension 从 getTorquePerWheel() 读取可用扭矩
 *   摩擦圆约束决定实际地面驱动力（轮胎是唯一限幅器）
 * </pre>
 */
public class CockpitBlockEntity extends PartBlockEntity {

    @Override
    public void onLoad() {
        super.onLoad();
        // 油门始终 100%：引擎满扭矩恒备，WASD 只控制方向不控制油门深浅。
        this.throttleLevel = 1.0;
        this.rawThrottleDirection = 0;
    }

    // ====================================================================
    //  朝向（PartBlockEntity 覆写）
    // ====================================================================
    /** 朝北 — 模型默认朝向，单位四元数 */
    private static final Quaterniond ORIENT_NORTH = new Quaterniond();
    /** 朝南 — 绕 Y 轴旋转 180° */
    private static final Quaterniond ORIENT_SOUTH = new Quaterniond().rotateY(Math.PI);
    /** 朝东 — 绕 Y 轴旋转 +90° */
    private static final Quaterniond ORIENT_EAST  = new Quaterniond().rotateY(-Math.PI / 2);
    /** 朝西 — 绕 Y 轴旋转 -90° */
    private static final Quaterniond ORIENT_WEST  = new Quaterniond().rotateY(Math.PI / 2);

    /**
     * 根据方块的 {@link CockpitBlock#FACING} 属性返回朝向四元数。
     * 如果 BlockState 没有 FACING 属性（如 {@code BaseCabinBlock}），返回单位四元数。
     */
    @Override
    public @NotNull Quaterniondc orientation() {
        BlockState state = getBlockState();
        if (state.hasProperty(CockpitBlock.FACING)) {
            switch (state.getValue(CockpitBlock.FACING)) {
                case SOUTH: return ORIENT_SOUTH;
                case EAST:  return ORIENT_EAST;
                case WEST:  return ORIENT_WEST;
                default:    return ORIENT_NORTH;
            }
        }
        return ORIENT_NORTH;
    }

    // ====================================================================
    //  运行时状态
    // ====================================================================
    /**
     * 当前档位：-1=R, 0=N, 1～5=前进档
     */
    private int currentGear = 0;

    /**
     * 发动机当前转速（RPM）
     */
    private double engineRpm = ENGINE_IDLE_RPM;

    /**
     * 油门踏板位置 [0.0, 1.0]。始终为 1.0（默认全油门），引擎始终满扭矩输出。
     */
    private double throttleLevel = 1.0;

    /**
     * 当前 tick 的引擎输出扭矩（Nm），油门线性，与 RPM 解耦。 即 computeThrottleControlledRun()
     * 的计算结果缓存。
     */
    private double effectiveTorque = PowertrainConstants.TORQUE_MAX;

    /**
     * 智能映射启用
     */
    private boolean smartMappingActive = false;

    /**
     * 智能变速启用。开启后发动力不足时自动降档到 1 档。
     */
    private boolean autoShiftEnabled = false;

    /**
     * 当前驾驶技能 ID
     */
    private String activeSkillId = com.hainabaichuan75.iac_p.skill.SkillRegistry.DEFAULT_SKILL_ID;

    /**
     * 原始油门方向（+1/-1/0），由 VehicleControlC2SPacket 设置
     */
    private int rawThrottleDirection = 0;

    // ── 扭矩源模型新增字段 ──
    /**
     * 每轮可用扭矩（Nm），供悬挂 P 控制器限幅
     */
    private double torquePerWheel = 0.0;

    /**
     * 发动机是否已熄火
     */
    private boolean stalled = false;

    /**
     * 上次更新时在档位中的轮子总数（用于扭矩均摊）
     */
    private int lastWheelCount = 0;

    // ── 换挡状态 ──
    public CockpitBlockEntity(BlockPos pos, BlockState state) {
        super(ModCockpitBlockEntityTypes.COCKPIT.get(), pos, state);
    }

    /**
     * 供子类使用的保护构造器（如 {@link com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlockEntity}），
     * 允许使用不同的 BlockEntityType。
     */
    protected CockpitBlockEntity(BlockEntityType<? extends CockpitBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ====================================================================
    //  动力系统接口（供 SuspensionTestBlockEntity 查询）
    // ====================================================================
    /**
     * @return 每轮可用扭矩（Nm）。空档或熄火时返回 0。
     */
    public double getTorquePerWheel() {
        if (stalled || currentGear == 0) {
            return 0;
        }
        return torquePerWheel;
    }

    /**
     * @return 轮端目标 RPM（由发动机当前转速通过齿比推算）。 正值前进，负值倒车。空档或熄火时返回 0。
     */
    public double getTargetWheelRpm() {
        if (stalled) {
            return 0;
        }
        return TransmissionModel.computeTargetWheelRpm(currentGear, engineRpm);
    }

    /**
     * @return 方向符号：+1 前进, -1 倒车, 0 空档/熄火
     */
    public double getDirectionSign() {
        if (stalled) {
            return 0;
        }
        return TransmissionModel.getDirectionSign(currentGear);
    }

    /**
     * @return 发动机是否已熄火
     */
    public boolean isStalled() {
        return stalled;
    }

    /**
     * 尝试重启发动机（仅熄火时有效）。
     */
    public void tryRestart() {
        if (!stalled) {
            return;
        }
        stalled = false;
        engineRpm = ENGINE_IDLE_RPM;
        IACP.LOGGER.info("[Cockpit] 发动机重启");
        setChanged();
        sendData();
    }

    // ====================================================================
    //  换挡操作
    // ====================================================================
    /**
     * 升档：R → N → 1 → 2 → 3 → 4 → 5。
     * <p>
     * 非瞬时完成：启动换挡序列 → SHIFT_TIME_TICKS tick 动力中断 → 档位切换。 换挡期间 torquePerWheel =
     * 0，发动机空载运行。
     */
    public void gearUp() {
        if (isShifting) {
            return;
        }
        var result = TransmissionModel.gearUp(this.currentGear, this.engineRpm);
        if (result.gear() == this.currentGear) {
            return;
        }
        startShiftSequence(result.gear());
    }

    /**
     * 降档：5 → 4 → 3 → 2 → 1 → N → R。
     * <p>
     * 非瞬时完成：启动换挡序列 → SHIFT_TIME_TICKS tick 动力中断 → 档位切换。
     */
    public void gearDown() {
        if (isShifting) {
            return;
        }
        var result = TransmissionModel.gearDown(this.currentGear, this.engineRpm);
        if (result.gear() == this.currentGear) {
            return;
        }
        startShiftSequence(result.gear());
    }

    /**
     * 直接降档到 1 档（智能变速用）。
     * <p>
     * 跳过逐级降档，直接设定目标档位为 1。同样有 6 tick 换挡真空期。
     */
    public void shiftToFirst() {
        if (isShifting || currentGear <= 1) {
            return;
        }
        startShiftSequence(1);
    }

    /**
     * @return 当前档位：-1=R, 0=N, 1-5=前进档
     */
    public int getCurrentGear() {
        return currentGear;
    }

    /**
     * @return 发动机当前转速（RPM）
     */
    public double getEngineRpm() {
        return engineRpm;
    }

    /**
     * @return 当前 tick 的引擎输出扭矩（Nm），含扭矩曲线修正 × 油门
     */
    public double getEffectiveTorque() {
        return effectiveTorque;
    }

    /**
     * @return 当前油门位置 [0.0, 1.0]
     */
    public double getThrottleLevel() {
        return throttleLevel;
    }

    // ====================================================================
    //  智能映射与技能
    // ====================================================================
    public boolean isSmartMappingActive() {
        return smartMappingActive;
    }

    public void setSmartMappingActive(boolean active) {
        this.smartMappingActive = active;
        setChanged();
        sendData();
    }

    public boolean isAutoShiftEnabled() {
        return autoShiftEnabled;
    }

    public void setAutoShiftEnabled(boolean enabled) {
        this.autoShiftEnabled = enabled;
        setChanged();
        sendData();
    }

    public String getActiveSkillId() {
        return activeSkillId;
    }

    public void setActiveSkillId(String skillId) {
        this.activeSkillId = skillId != null ? skillId : com.hainabaichuan75.iac_p.skill.SkillRegistry.DEFAULT_SKILL_ID;
        setChanged();
        sendData();
    }

    // ====================================================================
    //  控制输入
    // ====================================================================
    /**
     * 设置原始油门方向。由 VehicleControlC2SPacket 每 tick 调用。
     */
    public void setRawThrottleDirection(int dir) {
        this.rawThrottleDirection = dir;
    }

    /**
     * @return 当前档位的人类可读名称
     */
    public String getGearDisplayName() {
        return PowertrainConstants.gearName(this.currentGear);
    }

    /**
     * @return 是否正在换挡（动力中断期间）
     */
    public boolean isShifting() {
        return isShifting;
    }

    /**
     * 启动换挡序列。
     * <p>
     * 立即切断轮端扭矩（torquePerWheel = 0），启动换挡计时器。 计时器归零时由 tick() 完成档位切换。 换挡后 RPM
     * 由实际轮速（车速）决定，而非旧 RPM × 齿比推算。
     *
     * @param targetGear 目标档位
     */
    private void startShiftSequence(int targetGear) {
        this.isShifting = true;
        this.shiftingTimer = PowertrainConstants.SHIFT_TIME_TICKS;
        this.targetShiftGear = targetGear;
        this.torquePerWheel = 0;

        // ── 降档自动补油目标（Rev-Match）──
        // 前进档降档：target < current（如 4→3, 2→1），需更高 RPM 匹配低档位
        // 换挡期间油门直控模式下临时提油使 RPM 升到目标值
        if (this.currentGear >= 2 && targetGear >= 1 && targetGear < this.currentGear) {
            double oldRatio = PowertrainConstants.getRatioForGear(this.currentGear);
            double newRatio = PowertrainConstants.getRatioForGear(targetGear);
            this.revMatchTargetRpm = this.engineRpm * newRatio / oldRatio;
            this.revMatchTargetRpm = net.minecraft.util.Mth.clamp(
                    this.revMatchTargetRpm, ENGINE_IDLE_RPM, ENGINE_MAX_RPM);
        } else {
            this.revMatchTargetRpm = 0;
        }

        IACP.LOGGER.debug("[Cockpit] 换挡开始 → {} (revMatch={} RPM)",
                PowertrainConstants.gearName(targetGear), (int) this.revMatchTargetRpm);
        setChanged();
        sendData();
    }

    /**
     * 将发动机重置到怠速。下车/断线/重启时调用。 油门保持 100%（引擎始终满扭矩），只重置 RPM 和方向输入。
     */
    public void resetEngineToIdle() {
        this.engineRpm = ENGINE_IDLE_RPM;
        this.throttleLevel = 1.0;
        this.rawThrottleDirection = 0;
        this.stalled = false;
    }

    /**
     * 直接设置发动机转速（用于外部强制复位）。
     */
    public void setEngineRpm(double rpm) {
        this.engineRpm = net.minecraft.util.Mth.clamp(rpm, 0, ENGINE_MAX_RPM);
    }

    // ====================================================================
    //  每 tick 更新
    // ====================================================================
    //  空档：油门直控转速，变速箱断开，发动机在测试架上独立运转。
    //  在档：变速箱纯比率变换，发动机转速由轮速运动学约束。
    /**
     * 缓存总质量（kg），仅用于覆盖层显示
     */
    private double totalMass = 1000.0;

    /**
     * 上次同步到客户端的油门值，用于阈值检测避免每 tick 刷包
     */
    private double lastSyncedThrottle = -1.0;

    /**
     * 状态同步包冷却计数器（每 2 tick 向客户端推送一次实时状态）
     */
    private int stateSyncCooldown = 0;
    /**
     * 上次同步时的速度（m/s），用于加速度差分计算
     */
    private double lastSyncSpeedMs = 0;
    /**
     * 最近计算的加速度（m/s²），供自动变速逻辑和覆盖层使用
     */
    private double currentAccelMs2 = 0;

    // ── 自动变速 ──
    /**
     * 升档节流计数器（每 10 tick 检查一次）
     */
    private int upshiftTimer = 0;
    /**
     * 上次升档检查时的速度（m/s）
     */
    private double lastUpshiftSpeed = 0;
    /**
     * 降档持续计时器（速度比连续 N tick < 阈值才降，防转弯误触发）
     */
    private int downshiftStallTimer = 0;
    /**
     * 上次换挡的游戏刻（升档在此 tick 内不触发，防升降档振荡）
     */
    private int lastShiftTick = 0;

    // ── 换挡状态 ──
    /**
     * 是否正在换挡（动力中断期间）。期间 torquePerWheel = 0，发动机空载运行。
     */
    private boolean isShifting = false;
    /**
     * 换挡倒计时（tick），归零时完成换挡
     */
    private int shiftingTimer = 0;
    /**
     * 换挡目标档位
     */
    private int targetShiftGear = 0;
    /**
     * 降档自动补油（Rev-Match）目标 RPM。降档时发动机需升转匹配低档位， 此值为目标转速，在换挡期间自动补油使 RPM 平滑接近此值。
     */
    private double revMatchTargetRpm = 0;

    // ==================================================================
    //  本 tick 缓存：避免重复扫描 SubLevel
    // ==================================================================
    /**
     * 缓存的悬挂部件列表（本 tick 内复用）。 由 {@link #scanWheelRpm} 刷新，被 {@link #hasAnyThrottleInput}
     * 和 {@link #hasAnySteeringInput} 等复用。
     */
    @Nullable
    private List<SuspensionTestBlockEntity> cachedSuspensions = null;

    /**
     * 刷新本 tick 的悬挂部件缓存。
     */
    private List<SuspensionTestBlockEntity> getOrRefreshSuspensions(SubLevel subLevel) {
        if (this.cachedSuspensions != null) {
            return this.cachedSuspensions;
        }
        this.cachedSuspensions = PartQuery.findParts(subLevel, SuspensionTestBlockEntity.class);
        return this.cachedSuspensions;
    }

    public void tick() {
        if (level == null) {
            return;
        }

        // ── 每 tick 重置缓存（下次 tick 重新获取） ──
        this.cachedSuspensions = null;

        // ── 延迟注册重试（SubLevel 未就绪时排队注册） ──
        com.hainabaichuan75.iac_p.affiliation.DeferredRegistration.tick(this);

        SubLevel sl = Sable.HELPER.getContaining(this);
        if (sl == null) {
            return;
        }

        // ── 读取物理质量（仅服务端，仅用于覆盖层显示）──
        if (sl instanceof ServerSubLevel ssl) {
            try {
                this.totalMass = ssl.getMassTracker().getMass();
            } catch (Exception ignored) {
            }
        }

        // ── 油门始终 100%（服务端才有 rawThrottleDirection，客户端跳过）──
        this.throttleLevel = 1.0;

        // ── 全部引擎计算仅服务端执行 ──
        if (sl instanceof ServerSubLevel serverSl) {
            // ── 状态同步（引擎计算之前执行，确保提前返回也能发送）──
            if (Math.abs(this.throttleLevel - this.lastSyncedThrottle) > 0.02) {
                this.lastSyncedThrottle = this.throttleLevel;
                setChanged();
                sendData();
            }
            trySyncStateToClient(serverSl);

            // 熄火：切断扭矩输出
            if (stalled) {
                this.torquePerWheel = 0;
                this.effectiveTorque = 0;
                return;
            }

            // 换挡真空期
            if (tryProcessShifting()) return;

            // ═══ 正常行驶：发动机永远独立运行 ═══
            var result = EngineModel.computeThrottleControlledRun(this.throttleLevel);
            this.effectiveTorque = result.engineTorque();
            this.engineRpm = result.rpm();

            // 空档自动挂档
            tryAutoEngageGear();

            if (this.currentGear == 0) {
                this.torquePerWheel = 0;
            } else {
                // 在档：变速箱做纯数学变换
                WheelScanResult wheels = scanWheelRpm(sl);
                this.lastWheelCount = wheels.wheelCount;
                int wheelCount = Math.max(wheels.wheelCount, 1);
                var gbOut = TransmissionModel.computeOutput(result.engineTorque(), result.rpm(), this.currentGear);
                this.torquePerWheel = gbOut.torqueB() / wheelCount;

                // 憋住救急
                double curSpeed = Math.abs(wheels.avgWheelRpm()) * Math.PI * 2.0 / 60.0 * 0.5;
                tryStallRescue(sl, curSpeed);

                // 自动降档
                tryAutoDownshift(sl, wheels);

                // 自动升档
                tryAutoUpshift(sl);
            }
        }
    }

    /**
     * 处理熄火：标记状态，清空扭矩输出。
     */
    private void handleStall() {
        this.stalled = true;
        this.engineRpm = 0;
        this.torquePerWheel = 0;
        this.effectiveTorque = 0;
        IACP.LOGGER.info("[Cockpit] 发动机熄火！");
        setChanged();
        sendData();
    }

    // ====================================================================
    //  车轮扫描（简化版：仅获取轮速和数量，无需消耗扭矩）
    // ====================================================================
    private record WheelScanResult(double avgWheelRpm, int wheelCount) {

    }

    /**
     * 扫描轮速。优先使用缓存（本 tick 内已缓存则不重新查询），
     * 只在缓存为空时尝试 SubLevel 全量扫描。
     */
    private WheelScanResult scanWheelRpm(SubLevel sl) {
        var entries = getOrRefreshSuspensions(sl);

        if (!entries.isEmpty()) {
            return scanRpmFromSuspensions(entries);
        }

        // 降级：缓存为空 → 直接全量扫描
        return scanRpmFallback(sl);
    }

    private static WheelScanResult scanRpmFromSuspensions(List<SuspensionTestBlockEntity> entries) {
        double totalRpm = 0;
        int count = 0;

        for (var sbe : entries) {
            totalRpm += sbe.getCurrentWheelRpm();
            count++;
        }

        double avgRpm = count > 0 ? totalRpm / count : 0;
        return new WheelScanResult(avgRpm, count);
    }

    private WheelScanResult scanRpmFallback(SubLevel sl) {
        double[] totalRpm = {0};
        int[] count = {0};

        SubLevelScanner.forEachBlock(sl, level, (worldPos, state, be) -> {
            if (!(state.getBlock() instanceof SuspensionTestBlock)) {
                return;
            }
            if (!(be instanceof SuspensionTestBlockEntity sbe)) {
                return;
            }
            totalRpm[0] += sbe.getCurrentWheelRpm();
            count[0]++;
        });

        double avgRpm = count[0] > 0 ? totalRpm[0] / count[0] : 0;
        return new WheelScanResult(avgRpm, count[0]);
    }

    /**
     * 检查是否有任何悬挂方块有驱动输入（W/S 按下）。 使用缓存避免重复查询。
     */
    private boolean hasAnyThrottleInput(SubLevel sl) {
        var entries = getOrRefreshSuspensions(sl);
        for (var sbe : entries) {
            if (sbe.hasThrottle()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否有任何悬挂方块有转向输入（A/D 按下）。 使用缓存避免重复查询。
     */
    private boolean hasAnySteeringInput(SubLevel sl) {
        var entries = getOrRefreshSuspensions(sl);
        for (var sbe : entries) {
            if (Math.abs(sbe.getTargetSteeringYaw()) > 0.01) {
                return true;
            }
        }
        return false;
    }

    // ====================================================================
    //  tick() 子方法提取
    // ====================================================================

    /**
     * 每 2 tick 向骑乘者推送一次实时状态（RPM/档位/速度/加速度等）。
     */
    private void trySyncStateToClient(ServerSubLevel serverSl) {
        if (--this.stateSyncCooldown > 0) return;
        this.stateSyncCooldown = 2;

        double speedMs = 0;
        try {
            org.joml.Vector3d vel = dev.ryanhcode.sable.Sable.HELPER.getVelocity(level,
                    new org.joml.Vector3d(
                            this.worldPosition.getX() + 0.5,
                            this.worldPosition.getY() + 0.5,
                            this.worldPosition.getZ() + 0.5));
            if (vel != null) speedMs = vel.length();
        } catch (Exception ignored) {}

        double accelMs2 = (speedMs - this.lastSyncSpeedMs) / 0.1;
        this.lastSyncSpeedMs = speedMs;
        this.currentAccelMs2 = Math.abs(accelMs2);

        var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(this);
        if (subLevel != null) {
            var player = com.hainabaichuan75.iac_p.events.PlayerMountTracker.getPlayerForSubLevel(
                    subLevel.getUniqueId(), (net.minecraft.server.level.ServerLevel) level);
            if (player != null) {
                com.hainabaichuan75.iac_p.network.ModNetworking.sendToPlayer(player,
                        new com.hainabaichuan75.iac_p.network.packets.VehicleStateS2CPacket(
                                this.engineRpm, this.throttleLevel, this.currentGear,
                                this.stalled, this.effectiveTorque,
                                speedMs, accelMs2, this.isShifting));
            }
        }
    }

    /**
     * 处理换挡真空期。降档时执行 Rev-Match 自动补油， 计时器归零后完成档位切换。
     *
     * @return true 如果仍在换挡真空期（调用方应提前 return）
     */
    private boolean tryProcessShifting() {
        if (!this.isShifting) return false;

        this.torquePerWheel = 0;
        this.effectiveTorque = 0;

        double effectiveThrottle = this.throttleLevel;
        if (this.revMatchTargetRpm > 0) {
            double rpmNow = EngineModel.computeThrottleControlledRun(this.throttleLevel).rpm();
            if (this.revMatchTargetRpm > rpmNow) {
                double blip = (this.revMatchTargetRpm - ENGINE_IDLE_RPM)
                        / (ENGINE_MAX_RPM - ENGINE_IDLE_RPM);
                effectiveThrottle = Math.max(effectiveThrottle, Math.min(blip, 0.8));
            }
        }

        var sr = EngineModel.computeThrottleControlledRun(effectiveThrottle);
        this.engineRpm = sr.rpm();

        if (--this.shiftingTimer <= 0) {
            this.currentGear = this.targetShiftGear;
            this.isShifting = false;
            IACP.LOGGER.debug("[Cockpit] 换挡完成 → {}", PowertrainConstants.gearName(this.currentGear));
            setChanged();
            sendData();
        }
        return true;
    }

    /**
     * 空档自动挂档：按下 W/S 时自动跳入 1 档。
     */
    private void tryAutoEngageGear() {
        if (this.currentGear != 0 || this.rawThrottleDirection == 0) return;

        this.currentGear = 1;
        this.isShifting = false;
        this.shiftingTimer = 0;
        this.targetShiftGear = 0;
        this.revMatchTargetRpm = 0;
        setChanged();
        sendData();
    }

    /**
     * 憋住救急：静止踩油门下从高档直接瞬跳 1 档，防止卡在 5 档起不来。
     */
    private void tryStallRescue(SubLevel sl, double currentSpeed) {
        if (!autoShiftEnabled || currentGear <= 1 || !hasAnyThrottleInput(sl)) return;
        if (currentSpeed >= 0.5) return;

        IACP.LOGGER.info("[Cockpit] 憋住救急: 瞬跳 1 档 (gear={})", currentGear);
        this.currentGear = 1;
        this.isShifting = false;
        this.shiftingTimer = 0;
        this.targetShiftGear = 0;
        this.revMatchTargetRpm = 0;
        setChanged();
        sendData();
    }

    /**
     * 自动降档：当前速度 < 低一档在当前 RPM 下的理想速度时降档。
     * 转向时不降档，刚换挡后 40 tick 内不降。
     */
    private void tryAutoDownshift(SubLevel sl, WheelScanResult wheels) {
        if (!autoShiftEnabled || isShifting || currentGear < 2
                || this.throttleLevel <= 0.3 || !hasAnyThrottleInput(sl)
                || hasAnySteeringInput(sl)) return;

        int gameTime = this.level == null ? 0 : (int) this.level.getGameTime();
        if (gameTime - this.lastShiftTick <= 40) return;

        double currentSpeed = Math.abs(wheels.avgWheelRpm()) * Math.PI * 2.0 / 60.0 * 0.5;
        double prevIdealRpm = TransmissionModel.computeTargetWheelRpm(
                currentGear - 1, this.engineRpm);
        double prevIdealSpeed = Math.abs(prevIdealRpm) * Math.PI * 2.0 / 60.0 * 0.5;

        if (currentSpeed < prevIdealSpeed && currentSpeed > 0.5) {
            IACP.LOGGER.info("[Cockpit] 自动降档: {}→{} (speed {} < ideal({}) {})",
                    currentGear, currentGear - 1,
                    String.format("%.1f", currentSpeed),
                    currentGear - 1,
                    String.format("%.1f", prevIdealSpeed));
            gearDown();
            this.lastShiftTick = gameTime;
        }
    }

    /**
     * 自动升档：每 10 tick 检查加速度和速度， 条件满足时升档。刚降档后 30 tick 内不升。
     */
    private void tryAutoUpshift(SubLevel sl) {
        if (!autoShiftEnabled || isShifting || currentGear < 1
                || currentGear >= PowertrainConstants.NUM_FORWARD_GEARS) {
            this.upshiftTimer = 0;
            return;
        }

        int gameTime = this.level == null ? 0 : (int) this.level.getGameTime();
        if (gameTime - this.lastShiftTick <= 30) return;
        if (++this.upshiftTimer < 10) return;

        this.upshiftTimer = 0;
        double nowSpeed = 0;
        try {
            org.joml.Vector3d vel = dev.ryanhcode.sable.Sable.HELPER.getVelocity(
                    level, new org.joml.Vector3d(
                            this.worldPosition.getX() + 0.5,
                            this.worldPosition.getY() + 0.5,
                            this.worldPosition.getZ() + 0.5));
            if (vel != null) nowSpeed = vel.length();
        } catch (Exception ignored) {}

        double accel = Math.abs(nowSpeed - this.lastUpshiftSpeed) / 0.5;
        this.lastUpshiftSpeed = nowSpeed;

        double prevTargetRpm = TransmissionModel.computeTargetWheelRpm(
                currentGear - 1, this.engineRpm);
        double prevIdealSpeed = Math.abs(prevTargetRpm) * Math.PI * 2.0 / 60.0 * 0.5;

        if (accel < 1.0 && nowSpeed > prevIdealSpeed && nowSpeed > 0.5) {
            IACP.LOGGER.info("[Cockpit] 自动升档: {}→{} (accel={}, speed={})",
                    currentGear, currentGear + 1,
                    String.format("%.2f", accel),
                    String.format("%.1f", nowSpeed));
            gearUp();
            this.lastShiftTick = gameTime;
        }
    }

    /**
     * @return 当前加速度绝对值（m/s²），由状态同步段每 2 tick 更新
     */
    private double getCurrentAccel() {
        return this.currentAccelMs2;
    }

    // ====================================================================
    //  NBT 持久化 & 同步
    // ====================================================================
    private static final String TAG_GEAR = "CurrentGear";
    private static final String TAG_RPM = "EngineRpm";
    /**
     * 油门深度写入 NBT 供客户端同步（覆盖层显示需要）。 onLoad() 强制归零，保证重登后不会油门残留。
     */
    private static final String TAG_THROTTLE_LEVEL = "ThrottleLevel";
    private static final String TAG_EFFECTIVE_TORQUE = "EffectiveTorque";
    private static final String TAG_SMART_MAPPING = "SmartMappingActive";
    private static final String TAG_AUTO_SHIFT = "AutoShiftEnabled";
    private static final String TAG_SKILL_ID = "ActiveSkillId";
    private static final String TAG_STALLED = "Stalled";

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_GEAR, this.currentGear);
        tag.putDouble(TAG_RPM, this.engineRpm);
        tag.putDouble(TAG_THROTTLE_LEVEL, this.throttleLevel);
        tag.putDouble(TAG_EFFECTIVE_TORQUE, this.effectiveTorque);
        tag.putBoolean(TAG_SMART_MAPPING, this.smartMappingActive);
        tag.putBoolean(TAG_AUTO_SHIFT, this.autoShiftEnabled);
        tag.putString(TAG_SKILL_ID, this.activeSkillId);
        tag.putBoolean(TAG_STALLED, this.stalled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_GEAR)) {
            this.currentGear = tag.getInt(TAG_GEAR);
        }
        if (tag.contains(TAG_RPM)) {
            this.engineRpm = tag.getDouble(TAG_RPM);
        }
        // 读 throttleLevel 用于客户端同步（覆盖层显示），
        // 但 onLoad() 会在 world load 时强制归零。
        if (tag.contains(TAG_THROTTLE_LEVEL)) {
            this.throttleLevel = tag.getDouble(TAG_THROTTLE_LEVEL);
        }
        if (tag.contains(TAG_EFFECTIVE_TORQUE)) {
            this.effectiveTorque = tag.getDouble(TAG_EFFECTIVE_TORQUE);
        }
        if (tag.contains(TAG_SMART_MAPPING)) {
            this.smartMappingActive = tag.getBoolean(TAG_SMART_MAPPING);
        }
        if (tag.contains(TAG_AUTO_SHIFT)) {
            this.autoShiftEnabled = tag.getBoolean(TAG_AUTO_SHIFT);
        }
        if (tag.contains(TAG_SKILL_ID)) {
            this.activeSkillId = tag.getString(TAG_SKILL_ID);
        }
        if (tag.contains(TAG_STALLED)) {
            this.stalled = tag.getBoolean(TAG_STALLED);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 向客户端发送方块更新包（等同于 SmartBlockEntity.sendData()）。
     */
    private void sendData() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
