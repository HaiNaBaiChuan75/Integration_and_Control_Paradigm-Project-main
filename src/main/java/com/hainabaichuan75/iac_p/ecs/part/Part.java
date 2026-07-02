package com.hainabaichuan75.iac_p.ecs.part;

import com.hainabaichuan75.iac_p.IACP;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 载具部件的核心接口。
 * <p>
 * 定义部件作为载具"零件"所需的契约：
 * 朝向、SubLevel 关联、坐标变换。
 * <p>
 * <b>约定</b>：系统中所有 Part 同时也是 {@link net.minecraft.world.level.block.entity.BlockEntity}，
 * {@link #getBlockPos()} 由 {@link net.minecraft.world.level.block.entity.BlockEntity#getBlockPos()} 提供。
 * 非 BE 的 Part 在目前设计中没有出现，且没有计划支持。
 * <p>
 * 坐标系约定与 {@link com.hainabaichuan75.iac_p.ecs.part.PartBlockEntity} 一致，见其文档。
 *
 * @see PartBlockEntity
 * @see SubLevel#logicalPose()
 */
public interface Part extends BlockEntitySubLevelActor {

    /**
     * 单位四元数（无旋转）。
     * 作为 {@link #orientation()} 的默认返回值。
     */
    Quaterniond IDENTITY_QUAT = new Quaterniond();

    /**
     * 返回部件自身的朝向偏移四元数。默认返回单位四元数（无旋转）。
     * 子类若具有方向则务必重写此方法以提供特定的朝向偏移。
     */
    @NotNull
    default Quaterniondc orientation() {
        return IDENTITY_QUAT;
    }

    ;

    @NotNull
    default BlockEntity getBlockEntity() {
        return (BlockEntity) this;
    }

    ;

    /**
     * 获取此部件所在的 SubLevel。
     * <p>
     * 本方法保留为 abstract 是因为 {@code Sable.HELPER.getContaining()} 要求参数为
     * {@link net.minecraft.world.level.block.entity.BlockEntity} 或 {@link net.minecraft.world.entity.Entity}，
     * 而接口的 default 方法中 {@code this} 无法保证满足此类型约束。
     * 所有实现者（本质上都是 BlockEntity）在实现时只需一行：
     * <pre>{@code
     * return Sable.HELPER.getContaining(this);
     * }</pre>
     *
     * @return SubLevel，如果尚未关联则返回 null
     */
    @Nullable
    default SubLevel getSubLevel() {
        return Sable.HELPER.getContaining(getBlockEntity());
    }

    /**
     * 获取 SubLevel 的逻辑姿态（local → logical 变换）。
     * <p>
     * SubLevel 不存在时返回单位姿态（零平移、单位朝向），
     * 使下游计算不会 NPE，得到的是未变换的 local 坐标。
     */
    @NotNull
    private Pose3d getSubLevelPose() {
        SubLevel subLevel = getSubLevel();
        return subLevel == null ? new Pose3d() : subLevel.logicalPose();
    }

    /**
     * 返回此 Part 在父 Level 坐标系中的组合变换。
     * <p>
     * 与 {@link SubLevel#logicalPose()} 属同一层抽象：
     * 名称中的 "logical" 遵循 Sable 的命名约定，指 SubLevel/Part 所在的父 Level 世界坐标空间。
     * <p>
     * 组合过程：
     * <ol>
     *   <li>获取 SubLevel 的 {@link SubLevel#logicalPose()}（位置、朝向、缩放）</li>
     *   <li>将部件自身的 {@link #orientation()} 乘到 SubLevel 朝向上</li>
     *   <li>计算方块中心相对 SubLevel 旋转中心的偏移，应用均匀缩放</li>
     *   <li>返回组合后的 Pose3d，可直接通过 {@link Pose3d#transformPosition} 将 local 坐标转为 logical 坐标</li>
     * </ol>
     *
     * @return 组合 Pose3d，将 Part local 坐标映射到父 Level logical 空间
     * @see SubLevel#logicalPose()
     * @see Pose3d
     */
    @NotNull
    default Pose3d partLogicalPose() {
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
        Vector3d blockCenter = JOMLConversion.atCenterOf(getBlockEntity().getBlockPos());
        Vector3d offset = new Vector3d(blockCenter).sub(subPose.rotationPoint()).mul(uniformScale);
        subPose.orientation().transform(offset);
        offset.add(subPose.position());
        return new Pose3d(offset, combinedOri, new Vector3d(), uniformScaleVec);
    }

    /**
     * 获取方块中心在世界坐标中的位置。
     *
     * @return 世界坐标向量
     */
    @NotNull
    default Vector3dc getCenterInWorld() {
        return getSubLevelPose().transformPosition(JOMLConversion.atCenterOf(getBlockEntity().getBlockPos()));
    }
}
