package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionConstants;
import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionTestBlockEntity;
import com.hainabaichuan75.iac_p.ecs.part.*;
import com.hainabaichuan75.iac_p.ecs.system.VehicleClientSystem;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

import static com.hainabaichuan75.iac_p.block.suspension_test.SuspensionConstants.*;

/**
 * 轮子视觉 System —— 客户端轮子旋转角度和悬挂视觉更新。
 * <p>
 * 来源：{@link SuspensionTestBlockEntity#tick()} 中的客户端视觉逻辑。
 * <p>
 * <b>数据流</b>：
 * <ol>
 *   <li>读取 {@link WheelPart#getCurrentWheelRpm()}、{@link WheelPart#isBraking()} 等</li>
 *   <li>计算轮子旋转角（angle/angVel）和悬挂视觉伸展量</li>
 *   <li>写入 WheelPart 的视觉字段，供 Renderer 在 partialTick 插值中使用</li>
 * </ol>
 * <p>
 * <b>仅客户端执行</b>：此 System 在 {@link VehicleClientSystem} 中注册，仅在物理客户端运行。
 */
public class WheelVisualSystem implements VehicleClientSystem {

    @Override
    public void onTick(@NotNull ClientSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {
        // 查找引擎/变速箱（用于视觉 RPM 计算）
        TransmissionPart transmission = null;
        EnginePart engine = null;
        for (PartBlockEntity p : parts) {
            if (p instanceof TransmissionPart tp) transmission = tp;
            if (p instanceof EnginePart ep) engine = ep;
        }

        for (PartBlockEntity part : parts) {
            if (!(part instanceof WheelPart)) continue;
            if (!(part instanceof SuspensionTestBlockEntity sw)) continue;

            ItemStack heldItem = sw.getHeldItem();
            TireLike tire = heldItem.get(OffroadDataComponents.TIRE);

            // 无轮子：视觉归零
            if (tire == null) {
                sw.setAngleVisual(0, 0, 0);
                sw.setExtensionVisual(
                        Mth.lerp(0.6, sw.getExtension(), SuspensionConstants.NO_WHEEL_EXT));
                continue;
            }

            float rad = tire.radius();

            // 悬挂伸展视觉平滑
            sw.setExtensionVisual(
                    Mth.lerp(0.7, sw.getExtension(), computeMaxExtension(sw, rad)));

            // 视觉 RPM 计算
            double visualRpm = computeVisualRpm(sw, engine, transmission, subLevel);

            // 轮子旋转角
            if (sw.isLifted()) {
                // 悬空：仅主动 RPM 驱动旋转
                double rpmAV = -visualRpm * RPM_TO_RAD_PER_TICK;
                sw.setAngleVisual(rpmAV, rpmAV);
            } else {
                // 贴地：被动摩擦滚动 + 主动 RPM
                Direction f = sw.getBlockState().getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                Vector3d vel = Sable.HELPER.getVelocity(
                        subLevel.getLevel(),
                        JOMLConversion.atCenterOf(sw.getBlockPos().relative(f)));
                Vector3d lv = subLevel.logicalPose()
                        .transformNormalInverse(vel).div(20.0);
                Vector3dc fwdD = f.getAxis() == Direction.Axis.X
                        ? new Vector3d(0, 0, 1) : new Vector3d(1, 0, 0);

                double trans = lv.dot(fwdD);
                double circ = TWO_PI * rad;
                double frictionDelta = -trans / circ * TWO_PI;
                double rpmDelta = -visualRpm * RPM_TO_RAD_PER_TICK;

                double combinedDelta;
                if (sw.isBraking()) {
                    combinedDelta = 0.0; // 手刹锁轮
                } else if (Math.abs(visualRpm) > 0.1) {
                    combinedDelta = rpmDelta; // 引擎驱动
                } else {
                    combinedDelta = frictionDelta; // 被动滑行
                }

                sw.setAngleVisual(combinedDelta, combinedDelta);
            }
        }
    }

    /**
     * 计算视觉轮子 RPM。
     * <ul>
     *   <li>刹车 → 0（轮子锁死不转）</li>
     *   <li>抓地 → 物理车速转速（currentWheelRpm）</li>
     *   <li>打滑 → 理想转速（engineRpm / 齿比）</li>
     *   <li>无引擎/熄火 → 退化到物理转速</li>
     * </ul>
     */
    private static double computeVisualRpm(SuspensionTestBlockEntity sw,
                                            EnginePart engine, TransmissionPart transmission,
                                            ClientSubLevel subLevel) {
        if (sw.isBraking()) return 0.0;

        if (engine != null && transmission != null && !engine.isStalled()) {
            double idealRpm = transmission.getTargetWheelRpm();
            return sw.isGripping() ? sw.getCurrentWheelRpm() : idealRpm;
        }
        return sw.getCurrentWheelRpm();
    }

    private static double computeMaxExtension(SuspensionTestBlockEntity sw, float rad) {
        // 简化的悬挂伸展计算 - 复用已有的 extension + lerp
        return sw.getExtension();
    }
}
