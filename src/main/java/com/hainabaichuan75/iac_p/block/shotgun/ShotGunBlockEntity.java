package com.hainabaichuan75.iac_p.block.shotgun;

import com.hainabaichuan75.iac_p.core.vehicle.PartRenderer;
import com.hainabaichuan75.iac_p.core.vehicle.VehiclePartBlockEntity;
import com.hainabaichuan75.iac_p.registry.IACPBlockEntities;
import com.hainabaichuan75.iac_p.test_system.Aimable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 霰弹枪炮塔方块实体。
 * <ul>
 *   <li>继承 {@link VehiclePartBlockEntity} 接入载具子系统</li>
 *   <li>实现 {@link Aimable} 接收外部瞄准指令</li>
 *   <li>实现 {@link GeoAnimatable} 接入 GeckoLib 渲染管线</li>
 *   <li>在 {@link #sable$tick} 中以固定角速度平滑转向瞄准点</li>
 * </ul>
 *
 * <p>yaw/pitch 的实时骨骼控制由 {@link PartRenderer} 完成。
 */
public class ShotGunBlockEntity extends VehiclePartBlockEntity implements Aimable, GeoBlockEntity {

    private static final Int2ObjectMap<Quaterniondc> ORIENTATIONS = Util.make(new Int2ObjectOpenHashMap<>(4), map -> {
        for (int i = 0; i < 4; i++) {
            map.put(i, new Quaterniond().rotateY(Math.toRadians(-i * 90)));
        }
    });

    /**
     * 旋转速度（度/秒）
     */
    private static final double ROTATION_SPEED_DEG_PER_SEC = 180.0;

    /* ==================== 朝向 ==================== */

    private byte facingIndex;   // 0-3, 对应 ORIENTATIONS 中的键

    public void setFacingIndex(int index) {
        this.facingIndex = (byte) (index & 3);
        setChanged();
    }

    @Override
    public Quaterniondc orientation() {
        return ORIENTATIONS.get(facingIndex);
    }

    /* ==================== 状态 ==================== */

    /**
     * 目标瞄准点（绝对世界坐标），{@code null} 表示不进行瞄准
     */
    @Nullable
    public Vector3dc targetAbsPoint;

    /**
     * 当前水平旋转角度（度），0 = 模型默认方向
     */
    public double yaw;
    public double prevYaw;
    public double pitch;
    public double prevPitch;

    // ==================== GeckoLib 动画缓存 ====================

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ShotGunBlockEntity(BlockPos pos, BlockState state) {
        super(IACPBlockEntities.SHOT_GUN.get(), pos, state);
    }

    /* ==================== Aimable ==================== */

    @Override
    public void aimAt(Vector3dc targetAbsPoint) {
        this.targetAbsPoint = targetAbsPoint;
    }

    /* ==================== Sable tick ==================== */

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        prevPitch = pitch;
        prevYaw = yaw;

        if (level != null && level.getGameTime() % 20 == 0) {
            triggerAnim("base", "firing");
        }

        if (targetAbsPoint == null) return;

        double originX = getAnchor().x();
        double originY = getAnchor().y();
        double originZ = getAnchor().z();

        double dx = targetAbsPoint.x() - originX;
        double dy = targetAbsPoint.y() - originY;
        double dz = targetAbsPoint.z() - originZ;

        double horizontalDistSq = dx * dx + dz * dz;
        if (horizontalDistSq < 1.0e-5) {
            return;
        }

        double desiredYaw = Math.toDegrees(Math.atan2(dx, dz));
        double horizontalDist = Math.sqrt(horizontalDistSq);
        double desiredPitch = Math.toDegrees(Math.atan2(dy, horizontalDist));
        desiredPitch = Math.clamp(desiredPitch, -45, 45);

        double maxStep = ROTATION_SPEED_DEG_PER_SEC / 20.0;

        double yawDiff = desiredYaw - this.yaw;
        yawDiff = ((yawDiff % 360) + 540) % 360 - 180;

        if (Math.abs(yawDiff) <= maxStep) {
            this.yaw = desiredYaw;
        } else {
            this.yaw += Math.signum(yawDiff) * maxStep;
        }

        if (this.yaw > 180) this.yaw -= 360;
        if (this.yaw <= -180) this.yaw += 360;

        double pitchDiff = desiredPitch - this.pitch;
        if (Math.abs(pitchDiff) <= maxStep) {
            this.pitch = desiredPitch;
        } else {
            this.pitch += Math.signum(pitchDiff) * maxStep;
        }

        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /* ==================== GeoAnimatable ==================== */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 0,
                state -> state.setAndContinue(RawAnimation.begin().then("idle", Animation.LoopType.LOOP))).triggerableAnim("firing", RawAnimation.begin().then("firing", Animation.LoopType.PLAY_ONCE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /* ==================== NBT 持久化 ==================== */

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByte("facingIndex", facingIndex);
        tag.putDouble("yaw", this.yaw);
        tag.putDouble("pitch", this.pitch);
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.facingIndex = tag.getByte("facingIndex");
        this.yaw = tag.getDouble("yaw");
        this.pitch = tag.getDouble("pitch");
    }

    /* ==================== 网络同步 ==================== */

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.@NotNull Provider lookupProvider) {
        handleUpdateTag(pkt.getTag(), lookupProvider);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putByte("facingIndex", facingIndex);
        tag.putDouble("yaw", this.yaw);
        tag.putDouble("pitch", this.pitch);
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
        this.facingIndex = tag.getByte("facingIndex");
        yaw = tag.getDouble("yaw");
        pitch = tag.getDouble("pitch");
    }
}
