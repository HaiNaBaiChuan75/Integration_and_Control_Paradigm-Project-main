package com.hainabaichuan75.iac_p.system;

import com.hainabaichuan75.iac_p.ecs.part.Part;
import com.hainabaichuan75.iac_p.ecs.system.VehicleTickSystem;
import com.hainabaichuan75.iac_p.part.Controller;
import com.hainabaichuan75.iac_p.part.SteeringWheel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.util.Mth;
import org.joml.Vector3dc;

import java.util.List;

/**
 * 车轮转向 System —— 将控制器的横向移动意图解算为各转向轮的转向输入。
 * <p>
 * <b>职责</b>：
 * <ol>
 *   <li>从 {@link Controller#getMovementIntent()} 提取横向（x）分量作为原始转向输入</li>
 *   <li>死区处理，消除摇杆微小偏移导致的漂移</li>
 *   <li>Chasing 平滑插值，避免转向突变</li>
 *   <li>将计算结果写入每个 {@link SteeringWheel#setSteeringInput(double)}</li>
 * </ol>
 * <p>
 * <b>数据流</b>：
 * <pre>
 * 网络包 → Controller.getMovementIntent() (x = 横向)
 *   → SteeringSystem.onTick()
 *     → 提取 x → 死区处理 → chasing 平滑
 *     → SteeringWheel.setSteeringInput(归一化标量 [-1, 1])
 *     → SuspensionPhysicsSystem 读取并映射为实际偏转角
 * </pre>
 * <p>
 * <b>依赖关系</b>：逻辑上独立于引擎 System，但必须早于物理 tick 运行。
 * 在注册表中位于 {@link WeaponAimSystem} 之前。
 * <p>
 * <b>多控制器选举</b>：按 {@code BlockPos} 字典序（x→y→z）选最小者。
 */
public class SteeringSystem implements VehicleTickSystem {

    // ==================================================================
    //  死区与平滑参数
    // ==================================================================

    /**
     * Chasing 速率 {@code (0, 1]}，每 tick 向目标值靠拢的比例。
     * <p>
     * 值越小过渡越平滑（响应变慢），值越大响应越快（可能突兀）。
     * 0.15 ≈ 约 7 tick（350ms @20Hz）收敛至目标。
     */
    private static final double CHASE_RATE = 0.15;

    /**
     * 收敛阈值 —— |delta| 低于此值时直接跳到目标值，
     * 避免微小误差持续 tick 消耗算力。
     */
    private static final double CONVERGENCE_THRESHOLD = 0.005;

    @Override
    public void onTick(ServerSubLevel subLevel, List<? extends Part> parts) {
        // ── 1. 找到主控制器 ─────────────────────────────────────
        Controller ctrl = ControllerElection.findPrimary(parts);
        if (ctrl == null) return;

        Vector3dc movement = ctrl.getMovementIntent();
        if (movement == null) return;

        // ── 2. 提取横向分量（x+ = 右）作为原始转向输入 ──────────
        // Controller 约定：x+ = 右
        // SteeringWheel 约定：正值 = 右转
        // 直接映射，无需取反
        double desiredInput = Mth.clamp(movement.x(), -1.0, 1.0);

        // ── 3. 遍历所有转向轮，写入平滑后的转向输入 ───────────
        for (Part part : parts) {
            if (part instanceof SteeringWheel wheel) {
                double current = wheel.getSteeringInput();
                // Mth.lerp = 低通滤波：current + rate * (target - current)
                double delta = desiredInput - current;
                double smoothed = Math.abs(delta) < CONVERGENCE_THRESHOLD ? desiredInput : Mth.lerp(CHASE_RATE,
                        current, desiredInput);
                wheel.setSteeringInput(smoothed);
            }
        }
    }
}
