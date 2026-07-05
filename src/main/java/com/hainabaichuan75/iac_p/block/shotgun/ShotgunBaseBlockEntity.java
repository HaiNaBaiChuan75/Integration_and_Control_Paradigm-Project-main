package com.hainabaichuan75.iac_p.block.shotgun;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.affiliation.AffiliationHelper;
import com.hainabaichuan75.iac_p.affiliation.AffiliationRegistry;
import com.hainabaichuan75.iac_p.affiliation.AffiliationRole;
import com.hainabaichuan75.iac_p.affiliation.AffiliationTag;
import com.hainabaichuan75.iac_p.ecs.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.index.ModBlockEntityTypes;
import com.hainabaichuan75.iac_p.network.packets.AnchorDataS2CPacket;
import com.hainabaichuan75.iac_p.part.AimingMount;
import com.hainabaichuan75.iac_p.part.field.YawPitch;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.EnumSet;
import java.util.UUID;

/**
 * ShotgunBaseBlockEntity —— 霰弹枪底座 BE。
 */
public class ShotgunBaseBlockEntity extends PartBlockEntity implements AimingMount {

    private static final java.util.Map<java.util.UUID, BlockPos> SG_GRINDSTONE_OWNER_MAP = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, BlockPos> SG_ROD_OWNER_MAP = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, double[]> SG_GRINDSTONE_ANCHOR_MAP = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, double[]> SG_GRINDSTONE_LINE_CACHE = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, java.util.List<BlockPos>> SG_CARPET_LOCAL_POS_MAP = new java.util.HashMap<>();

    public static java.util.Map<java.util.UUID, java.util.List<BlockPos>> getCarpetLocalPosMap() { return SG_CARPET_LOCAL_POS_MAP; }
    public static java.util.Map<java.util.UUID, double[]> getAnchorMap() { return SG_GRINDSTONE_ANCHOR_MAP; }
    public static java.util.Map<java.util.UUID, double[]> getLineCache() { return SG_GRINDSTONE_LINE_CACHE; }
    @Nullable public static BlockPos findOwnerByGrindstoneUUID(UUID uuid) { return SG_GRINDSTONE_OWNER_MAP.get(uuid); }
    @Nullable public static BlockPos findOwnerByRodUUID(UUID uuid) { return SG_ROD_OWNER_MAP.get(uuid); }

    private boolean assembled = false;
    @Nullable private UUID groupId;
    @Nullable private UUID grindstoneSubLevelId;
    @Nullable private UUID lightningRodSubLevelId;
    @Nullable private PhysicsConstraintHandle barrelPitchHandle;
    @Nullable private UUID vehicleSubLevelId;
    @Nullable private PhysicsConstraintHandle rodVehicleFreeHandle;
    @Nullable private PhysicsConstraintHandle swivelBearingHandle;
    private double anchorX = 0.0, anchorY = 0.0, anchorZ = 0.0;
    private int deferredRebuildTicks = -1;
    private int rebuildRetryCount = 0;
    private static final int MAX_REBUILD_RETRIES = 10;

    private static final double ANCHOR_ROD_X = 0.0, ANCHOR_ROD_Y = 0.0, ANCHOR_ROD_Z = -0.5;
    private static final double ANCHOR_GS_ROD_X = 0.0, ANCHOR_GS_ROD_Y = 0.1, ANCHOR_GS_ROD_Z = 0.0;
    private static final double ANCHOR_GS_SWIVEL_X = 0.0, ANCHOR_GS_SWIVEL_Y = 0.0, ANCHOR_GS_SWIVEL_Z = 0.0;
    private static final double ANCHOR_VEHICLE_X = 0.0, ANCHOR_VEHICLE_Y = 0.0, ANCHOR_VEHICLE_Z = 0.0;

    private double targetAngleDegrees = 0;
    private double lastTargetAngleDegrees = 0;
    private static final double SERVO_STIFFNESS = 5000.0;
    private static final double SERVO_DAMPING = 20.0;

    private double targetPitchAngleDegrees = 0;
    private double lastTargetPitchAngleDegrees = 0;
    private static final double PITCH_SERVO_STIFFNESS = 5000.0;
    private static final double PITCH_SERVO_DAMPING = 20.0;

    @Override
    public void onChunkUnloaded() {
        cleanupStaticMaps();
        super.onChunkUnloaded();
    }

    public ShotgunBaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SHOTGUN_BASE.get(), pos, state);
    }

    @Override
    public @NotNull Quaterniondc orientation() {return IDENTITY_QUAT;}

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide && this.assembled && this.grindstoneSubLevelId != null) {
            if (this.swivelBearingHandle == null) {
                IACP.LOGGER.info("[ShotgunBase] onLoad() 触发约束重建 @ {}", this.worldPosition);
                reestablishConstraints();
            }
            boolean yawOk = this.swivelBearingHandle != null && this.swivelBearingHandle.isValid();
            boolean pitchOk = this.barrelPitchHandle != null && this.barrelPitchHandle.isValid();
            if (yawOk && pitchOk) {
                this.deferredRebuildTicks = -1;
            } else {
                this.deferredRebuildTicks = 10;
            }
        }
    }

    public void tick() {
        if (level == null || level.isClientSide) return;
        if (deferredRebuildTicks > 0) {
            deferredRebuildTicks--;
            if (deferredRebuildTicks == 0) {
                reestablishConstraints();
                boolean yawOk = swivelBearingHandle != null && swivelBearingHandle.isValid();
                boolean pitchOk = barrelPitchHandle != null && barrelPitchHandle.isValid();
                if (!yawOk || !pitchOk) {
                    rebuildRetryCount++;
                    if (rebuildRetryCount >= MAX_REBUILD_RETRIES) { disassemble(); rebuildRetryCount = 0; }
                    else deferredRebuildTicks = 40;
                } else rebuildRetryCount = 0;
            }
        }
        if (assembled && swivelBearingHandle != null && swivelBearingHandle.isValid()) updateYawServo();
        if (assembled && barrelPitchHandle != null && barrelPitchHandle.isValid()) updatePitchServo();
    }

    private void updateYawServo() {
        if (!assembled || swivelBearingHandle == null || !swivelBearingHandle.isValid()) return;
        float goal = AngleHelper.rad(AngleHelper.angleLerp(1.0f, (float) lastTargetAngleDegrees, (float) targetAngleDegrees));
        swivelBearingHandle.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, goal, SERVO_STIFFNESS, SERVO_DAMPING, false, 0.0);
        swivelBearingHandle.setContactsEnabled(false);
        this.lastTargetAngleDegrees = this.targetAngleDegrees;
    }

    public void setTargetYawAbsolute(double degrees) { this.lastTargetAngleDegrees = this.targetAngleDegrees; this.targetAngleDegrees = degrees; }
    public double getTargetYawAngle() { return this.targetAngleDegrees; }

    private void updatePitchServo() {
        if (!assembled || barrelPitchHandle == null || !barrelPitchHandle.isValid()) return;
        float goal = AngleHelper.rad(AngleHelper.angleLerp(1.0f, (float) lastTargetPitchAngleDegrees, (float) targetPitchAngleDegrees));
        barrelPitchHandle.setMotor(ConstraintJointAxis.ANGULAR_X, goal, PITCH_SERVO_STIFFNESS, PITCH_SERVO_DAMPING, false, 0.0);
        barrelPitchHandle.setContactsEnabled(false);
        this.lastTargetPitchAngleDegrees = this.targetPitchAngleDegrees;
    }

    public void setTargetPitchAbsolute(double degrees) { this.lastTargetPitchAngleDegrees = this.targetPitchAngleDegrees; this.targetPitchAngleDegrees = degrees; }
    public double getTargetPitchAngle() { return this.targetPitchAngleDegrees; }

    public void driveImmediate(float yawDeg, float pitchDeg) {
        this.lastTargetAngleDegrees = this.targetAngleDegrees; this.targetAngleDegrees = yawDeg;
        this.lastTargetPitchAngleDegrees = this.targetPitchAngleDegrees; this.targetPitchAngleDegrees = pitchDeg;
        if (assembled && swivelBearingHandle != null && swivelBearingHandle.isValid()) updateYawServo();
        if (assembled && barrelPitchHandle != null && barrelPitchHandle.isValid()) updatePitchServo();
    }

    // ==================================================================
    //  装配 / 拆卸
    // ==================================================================
    public void assemble() {
        if (this.assembled) return;
        if (this.level == null || this.level.isClientSide) return;
        IACP.LOGGER.info("[ShotgunBase] ====== 开始装配 @ {} ======", this.worldPosition);
        ServerLevel serverLevel = (ServerLevel) this.level;
        ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
        if (container == null) return;
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        if (pipeline == null) return;

        SubLevel containingSubLevel = Sable.HELPER.getContaining(this);
        BlockPos searchOrigin;
        if (containingSubLevel != null) {
            Vector3d localCenter = new Vector3d(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5);
            containingSubLevel.logicalPose().transformPosition(localCenter);
            searchOrigin = BlockPos.containing(Math.floor(localCenter.x), Math.floor(localCenter.y), Math.floor(localCenter.z));
            this.vehicleSubLevelId = containingSubLevel.getUniqueId();
        } else { searchOrigin = this.worldPosition; }

        BlockPos spotA = findEmptySpot(serverLevel, searchOrigin);
        if (spotA == null) { IACP.LOGGER.error("[ShotgunBase] 找不到空位！"); return; }

        Quaterniond grindstoneOrient = new Quaterniond();
        Vector3d grindstoneSpawnVec;
        if (containingSubLevel instanceof ServerSubLevel vehicleSL_pre) {
            var vPose = vehicleSL_pre.logicalPose();
            Vector3d carpetWorld = vPose.transformPosition(new Vector3d(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5));
            grindstoneSpawnVec = new Vector3d(carpetWorld.x - anchorX, carpetWorld.y - anchorY, carpetWorld.z - anchorZ);
            grindstoneOrient.set(vPose.orientation());
        } else { grindstoneSpawnVec = new Vector3d(spotA.getX() + 0.5, spotA.getY() + 0.5, spotA.getZ() + 0.5); }

        ServerSubLevel grindstoneSL;
        try {
            Pose3d pose = new Pose3d(); pose.position().set(grindstoneSpawnVec); pose.orientation().set(grindstoneOrient);
            grindstoneSL = (ServerSubLevel) container.allocateNewSubLevel(pose);
            initSingleBlockSubLevel(grindstoneSL, Blocks.GRINDSTONE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
            pipeline.teleport(grindstoneSL, grindstoneSpawnVec, grindstoneOrient);
            grindstoneSL.updateLastPose();
        } catch (Exception e) { IACP.LOGGER.error("[ShotgunBase] 砂轮创建失败", e); return; }
        this.grindstoneSubLevelId = grindstoneSL.getUniqueId();
        SG_GRINDSTONE_OWNER_MAP.put(grindstoneSL.getUniqueId(), this.worldPosition);
        SG_GRINDSTONE_ANCHOR_MAP.put(grindstoneSL.getUniqueId(), new double[]{anchorX, anchorY, anchorZ});
        sendAnchorDataToClients();
        this.groupId = UUID.randomUUID();
        if (this.vehicleSubLevelId != null) {
            AffiliationHelper.registerMachineGunPart(grindstoneSL.getUniqueId(), this.vehicleSubLevelId, this.groupId, AffiliationRole.SHOTGUN_YAW, AffiliationTag.FACTION_NEUTRAL);
        }

        if (containingSubLevel instanceof ServerSubLevel vehicleSL) {
            try {
                BlockPos gc = grindstoneSL.getPlot().getCenterBlock();
                Vector3d pos1 = new Vector3d(gc.getX() + 0.5, gc.getY() + 0.5, gc.getZ() + 0.5).add(ANCHOR_GS_SWIVEL_X, ANCHOR_GS_SWIVEL_Y, ANCHOR_GS_SWIVEL_Z);
                Vector3d pos2 = new Vector3d(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5).add(ANCHOR_VEHICLE_X, ANCHOR_VEHICLE_Y, ANCHOR_VEHICLE_Z);
                this.swivelBearingHandle = pipeline.addConstraint(grindstoneSL, vehicleSL, new RotaryConstraintConfiguration(pos1, pos2, new Vector3d(0, 1, 0), new Vector3d(0, 1, 0)));
                this.swivelBearingHandle.setContactsEnabled(false);
            } catch (Exception e) { IACP.LOGGER.warn("[ShotgunBase] 旋转轴承失败", e); }
        }

        {
            try {
                Pose3d poseB = new Pose3d(); poseB.position().set(grindstoneSpawnVec); poseB.orientation().set(grindstoneOrient);
                ServerSubLevel rodSL = (ServerSubLevel) container.allocateNewSubLevel(poseB);
                initSingleBlockSubLevel(rodSL, Blocks.LIGHTNING_ROD.defaultBlockState().setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, Direction.SOUTH));
                pipeline.teleport(rodSL, grindstoneSpawnVec, grindstoneOrient);
                rodSL.updateLastPose();
                this.lightningRodSubLevelId = rodSL.getUniqueId();
                SG_ROD_OWNER_MAP.put(rodSL.getUniqueId(), this.worldPosition);
                if (this.vehicleSubLevelId != null && this.groupId != null) {
                    AffiliationHelper.registerMachineGunPart(rodSL.getUniqueId(), this.vehicleSubLevelId, this.groupId, AffiliationRole.SHOTGUN_PITCH, AffiliationTag.FACTION_NEUTRAL);
                }
                try {
                    BlockPos rc = rodSL.getPlot().getCenterBlock();
                    Vector3d pos1 = new Vector3d(rc.getX() + 0.5, rc.getY() + 0.5, rc.getZ() + 0.5).add(ANCHOR_ROD_X, ANCHOR_ROD_Y, ANCHOR_ROD_Z);
                    BlockPos gc = grindstoneSL.getPlot().getCenterBlock();
                    Vector3d pos2 = new Vector3d(gc.getX() + 0.5, gc.getY() + 0.5, gc.getZ() + 0.5).add(ANCHOR_GS_ROD_X, ANCHOR_GS_ROD_Y, ANCHOR_GS_ROD_Z);
                    this.barrelPitchHandle = pipeline.addConstraint(rodSL, grindstoneSL, new GenericConstraintConfiguration(pos1, pos2, new Quaterniond(), new Quaterniond(),
                            EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z, ConstraintJointAxis.ANGULAR_Y, ConstraintJointAxis.ANGULAR_Z)));
                    this.barrelPitchHandle.setContactsEnabled(false);
                    if (containingSubLevel instanceof ServerSubLevel vehicleSL2) {
                        try {
                            Vector3d rodCenter = new Vector3d(rodSL.getPlot().getCenterBlock().getX() + 0.5, rodSL.getPlot().getCenterBlock().getY() + 0.5, rodSL.getPlot().getCenterBlock().getZ() + 0.5);
                            Vector3d vehicleCenter = new Vector3d(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5);
                            this.rodVehicleFreeHandle = pipeline.addConstraint(rodSL, vehicleSL2, new FreeConstraintConfiguration(rodCenter, vehicleCenter, new Quaterniond()));
                            this.rodVehicleFreeHandle.setContactsEnabled(false);
                        } catch (Exception e2) {}
                    }
                } catch (Exception e) {}
            } catch (Exception e) { this.lightningRodSubLevelId = null; }
        }

        this.assembled = true; this.setChanged(); this.sendData();
    }

    private void cleanupStaticMaps() {
        if (this.grindstoneSubLevelId != null) { SG_GRINDSTONE_OWNER_MAP.remove(this.grindstoneSubLevelId); SG_GRINDSTONE_ANCHOR_MAP.remove(this.grindstoneSubLevelId); SG_GRINDSTONE_LINE_CACHE.remove(this.grindstoneSubLevelId); }
        if (this.lightningRodSubLevelId != null) SG_ROD_OWNER_MAP.remove(this.lightningRodSubLevelId);
    }

    public void disassemble() {
        if (!this.assembled || this.level == null || this.level.isClientSide) return;
        try {
            ServerLevel serverLevel = (ServerLevel) this.level;
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
            if (container == null) return;
            removeConstraint(this.barrelPitchHandle); this.barrelPitchHandle = null;
            removeConstraint(this.rodVehicleFreeHandle); this.rodVehicleFreeHandle = null;
            removeConstraint(this.swivelBearingHandle); this.swivelBearingHandle = null;
            if (this.groupId != null) { AffiliationRegistry.unregisterGroup(this.groupId); this.groupId = null; }
            cleanupStaticMaps();
            removeSubLevelById(container, this.grindstoneSubLevelId); this.grindstoneSubLevelId = null;
            removeSubLevelById(container, this.lightningRodSubLevelId); this.lightningRodSubLevelId = null;
        } catch (Exception e) {}
        this.vehicleSubLevelId = null;
        this.assembled = false; this.setChanged(); this.sendData();
    }

    @Nullable public UUID getGrindstoneSubLevelId() { return this.grindstoneSubLevelId; }
    @Nullable public UUID getVehicleSubLevelId() { return this.vehicleSubLevelId; }
    @Nullable public PhysicsConstraintHandle getSwivelBearingHandle() { return this.swivelBearingHandle; }
    @Nullable public PhysicsConstraintHandle getBarrelPitchHandle() { return this.barrelPitchHandle; }
    public double[] getAnchor() { return new double[]{this.anchorX, this.anchorY, this.anchorZ}; }
    public void setAnchor(double x, double y, double z) { this.anchorX = x; this.anchorY = y; this.anchorZ = z; this.setChanged(); this.sendData(); sendAnchorDataToClients(); }

    private void sendAnchorDataToClients() {
        if (this.level == null || this.level.isClientSide || this.grindstoneSubLevelId == null) return;
        try {
            ServerLevel serverLevel = (ServerLevel) this.level;
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
            if (container == null) return;
            ServerSubLevel sub = (ServerSubLevel) container.getSubLevel(this.grindstoneSubLevelId);
            if (sub == null || sub.isRemoved()) { sendAnchorOnly(serverLevel); return; }
            var pose = sub.logicalPose();
            if (pose == null) { sendAnchorOnly(serverLevel); return; }
            Vector3d o = pose.transformPosition(new Vector3d(anchorX, anchorY, anchorZ));
            Vector3d x = pose.transformPosition(new Vector3d(anchorX + 20, anchorY, anchorZ));
            Vector3d y = pose.transformPosition(new Vector3d(anchorX, anchorY + 20, anchorZ));
            Vector3d z = pose.transformPosition(new Vector3d(anchorX, anchorY, anchorZ + 20));
            PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(this.worldPosition),
                    new AnchorDataS2CPacket(this.grindstoneSubLevelId, anchorX, anchorY, anchorZ, new double[]{o.x, o.y, o.z, x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z}));
        } catch (Exception e) {}
    }

    private void sendAnchorOnly(ServerLevel serverLevel) {
        PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(this.worldPosition), new AnchorDataS2CPacket(this.grindstoneSubLevelId, anchorX, anchorY, anchorZ, new double[12]));
    }

    @Nullable public UUID getLightningRodSubLevelId() { return this.lightningRodSubLevelId; }

    public boolean isAssembled() { return this.assembled; }

    public void setGrindstoneFacing(Direction facing) {
        if (this.grindstoneSubLevelId == null || this.level == null || this.level.isClientSide) return;
        try {
            ServerLevel serverLevel = (ServerLevel) this.level;
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
            if (container == null) return;
            ServerSubLevel subLevel = (ServerSubLevel) container.getSubLevel(this.grindstoneSubLevelId);
            if (subLevel == null || subLevel.isRemoved()) return;
            LevelPlot plot = subLevel.getPlot();
            if (plot == null) return;
            plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.GRINDSTONE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.GrindstoneBlock.FACING, facing), 3);
        } catch (Exception e) { IACP.LOGGER.error("[ShotgunBase] 更改砂轮朝向失败", e); }
    }

    public void setLightningRodFacing(Direction facing) {
        if (this.lightningRodSubLevelId == null || this.level == null || this.level.isClientSide) return;
        try {
            ServerLevel serverLevel = (ServerLevel) this.level;
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
            if (container == null) return;
            ServerSubLevel subLevel = (ServerSubLevel) container.getSubLevel(this.lightningRodSubLevelId);
            if (subLevel == null || subLevel.isRemoved()) return;
            LevelPlot plot = subLevel.getPlot();
            if (plot == null) return;
            plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.LIGHTNING_ROD.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, facing)
                    .setValue(net.minecraft.world.level.block.LightningRodBlock.POWERED, false), 3);
        } catch (Exception e) { IACP.LOGGER.error("[ShotgunBase] 更改避雷针朝向失败", e); }
    }

    private static void removeConstraint(@Nullable PhysicsConstraintHandle h) { if (h != null) try { if (h.isValid()) h.remove(); } catch (Exception e) {} }
    private static void removeSubLevelById(ServerSubLevelContainer c, @Nullable UUID id) { if (id != null) try { ServerSubLevel s = (ServerSubLevel) c.getSubLevel(id); if (s != null && !s.isRemoved()) c.removeSubLevel(s, SubLevelRemovalReason.REMOVED); } catch (Exception e) {} }
    private static void initSingleBlockSubLevel(ServerSubLevel sl, BlockState bs) {
        LevelPlot plot = sl.getPlot(); if (plot == null) return;
        try { plot.newEmptyChunk(plot.getCenterChunk()); } catch (Exception e) { return; }
        try { plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, bs, 3); } catch (Exception e) {}
        try { sl.updateMergedMassData(0.001f); } catch (Exception e) {}
    }
    @Nullable private static BlockPos findEmptySpot(Level l, BlockPos o) { for (int y = o.getY() + 3; y <= l.getMaxBuildHeight() - 2; y++) { BlockPos c = new BlockPos(o.getX(), y, o.getZ()); if (isAllFacesAir(l, c)) return c; } return null; }
    private static boolean isAllFacesAir(Level l, BlockPos p) { return l.getBlockState(p).isAir() && l.getBlockState(p.above()).isAir() && l.getBlockState(p.below()).isAir() && l.getBlockState(p.north()).isAir() && l.getBlockState(p.south()).isAir() && l.getBlockState(p.east()).isAir() && l.getBlockState(p.west()).isAir(); }

    // ==================================================================
    //  NBT
    // ==================================================================
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Assembled", this.assembled);
        if (this.grindstoneSubLevelId != null) tag.putUUID("GrindstoneSubLevel", this.grindstoneSubLevelId);
        if (this.lightningRodSubLevelId != null) tag.putUUID("LightningRodSubLevel", this.lightningRodSubLevelId);
        if (this.vehicleSubLevelId != null) tag.putUUID("VehicleSubLevel", this.vehicleSubLevelId);
        if (this.groupId != null) tag.putUUID("ShotgunGroupId", this.groupId);
        if (this.vehicleSubLevelId != null) { tag.putUUID(AffiliationHelper.TAG_VEHICLE_ID, this.vehicleSubLevelId); tag.putString(AffiliationHelper.TAG_ROLE, AffiliationRole.SHOTGUN_BASE.name()); tag.putInt(AffiliationHelper.TAG_FACTION, AffiliationTag.FACTION_NEUTRAL); }
        tag.putDouble("AnchorX", this.anchorX); tag.putDouble("AnchorY", this.anchorY); tag.putDouble("AnchorZ", this.anchorZ);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.assembled = tag.getBoolean("Assembled");
        this.grindstoneSubLevelId = tag.hasUUID("GrindstoneSubLevel") ? tag.getUUID("GrindstoneSubLevel") : null;
        this.lightningRodSubLevelId = tag.hasUUID("LightningRodSubLevel") ? tag.getUUID("LightningRodSubLevel") : null;
        this.vehicleSubLevelId = tag.hasUUID("VehicleSubLevel") ? tag.getUUID("VehicleSubLevel") : null;
        this.groupId = tag.hasUUID("ShotgunGroupId") ? tag.getUUID("ShotgunGroupId") : null;
        this.anchorX = tag.getDouble("AnchorX"); this.anchorY = tag.getDouble("AnchorY"); this.anchorZ = tag.getDouble("AnchorZ");
        if (this.grindstoneSubLevelId != null) SG_GRINDSTONE_ANCHOR_MAP.put(this.grindstoneSubLevelId, new double[]{anchorX, anchorY, anchorZ});
        if (this.level != null && !this.level.isClientSide && this.assembled) {
            if (this.vehicleSubLevelId != null) {
                if (this.grindstoneSubLevelId != null && this.groupId != null) AffiliationHelper.registerMachineGunPart(this.grindstoneSubLevelId, this.vehicleSubLevelId, this.groupId, AffiliationRole.SHOTGUN_YAW, AffiliationTag.FACTION_NEUTRAL);
                if (this.lightningRodSubLevelId != null && this.groupId != null) AffiliationHelper.registerMachineGunPart(this.lightningRodSubLevelId, this.vehicleSubLevelId, this.groupId, AffiliationRole.SHOTGUN_PITCH, AffiliationTag.FACTION_NEUTRAL);
            }
            if (this.deferredRebuildTicks < 0) this.deferredRebuildTicks = 20;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (this.grindstoneSubLevelId != null && this.level instanceof ServerLevel sl) {
            ServerSubLevelContainer c = (ServerSubLevelContainer) SubLevelContainer.getContainer(sl);
            if (c != null) {
                ServerSubLevel sub = (ServerSubLevel) c.getSubLevel(this.grindstoneSubLevelId);
                if (sub != null && !sub.isRemoved()) {
                    var pose = sub.logicalPose();
                    if (pose != null) {
                        var o = pose.transformPosition(new Vector3d(anchorX, anchorY, anchorZ));
                        var x = pose.transformPosition(new Vector3d(anchorX + 20, anchorY, anchorZ));
                        var y = pose.transformPosition(new Vector3d(anchorX, anchorY + 20, anchorZ));
                        var z = pose.transformPosition(new Vector3d(anchorX, anchorY, anchorZ + 20));
                        tag.putDouble("LOX", o.x); tag.putDouble("LOY", o.y); tag.putDouble("LOZ", o.z);
                        tag.putDouble("LXX", x.x); tag.putDouble("LXY", x.y); tag.putDouble("LXZ", x.z);
                        tag.putDouble("LYX", y.x); tag.putDouble("LYY", y.y); tag.putDouble("LYZ", y.z);
                        tag.putDouble("LZX", z.x); tag.putDouble("LZY", z.y); tag.putDouble("LZZ", z.z);
                    }
                }
            }
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        if (this.grindstoneSubLevelId != null) {
            SG_GRINDSTONE_ANCHOR_MAP.put(this.grindstoneSubLevelId, new double[]{anchorX, anchorY, anchorZ});
            if (tag.contains("LOX")) {
                SG_GRINDSTONE_LINE_CACHE.put(this.grindstoneSubLevelId, new double[]{tag.getDouble("LOX"), tag.getDouble("LOY"), tag.getDouble("LOZ"), tag.getDouble("LXX"), tag.getDouble("LXY"), tag.getDouble("LXZ"), tag.getDouble("LYX"), tag.getDouble("LYY"), tag.getDouble("LYZ"), tag.getDouble("LZX"), tag.getDouble("LZY"), tag.getDouble("LZZ")});
            }
        }
        if (this.level != null && this.level.isClientSide) {
            try { SubLevel containing = Sable.HELPER.getContaining(this); if (containing != null) SG_CARPET_LOCAL_POS_MAP.computeIfAbsent(containing.getUniqueId(), k -> new java.util.ArrayList<>()).add(this.worldPosition); } catch (Exception ignored) {}
        }
    }

    private void reestablishConstraints() {
        if (this.level == null || this.level.isClientSide || !this.assembled || this.grindstoneSubLevelId == null) return;
        try {
            ServerLevel serverLevel = (ServerLevel) this.level;
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
            if (container == null) return;
            ServerSubLevel grindstoneSL = (ServerSubLevel) container.getSubLevel(this.grindstoneSubLevelId);
            if (grindstoneSL == null || grindstoneSL.isRemoved()) return;
            PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
            if (pipeline == null) return;
            ServerSubLevel vehicleSL = null;
            if (this.vehicleSubLevelId != null) vehicleSL = (ServerSubLevel) container.getSubLevel(this.vehicleSubLevelId);
            if (vehicleSL != null && !vehicleSL.isRemoved() && this.swivelBearingHandle == null) {
                try {
                    BlockPos gc = grindstoneSL.getPlot().getCenterBlock();
                    Vector3d pos1 = new Vector3d(gc.getX() + 0.5, gc.getY() + 0.5, gc.getZ() + 0.5).add(ANCHOR_GS_SWIVEL_X, ANCHOR_GS_SWIVEL_Y, ANCHOR_GS_SWIVEL_Z);
                    Vector3d pos2 = new Vector3d(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5).add(ANCHOR_VEHICLE_X, ANCHOR_VEHICLE_Y, ANCHOR_VEHICLE_Z);
                    this.swivelBearingHandle = pipeline.addConstraint(grindstoneSL, vehicleSL, new RotaryConstraintConfiguration(pos1, pos2, new Vector3d(0, 1, 0), new Vector3d(0, 1, 0)));
                    this.swivelBearingHandle.setContactsEnabled(false);
                } catch (Exception e) {}
            }
            if (this.lightningRodSubLevelId != null && this.barrelPitchHandle == null) {
                ServerSubLevel rodSL = (ServerSubLevel) container.getSubLevel(this.lightningRodSubLevelId);
                if (rodSL != null && !rodSL.isRemoved()) {
                    try {
                        BlockPos rc = rodSL.getPlot().getCenterBlock();
                        Vector3d pos1 = new Vector3d(rc.getX() + 0.5, rc.getY() + 0.5, rc.getZ() + 0.5).add(ANCHOR_ROD_X, ANCHOR_ROD_Y, ANCHOR_ROD_Z);
                        BlockPos gc2 = grindstoneSL.getPlot().getCenterBlock();
                        Vector3d pos2 = new Vector3d(gc2.getX() + 0.5, gc2.getY() + 0.5, gc2.getZ() + 0.5).add(ANCHOR_GS_ROD_X, ANCHOR_GS_ROD_Y, ANCHOR_GS_ROD_Z);
                        this.barrelPitchHandle = pipeline.addConstraint(rodSL, grindstoneSL, new GenericConstraintConfiguration(pos1, pos2, new Quaterniond(), new Quaterniond(),
                                EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z, ConstraintJointAxis.ANGULAR_Y, ConstraintJointAxis.ANGULAR_Z)));
                        this.barrelPitchHandle.setContactsEnabled(false);
                    } catch (Exception e) {}
                    if (vehicleSL != null && !vehicleSL.isRemoved() && this.rodVehicleFreeHandle == null) {
                        try {
                            Vector3d rodCenter = new Vector3d(rodSL.getPlot().getCenterBlock().getX() + 0.5, rodSL.getPlot().getCenterBlock().getY() + 0.5, rodSL.getPlot().getCenterBlock().getZ() + 0.5);
                            Vector3d vehicleCenter = new Vector3d(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5);
                            this.rodVehicleFreeHandle = pipeline.addConstraint(rodSL, vehicleSL, new FreeConstraintConfiguration(rodCenter, vehicleCenter, new Quaterniond()));
                            this.rodVehicleFreeHandle.setContactsEnabled(false);
                        } catch (Exception e) {}
                    }
                }
            }
        } catch (Exception e) {}
    }

    // ==================================================================
    //  AimingMount 实现
    // ==================================================================
    @Override
    public void setAngles(YawPitch angles) {
        // YawPitch.from() 输出 CCW+，RotaryConstraint 同为 CCW+，但约束初始零位与模型朝向差 180°
        driveImmediate((float) -(angles.yaw() + 180), (float) angles.pitch());
    }

    @Override
    public YawPitch getAngles() {
        return new YawPitch(targetAngleDegrees, targetPitchAngleDegrees);
    }

    private void sendData() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
}
