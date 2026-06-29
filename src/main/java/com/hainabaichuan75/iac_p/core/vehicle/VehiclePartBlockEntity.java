package com.hainabaichuan75.iac_p.core.vehicle;

import com.hainabaichuan75.iac_p.IACP;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public abstract class VehiclePartBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    public static final Quaterniond ZERO_QUATERNIOND = new Quaterniond();

    public VehiclePartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public Quaterniondc orientation() {
        return ZERO_QUATERNIOND;
    }

    @Nullable
    public SubLevel getSubLevel() {
        return Sable.HELPER.getContaining(this);
    }

    /**
     * 返回一个组合变换 {@link Pose3d}，用于将方块实体自身局部空间中的坐标
     * （原点在实体的几何中心，轴向由 {@link #orientation()} 定义）转换到物理引擎的世界坐标。
     * <p>
     * 转换过程等价于：
     * <ol>
     *   <li>对局部坐标先应用实体自身的旋转（{@link #orientation()}）；</li>
     *   <li>再应用其所属 {@link SubLevel} 的逻辑姿态（由 {@link SubLevel#logicalPose()} 提供），
     *       该姿态包含平移、旋转和缩放。</li>
     * </ol>
     * 若 SubLevel 的逻辑姿态包含非均匀缩放，本方法会使用三个轴缩放的平均值作为均匀缩放，
     * 并记录一条警告日志（级别 WARN）。此时结果与严格分步计算可能存在微小偏差。
     * <p>
     * 注意：当 {@link #getSubLevel()} 返回 {@code null} 时，返回单位变换（即局部坐标与世界坐标重合）。
     *
     * @return 一个新的 {@link Pose3d} 实例，代表从实体局部空间到世界空间的刚体变换
     * @see SubLevel#logicalPose()
     * @see Pose3d#transformPosition(Vector3d) 使用该变换将局部点转换到世界坐标
     */
    public Pose3d localPose() {
        SubLevel subLevel = getSubLevel();
        Pose3d subPose = subLevel == null ? new Pose3d() : subLevel.logicalPose();
        Vector3d subScale = subPose.scale();
        double sx = subScale.x, sy = subScale.y, sz = subScale.z;
        // 检查缩放是否均匀，若不均匀则告警并用平均值近似
        double uniformScale;
        if (Math.abs(sx - sy) > 1e-6 || Math.abs(sx - sz) > 1e-6) {
            IACP.LOGGER.warn("SubLevel logical pose has non-uniform scale [{}, {}, {}], using average.", sx, sy, sz);
            uniformScale = (sx + sy + sz) / 3.0;
        } else {
            uniformScale = sx;
        }
        Vector3d uniformScaleVec = new Vector3d(uniformScale);
        // 组合旋转：Q_sub * Q_ent （先施加实体自身朝向，再被 SubLevel 的整体旋转影响）
        Quaterniond combinedOri = subPose.orientation().mul(orientation(), new Quaterniond());
        // 计算平移量：position = Q_sub·( uniformScale · (C_ent - R_sub) ) + P_sub
        Vector3d blockCenter = JOMLConversion.atCenterOf(worldPosition);
        Vector3d offset = new Vector3d(blockCenter).sub(subPose.rotationPoint()).mul(uniformScale);          // 均匀缩放分量相乘
        subPose.orientation().transform(offset); // 旋转该偏移
        offset.add(subPose.position());          // 加上 SubLevel 的世界平移
        // 旋转点置零（因为局部偏移已经以方块中心为原点），缩放使用均匀值
        return new Pose3d(offset, combinedOri, new Vector3d(), uniformScaleVec);
    }

    public Vector3dc getAnchor() {
        return localPose().transformNormalInverse(new Vector3d());
    }

}
