package com.hainabaichuan75.iac_p.content.blocks.turret;

import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.index.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * TurretTestBlockEntity —— 炮塔测试 BlockEntity。
 * <p>
 * 实现了 GeckoLib 的 GeoBlockEntity 接口，使用动画系统控制 yaw/pitch 骨骼旋转。
 * <p>
 * <b>设计</b>：
 * <ul>
 * <li>动画文件使用 {@code "vector": [0, 0, 0]} 格式定义 yaw/pitch 骨骼旋转，</li>
 * <li>渲染器在 {@code renderRecursively} 中拦截骨骼渲染，
 *     在 {@code handleAnimations} 之后注入动态旋转值，</li>
 * <li>BE 存储目标角度并支持 partialTick 插值，实现平滑旋转。</li>
 * </ul>
 * <p>
 * <b>测试交互</b>：
 * <ul>
 * <li>自动旋转模式（默认关闭）：yaw 每 tick +2°</li>
 * <li>右键切换自动旋转（通过 BlockState 属性自动同步到客户端），潜行+右键重置</li>
 * </ul>
 */
public class TurretTestBlockEntity extends BlockEntity implements GeoBlockEntity {

    // 动画定义（仅用于激活 GeckoLib 动画系统，实际旋转由渲染器覆盖）
    private static final RawAnimation MAIN_ANIM = RawAnimation.begin().thenLoop("main");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ==================================================================
    //  状态：偏航/俯仰角度（度）
    // ==================================================================
    /** 当前帧的偏航角度（度） */
    private double yawDeg = 0;
    /** 当前帧的俯仰角度（度），正 = 上抬 */
    private double pitchDeg = 0;

    /** 上一帧的偏航角度（用于 partialTick 插值） */
    private double lastYawDeg = 0;
    /** 上一帧的俯仰角度（用于 partialTick 插值） */
    private double lastPitchDeg = 0;

    /** 自动旋转速度（度/游戏刻） */
    private static final double AUTO_ROTATE_SPEED = 2.0;

    // ==================================================================
    //  ClearanceSolver 缓存
    // ==================================================================
    /** 间隙求解器（惰性初始化） */
    @Nullable
    private TurretClearanceSolver clearanceSolver;

    /** 缓存的求解结果 */
    @Nullable
    private TurretClearanceSolver.ClearanceResult clearanceCache;

    /** 缓存重算冷却 */
    private int clearanceRecalcCooldown = 0;

    /** 缓存重算间隔（tick） */
    private static final int CLEARANCE_RECALC_INTERVAL = 10;

    /**
     * 创建适用于 TurretTest 模型的默认 ClearanceSolver。
     * <p>
     * footprint 定义：
     * <ul>
     *   <li>(0,0,0) — 方块本体（base）</li>
     *   <li>(0,1,0) — yaw 骨骼区域（方向机）</li>
     *   <li>(0,2,0) — pitch 骨骼枢轴区域（高低机）</li>
     *   <li>(0,2,1), (0,2,2) — 炮管向前延伸</li>
     * </ul>
     * 俯仰枢轴在 (0,2,0)，对应模型 pitch bone pivot 的近似 BlockPos。
     */
    private static TurretClearanceSolver createClearanceSolver() {
        List<BlockPos> footprint = List.of(
                new BlockPos(0, 0, 0),   // base
                new BlockPos(0, 1, 0),   // yaw 骨骼
                new BlockPos(0, 2, 0),   // pitch 枢轴
                new BlockPos(0, 2, 1),   // 炮管延伸
                new BlockPos(0, 2, 2)    // 炮管尖端
        );
        return new TurretClearanceSolver(footprint, new BlockPos(0, 2, 0));
    }

    /**
     * 将目标角度通过 ClearanceSolver 限位后返回。
     * <p>
     * <b>注意</b>：当前 ClearanceSolver 在 SubLevel 中扫描性能开销过大（每 10 tick 扫描
     * ~2000 方块），导致服务端卡顿。暂时跳过限位，仅做直通。
     *
     * @param targetYaw   期望偏航角（度）
     * @param targetPitch 期望俯仰角（度）
     * @return [clampedYaw, clampedPitch]
     */
    private float[] clearanceClamp(float targetYaw, float targetPitch) {
        // 🔧 调试期间跳过 ClearanceSolver（避免 SubLevel 扫描性能问题）
        return new float[]{targetYaw, targetPitch};
    }

    public TurretTestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.TURRET_TEST.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    /**
     * 立即驱动炮塔到指定角度（由 AimController 调用）。
     * <p>
     * 相比旧系统的 {@code driveImmediate(yaw, pitch)} 驱动约束马达，
     * 此处直接设置 yaw/pitch 角度，由 GeckoLib 渲染器插值实现视觉旋转。
     * <p>
     * <b>限位</b>：通过 ClearanceSolver 将目标角度限制在无碰撞范围内。
     *
     * @param yawDeg 目标偏航角（度，载具局部空间）
     * @param pitchDeg 目标俯仰角（度，正=上仰）
     */
    public void driveImmediate(float yawDeg, float pitchDeg) {
        // ClearanceSolver 限位
        float[] clamped = clearanceClamp(yawDeg, pitchDeg);

        // 保存旧值用于插值
        this.lastYawDeg = this.yawDeg;
        this.lastPitchDeg = this.pitchDeg;
        this.yawDeg = clamped[0];
        this.pitchDeg = clamped[1];

        // 标记数据变更，触发服务端→客户端同步
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ==================================================================
    //  Tick：自动旋转（双端运行，通过 BlockState 同步开关状态）
    // ==================================================================
    public void tick() {
        if (level == null) {
            return;
        }
        // 从 BlockState 读取自动旋转状态（服务端修改后自动同步到客户端）
        if (level.getBlockState(worldPosition).getValue(TurretTestBlock.AUTO_ROTATE)) {
            setYaw(yawDeg + AUTO_ROTATE_SPEED);
        }
    }

    // ==================================================================
    //  GeckoLib 动画注册
    // ==================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, animState -> {
            return animState.setAndContinue(MAIN_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ==================================================================
    //  旋转控制
    // ==================================================================

    /**
     * 设置绝对偏航角度（度）。
     * 保留 lastYawDeg 用于渲染插值。
     */
    public void setYaw(double degrees) {
        this.lastYawDeg = this.yawDeg;
        this.yawDeg = degrees;
    }

    /**
     * 设置绝对俯仰角度（度）。正 = 上抬。
     */
    public void setPitch(double degrees) {
        this.lastPitchDeg = this.pitchDeg;
        this.pitchDeg = degrees;
    }

    /**
     * 同时设置偏航和俯仰。
     */
    public void setAngles(double yawDeg, double pitchDeg) {
        setYaw(yawDeg);
        setPitch(pitchDeg);
    }

    /**
     * 重置角度为 0。
     */
    public void resetAngles() {
        setAngles(0, 0);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * 获取当前目标偏航角度（度）。
     */
    public double getYaw() {
        return yawDeg;
    }

    /**
     * 获取当前目标俯仰角度（度）。
     */
    public double getPitch() {
        return pitchDeg;
    }

    /**
     * 获取插值后的渲染偏航角度（度）。
     *
     * @param partialTick 当前渲染帧相对于上一 tick 的进度 [0, 1)
     */
    public float getRenderYaw(float partialTick) {
        return (float) (lastYawDeg + (yawDeg - lastYawDeg) * partialTick);
    }

    /**
     * 获取插值后的渲染俯仰角度（度）。
     */
    public float getRenderPitch(float partialTick) {
        return (float) (lastPitchDeg + (pitchDeg - lastPitchDeg) * partialTick);
    }

    /**
     * 获取当前目标偏航角度（不带插值）。用于 ClearanceSolver 等逻辑。
     */
    public double getTargetYaw() {
        return yawDeg;
    }

    /**
     * 获取当前目标俯仰角度（不带插值）。
     */
    public double getTargetPitch() {
        return pitchDeg;
    }

    // ==================================================================
    //  网络同步：服务端 → 客户端
    // ==================================================================
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putDouble("yaw", this.yawDeg);
        tag.putDouble("pitch", this.pitchDeg);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        if (tag.contains("yaw")) {
            this.lastYawDeg = this.yawDeg;
            this.yawDeg = tag.getDouble("yaw");
        }
        if (tag.contains("pitch")) {
            this.lastPitchDeg = this.pitchDeg;
            this.pitchDeg = tag.getDouble("pitch");
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        // 使用 getUpdateTag 构建同步数据包
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }
}
