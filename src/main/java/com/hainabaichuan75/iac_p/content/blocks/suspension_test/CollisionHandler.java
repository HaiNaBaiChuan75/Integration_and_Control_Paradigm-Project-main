/*
 * 地形碰撞检测处理器 —— 悬挂与地面的射线碰撞检测。
 *
 * 职责：
 *   1. rayTerrain：从悬挂位置向下发射 3 条射线，检测地面距离和法线
 *   2. compMaxExt：计算当前悬挂的最大伸展量，检测轮子是否离地
 *
 * 从 SuspensionTestBlockEntity 提取的纯逻辑层，不持有 BE 引用。
 * 所有需要的外部参数通过方法参数传入。
 */
package com.hainabaichuan75.iac_p.content.blocks.suspension_test;

import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.util.Mth;

/**
 * 地形碰撞检测处理器。
 * <p>
 * 纯函数风格：所有输入通过参数传递，无内部状态。
 */
public final class CollisionHandler {

    private CollisionHandler() {}

    /**
     * 射线检测结果。
     *
     * @param maxExtension       最大伸展量（从悬挂点到地面的距离）
     * @param normal             地面法线方向
     * @param subLevel           命中的 SubLevel（如果有）
     * @param minInteractingBlock 最近交互方块的位置
     */
    public record TerrainCastResult(
            double maxExtension,
            Direction normal,
            @Nullable SubLevel subLevel,
            @Nullable BlockPos minInteractingBlock
    ) {}

    // ==================================================================
    //  fudge：地面摩擦系数平滑处理
    // ==================================================================

    /**
     * 对地面摩擦系数做平滑处理：低摩擦地面保底 0.1 + 0.9×原值， 防止冰面等极端低摩擦表面导致车辆完全失控。
     */
    public static double fudgeGroundFriction(double rawFriction) {
        return rawFriction < 1 ? 0.1 + 0.9 * rawFriction : rawFriction;
    }

    // ==================================================================
    //  射线检测
    // ==================================================================

    /**
     * 从悬挂位置向下发射 3 条射线（沿 nd 方向偏移 -1, 0, +1）， 检测地面距离和法线。
     * <p>
     * 与 SuspensionTestBlockEntity 原始实现完全一致。
     *
     * @param level   世界
     * @param blockPos 悬挂方块的世界坐标
     * @param facing  悬挂方块的朝向
     * @param nd      横向偏移方向单位向量
     * @param pose    SubLevel 的位姿（用于坐标变换）
     * @param ignoredSubLevel 需要忽略的 SubLevel（自身）
     * @return 射线检测结果
     */
    public static TerrainCastResult rayTerrain(
            Level level, BlockPos blockPos, Direction facing,
            Vector3dc nd, Pose3dc pose, @Nullable SubLevel ignoredSubLevel) {

        Vec3 c = blockPos.relative(facing).getCenter();
        double minE = 5.0;
        Direction minN = Direction.UP;
        SubLevel minSL = null;
        BlockPos minBP = null;

        for (int i = -1; i <= 1; i++) {
            Vec3 o = c.add(JOMLConversion.toMojang(nd).scale(i));
            ClipContext ctx = new ClipContext(o, o.subtract(0, 5, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
            ((ClipContextExtension) ctx).sable$setIgnoredSubLevel(ignoredSubLevel);
            BlockHitResult hit = level.clip(ctx);
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }

            SubLevel hsl = Sable.HELPER.getContaining(level, hit.getLocation());
            Vec3 lh = pose.transformPositionInverse(
                    hsl == null ? hit.getLocation() : hsl.logicalPose().transformPosition(hit.getLocation()));
            if (lh.y > c.y || o.distanceTo(lh) < 0.05) {
                continue;
            }
            double d = c.y - lh.y;
            if (d <= 1e-5) {
                continue;
            }

            Vector3d hn = new Vector3d(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
            if (hsl != null) {
                hsl.logicalPose().transformNormal(hn);
            }
            pose.transformNormalInverse(hn);
            if (hn.dot(0, 1, 0) < 0.5) {
                continue;
            }
            if (d < minE) {
                minE = d;
                minN = hit.getDirection();
                minSL = hsl;
                minBP = hit.getBlockPos();
            }
        }
        return new TerrainCastResult(minE, minN, minSL, minBP);
    }

    // ==================================================================
    //  最大伸展量计算
    // ==================================================================

    /**
     * 计算当前悬挂的最大伸展量，检测轮子是否离地。
     * <p>
     * 与原 SuspensionTestBlockEntity.compMaxExt 逻辑完全一致。
     *
     * @param level         世界
     * @param blockPos      悬挂方块的世界坐标
     * @param facing        悬挂方块的朝向
     * @param wheelRadius   轮子半径
     * @param maxExt        悬挂最大伸展常量
     * @param ignoredSubLevel 需要忽略的 SubLevel（自身）
     * @param pose          SubLevel 的位姿
     * @param nd            横向偏移方向单位向量
     * @param outLifted     输出：轮子是否离地
     * @param outTouchFriction 输出：地面摩擦系数
     * @return 钳制后的最大伸展量
     */
    public static double computeMaxExtension(
            Level level, BlockPos blockPos, Direction facing,
            float wheelRadius, double maxExt, @Nullable SubLevel ignoredSubLevel,
            Pose3dc pose, Vector3dc nd,
            MutableBoolean outLifted, MutableDouble outTouchFriction) {

        var r = rayTerrain(level, blockPos, facing, nd, pose, ignoredSubLevel);
        double u = r.maxExtension - wheelRadius;
        outLifted.value = u > maxExt;

        double friction = 1.0;
        if (r.minInteractingBlock() != null) {
            BlockState state = level.getBlockState(r.minInteractingBlock());
            friction = fudgeGroundFriction(PhysicsBlockPropertyHelper.getFriction(state));
        }
        outTouchFriction.value = friction;

        return Mth.clamp(u, -0.45, maxExt);
    }

    // ==================================================================
    //  可变值容器（避免每次分配 double[]）
    // ==================================================================

    /**
     * 可变的 boolean 值容器，用于返回多个输出参数。
     */
    public static final class MutableBoolean {
        public boolean value;
    }

    /**
     * 可变的 double 值容器，用于返回多个输出参数。
     */
    public static final class MutableDouble {
        public double value;
    }
}
