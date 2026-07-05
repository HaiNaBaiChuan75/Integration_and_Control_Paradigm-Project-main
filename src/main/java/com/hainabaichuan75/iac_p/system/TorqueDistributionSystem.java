package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.ecs.system.VehicleTickSystem;
import com.hainabaichuan75.iac_p.part.Controller;
import com.hainabaichuan75.iac_p.part.DriveWheel;
import com.hainabaichuan75.iac_p.part.EnginePart;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.util.Mth;
import org.joml.Vector3dc;

import java.util.List;

/**
 * 扭矩分配 System —— 根据引擎输出和油门输入，向各驱动轮分配扭矩。
 * <p>
 * <b>职责</b>：
 * <ol>
 *   <li>从 {@link Controller#getMovementIntent()} 提取纵向（z）分量作为油门输入</li>
 *   <li>读取 {@link EnginePart#getMaxTorque()} 计算引擎实际输出扭矩</li>
 *   <li>刹车时扭矩归零（刹车力由物理 System 施加）</li>
 *   <li>将引擎扭矩均匀分配到每个 {@link DriveWheel}</li>
 * </ol>
 * <p>
 * <b>数据流</b>：
 * <pre>
 * Controller.getMovementIntent().z
 *   → TorqueDistributionSystem
 *     → 提取 -z → 钳位 → 刹车检查
 *     → EnginePart.setTorque(throttle × maxTorque)
 *     → DriveWheel.setTorqueInput(engineTorque / N)
 *     → SuspensionPhysicsSystem 下一物理 tick 读取并推进
 * </pre>
 * <p>
 * <b>注册顺序</b>：在 {@code VehicleSystemRegistry} 中位于
 * {@link SteeringSystem} 之后（转向先运行）、
 * {@link WeaponAimSystem} 之前。
 * <p>
 * <b>多控制器选举</b>：委托 {@link ControllerElection#findPrimary}。
 */
public class TorqueDistributionSystem implements VehicleTickSystem {

    @Override
    public void onTick(ServerSubLevel subLevel, List<? extends Part> parts) {
        // ── 1. 找到主控制器 ─────────────────────────────────────
        Controller ctrl = ControllerElection.findPrimary(parts);
        if (ctrl == null) return;

        Vector3dc movement = ctrl.getMovementIntent();
        if (movement == null) return;

        // ── 2. 提取纵向分量（z- = 前，z+ = 后）作为油门 ────────
        double throttle = -movement.z();
        throttle = Mth.clamp(throttle, -1.0, 1.0);

        // 刹车时扭矩归零 —— 物理 System 处理刹车力
        if (ctrl.isBraking()) {
            throttle = 0.0;
        }

        // ── 3. 找到引擎，计算输出扭矩 ─────────────────────────
        EnginePart engine = findEngine(parts);
        if (engine == null) return;

        double engineTorque = throttle * engine.getMaxTorque();
        engine.setTorque(engineTorque);

        // ── 4. 统计驱动轮数量 ─────────────────────────────────
        int driveWheelCount = 0;
        for (Part part : parts) {
            if (part instanceof DriveWheel) {
                driveWheelCount++;
            }
        }
        if (driveWheelCount == 0) return;

        // ── 5. 均匀分配扭矩到每个驱动轮 ──────────────────────
        double torquePerWheel = engineTorque / driveWheelCount;
        for (Part part : parts) {
            if (part instanceof DriveWheel wheel) {
                wheel.setTorqueInput(torquePerWheel);
            }
        }
    }

    /**
     * 从 Part 列表中查找唯一的 {@link EnginePart}。
     * <p>
     * 载具应且仅应有一个引擎。超过一个时取第一个（通常也是唯一一个）。
     *
     * @param parts 当前 SubLevel 的所有 Part
     * @return 引擎 Part，或 {@code null}
     */
    private static EnginePart findEngine(List<? extends Part> parts) {
        for (Part part : parts) {
            if (part instanceof EnginePart engine) {
                return engine;
            }
        }
        return null;
    }
}
