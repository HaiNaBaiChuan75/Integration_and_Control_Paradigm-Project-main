package com.hainabaichuan75.iac_p.ecs.v2.part.rotation;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.ecs.v2.part.Part;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import static com.hainabaichuan75.iac_p.ecs.v2.part.rotation.Rotation.IDENTITY;

/**
 * 部件旋转与姿态 —— 朝向解析 + 坐标变换。
 * <p>
 * 两个职责：
 * <ol>
 *   <li><b>朝向解析</b> — {@link #resolveOrientation(Part)}
 *       按 fallback 链查找部件的旋转状态</li>
 *   <li><b>坐标变换</b> — {@link #logicalPose(BlockEntity, Quaterniondc)}
 * </ol>
 * <p>
 * <b>朝向解析的 fallback 链</b>（由 {@link #resolveOrientation(Part)} 执行）：
 * <ol>
 *   <li>{@link Rotation} — 任意四元数（45&deg; 椅子等）</li>
 *   <li>{@link CubeRotation} — 24 种离散立方体旋转</li>
 *   <li>{@link BlockState} — 读取 {@code FACING} 属性</li>
 *   <li>默认 — 单位四元数（北方）</li>
 * </ol>
 */
public final class PartRotation {

    private PartRotation() {}

    // ====================================================================
    //  朝向解析（Fallback 链）
    // ====================================================================

    /**
     * 沿 fallback 链解析部件的旋转朝向。
     * <p>
     * 按 {@link Rotation} &rarr; {@link CubeRotation}
     * &rarr; BlockState {@code FACING} &rarr; 单位四元数的顺序查找，
     * 返回第一个非 null 结果。
     *
     * @param part 部件实例
     * @return 旋转四元数，永不 null
     */
    @Contract(pure = true)
    public static @NotNull Quaterniondc resolveOrientation(@NotNull Part part) {
        // 1. 任意四元数（最精确，优先）
        Rotation rot = part.getComponent(Rotation.KEY);
        if (rot != null) return rot.quaternion();

        // 2. 24 种立方体旋转
        CubeRotation cube = part.getComponent(CubeRotation.KEY);
        if (cube != null) return cube.quaternion;

        // 3. BlockState FACING 属性
        Quaterniondc fromBlock = fromBlockState(part.getBlockEntity().getBlockState());
        if (fromBlock != null) return fromBlock;

        // 5. 默认：单位四元数（北方）
        return IDENTITY.quaternion();
    }

    /**
     * 从 BlockState 的标准 {@code FACING} 属性提取方向并转换为四元数。
     * <p>
     * 先后检查 {@link BlockStateProperties#HORIZONTAL_FACING} 和
     * {@link BlockStateProperties#FACING}，前者优先。
     *
     * @param state 方块状态
     * @return 四元数，无 {@code FACING} 属性时返回 {@code null}
     */
    @Nullable
    @Contract(pure = true)
    private static Quaterniondc fromBlockState(@NotNull BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction dir = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return CubeRotation.fromDirection(dir).quaternion;
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction dir = state.getValue(BlockStateProperties.FACING);
            return CubeRotation.fromDirection(dir).quaternion;
        }
        return null;
    }

    // ====================================================================
    //  便捷：解析 + 计算一步完成
    // ====================================================================

    /**
     * 解析部件朝向并计算组合姿态（一步完成）。
     * <p>
     * 等价于：
     * <pre>{@code
     * Quaterniondc q = resolveOrientation(part);
     * return logicalPose(part.getBlockEntity(), q);
     * }</pre>
     *
     * @param part 部件实例
     * @return 组合 Pose3d
     */
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull Pose3d logicalPose(@NotNull Part part) {
        return logicalPose(part.getBlockEntity(), resolveOrientation(part));
    }

    // ====================================================================
    //  SubLevel 查询
    // ====================================================================

    /**
     * 获取指定 BE 所属的 SubLevel。
     *
     * @param be 方块实体
     * @return SubLevel，未关联时返回 {@code null}
     */
    @Contract(pure = true)
    public static @Nullable SubLevel getSubLevel(@NotNull BlockEntity be) {
        return Sable.HELPER.getContaining(be);
    }

    /**
     * 获取 SubLevel 的逻辑姿态（local &rarr; logical 变换）。
     * <p>
     * SubLevel 不存在时返回单位姿态（零平移、单位朝向、单位缩放），
     * 使下游计算不会 NPE，得到的是未变换的 local 坐标。
     *
     * @param be 方块实体
     * @return SubLevel 的 logicalPose，或单位姿态
     */
    @Contract(pure = true)
    public static @NotNull Pose3d getSubLevelPose(@NotNull BlockEntity be) {
        SubLevel subLevel = getSubLevel(be);
        return subLevel == null ? new Pose3d() : subLevel.logicalPose();
    }

    // ====================================================================
    //  组合变换
    // ====================================================================

    /**
     * 计算 Part 在其父 Level 坐标系中的组合变换。
     * <p>
     * 组合过程：
     * <ol>
     *   <li>获取 SubLevel 的 {@link SubLevel#logicalPose()}（位置、朝向、缩放）</li>
     *   <li>将部件自身的朝向四元数乘到 SubLevel 朝向上</li>
     *   <li>计算方块中心相对 SubLevel 旋转中心的偏移，应用均匀缩放</li>
     *   <li>返回组合后的 Pose3d，可直接通过 {@link Pose3d#transformPosition} 将 local 坐标转为 logical 坐标</li>
     * </ol>
     *
     * @param be          方块实体
     * @param orientation 部件自身朝向（无旋转时传单位四元数）
     * @return 组合 Pose3d，将 Part local 坐标映射到父 Level logical 空间
     */
    @Contract(value = "_, _ -> new", pure = true)
    public static @NotNull Pose3d logicalPose(@NotNull BlockEntity be, @NotNull Quaterniondc orientation) {
        Pose3d subPose = getSubLevelPose(be);

        // ── 缩放处理（Sable 要求均匀缩放）──
        Vector3dc subScale = subPose.scale();
        double sx = subScale.x(), sy = subScale.y(), sz = subScale.z();
        double uniformScale;
        if (Math.abs(sx - sy) > 1e-6 || Math.abs(sx - sz) > 1e-6) {
            IACP.LOGGER.warn("SubLevel logical pose has non-uniform scale [{}, {}, {}], using average.", sx, sy, sz);
            uniformScale = (sx + sy + sz) / 3.0;
        } else {
            uniformScale = sx;
        }
        Vector3d uniformScaleVec = new Vector3d(uniformScale);

        // ── 组合朝向：SubLevel 朝向 x Part 朝向 ──
        Quaterniond combinedOri = new Quaterniond(subPose.orientation()).mul(orientation, new Quaterniond());

        // ── 平移：方块中心 &rarr; SubLevel 旋转中心 &rarr; 缩放 &rarr; 还原到 SubLevel 位置 ──
        BlockPos blockPos = be.getBlockPos();
        Vector3d blockCenter = JOMLConversion.atCenterOf(blockPos);
        Vector3d offset = new Vector3d(blockCenter).sub(subPose.rotationPoint()).mul(uniformScale);
        subPose.orientation().transform(offset);
        offset.add(subPose.position());

        return new Pose3d(offset, combinedOri, new Vector3d(), uniformScaleVec);
    }

    /**
     * 以 {@link Part} + 显式朝向为输入的便捷重载。
     *
     * @param part        Part 实例
     * @param orientation 部件自身朝向
     * @return 组合 Pose3d
     * @see #logicalPose(BlockEntity, Quaterniondc)
     */
    @Contract(value = "_, _ -> new", pure = true)
    public static @NotNull Pose3d logicalPose(@NotNull Part part, @NotNull Quaterniondc orientation) {
        return logicalPose(part.getBlockEntity(), orientation);
    }

    // ====================================================================
    //  世界坐标
    // ====================================================================

    /**
     * 获取方块中心在世界坐标中的位置。
     *
     * @param be 方块实体
     * @return 世界坐标向量
     */
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull Vector3dc centerInWorld(@NotNull BlockEntity be) {
        return getSubLevelPose(be).transformPosition(JOMLConversion.atCenterOf(be.getBlockPos()));
    }

    /**
     * 已持有 SubLevel 的优化重载（避免重复查询）。
     *
     * @param be       方块实体
     * @param subLevel 该 BE 所属的 SubLevel
     * @return 世界坐标向量
     */
    @Contract(value = "_, _ -> new", pure = true)
    public static @NotNull Vector3dc centerInWorld(@NotNull BlockEntity be, @NotNull SubLevel subLevel) {
        Pose3d subPose = subLevel.logicalPose();
        return subPose.transformPosition(JOMLConversion.atCenterOf(be.getBlockPos()));
    }

    @Contract(value = "_ -> new", pure = true)
    public static @NotNull Vector3dc centerInWorld(@NotNull Part part) {
        return centerInWorld(part.getBlockEntity());
    }
}
