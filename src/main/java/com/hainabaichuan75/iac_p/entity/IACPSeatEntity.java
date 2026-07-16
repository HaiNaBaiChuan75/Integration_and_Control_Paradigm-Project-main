package com.hainabaichuan75.iac_p.entity;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlock;
import com.hainabaichuan75.iac_p.events.PlayerMountTracker;
import com.hainabaichuan75.iac_p.events.ServerMountHandler;
import com.hainabaichuan75.iac_p.index.ModEntities;
import com.hainabaichuan75.iac_p.network.ModNetworking;
import com.hainabaichuan75.iac_p.network.packets.MountedStateS2CPacket;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

public class IACPSeatEntity extends Entity implements IEntityWithComplexSpawn {

    public IACPSeatEntity(EntityType<? extends IACPSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        AABB bb = getBoundingBox();
        Vec3 diff = new Vec3(x, y, z).subtract(bb.getCenter());
        setBoundingBox(bb.move(diff));
    }

    public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
        @SuppressWarnings("unchecked")
        EntityType.Builder<IACPSeatEntity> entityBuilder = (EntityType.Builder<IACPSeatEntity>) builder;
        return entityBuilder.sized(0.25f, 0.35f);
    }

    //坐姿
    @Override
    protected void positionRider(Entity pEntity, Entity.MoveFunction pCallback) {
        if (!this.hasPassenger(pEntity))
            return;
        double heightOffset = this.getPassengerRidingPosition(pEntity).y - pEntity.getVehicleAttachmentPoint(this).y;

        pCallback.accept(pEntity, this.getX(), 1.0 / 16.0 + heightOffset + getCustomEntitySeatOffset(pEntity), this.getZ());
    }

    @Override
    public void onPassengerTurned(Entity entity) {
        entity.setYHeadRot(entity.getYRot());
    }

    public static double getCustomEntitySeatOffset(Entity entity) {
        if (entity instanceof Slime)
            return 0.0f;
        if (entity instanceof Parrot)
            return 1 / 12f;
        if (entity instanceof Skeleton)
            return 1 / 8f;
        if (entity instanceof Cat)
            return 1 / 12f;
        if (entity instanceof Wolf)
            return 1 / 16f;
        if (entity instanceof Frog)
            return 1.5 / 16f;
        if (entity instanceof Spider)
            return 1 / 8.0;
        return 0;
    }

    //不可被推动
    @Override
    public void setDeltaMovement(Vec3 vec) {
    }

    @Override
    public void tick() {
        if (level().isClientSide)
            return;
        boolean blockPresent = level().getBlockState(blockPosition())
                .getBlock() instanceof BaseCabinBlock;
        if (isVehicle() && blockPresent)
            return;
        this.discard();
    }

    @Override
    protected boolean canRide(Entity entity) {
        // Fake Players (tested with deployers) have a BUNCH of weird issues, don't let
        // them ride seats
        return !(entity instanceof FakePlayer);
    }

    @Override
    protected void removePassenger(Entity entity) {
        super.removePassenger(entity);
        if (entity instanceof TamableAnimal ta)
            ta.setInSittingPose(false);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity pLivingEntity) {
        return super.getDismountLocationForPassenger(pLivingEntity).add(0, 0.5f, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    public static class Render extends EntityRenderer<IACPSeatEntity> {

        public Render(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public boolean shouldRender(IACPSeatEntity seatEntity, Frustum frustum, double p_225626_3_, double p_225626_5_,
                                    double p_225626_7_) {
            return false;
        }

        @Override
        public ResourceLocation getTextureLocation(IACPSeatEntity seatEntity) {
            return null;
        }
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {}

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {}

    /** 座位对应的方块位置（SubLevel 局部坐标 = 原始世界位置） */
    @Nullable
    private BlockPos homePos;

    private float lastYawDelta = 0;

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




    /**
     * 根据 SubLevel 的 logicalPose 更新实体位置和朝向，并将偏航变化传递给骑乘者。
     * <p>
     * {@code homePos} 是方块在 SubLevel 坐标系中的原始世界位置，
     * {@code logicalPose().transformPosition()} 将其变换到当前物理世界坐标。
     */

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
