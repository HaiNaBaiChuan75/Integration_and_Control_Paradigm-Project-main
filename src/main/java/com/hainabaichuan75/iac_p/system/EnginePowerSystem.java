package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.cockpit.EngineModel;
import com.hainabaichuan75.iac_p.block.cockpit.PowertrainConstants;
import com.hainabaichuan75.iac_p.block.cockpit.TransmissionModel;
import com.hainabaichuan75.iac_p.ecs.part.*;
import com.hainabaichuan75.iac_p.ecs.system.VehicleTickSystem;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 引擎动力 System —— 发动机计算、变速箱换挡、扭矩分配。
 * <p>
 * 来源：{@link com.hainabaichuan75.iac_p.block.cockpit.CockpitBlockEntity#tick()} 中的引擎/变速箱逻辑。
 * <p>
 * <b>执行顺序</b>：应在 {@link SteeringSystem} 之后、{@link WeaponAimSystem} 无关顺序、
 * 客户端同步之前执行。
 * <p>
 * <b>数据流</b>：
 * <ol>
 *   <li>从 {@link Controller} 读取油门输入</li>
 *   <li>从 {@link WheelPart} 读取轮速（用于负载/换挡决策）</li>
 *   <li>内部持有引擎模型和变速箱模型，计算 RPM、扭矩、档位</li>
 *   <li>写入 {@link EnginePart} / {@link TransmissionPart} 状态</li>
 *   <li>计算轮上扭矩分配，写入 {@link WheelPart#setTorqueInput(double)}</li>
 * </ol>
 */
public class EnginePowerSystem implements VehicleTickSystem {

    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {
        // 1. 找到关键部件
        Controller ctrl = findPart(parts, Controller.class);
        EnginePart engine = findPart(parts, EnginePart.class);
        TransmissionPart transmission = findPart(parts, TransmissionPart.class);
        if (engine == null || transmission == null) return;

        // 2. 收集轮速数据
        WheelScanResult wheels = scanWheelRpm(parts);
        int wheelCount = Math.max(wheels.wheelCount, 1);

        // 3. 引擎永远独立运行：油门直控
        double throttleLevel = 1.0;
        var engResult = EngineModel.computeThrottleControlledRun(throttleLevel);
        engine.setTorque(engResult.engineTorque());
        engine.setRpm(engResult.rpm());

        // 4. 熄火检查
        if (engine.isStalled()) {
            setCockpitTorquePerWheel(parts, 0);
            setAllTorqueInput(parts, 0);
            return;
        }

        // 5. 换挡真空期处理
        if (tryProcessShifting(engine, transmission)) {
            setCockpitTorquePerWheel(parts, 0);
            setAllTorqueInput(parts, 0);
            return;
        }

        // 6. 空档自动挂档
        if (transmission.getGear() == 0 && ctrl != null && ctrl.getRawThrottleDirection() != 0) {
            transmission.setGear(1);
            transmission.setShifting(false);
            transmission.setShiftingTimer(0);
            transmission.setTargetShiftGear(0);
            transmission.setRevMatchTargetRpm(0);
        }

        // 7. 扭矩分配
        if (transmission.getGear() == 0) {
            setCockpitTorquePerWheel(parts, 0);
            setAllTorqueInput(parts, 0);
        } else {
            transmission.setLastWheelCount(wheelCount);
            var gbOut = TransmissionModel.computeOutput(engine.getTorque(), engine.getRpm(), transmission.getGear());
            double torquePerWheel = gbOut.torqueB() / wheelCount;

            // 写回 CockpitBE 的 torquePerWheel（供 SuspensionBE 物理 tick 通过 getTorquePerWheel() 读取）
            setCockpitTorquePerWheel(parts, torquePerWheel);

            // 写入各轮扭矩（正=前进方向，按 WASD 符号调整）
            double direction = getDirectionFromController(ctrl);
            for (PartBlockEntity part : parts) {
                if (part instanceof WheelPart wheel) {
                    wheel.setTorqueInput(torquePerWheel * direction);
                }
            }

            // 憋住救急
            double curSpeed = Math.abs(wheels.avgWheelRpm()) * Math.PI * 2.0 / 60.0 * 0.5;
            tryStallRescue(engine, transmission, ctrl, curSpeed);

            // 自动降档
            tryAutoDownshift(engine, transmission, ctrl, wheels, subLevel);

            // 自动升档
            tryAutoUpshift(engine, transmission, ctrl, subLevel);
        }
    }

    // ==================================================================
    //  换挡处理
    // ==================================================================

    /**
     * 处理换挡真空期。降档时执行 Rev-Match 自动补油。
     *
     * @return true 如果仍在换挡真空期（调用方应跳过扭矩输出）
     */
    private boolean tryProcessShifting(EnginePart engine, TransmissionPart transmission) {
        if (!transmission.isShifting()) return false;

        double throttleLevel = 1.0;
        if (transmission.getRevMatchTargetRpm() > 0) {
            double rpmNow = EngineModel.computeThrottleControlledRun(throttleLevel).rpm();
            if (transmission.getRevMatchTargetRpm() > rpmNow) {
                double blip = (transmission.getRevMatchTargetRpm() - PowertrainConstants.ENGINE_IDLE_RPM)
                        / (PowertrainConstants.ENGINE_MAX_RPM - PowertrainConstants.ENGINE_IDLE_RPM);
                throttleLevel = Math.max(throttleLevel, Math.min(blip, 0.8));
            }
        }

        var sr = EngineModel.computeThrottleControlledRun(throttleLevel);
        engine.setRpm(sr.rpm());

        if (transmission.getShiftingTimer() - 1 <= 0) {
            transmission.setGear(transmission.getTargetShiftGear());
            transmission.setShifting(false);
            IACP.LOGGER.debug("[EnginePower] 换挡完成 → {}", PowertrainConstants.gearName(transmission.getGear()));
        } else {
            transmission.setShiftingTimer(transmission.getShiftingTimer() - 1);
        }
        return true;
    }

    /**
     * 憋住救急：静止踩油门下从高档直接瞬跳 1 档。
     */
    private void tryStallRescue(EnginePart engine, TransmissionPart transmission,
                                 @Nullable Controller ctrl, double currentSpeed) {
        if (!transmission.isAutoShiftEnabled() || transmission.getGear() <= 1) return;
        if (ctrl == null || (!ctrl.isThrottleForward() && !ctrl.isThrottleBackward())) return;
        if (currentSpeed >= 0.5) return;

        IACP.LOGGER.info("[EnginePower] 憋住救急: 瞬跳 1 档 (gear={})", transmission.getGear());
        transmission.setGear(1);
        transmission.setShifting(false);
        transmission.setShiftingTimer(0);
        transmission.setTargetShiftGear(0);
        transmission.setRevMatchTargetRpm(0);
    }

    /**
     * 自动降档：当前速度低于低一档在当前 RPM 下的理想速度时降档。
     */
    private void tryAutoDownshift(EnginePart engine, TransmissionPart transmission,
                                   @Nullable Controller ctrl, WheelScanResult wheels,
                                   ServerSubLevel subLevel) {
        if (!transmission.isAutoShiftEnabled() || transmission.isShifting()
                || transmission.getGear() < 2 || ctrl == null
                || (!ctrl.isThrottleForward() && !ctrl.isThrottleBackward())) return;

        int gameTime = (int) subLevel.getLevel().getGameTime();
        if (gameTime - transmission.getLastShiftTick() <= 40) return;

        double currentSpeed = Math.abs(wheels.avgWheelRpm()) * Math.PI * 2.0 / 60.0 * 0.5;
        double prevIdealRpm = TransmissionModel.computeTargetWheelRpm(
                transmission.getGear() - 1, engine.getRpm());
        double prevIdealSpeed = Math.abs(prevIdealRpm) * Math.PI * 2.0 / 60.0 * 0.5;

        if (currentSpeed < prevIdealSpeed && currentSpeed > 0.5) {
            IACP.LOGGER.info("[EnginePower] 自动降档: {}→{} (speed {} < ideal({}) {})",
                    transmission.getGear(), transmission.getGear() - 1,
                    String.format("%.1f", currentSpeed),
                    transmission.getGear() - 1,
                    String.format("%.1f", prevIdealSpeed));
            doGearChange(transmission,
                    TransmissionModel.gearDown(transmission.getGear(), engine.getRpm()));
            transmission.setLastShiftTick(gameTime);
        }
    }

    /**
     * 自动升档：每 10 tick 检查加速度和速度，条件满足时升档。
     */
    private void tryAutoUpshift(EnginePart engine, TransmissionPart transmission,
                                 @Nullable Controller ctrl, ServerSubLevel subLevel) {
        if (!transmission.isAutoShiftEnabled() || transmission.isShifting()
                || transmission.getGear() < 1
                || transmission.getGear() >= PowertrainConstants.NUM_FORWARD_GEARS) {
            transmission.setUpshiftTimer(0);
            return;
        }

        int gameTime = (int) subLevel.getLevel().getGameTime();
        if (gameTime - transmission.getLastShiftTick() <= 30) return;
        if (transmission.getUpshiftTimer() + 1 < 10) {
            transmission.setUpshiftTimer(transmission.getUpshiftTimer() + 1);
            return;
        }

        transmission.setUpshiftTimer(0);
        double nowSpeed = 0;
        try {
            var vel = dev.ryanhcode.sable.Sable.HELPER.getVelocity(
                    subLevel.getLevel(),
                    new org.joml.Vector3d(0, 0, 0)); // 使用 SubLevel 整体速度？无法获取 Part 位置
            if (vel != null) nowSpeed = vel.length();
        } catch (Exception ignored) {}

        double accel = Math.abs(nowSpeed - transmission.getLastUpshiftSpeed()) / 0.5;
        transmission.setLastUpshiftSpeed(nowSpeed);

        double prevTargetRpm = TransmissionModel.computeTargetWheelRpm(
                transmission.getGear() - 1, engine.getRpm());
        double prevIdealSpeed = Math.abs(prevTargetRpm) * Math.PI * 2.0 / 60.0 * 0.5;

        if (accel < 1.0 && nowSpeed > prevIdealSpeed && nowSpeed > 0.5) {
            IACP.LOGGER.info("[EnginePower] 自动升档: {}→{} (accel={}, speed={})",
                    transmission.getGear(), transmission.getGear() + 1,
                    String.format("%.2f", accel),
                    String.format("%.1f", nowSpeed));
            doGearChange(transmission,
                    TransmissionModel.gearUp(transmission.getGear(), engine.getRpm()));
            transmission.setLastShiftTick(gameTime);
        }
    }

    // ==================================================================
    //  工具方法
    // ==================================================================

    /**
     * 执行换挡：启动换挡序列。
     */
    private void doGearChange(TransmissionPart transmission, TransmissionModel.GearShiftResult result) {
        if (transmission.isShifting()) return;
        if (result.gear() == transmission.getGear()) return;

        transmission.setShifting(true);
        transmission.setShiftingTimer(PowertrainConstants.SHIFT_TIME_TICKS);
        transmission.setTargetShiftGear(result.gear());

        // Rev-Match
        int currentGear = transmission.getGear();
        if (currentGear >= 2 && result.gear() >= 1 && result.gear() < currentGear) {
            double oldRatio = PowertrainConstants.getRatioForGear(currentGear);
            double newRatio = PowertrainConstants.getRatioForGear(result.gear());
            double targetRpm = result.engineRpm() * newRatio / oldRatio;
            targetRpm = net.minecraft.util.Mth.clamp(
                    targetRpm, PowertrainConstants.ENGINE_IDLE_RPM, PowertrainConstants.ENGINE_MAX_RPM);
            transmission.setRevMatchTargetRpm(targetRpm);
        } else {
            transmission.setRevMatchTargetRpm(0);
        }

        // 同步 RPM
        // (result.engineRpm already has the sync'd RPM from TransmissionModel)
    }

    /**
     * 从 Controller 获取方向符号。
     */
    private static double getDirectionFromController(@Nullable Controller ctrl) {
        if (ctrl == null) return 0;
        if (ctrl.isThrottleForward() && !ctrl.isThrottleBackward()) return 1.0;
        if (ctrl.isThrottleBackward() && !ctrl.isThrottleForward()) return -1.0;
        return 0;
    }

    /**
     * 扫描所有轮子的 RPM。
     */
    private static WheelScanResult scanWheelRpm(List<PartBlockEntity> parts) {
        double totalRpm = 0;
        int count = 0;
        for (PartBlockEntity part : parts) {
            if (part instanceof WheelPart wheel) {
                totalRpm += wheel.getCurrentWheelRpm();
                count++;
            }
        }
        return new WheelScanResult(count > 0 ? totalRpm / count : 0, count);
    }

    /**
     * 将 torquePerWheel 写回 CockpitBlockEntity（供 SuspensionBE 物理 tick 通过 getTorquePerWheel() 读取）。
     * <p>
     * 这是迁移期的桥接方法：待 SuspensionPhysicsSystem 启用后，此方法可移除。
     */
    private static void setCockpitTorquePerWheel(List<PartBlockEntity> parts, double torque) {
        for (PartBlockEntity part : parts) {
            if (part instanceof com.hainabaichuan75.iac_p.block.cockpit.CockpitBlockEntity cockpit) {
                cockpit.setTorquePerWheel(torque);
                return;
            }
        }
    }

    /**
     * 设置所有轮子的扭矩输入。
     */
    private static void setAllTorqueInput(List<PartBlockEntity> parts, double torque) {
        for (PartBlockEntity part : parts) {
            if (part instanceof WheelPart wheel) {
                wheel.setTorqueInput(torque);
            }
        }
    }

    /**
     * 从 parts 列表中查找第一个指定类型的实例。
     */
    @Nullable
    private static <T> T findPart(List<PartBlockEntity> parts, Class<T> type) {
        for (PartBlockEntity part : parts) {
            if (type.isInstance(part)) {
                return type.cast(part);
            }
        }
        return null;
    }

    private record WheelScanResult(double avgWheelRpm, int wheelCount) {}
}
