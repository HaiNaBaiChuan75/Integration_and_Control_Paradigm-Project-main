package com.hainabaichuan75.iac_p.core.part;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public abstract class PartBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    public static final Quaterniond IDENTITY_QUAT = new Quaterniond();

    public PartBlockEntity(@NotNull BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState blockState) {
        super(type, pos, blockState);
    }

    @NotNull
    public Quaterniondc orientation() {
        return IDENTITY_QUAT;
    }

    @Nullable
    public SubLevel getSubLevel() {
        return Sable.HELPER.getContaining(this);
    }

    /**
     * 返回一个组合变换 {@link Pose3d}，用于将方块实体自身局部空间中的坐标与世界绝对坐标转换
     * 默认z-方向为前方, x+为右方
     * @return 一个 {@link Pose3d} 实例
     * @see SubLevel#logicalPose()
     * @see Pose3d
     */
    @NotNull
    public Pose3d worldPose() {
        Pose3d subPose = getSubLevelPose();
        Vector3d subScale = subPose.scale();
        double sx = subScale.x, sy = subScale.y, sz = subScale.z;
        double uniformScale;
        if (Math.abs(sx - sy) > 1e-6 || Math.abs(sx - sz) > 1e-6) {
            IACP.LOGGER.warn("SubLevel logical pose has non-uniform scale [{}, {}, {}], using average.", sx, sy, sz);
            uniformScale = (sx + sy + sz) / 3.0;
        } else {
            uniformScale = sx;
        }
        Vector3d uniformScaleVec = new Vector3d(uniformScale);
        Quaterniond combinedOri = subPose.orientation().mul(orientation(), new Quaterniond());
        Vector3d blockCenter = JOMLConversion.atCenterOf(worldPosition);
        Vector3d offset = new Vector3d(blockCenter).sub(subPose.rotationPoint()).mul(uniformScale);          // 均匀缩放分量相乘
        subPose.orientation().transform(offset);
        offset.add(subPose.position());
        return new Pose3d(offset, combinedOri, new Vector3d(), uniformScaleVec);
    }

    @NotNull
    private Pose3d getSubLevelPose() {
        SubLevel subLevel = getSubLevel();
        return subLevel == null ? new Pose3d() : subLevel.logicalPose();
    }

    @NotNull
    public Vector3dc getCenterInWorld() {
        return getSubLevelPose().transformPosition(JOMLConversion.atCenterOf(worldPosition));
    }

}
