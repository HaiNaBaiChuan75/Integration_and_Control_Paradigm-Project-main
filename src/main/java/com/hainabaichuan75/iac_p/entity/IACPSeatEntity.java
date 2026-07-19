package com.hainabaichuan75.iac_p.entity;

import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlock;
import com.simibubi.create.AllEntityTypes;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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

public class IACPSeatEntity extends Entity implements IEntityWithComplexSpawn {

    public IACPSeatEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public IACPSeatEntity(Level level) {
        this(AllEntityTypes.SEAT.get(), level);
        noPhysics = true;
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

}
