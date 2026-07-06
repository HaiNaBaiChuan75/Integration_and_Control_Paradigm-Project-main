package com.hainabaichuan75.iac_p.ecs.v2.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View.Views2;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.TickSystem;
import com.hainabaichuan75.iac_p.ecs.v2.component.ControlState;
import com.hainabaichuan75.iac_p.ecs.v2.component.GimbalDef;
import com.hainabaichuan75.iac_p.ecs.v2.component.GimbalState;
import com.hainabaichuan75.iac_p.ecs.v2.component.rotation.PartTransform;
import com.hainabaichuan75.iac_p.part.field.YawPitch;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3dc;

import java.util.List;

/**
 * 瞄准解算 System —— 将控制器的瞄准目标解算为各云台的目标角速度（速度指令）。
 * <p>
 * <b>职责</b>：只设置角速度，不修改角度。速度积分由 {@link GimbalSystem} 完成。
 * <p>
 * <b>数据流</b>：
 * <pre>
 * ControlState.aimTarget（世界坐标）
 *   → PartTransform.toRelativePos() → 局部方向向量
 *   → YawPitch.from() → 目标角度
 *   → GimbalDef 钳制（角度限位 + 速度限幅）
 *   → GimbalState.velYaw / velPitch
 * </pre>
 * <p>
 * 无瞄准目标（aimTarget == null）时将所有云台速度归零，维持当前姿态。
 * <p>
 * <b>执行时序</b>：必须在 {@link GimbalSystem} 之前执行，
 * 确保同一 tick 内先解算速度后积分。
 */
public class AimSystem implements TickSystem {

    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<? extends Part> parts) {
        // ── 1. 获取瞄准目标 ─────────────────────────────────────
        View<ControlState> cv = View.findPrimary(parts, null, ControlState.KEY);
        Vector3dc aimTarget = cv == null ? null : cv.get().aimTarget();

        // ── 2. 遍历各云台 ───────────────────────────────────────
        for (var entry : View.find(parts, GimbalDef.KEY, GimbalState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            var gd = defView.get();

            if (aimTarget == null) {
                // 无目标 → 速度归零，云台保持当前姿态
                var gs = stateView.get();
                if (gs.velYaw() != 0 || gs.velPitch() != 0) {
                    stateView.set(gs.withVelocity(0, 0));
                }
                continue;
            }

            // 世界坐标 → Part 局部方向
            PartTransform tx = PartTransform.of(stateView.part());
            Vector3dc localDir = tx.toRelativePos(aimTarget);

            // 方向 → 目标角度（受机械限位钳制）
            YawPitch target = gd.clampAngles(YawPitch.from(localDir));

            // 当前角度 → 目标角度：计算误差，限速后写入角速度
            var gs = stateView.get();
            double velYaw = gd.clampSpeedYaw(YawPitch.shortestYawDelta(gs.angles().yaw(), target.yaw()));
            double velPitch = gd.clampSpeedPitch(target.pitch() - gs.angles().pitch());

            stateView.set(gs.withVelocity(velYaw, velPitch));
        }
    }
}
