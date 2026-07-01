package com.hainabaichuan75.iac_p.ecs.part;

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

/**
 * 载具部件的抽象基类。
 * 所有载具功能方块（悬挂、轮子、武器、驾驶舱）<b>必须</b>继承此类。
 * <p>
 * <b>坐标系约定</b>
 * <p>
 * Sable 定义了双层坐标空间：
 * <ul>
 *   <li><b>SubLevel 内部坐标 (local)</b> — 方块在 SubLevel 内的 BlockPos，原点为其旋转中心</li>
 *   <li><b>父 Level 坐标 (logical)</b> — 经 {@link SubLevel#logicalPose()} 缩放+旋转+平移后的世界坐标，
 *       Sable 称为 "logical" 空间，是物理引擎的参考系</li>
 * </ul>
 * Part 与 SubLevel 一样位于 local 空间，需要同样的 logical 变换才能进入物理/渲染的坐标域：
 * <pre>
 *   Part local 坐标
 *     → {@link #partLogicalPose()} = SubLevel logicalPose × orientation × blockCenterOffset
 *     → logical 空间（父 Level 世界坐标）
 * </pre>
 * z- 方向为前方，x+ 为右方。
 * <p>
 * 继承 {@link BlockEntitySubLevelActor} 接口，自动获得与 Sable SubLevel 的关联。
 *
 * @see SubLevel#logicalPose()
 */
public abstract class PartBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {

    public static final Quaterniond IDENTITY_QUAT = new Quaterniond();

    public PartBlockEntity(@NotNull BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState blockState) {
        super(type, pos, blockState);
    }

    // ============================================================
    //  朝向
    // ============================================================
    /**
     * 返回部件自身的朝向偏移四元数。默认返回单位四元数（无旋转）。
     * 子类若具有方向则务必重写此方法以提供特定的朝向偏移。
     */
    @NotNull
    public Quaterniondc orientation() {
        return IDENTITY_QUAT;
    }

    // ============================================================
    //  SubLevel 关联
    // ============================================================
    /**
     * 获取此部件所在的 SubLevel。
     *
     * @return SubLevel，如果尚未关联则返回 null
     */
    @Nullable
    public SubLevel getSubLevel() {
        return Sable.HELPER.getContaining(this);
    }

    // ============================================================
    //  坐标系变换（核心）
    // ============================================================
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
    public Pose3d partLogicalPose() {
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
        Vector3d offset = new Vector3d(blockCenter).sub(subPose.rotationPoint()).mul(uniformScale);
        subPose.orientation().transform(offset);
        offset.add(subPose.position());
        return new Pose3d(offset, combinedOri, new Vector3d(), uniformScaleVec);
    }

    @NotNull
    private Pose3d getSubLevelPose() {
        SubLevel subLevel = getSubLevel();
        return subLevel == null ? new Pose3d() : subLevel.logicalPose();
    }

    /**
     * 获取方块中心在世界坐标中的位置。
     *
     * @return 世界坐标向量
     */
    @NotNull
    public Vector3dc getCenterInWorld() {
        return getSubLevelPose().transformPosition(JOMLConversion.atCenterOf(worldPosition));
    }
}

