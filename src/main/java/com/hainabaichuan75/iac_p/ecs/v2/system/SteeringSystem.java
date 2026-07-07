package com.hainabaichuan75.iac_p.ecs.v2.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View.Views2;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.TickSystem;
import com.hainabaichuan75.iac_p.ecs.v2.component.ControlState;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelDef;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelState;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 车轮转向 System (V2) —— 从控制器的横向意图解算各转向轮的偏转角。
 */
public class SteeringSystem implements TickSystem {

    private static final double CHASE_RATE = 0.15;
    private static final double CONVERGENCE_THRESHOLD = 0.005;

    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<? extends Part> parts) {
        // ── 1. 获取控制器输入 ─────────────────────────────────────
        View<ControlState> cv = View.findPrimary(parts, null, ControlState.KEY);
        if (cv == null) return;
        double desiredInput = Mth.clamp(cv.get().intent().x(), -1.0, 1.0);

        // ── 2. 遍历各轮 ───────────────────────────────────────────
        for (var entry : View.find(parts, WheelDef.KEY, WheelState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            double maxSteerAngle = defView.get().maxSteeringAngle();
            if (maxSteerAngle <= 0.0) continue;

            var ws = stateView.get();

            // Chasing 平滑
            double targetAngle = desiredInput * maxSteerAngle;
            double delta = targetAngle - ws.steeringAngle();
            double smoothed = Math.abs(delta) < CONVERGENCE_THRESHOLD ? targetAngle : Mth.lerp(CHASE_RATE,
                    ws.steeringAngle(), targetAngle);

            stateView.set(new WheelState(ws.angularVelocity(), ws.suspensionCompression(), smoothed, ws.torque(), ws.braking(), ws.prevCompression(), ws.contactPointLocal()));
        }
    }
}
