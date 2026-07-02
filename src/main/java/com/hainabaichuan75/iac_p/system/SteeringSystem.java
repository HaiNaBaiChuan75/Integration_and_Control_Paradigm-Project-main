package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.block.suspension_test.SuspensionConstants;
import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.ecs.system.VehicleTickSystem;
import com.hainabaichuan75.iac_p.part.Controller;
import com.hainabaichuan75.iac_p.part.WheelPart;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 转向 System —— 速度自适应转向角 + chasingYaw 匀速插值。
 * <p>
 * 来源：{@link com.hainabaichuan75.iac_p.block.suspension_test.SuspensionTestBlockEntity#tick()} 中的转向逻辑。
 * <p>
 * <b>数据流</b>：
 * <ol>
 *   <li>读取 {@link Controller#getTargetSteeringYaw()} 获取原始转向输入</li>
 *   <li>根据 {@link WheelPart#getCurrentWheelRpm()} 做速度自适应角度限制</li>
 *   <li>计算 chasingYaw（匀速趋近目标角度）</li>
 *   <li>写入 {@link WheelPart#setSteeringAngle(double)}</li>
 * </ol>
 */
public class SteeringSystem implements VehicleTickSystem {

    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<? extends Part> parts) {
        // 1. 找到主控输入源
        Controller controller = findPrimaryController(parts);
        if (controller == null) return;

        double rawTarget = controller.getTargetSteeringYaw();

        // 2. 遍历所有车轮，计算并写入转向角
        for (Part part : parts) {
            if (!(part instanceof WheelPart wheel)) continue;

            double target = rawTarget;

            // 无转向输入且启用自动归正时 target=0（否则保持当前角度）
            if (target == 0.0 && !SuspensionConstants.AUTO_CENTER) {
                target = wheel.getChasingYaw();
            }

            // ═══ 速度自适应转向 ═══
            double absRpm = Math.abs(wheel.getCurrentWheelRpm());
            double adaptiveMaxDeg;
            if (absRpm < 100) {
                adaptiveMaxDeg = SuspensionConstants.MAX_STEERING_ANGLE;
            } else if (absRpm > 400) {
                adaptiveMaxDeg = SuspensionConstants.MIN_STEERING_ANGLE;
            } else {
                double t = (absRpm - 100) / 300.0;
                adaptiveMaxDeg = Mth.lerp(t,
                        SuspensionConstants.MAX_STEERING_ANGLE,
                        SuspensionConstants.MIN_STEERING_ANGLE);
            }
            double adaptiveMaxRad = Math.toRadians(adaptiveMaxDeg);
            target = Mth.clamp(target, -adaptiveMaxRad, adaptiveMaxRad);

            // 匀速转向：每 tick 最多转动 STEERING_SPEED 度
            double currentAngle = wheel.getSteeringAngle();
            double yawDiff = target - currentAngle;
            double maxStep = SuspensionConstants.STEERING_SPEED_RAD;
            double newAngle;
            if (Math.abs(yawDiff) <= maxStep) {
                newAngle = target;
            } else {
                newAngle = currentAngle + Math.signum(yawDiff) * maxStep;
            }

            wheel.setSteeringAngle(newAngle);
        }
    }

    /**
     * 从 parts 列表中查找第一个主控 Controller。
     * 按字典序规则选举（目前取第一个出现的）。
     */
    private static Controller findPrimaryController(List<? extends Part> parts) {
        for (Part part : parts) {
            if (part instanceof Controller ctrl) {
                return ctrl;
            }
        }
        return null;
    }
}
