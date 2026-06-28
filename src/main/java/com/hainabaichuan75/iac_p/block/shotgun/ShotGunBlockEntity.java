package com.hainabaichuan75.iac_p.block.shotgun;

import com.hainabaichuan75.iac_p.registry.IACPBlockEntities;
import com.hainabaichuan75.iac_p.vehicle.Aimable;
import com.hainabaichuan75.iac_p.vehicle.VehiclePartBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
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
 * <p>yaw/pitch 的实时骨骼控制由 {@link ShotGunBlockRenderer#renderRecursively} 完成。
 */
public class ShotGunBlockEntity extends VehiclePartBlockEntity implements Aimable, GeoBlockEntity {

    /**
     * 旋转速度（度/秒）
     */
    private static final double ROTATION_SPEED_DEG_PER_SEC = 180.0;

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

    /**
     * 设定瞄准目标点（绝对世界坐标）。
     * 不会立即改变朝向，而是由 {@link #sable$tick} 每 tick 平滑转向该点。
     */
    @Override
    public void aimAt(Vector3dc targetAbsPoint) {
        this.targetAbsPoint = targetAbsPoint;
    }

    /* ==================== Sable tick（服务端每 tick 调用） ==================== */

    @Override
    public void requestModelDataUpdate() {
        super.requestModelDataUpdate();
    }

    /**
     * 每 tick 将炮塔向瞄准点方向旋转，角速度由 {@link #ROTATION_SPEED_DEG_PER_SEC} 决定。
     * 到达目标角度附近时停止增量，避免震荡。
     */
    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        prevPitch = pitch;
        prevYaw = yaw;

        if (level != null && level.getGameTime() % 20 == 0) {
            triggerAnim("base", "firing");
        }

        if (targetAbsPoint == null) return;

        double originX = getAbsPosition(subLevel).x();
        double originY = getAbsPosition(subLevel).y();
        double originZ = getAbsPosition(subLevel).z();

        double dx = targetAbsPoint.x() - originX;
        double dy = targetAbsPoint.y() - originY;
        double dz = targetAbsPoint.z() - originZ;

        double horizontalDistSq = dx * dx + dz * dz;
        if (horizontalDistSq < 1.0e-5) {
            return; // 目标就在炮塔正上方/下方，忽略
        }

        // 期望角度
        double desiredYaw = Math.toDegrees(Math.atan2(dx, dz));
        double horizontalDist = Math.sqrt(horizontalDistSq);
        double desiredPitch = Math.toDegrees(Math.atan2(dy, horizontalDist));
        desiredPitch = Math.clamp(desiredPitch, -45, 45);

        double maxStep = ROTATION_SPEED_DEG_PER_SEC / 20.0; // 每 tick 最大步长

        // —— 平滑旋转 yaw（处理 360° 环绕） ——
        double yawDiff = desiredYaw - this.yaw;
        yawDiff = ((yawDiff % 360) + 540) % 360 - 180; // 归一化到 (-180, 180]

        if (Math.abs(yawDiff) <= maxStep) {
            this.yaw = desiredYaw;
        } else {
            this.yaw += Math.signum(yawDiff) * maxStep;
        }

        // 保持 yaw 在 (-180, 180]
        if (this.yaw > 180) this.yaw -= 360;
        if (this.yaw <= -180) this.yaw += 360;

        // —— 平滑旋转 pitch（无需环绕） ——
        double pitchDiff = desiredPitch - this.pitch;
        if (Math.abs(pitchDiff) <= maxStep) {
            this.pitch = desiredPitch;
        } else {
            this.pitch += Math.signum(pitchDiff) * maxStep;
        }

        // 同步到客户端
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /* ==================== GeoAnimatable ==================== */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 0, state -> {
            // 默认循环 idle
            return state.setAndContinue(RawAnimation.begin().then("idle", Animation.LoopType.LOOP));
        })
                // 注册可触发的开火动画（PLAY_ONCE = 播放一次后自动回到 idle）
                .triggerableAnim("firing", RawAnimation.begin().then("firing", Animation.LoopType.PLAY_ONCE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }



    /* ==================== NBT 持久化 ==================== */

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("yaw", this.yaw);
        tag.putDouble("pitch", this.pitch);
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.yaw = tag.getDouble("yaw");
        this.pitch = tag.getDouble("pitch");
    }

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
        tag.putDouble("yaw", this.yaw);
        tag.putDouble("pitch", this.pitch);
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider lookupProvider) {
        yaw = tag.getDouble("yaw");
        pitch = tag.getDouble("pitch");
    }
}
