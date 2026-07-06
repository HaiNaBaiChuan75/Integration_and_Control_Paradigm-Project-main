package com.hainabaichuan75.iac_p.ecs.v2.component.rotation;

import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import static com.hainabaichuan75.iac_p.ecs.v2.component.rotation.Rotation.IDENTITY;

/**
 * Part 的坐标变换句柄 —— 在 Part-local 与父 Level 世界坐标之间双向切换。
 * <p>
 * 解决 {@link Pose3dc} 的 {@code transformPosition} / {@code transformPositionInverse}
 * 容易混淆方向的问题，用显式方法名代替。
 * <pre>{@code
 * var t = PartTransform.of(part);
 * Vector3dc local  = t.toRelativePos(worldPos);   // 世界 → Part 局部（瞄准用）
 * Vector3dc world  = t.fromRelativePos(localPos);  // Part 局部 → 世界（渲染用）
 * Vector3dc localN = t.toRelativeNormal(worldN);   // 世界 → Part 局部（法线）
 * Vector3dc worldN = t.fromRelativeNormal(localN); // Part 局部 → 世界（法线）
 * }</pre>
 *
 * @param orientation Part 自身朝向四元数
 * @param subPose     SubLevel 的 logicalPose（local → logical 变换）
 * @param blockCenter 方块中心在 SubLevel 局部坐标中的位置
 */
public record PartTransform(@NotNull Quaterniondc orientation, @NotNull Pose3dc subPose,
                            @NotNull Vector3dc blockCenter) {

    // ====================================================================
    //  防御性拷贝（JOML 对象约定：入口拷贝）
    // ====================================================================

    public PartTransform {
        orientation = new Quaterniond(orientation);
        subPose = new Pose3d(subPose);
        blockCenter = new Vector3d(blockCenter);
    }

    // ====================================================================
    //  工厂方法
    // ====================================================================

    /**
     * 从 v2 Part 构建变换句柄。
     * <p>
     * 朝向通过 {@link #resolveOrientation(Part)} 的 fallback 链解析。
     *
     * @param part v2 Part 实例
     * @return PartTransform
     */
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull PartTransform of(@NotNull Part part) {
        return of(part.getBlockEntity(), resolveOrientation(part));
    }

    /**
     * 从方块实体 + 显式朝向构建变换句柄。
     *
     * @param be          方块实体
     * @param orientation Part 自身朝向（无旋转时传单位四元数）
     * @return PartTransform
     */
    @Contract(value = "_, _ -> new", pure = true)
    public static @NotNull PartTransform of(@NotNull BlockEntity be, @NotNull Quaterniondc orientation) {
        return new PartTransform(orientation, getSubLevelPose(be), JOMLConversion.atCenterOf(be.getBlockPos()));
    }

    // ====================================================================
    //  朝向解析（Fallback 链）
    // ====================================================================

    /**
     * 沿 fallback 链解析部件的旋转朝向。
     * <p>
     * 按 {@link Rotation} &rarr; {@link CubeRotation} &rarr; 单位四元数的顺序查找，
     * 返回第一个非 null 结果。
     *
     * @param part 部件实例
     * @return 旋转四元数，永不 null
     */
    @Contract(pure = true)
    public static @NotNull Quaterniondc resolveOrientation(@NotNull Part part) {
        Rotation rot = part.getComponent(Rotation.KEY);
        if (rot != null) return rot.quaternion();

        CubeRotation cube = part.getComponent(CubeRotation.KEY);
        if (cube != null) return cube.quaternion;

        return IDENTITY.quaternion();
    }

    // ====================================================================
    //  SubLevel 查询
    // ====================================================================

    @Contract(pure = true)
    private static @Nullable SubLevel getSubLevel(@NotNull BlockEntity be) {
        return Sable.HELPER.getContaining(be);
    }

    /**
     * 获取 SubLevel 的逻辑姿态（local &rarr; logical 变换）。
     * <p>
     * SubLevel 不存在时返回单位姿态（零平移、单位朝向、单位缩放），
     * 使下游计算不会 NPE，得到的是未变换的 local 坐标。
     */
    @Contract(pure = true)
    private static @NotNull Pose3d getSubLevelPose(@NotNull BlockEntity be) {
        SubLevel subLevel = getSubLevel(be);
        return subLevel == null ? new Pose3d() : subLevel.logicalPose();
    }

    // ====================================================================
    //  坐标变换（位置点，含平移）
    // ====================================================================

    /**
     * 世界坐标 → Part 局部坐标。
     * <p>
     * 链：{@code world} → {@code subPose⁻¹} → SubLevel → {@code -blockCenter}
     * → 相对方块中心 → {@code orientation⁻¹} → local 空间。
     *
     * @param worldPos 父 Level 世界坐标中的位置
     * @return Part 局部坐标（相对于方块中心）
     */
    @Contract(value = "_ -> new", pure = true)
    public @NotNull Vector3dc toRelativePos(@NotNull Vector3dc worldPos) {
        var inSubLevel = subPose.transformPositionInverse(new Vector3d(worldPos));
        inSubLevel.sub(blockCenter);
        orientation.transformInverse(inSubLevel);
        return inSubLevel;
    }

    /**
     * Part 局部坐标 → 世界坐标。
     * <p>
     * 链：{@code localPos} → {@code orientation} → Part 转 →
     * {@code +blockCenter} → SubLevel → {@code subPose} → world。
     *
     * @param localPos Part 局部坐标（相对于方块中心）
     * @return 父 Level 世界坐标中的位置
     */
    @Contract(value = "_ -> new", pure = true)
    public @NotNull Vector3dc fromRelativePos(@NotNull Vector3dc localPos) {
        var step = orientation.transform(new Vector3d(localPos));
        step.add(blockCenter);
        return subPose.transformPosition(step);
    }

    // ====================================================================
    //  法线变换（不含平移，仅旋转 + 缩放）
    // ====================================================================

    /**
     * 世界法线 → Part 局部法线。
     *
     * @param worldNormal 父 Level 世界坐标中的法线向量
     * @return Part 局部法线
     */
    @Contract(value = "_ -> new", pure = true)
    public @NotNull Vector3dc toRelativeNormal(@NotNull Vector3dc worldNormal) {
        var step = subPose.transformNormalInverse(new Vector3d(worldNormal));
        orientation.transformInverse(step);
        return step;
    }

    /**
     * Part 局部法线 → 世界法线。
     *
     * @param localNormal Part 局部法线
     * @return 父 Level 世界坐标中的法线向量
     */
    @Contract(value = "_ -> new", pure = true)
    public @NotNull Vector3dc fromRelativeNormal(@NotNull Vector3dc localNormal) {
        var step = orientation.transform(new Vector3d(localNormal));
        return subPose.transformNormal(step);
    }
}
