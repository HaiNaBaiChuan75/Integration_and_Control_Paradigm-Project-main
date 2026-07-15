package com.hainabaichuan75.iac_p.ecs.v2.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View.Views2;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.TickSystem;
import com.hainabaichuan75.iac_p.ecs.v2.component.ControlState;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelDef;
import com.hainabaichuan75.iac_p.ecs.v2.component.WheelState;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.List;

/**
 * 驾驶控制输入映射 (TickSystem) — 读意图、写轮子。
 * <p>
 * 每 20Hz tick 读取 {@link ControlState} 的驾驶员意图并将控制值
 * 写入所有 {@link WheelState}。
 *
 * <h3>控制映射</h3>
 * <ul>
 *   <li>{@code intent.z < 0} → 前进扭矩 +ENGINE_TORQUE</li>
 *   <li>{@code intent.z > 0} → 后退扭矩 -ENGINE_TORQUE</li>
 *   <li>{@code intent.x +/=}  → 转向 ±30°</li>
 *   <li>{@code braking} → 全局刹车</li>
 * </ul>
 */
public class ControlInputSystem implements TickSystem {

    /** 引擎扭矩（Nm），暂时硬编码，后续从 EngineDef 读取 */
    private static final double ENGINE_TORQUE = 500.0;
    private static final double STEERING_SPEED = 10;

    @Override
    public void onTick(@NotNull ServerSubLevel sl, @NotNull List<? extends Part> parts) {
        View<ControlState> cv = View.findPrimary(parts, null, ControlState.KEY);
        double iz = 0, ix = 0;
        boolean gb = false;
        if (cv != null) {
            var c = cv.get();
            iz = c.intent().z();
            ix = c.intent().x();
            gb = c.braking();
        }

        double t = ENGINE_TORQUE * -Mth.clamp(iz, -1, 1);
        double s = 30.0 * Mth.clamp(ix, -1, 1);

        for (var entry : View.find(parts, WheelDef.KEY, WheelState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            var ws = stateView.get();
            boolean brk = gb || ws.braking();

            // ── 匀速转向平滑 ──
            double p = ws.steeringAngle();
            double d = s - p;
            double sd = Math.abs(d) <= STEERING_SPEED ? s : p + Math.signum(d) * STEERING_SPEED;

            stateView.set(new WheelState(ws.angularVelocity(), ws.suspensionCompression(),
                    sd, t, brk, ws.contactPointLocal(), ws.compressionDelta()));
        }
    }
}
