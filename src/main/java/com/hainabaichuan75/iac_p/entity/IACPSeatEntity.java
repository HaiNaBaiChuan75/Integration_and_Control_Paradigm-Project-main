package com.hainabaichuan75.iac_p.entity;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlock;
import com.hainabaichuan75.iac_p.events.PlayerMountTracker;
import com.hainabaichuan75.iac_p.events.ServerMountHandler;
import com.hainabaichuan75.iac_p.index.ModEntities;
import com.hainabaichuan75.iac_p.network.ModNetworking;
import com.hainabaichuan75.iac_p.network.packets.MountedStateS2CPacket;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * IACP 座位实体 —— 完全复刻 Create {@code SeatEntity}，+ SubLevel 跟随与骑乘者姿态同步。
 * <p>
 * 功能清单：
 * <ul>
 *   <li>极小碰撞箱（0.25×0.35），noPhysics</li>
 *   <li>不可渲染、不可推动</li>
 *   <li>{@code positionRider()} 玩家坐在方块表面上方 1/16 格</li>
 *   <li>{@code tick()} 双端执行 SubLevel 位姿跟随（客户端避免抖动）</li>
 *   <li>{@code blockPosition()} 下方方块失效时 discard()</li>
 *   <li>被骑乘 + 方块存在 → 保持存活（Create 模式）</li>
 *   <li>NBT 持久化 homePos，支持跨存档恢复</li>
 *   <li>帧间偏航变化量传递给骑乘者，身体朝向跟随 SubLevel 旋转</li>
 * </ul>
 */
public class IACPSeatEntity extends Entity implements IEntityWithComplexSpawn {

    /** 座位对应的方块位置（SubLevel 局部坐标 = 原始世界位置） */
    @Nullable
    private BlockPos homePos;

    /**
     * 帧间偏航变化量（度/帧），供客户端事件读取用于本地玩家身体跟随。
     * 仅在上一次调用 {@link #followSubLevelPose} 时更新。
     */
    private float lastYawDelta = 0;

    /**
     * 骑乘者世界空间位置缓存。
     * <p>
     * 由 {@link #followSubLevelPose} 每 tick 通过 {@code Pose3d.transformPosition()}
     * 做全量位姿变换计算（含旋转），供 {@link #positionRider} 直接使用。
     * 无乘客时置 {@code null}。
     */
    @Nullable
    private Vec3 riderWorldPos = null;

    /**
     * 首次 tick 标记，跳过第一次偏航 delta 防止骑乘初始瞬间快照旋转。
     */
    private boolean firstTick = true;

    // ==================================================================
    //  载具控制注册（首个骑乘者→PlayerMountTracker 集成）
    // ==================================================================

    /**
     * 已注册载具控制的玩家 UUID。null = 未注册。
     * 仅第一个上车的玩家获得载具控制权（{@link PlayerMountTracker} 独享）。
     */
    @Nullable
    private UUID registeredPlayerUUID = null;

    /**
     * 已注册的 SubLevel UUID。
     */
    @Nullable
    private UUID registeredSubLevelUUID = null;

    // ==================================================================
    //  构造
    // ==================================================================

    public IACPSeatEntity(EntityType<? extends IACPSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * 在指定方块位置创建座位实体。
     * <p>
     * 外部通过 {@code sitDown()} 静态工厂调用。
     */
    public IACPSeatEntity(Level level, BlockPos homePos) {
        this(ModEntities.IACP_SEAT.get(), level);
        this.homePos = homePos;
        setPos(homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5);
        setBoundingBox(makeBoundingBox());
    }

    /**
     * 覆写 setPos 使碰撞箱居中于实体位置（Create {@code SeatEntity} 模式）。
     * <p>
     * 默认 {@code makeBoundingBox()} 将碰撞箱底部对齐于 entity position；
     * 此覆写对齐中心，使 tiny AABB (0.25×0.35) 的命中检测更加准确。
     */
    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        AABB bb = getBoundingBox();
        Vec3 diff = new Vec3(x, y, z).subtract(bb.getCenter());
        setBoundingBox(bb.move(diff));
    }

    // ==================================================================
    //  公开访问
    // ==================================================================

    /**
     * @return 座位对应的方块位置，用于 CameraMixin 等外部读取 SubLevel
     */
    @Nullable
    public BlockPos getHomePos() {
        return homePos;
    }

    /**
     * @return 上一帧的偏航变化量（度），客户端事件用于本地玩家身体跟随
     */
    public float getLastYawDelta() {
        return lastYawDelta;
    }

    // ==================================================================
    //  坐姿
    // ==================================================================

    @Override
    protected void positionRider(@NotNull Entity rider, @NotNull MoveFunction callback) {
        if (!this.hasPassenger(rider)) return;
        // 优先使用全量位姿变换后的骑乘者位置（含 SubLevel 旋转）
        if (this.riderWorldPos != null) {
            callback.accept(rider, riderWorldPos.x, riderWorldPos.y, riderWorldPos.z);
            return;
        }
        // fallback：原版世界 Y 偏移（位姿变换不可用时）
        double yOffset = getY() + 1.0 / 16.0;
        callback.accept(rider, getX(), yOffset, getZ());
    }

    @Override
    public void onPassengerTurned(@NotNull Entity entity) {
        entity.setYHeadRot(entity.getYRot());
    }

    @Override
    @NotNull
    public Vec3 getDismountLocationForPassenger(@NotNull LivingEntity rider) {
        return super.getDismountLocationForPassenger(rider).add(0, 0.5, 0);
    }

    // ==================================================================
    //  生命周期
    // ==================================================================

    @Override
    public void tick() {
        if (homePos == null) { riderWorldPos = null; discard(); return; }

        // 无乘客时清空缓存
        if (!isVehicle()) riderWorldPos = null;

        // 双端执行：跟随 SubLevel 位姿
        // 客户端上通过本地 SubLevel 状态更新位置，避免依赖服务端 20Hz 同步带来的视觉抖动
        SubLevel subLevel = Sable.HELPER.getContaining(level(),
                new Vector3d(homePos.getX() + 0.5, homePos.getY() + 0.5, homePos.getZ() + 0.5));
        if (subLevel != null) {
            followSubLevelPose(subLevel);
        }

        // ── 修复：在 super.tick() → baseTick() → positionRider() 之前调用
        //    followSubLevelPose 更新了 riderWorldPos，但 positionRider 会使用旧的缓存值。
        //    此处直接调用 positionRider 用新值重新定位骑乘者，消除一帧滞后 ──
        //    （super.tick 中 baseTick 也会调用 positionRider，但其使用旧值；
        //      我们在这里用新值覆盖，渲染时看到的就是最新位置）
        for (Entity passenger : getPassengers()) {
            positionRider(passenger, Entity::setPos);
        }
        super.tick();

        // ── 服务端：骑乘控制器注册 / 注销 ──
        if (!level().isClientSide) {
            // 获取首个骑乘者
            Entity firstPassenger = isVehicle() ? getPassengers().get(0) : null;

            if (firstPassenger instanceof ServerPlayer sp && registeredPlayerUUID == null) {
                // 玩家上车 → 尝试注册为载具控制器（首个有效）
                tryRegisterMountControl(sp);
            } else if (firstPassenger == null && registeredPlayerUUID != null) {
                // 玩家下车 → 注销载具控制器
                unregisterMountControl();
            } else if (firstPassenger instanceof ServerPlayer sp
                    && sp.getUUID().equals(registeredPlayerUUID)) {
                // 同一玩家持续骑乘中 → 每 tick 阻断玩家运动
                // （客户端 ClientMountGameHandler 已清零 WASD/跳跃，
                //  此处服务端兜底，防止包注入导致玩家实体移动）
                sp.setDeltaMovement(Vec3.ZERO);
            }

            // Create 模式：有乘客 OR 方块仍在 → 保持存活
            boolean blockPresent = level().getBlockState(homePos).getBlock() instanceof BaseCabinBlock;
            if (isVehicle() || blockPresent) return;

            this.discard();
            return;
        }

        // 客户端：无需生命周期检测
        // （seat 由 spawning 系统管理，客户端不触发 discard）
    }

    /**
     * 根据 SubLevel 的 logicalPose 更新实体位置和朝向，并将偏航变化传递给骑乘者。
     * <p>
     * {@code homePos} 是方块在 SubLevel 坐标系中的原始世界位置，
     * {@code logicalPose().transformPosition()} 将其变换到当前物理世界坐标。
     */
    private void followSubLevelPose(@NotNull SubLevel subLevel) {
        Pose3dc pose = subLevel.logicalPose();
        if (pose == null) return;

        // 方块底部中心 → 世界坐标
        Vector3d worldPos = new Vector3d();
        pose.transformPosition(
                new Vector3d(homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5),
                worldPos);
        setPos(worldPos.x, worldPos.y, worldPos.z);

        // 从 SubLevel 位姿计算朝向
        Vector3d localFwd = new Vector3d(0, 0, 1);
        Vector3d worldFwd = pose.transformNormal(localFwd, new Vector3d());
        float yaw = (float) Math.toDegrees(Math.atan2(-worldFwd.x(), worldFwd.z()));

        // 帧间偏航变化量
        float yawDelta = yaw - yRotO;
        yawDelta = (float) Math.IEEEremainder(yawDelta, 360.0);

        // 跳过首次 tick 的 delta（首次时 yRotO 为默认值 0，会触发快照旋转）
        if (firstTick) {
            yawDelta = 0;
            firstTick = false;
        }

        setYRot(yaw);
        setYHeadRot(yaw);

        // ── 全量位姿变换：骑乘者世界位置 ──
        // 将骑乘者局部偏移（块中心、座面上方 1/16 格）通过 Pose3d 的全量变换
        // （位移 + 旋转四元数）计算到世界空间。SubLevel 俯仰/侧倾时此偏移随之旋转，
        // 玩家始终正确坐在座面上。
        Vector3d riderLocal = new Vector3d(0.5, 1.0 / 16.0, 0.5);
        Vector3d riderWorld = new Vector3d();
        pose.transformPosition(
                new Vector3d(homePos.getX(), homePos.getY(), homePos.getZ()).add(riderLocal),
                riderWorld);
        this.riderWorldPos = new Vec3(riderWorld.x(), riderWorld.y(), riderWorld.z());

        // 记录供客户端事件读取
        this.lastYawDelta = yawDelta;

        // ── 将偏航变化传递给骑乘者（服务端权威同步） ──
        // 同时更新 yBodyRot 确保第三人称下玩家身体正确跟随载具旋转
        // 客户端上本地玩家的身体跟随由 ClientMountGameHandler 处理
        if (!level().isClientSide) {
            for (Entity passenger : getPassengers()) {
                passenger.setYRot(passenger.getYRot() + yawDelta);
                passenger.setYHeadRot(passenger.getYHeadRot() + yawDelta);
                if (passenger instanceof LivingEntity living) {
                    living.yBodyRot = living.yBodyRot + yawDelta;
                }
            }
        }
    }

    // ==================================================================
    //  载具控制器注册（首个骑乘者有效）
    // ==================================================================

    /**
     * 注册首个骑乘者为载具控制器。
     * <p>
     * 通过 {@link PlayerMountTracker} 建立"玩家 ↔ SubLevel"映射，
     * 并通知客户端启用载具控制输入（WASD 映射、悬挂扫描、信息覆盖层）。
     * 仅首个上车的玩家有效——若 SubLevel 已被其他玩家占用则静默跳过。
     */
    private void tryRegisterMountControl(@NotNull ServerPlayer passenger) {
        SubLevel subLevel = Sable.HELPER.getContaining(level(),
                new Vector3d(homePos.getX() + 0.5, homePos.getY() + 0.5, homePos.getZ() + 0.5));
        if (subLevel == null) return;

        UUID slUUID = subLevel.getUniqueId();

        // 限制：首个上车的玩家有效
        if (PlayerMountTracker.isSubLevelOccupiedByOther(slUUID, passenger)) {
            IACP.LOGGER.info("[IACPSeat] 玩家 {} 尝试骑乘已被占用的 SubLevel {}",
                    passenger.getName().getString(), slUUID);
            return;
        }

        double localX = homePos.getX() + 0.5;
        double localY = homePos.getY();
        double localZ = homePos.getZ() + 0.5;

        // 注册到 PlayerMountTracker
        PlayerMountTracker.mount(passenger, slUUID, localX, localY, localZ);

        // 获取载具质量
        double mass = 0;
        if (subLevel instanceof ServerSubLevel ssl) {
            try {
                mass = ssl.getMassTracker().getMass();
            } catch (Exception e) {
                IACP.LOGGER.warn("[IACPSeat] 获取 SubLevel 质量失败: {}", e.getMessage());
            }
        }

        // 通知客户端（启用载具控制 + 覆盖层 + 悬挂扫描）
        ModNetworking.sendToPlayer(passenger,
                new MountedStateS2CPacket(true, slUUID, mass, localX, localY, localZ));

        registeredPlayerUUID = passenger.getUUID();
        registeredSubLevelUUID = slUUID;

        IACP.LOGGER.info("[IACPSeat] 注册载具控制: player={}, subLevel={}, mass={}",
                passenger.getName().getString(), slUUID, mass);
    }

    /**
     * 注销载具控制器（玩家下车、实体销毁时触发）。
     * <p>
     * 重置悬挂输入、清理 PlayerMountTracker 状态、通知客户端关闭控制覆盖层。
     */
    private void unregisterMountControl() {
        if (registeredPlayerUUID == null || registeredSubLevelUUID == null) return;

        MinecraftServer server = level().getServer();
        if (server != null) {
            ServerPlayer sp = server.getPlayerList().getPlayer(registeredPlayerUUID);
            if (sp != null) {
                // 重置 SubLevel 内所有悬挂方块输入（保留刹车）
                ServerMountHandler.resetSuspensionInputsByUUID(server, registeredSubLevelUUID);
                // 清理 PlayerMountTracker 状态
                PlayerMountTracker.unmount(sp);
                // 通知客户端关闭控制模式
                ModNetworking.sendToPlayer(sp,
                        new MountedStateS2CPacket(false, new UUID(0, 0), 0.0, 0, 0, 0));
                IACP.LOGGER.info("[IACPSeat] 注销载具控制: player={}, subLevel={}",
                        sp.getName().getString(), registeredSubLevelUUID);
            }
        }

        registeredPlayerUUID = null;
        registeredSubLevelUUID = null;
    }

    /**
     * 实体移除时确保注册表清理（方块破坏、卸载等场景的兜底）。
     */
    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!level().isClientSide) {
            unregisterMountControl();
        }
        super.remove(reason);
    }

    // ==================================================================
    //  不可被推动
    // ==================================================================

    @Override
    public void setDeltaMovement(@NotNull Vec3 vec) {
        // 不可被推动（水流、爆炸等）
    }

    // ==================================================================
    //  数据持久化
    // ==================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 无需同步数据
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("HomePos")) {
            homePos = BlockPos.of(tag.getLong("HomePos"));
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        if (homePos != null) {
            tag.putLong("HomePos", homePos.asLong());
        }
    }

    // ==================================================================
    //  序列化（同步 homePos 到客户端）
    // ==================================================================

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(homePos);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        homePos = additionalData.readBlockPos();
    }

    // ==================================================================
    //  渲染（不可见）
    // ==================================================================

    public static class Render extends net.minecraft.client.renderer.entity.EntityRenderer<IACPSeatEntity> {
        public Render(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public boolean shouldRender(IACPSeatEntity entity,
                                    net.minecraft.client.renderer.culling.Frustum frustum,
                                    double x, double y, double z) {
            return false;
        }

        @Override
        @NotNull
        public ResourceLocation getTextureLocation(@NotNull IACPSeatEntity entity) {
            return null;
        }
    }

    // ==================================================================
    //  静态工厂
    // ==================================================================

    /**
     * 在指定方块的表面创建一个座位实体，使玩家坐下。
     * <p>
     * 对应 Create {@code SeatBlock.sitDown()}。
     */
    public static void sitDown(Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;
        IACPSeatEntity seat = new IACPSeatEntity(level, pos);
        level.addFreshEntity(seat);
        entity.startRiding(seat, true);
    }

    /**
     * 检查指定方块位置是否已有活跃的座位实体。
     */
    public static boolean isOccupied(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(IACPSeatEntity.class,
                new net.minecraft.world.phys.AABB(pos)).isEmpty();
    }
}
