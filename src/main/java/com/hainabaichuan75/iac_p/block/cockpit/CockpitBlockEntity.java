package com.hainabaichuan75.iac_p.block.cockpit;

import com.hainabaichuan75.iac_p.ecs.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.index.ModCockpitBlockEntityTypes;
import com.hainabaichuan75.iac_p.part.Controller;
import com.hainabaichuan75.iac_p.part.EnginePart;
import com.hainabaichuan75.iac_p.part.PlayerCommandReceiver;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 驾驶舱方块实体 —— ECS 载具控制中枢。
 * <p>
 * 不再管理变速箱/动态引擎逻辑。职责缩减为：
 * <ul>
 *   <li>作为 {@link Controller} 提供驾驶意图输入（由网络包写入 rawThrottleDirection）</li>
 *   <li>作为 {@link EnginePart} 提供引擎规格参数（最大扭矩/转速）</li>
 *   <li>{@link com.hainabaichuan75.iac_p.system.TorqueDistributionSystem} 从 Controller 读取油门、
 *       从 EnginePart 读取最大扭矩，计算后写入各 DriveWheel</li>
 * </ul>
 *
 * @see Controller
 * @see EnginePart
 * @see com.hainabaichuan75.iac_p.system.TorqueDistributionSystem
 */
public class CockpitBlockEntity extends PartBlockEntity implements EnginePart, Controller, PlayerCommandReceiver {

    // ====================================================================
    //  引擎规格常量
    // ====================================================================
    /**
     * 最大扭矩（Nm），供 TorqueDistributionSystem 计算轮端扭矩
     */
    public static final double MAX_TORQUE = 5.0;

    /**
     * 最大转速（RPM），引擎铭牌参数
     */
    public static final double MAX_RPM = 6000.0;

    // ====================================================================
    //  朝向（PartBlockEntity 覆写）
    // ====================================================================
    private static final Quaterniond ORIENT_NORTH = new Quaterniond();
    private static final Quaterniond ORIENT_SOUTH = new Quaterniond().rotateY(Math.PI);
    private static final Quaterniond ORIENT_EAST = new Quaterniond().rotateY(-Math.PI / 2);
    private static final Quaterniond ORIENT_WEST = new Quaterniond().rotateY(Math.PI / 2);

    @Override
    public @NotNull Quaterniondc orientation() {
        BlockState state = getBlockState();
        if (state.hasProperty(CockpitBlock.FACING)) {
            return switch (state.getValue(CockpitBlock.FACING)) {
                case SOUTH -> ORIENT_SOUTH;
                case EAST -> ORIENT_EAST;
                case WEST -> ORIENT_WEST;
                default -> ORIENT_NORTH;
            };
        }
        return ORIENT_NORTH;
    }

    // ====================================================================
    //  运行时状态
    // ====================================================================
    /** 原始油门方向：+1=前(W), -1=后(S), 0=无，由 VehicleControlC2SPacket 设置 */
    private int rawThrottleDirection = 0;

    // ── PlayerCommandReceiver 输入字段 ──
    /**
     * 移动意图向量：z=前后(±1), x=左右(±1)
     */
    @NotNull
    private final Vector3d inputMovement = new Vector3d();
    /**
     * 刹车输入
     */
    private boolean inputBrake = false;
    /**
     * 开火输入
     */
    private boolean inputFiring = false;
    /**
     * 瞄准目标世界坐标
     */
    @Nullable
    private Vector3d inputAimTarget = null;

    /** 当前扭矩（Nm），由 TorqueDistributionSystem 写入 */
    private double torque = 0;

    /**
     * 缓存总质量（kg），仅用于覆盖层显示
     */
    private double totalMass = 1000.0;

    // ====================================================================
    //  构造
    // ====================================================================
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

    @Override
    public void onLoad() {
        super.onLoad();
        this.rawThrottleDirection = 0;
    }

    // ====================================================================
    //  EnginePart 实现
    // ====================================================================
    @Override
    public double getTorque() {
        return torque;
    }

    @Override
    public void setTorque(double torque) {
        this.torque = torque;
    }

    @Override
    public double getMaxTorque() {
        return MAX_TORQUE;
    }

    @Override
    public double getMaxRpm() {
        return MAX_RPM;
    }

    // ====================================================================
    //  Controller 实现
    // ====================================================================

    /**
     * 驾驶意图向量。
     * <p>
     * z = 前后（z- = 前, z+ = 后），
     * x = 左右（x+ = 右, x- = 左），
     * 由 TorqueDistributionSystem / SteeringSystem 解释。
     */
    @Override
    public @NotNull Vector3dc getMovementIntent() {
        return inputMovement;
    }

    @Override
    public boolean isBraking() {
        return inputBrake;
    }

    @Override
    public boolean isFiring() {
        return inputFiring;
    }

    @Override
    public @Nullable Vector3dc getAimTarget() {
        return inputAimTarget;
    }

    // ====================================================================
    //  PlayerCommandReceiver 实现
    // ====================================================================
    @Override
    public void setMovementIntent(@NotNull Vector3dc intent) {
        this.inputMovement.set(intent);
    }

    @Override
    public void setBrake(boolean brake) {
        this.inputBrake = brake;
    }

    @Override
    public void setFiring(boolean firing) {
        this.inputFiring = firing;
    }

    @Override
    public void setAimTarget(@Nullable Vector3dc worldPos) {
        this.inputAimTarget = worldPos != null ? new Vector3d(worldPos) : null;
    }

    // ====================================================================
    //  控制输入接口
    // ====================================================================
    /**
     * 设置原始油门方向。由 VehicleControlC2SPacket 每 tick 调用。
     */
    public void setRawThrottleDirection(int dir) {
        this.rawThrottleDirection = dir;
    }

    // ====================================================================
    //  每 tick 更新
    // ====================================================================
    public void tick() {
        if (level == null) return;

        // ── 延迟注册重试（SubLevel 未就绪时排队注册） ──
        com.hainabaichuan75.iac_p.affiliation.DeferredRegistration.tick(this);

        SubLevel sl = Sable.HELPER.getContaining(this);
        if (sl == null) return;

        // ── 读取物理质量（仅服务端，仅用于覆盖层显示）──
        if (sl instanceof ServerSubLevel ssl) {
            try {
                this.totalMass = ssl.getMassTracker().getMass();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * @return 载具当前总质量（kg），仅供覆盖层显示
     */
    public double getTotalMass() {
        return totalMass;
    }

    // ====================================================================
    //  NBT 持久化
    // ====================================================================
    private static final String TAG_TORQUE = "Torque";

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(TAG_TORQUE, this.torque);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_TORQUE)) {
            this.torque = tag.getDouble(TAG_TORQUE);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ====================================================================
    //  ⚠ 旧兼容方法（逐步废弃）
    // ====================================================================

    /**
     * @deprecated 旧架构扭矩接口。ECS 迁移后轮端扭矩由 TorqueDistributionSystem → DriveWheel 管理。
     * 保留临时兼容，后续 SuspensionTestBlockEntity 接入 DriveWheel 后可删除。
     */
    @Deprecated
    public double getTorquePerWheel() {
        return 0;
    }

    /**
     * @deprecated 旧架构轮速接口。ECS 迁移后不再由座舱计算目标轮速。
     */
    @Deprecated
    public double getTargetWheelRpm() {
        return 0;
    }

    /**
     * @deprecated 旧架构方向符号。ECS 迁移后通过 Controller.getMovementIntent() 获取方向。
     */
    @Deprecated
    public double getDirectionSign() {
        return rawThrottleDirection;
    }

    /**
     * @deprecated 熄火状态已移除。引擎始终可用。
     */
    @Deprecated
    public boolean isStalled() {
        return false;
    }
}
