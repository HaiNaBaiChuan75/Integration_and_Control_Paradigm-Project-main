package com.hainabaichuan75.iac_p.block.shotgun;

import com.hainabaichuan75.iac_p.affiliation.ComponentRole;
import com.hainabaichuan75.iac_p.ecs.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.index.ModBlockEntityTypes;
import com.hainabaichuan75.iac_p.part.AimingMount;
import com.hainabaichuan75.iac_p.part.field.YawPitch;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * ShotGunBlockEntity —— 霰弹枪炮塔 BlockEntity。
 * <p>
 * 实现了 GeckoLib 的 GeoBlockEntity 接口，使用动画系统控制 yaw/pitch 骨骼旋转。
 * 实现 GeckoLib 骨骼旋转的炮塔 BlockEntity，
 * 并注册为 {@link ComponentRole#SHOTGUN_BASE} 以接入武器系统。
 * <p>
 * 骨骼层级：
 * <pre>
 * base (根)
 *   └── yaw (绕 Y 轴旋转，偏航)
 *       └── yaw_ani (动画承载)
 *           └── pitch (绕 X 轴旋转，俯仰)
 *               └── pitch_ani (动画承载)
 *                   └── 炮管几何体
 * </pre>
 */
public class ShotGunBlockEntity extends PartBlockEntity implements GeoBlockEntity, AimingMount {

    // ==================================================================
    //  朝向映射：facingIndex → 四元数
    // ==================================================================
    private static final Int2ObjectMap<Quaterniondc> ORIENTATIONS = Util.make(new Int2ObjectOpenHashMap<>(4), map -> {
        for (int i = 0; i < 4; i++) {
            map.put(i, new Quaterniond().rotateY(Math.toRadians(-i * 90)));
        }
    });

    // ==================================================================
    //  动画定义
    // ==================================================================
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation FIRING_ANIM = RawAnimation.begin().then("firing", Animation.LoopType.PLAY_ONCE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ==================================================================
    //  运行时状态
    // ==================================================================
    /** 朝向索引 0-3（0=南, 1=西, 2=北, 3=东） */
    private byte facingIndex;

    /** 当前偏航角度（度） */
    private double yawDeg = 0;
    /**
     * 上次偏航变化量（差值），用于 partialTick 插值。无变化时 = 0，渲染直接返回 yawDeg
     */
    private double yawDegDelta = 0;
    /** 当前俯仰角度（度），正 = 上仰 */
    private double pitchDeg = 0;
    /**
     * 上次俯仰变化量（差值），用于 partialTick 插值
     */
    private double pitchDegDelta = 0;

    /** 开火动画触发冷却（tick） */
    private int fireAnimCooldown = 0;

    // ==================================================================
    public ShotGunBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SHOTGUN_TURRET.get(), pos, state);
    }

    // ==================================================================
    //  朝向
    // ==================================================================
    public void setFacingIndex(int index) {
        this.facingIndex = (byte) (index & 3);
        setChanged();
    }

    public int getFacingIndex() {
        return facingIndex;
    }

    /** 返回朝向对应的四元数（绕 Y 轴旋转） */
    @Override
    public @NotNull Quaterniondc orientation() {
        return ORIENTATIONS.get(facingIndex);
    }

    // ==================================================================
    //  角度驱动（由 AimController / Network 调用）
    // ==================================================================
    /**
     * 立即驱动炮塔到指定角度。
     *
     * @param yawDeg   目标偏航角（度，载具局部空间）
     * @param pitchDeg 目标俯仰角（度，正=上仰）
     */
    public void driveImmediate(float yawDeg, float pitchDeg) {
        this.yawDegDelta = yawDeg - this.yawDeg;
        this.pitchDegDelta = pitchDeg - this.pitchDeg;
        this.yawDeg = yawDeg;
        this.pitchDeg = pitchDeg;

        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ==================================================================
    //  渲染插值接口（供 Renderer 调用）
    // ==================================================================
    /** 获取插值后的偏航角（度） */
    public float getRenderYaw(float partialTick) {
        return (float) (yawDeg - yawDegDelta * (1 - partialTick));
    }

    /** 获取插值后的俯仰角（度） */
    public float getRenderPitch(float partialTick) {
        return (float) (pitchDeg - pitchDegDelta * (1 - partialTick));
    }

    // ==================================================================
    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    // ==================================================================
    //  Tick
    // ==================================================================
    public void tick() {
        if (level == null) return;

        // 开火动画冷却递减
        if (fireAnimCooldown > 0) {
            fireAnimCooldown--;
        }

        // 服务端：定期触发开火动画（仅用于测试，正式由 WeaponOverlay 触发）
        if (!level.isClientSide() && level.getGameTime() % 20 == 0) {
            triggerFiringAnim();
        }
    }

    // ==================================================================
    //  AimingMount 实现
    // ==================================================================
    @Override
    public void setAngles(YawPitch angles) {
        driveImmediate((float) angles.yaw(), (float) angles.pitch());
    }

    @Override
    public YawPitch getAngles() {
        return new YawPitch(yawDeg, pitchDeg);
    }

    // ==================================================================
    /** 触发开火动画（由 WeaponOverlay/Network 调用） */
    public void triggerFiringAnim() {
        if (fireAnimCooldown <= 0) {
            triggerAnim("controller", "firing");
            fireAnimCooldown = 10; // 防止频繁触发
        }
    }

    // ==================================================================
    //  GeckoLib 动画注册
    // ==================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, animState ->
                animState.setAndContinue(IDLE_ANIM)
        ).triggerableAnim("firing", FIRING_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ==================================================================
    //  网络同步
    // ==================================================================
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putByte("facing", facingIndex);
        tag.putDouble("yaw", yawDeg);
        tag.putDouble("pitch", pitchDeg);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ==================================================================
    //  NBT 持久化
    // ==================================================================
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByte("facing", facingIndex);
        tag.putDouble("yaw", yawDeg);
        tag.putDouble("pitch", pitchDeg);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 在新值写入前计算变化量（delta），用于渲染插值
        double newYaw = tag.getDouble("yaw");
        double newPitch = tag.getDouble("pitch");
        this.yawDegDelta = newYaw - this.yawDeg;
        this.pitchDegDelta = newPitch - this.pitchDeg;
        this.facingIndex = tag.getByte("facing");
        this.yawDeg = newYaw;
        this.pitchDeg = newPitch;
    }
}
