package com.hainabaichuan75.iac_p.block.simplewheel;

import com.hainabaichuan75.iac_p.registry.IACPBlockEntities;
import com.hainabaichuan75.iac_p.vehicle.VehiclePartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class SimpleWheelBlockEntity extends VehiclePartBlockEntity {

    /**
     * 悬挂最大行程（与 raycast 距离一致）
     */
    private static final double SUSPENSION_TRAVEL = 5.0;

    public SimpleWheelBlockEntity(BlockPos pos, BlockState state) {
        super(IACPBlockEntities.SIMPLE_WHEEL.get(), pos, state);
    }

    /* ==================== 地面探测 ==================== */

    /**
     * 沿轮轴侧向扫描，返回轮心到地面的最短距离及碰撞信息。
     *
     * @param pose 子世界位姿
     * @return 最近地面探测结果，{@code maxExtension == SUSPENSION_TRAVEL} 表示未触地
     */
    private TerrainCastResult computeMaxExtensionToTerrain(ServerSubLevel subLevel) {
        Pose3d pose = subLevel.logicalPose();
        final Direction facing = this.getBlockState().getValue(SimpleWheelBlock.FACING);
        SimpleWheelBlock block = (SimpleWheelBlock) this.getBlockState().getBlock();
        Vector3dc normalD = pose.transformNormal(new Vector3d(facing.step()).mul(block.thick));
        Vector3dc logicCenter = getAbsPosition(subLevel);
        final Vec3 wheelPosCenter = new Vec3(logicCenter.x(), logicCenter.y(), logicCenter.z());
        double minExtension = SUSPENSION_TRAVEL;
        Direction minNormal = Direction.UP;
        SubLevel minHitSubLevel = null;
        BlockPos minInteractingBlock = null;

        for (int i = -1; i <= 1; i++) {
            final Vec3 localPosO = wheelPosCenter.add(JOMLConversion.toMojang(normalD).scale(i));
            final Vec3 rayEnd = localPosO.subtract(0.0, SUSPENSION_TRAVEL, 0.0);

            final ClipContext clipContext = new ClipContext(localPosO, rayEnd, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty());
            ((ClipContextExtension) clipContext).sable$setIgnoredSubLevel(Sable.HELPER.getContaining(this));
            final BlockHitResult clipResult = this.level.clip(clipContext);

            if (clipResult.getType() == HitResult.Type.MISS) {
                continue;
            }

            final SubLevel hitSubLevel = Sable.HELPER.getContaining(this.level, clipResult.getLocation());
            final Vec3 localHitPos = pose.transformPositionInverse(hitSubLevel == null ? clipResult.getLocation() :
                    hitSubLevel.logicalPose().transformPosition(clipResult.getLocation()));

            if (clipResult.getLocation().y > wheelPosCenter.y) {
                continue;
            }

            double distToStart = localPosO.distanceTo(localHitPos);
            if (distToStart < 0.05) {
                continue;
            }

            final double dist = wheelPosCenter.y - clipResult.getLocation().y;
            if (dist <= 1e-5) {
                continue;
            }

            final Direction dir = clipResult.getDirection();
            final Vector3d hitNormal = new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            if (hitSubLevel != null) hitSubLevel.logicalPose().transformNormal(hitNormal);
            pose.transformNormalInverse(hitNormal);
            double dot = hitNormal.dot(0.0, 1.0, 0.0);

            if (dot < 0.5) {
                continue;
            }

            if (dist < minExtension) {
                minExtension = dist;
                minNormal = clipResult.getDirection();
                minHitSubLevel = hitSubLevel;
                minInteractingBlock = clipResult.getBlockPos();
            }
        }

        return new TerrainCastResult(minExtension, minNormal, minHitSubLevel, minInteractingBlock);
    }


    /* ==================== 弹簧悬挂 ==================== */

    /**
     * Sable 物理 tick。
     * 每帧计算弹簧力 + 阻尼力 + 摩擦力，通过冲量施加到刚体上。
     *
     * <pre>
     *   弹簧力 Fs = k × x       (x = 压缩量, k = stiffness)
     *   阻尼力 Fd = −c × v_y   (v_y = 车身垂向速度, c = damping)
     *   F_total = Fs + Fd ≥ 0  (悬挂不产生拉力)
     *   摩擦力 Ff = μ × Fs     (μ = friction, 水平方向, 反对速度)
     * </pre>
     */
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        final BlockState blockState = getBlockState();
        final SimpleWheelBlock block = (SimpleWheelBlock) blockState.getBlock();
        final Vector3dc position = getAbsPosition(subLevel);
        final Pose3dc pose = subLevel.logicalPose();
        final Vector3dc upward = (new Vector3d(0, 1, 0));

        // 轮轴侧向方向（垂直于 FACING）
        final Direction lateralDir = blockState.getValue(SimpleWheelBlock.FACING);
        final Vector3d lateral = pose.transformNormal(new Vector3d(lateralDir.step()));

        // ── 地面探测 ──
        final TerrainCastResult terrain = computeMaxExtensionToTerrain(subLevel);
        final double compression = Math.max(block.radius - terrain.maxExtension(), 0);

        // ── 弹簧力（向上为正）──
        final Vector3d springForce = upward.mul(block.stiffness * compression, new Vector3d());
        subLevel.getOrCreateQueuedForceGroup(ForceGroups.LIFT.get()).applyAndRecordPointForce(position, springForce);

    }

    /* ==================== 轮心偏移 ==================== */

    @Override
    public Vector3dc getLogicCenter() {
        BlockState blockState = getBlockState();
        SimpleWheelBlock block = (SimpleWheelBlock) blockState.getBlock();
        return block.getWheelCenter(blockState);
    }

    // ==================== 内部记录 ====================

    private record TerrainCastResult(double maxExtension, @NotNull Direction normal, @Nullable SubLevel subLevel,
                                     @Nullable BlockPos minInteractingBlock) {  }
}
