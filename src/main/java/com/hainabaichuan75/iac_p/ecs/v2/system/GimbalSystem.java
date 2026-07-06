package com.hainabaichuan75.iac_p.ecs.v2.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View.Views2;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.TickSystem;
import com.hainabaichuan75.iac_p.ecs.v2.component.GimbalDef;
import com.hainabaichuan75.iac_p.ecs.v2.component.GimbalState;
import com.hainabaichuan75.iac_p.part.field.YawPitch;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 云台伺服 System —— 对 {@link AimSystem} 写入的角速度进行积分，
 * 同时处理机械限位（限速 + 限位）。
 * <p>
 * <b>职责</b>：
 * <ol>
 *   <li>读取 AimSystem 写入的 {@link GimbalState#velYaw} / {@link GimbalState#velPitch}</li>
 *   <li>积分到 {@link GimbalState#angles}：角度 += 角速度</li>
 *   <li>位置钳制：超过 {@link GimbalDef} 限位时 clamp</li>
 *   <li>限速：边界处将速度衰减为恰好到达边界的剩余距离，而非一刀切归零</li>
 * </ol>
 * <p>
 * <b>执行时序</b>：必须执行于 {@link AimSystem} 之后，
 * 确保同一 tick 内先解算速度再积分。
 * <p>
 * 本 System 不关心瞄准目标来源——它只是忠实地执行速度指令。
 */
public class GimbalSystem implements TickSystem {

    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<? extends Part> parts) {
        for (var entry : View.find(parts, GimbalDef.KEY, GimbalState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            var gd = defView.get();
            var gs = stateView.get();

            // 积分 + 归一化：YawPitch 构造器自动归一化偏航到 (-180, 180]
            var newAngles = new YawPitch(gs.angles().yaw() + gs.velYaw(), gs.angles().pitch() + gs.velPitch());

            // 位置钳制（机械限位），clampAngles 同时处理偏航和俯仰
            var clamped = gd.clampAngles(newAngles);

            // 剩余速度：用最短弧长而非直接减法，避免偏航过 ±180° 时跳变
            double clampedVelYaw = YawPitch.shortestYawDelta(gs.angles().yaw(), clamped.yaw());
            double clampedVelPitch = clamped.pitch() - gs.angles().pitch();

            stateView.set(new GimbalState(clamped, clampedVelYaw, clampedVelPitch));
        }
    }
}
