package com.hainabaichuan75.iac_p.ecs.v2.system;

import com.hainabaichuan75.iac_p.ecs.v2.api.component.View;
import com.hainabaichuan75.iac_p.ecs.v2.api.component.View.Views2;
import com.hainabaichuan75.iac_p.ecs.v2.api.entity.Part;
import com.hainabaichuan75.iac_p.ecs.v2.api.system.TickSystem;
import com.hainabaichuan75.iac_p.ecs.v2.component.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 扭矩分配 System (V2) —— 根据引擎输出和油门输入，向各驱动轮分配扭矩。
 * <p>
 * <b>数据流</b>：
 * <pre>
 * ControlState.intent.z → 油门
 *   → EngineDef.maxTorque → 引擎扭矩
 *   → 均分到各驱动轮 WheelState.torque
 *   → TractionForceSystem 下一物理 tick 读取并推进
 * </pre>
 * <p>
 * 刹车时扭矩归零（刹车力由物理 System 阻滞轮速）。
 */
public class TorqueDistributionSystem implements TickSystem {

    @Override
    public void onTick(@NotNull ServerSubLevel subLevel, @NotNull List<? extends Part> parts) {
        // ── 1. 控制器输入 ────────────────────────────────────────
        View<ControlState> cv = View.findPrimary(parts, null, ControlState.KEY);
        if (cv == null) return;
        var ctrl = cv.get();

        // 纵向分量：z- = 前（取反使前进为正）
        double throttle = -Mth.clamp(ctrl.intent().z(), -1.0, 1.0);
        if (ctrl.braking()) throttle = 0.0; // 刹车覆盖

        // ── 2. 找引擎 ────────────────────────────────────────────
        View<EngineDef> ev = View.findPrimary(parts, null, EngineDef.KEY);
        if (ev == null) return;
        double engineTorque = throttle * ev.get().maxTorque();

        // 写入引擎状态
        View<EngineState> esv = View.of(ev.part(), EngineState.KEY);
        if (esv != null) {
            esv.set(esv.get().withTorque(engineTorque));
        }

        // ── 3. 驱动轮计数 ──────────────────────────────────────
        int driveCount = 0;
        for (var entry : View.find(parts, WheelDef.KEY, WheelState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            if (defView.get().driven()) driveCount++;
        }
        if (driveCount == 0) return;

        // ── 4. 均分扭矩 ──────────────────────────────────────────
        double torquePerWheel = engineTorque / driveCount;

        for (var entry : View.find(parts, WheelDef.KEY, WheelState.KEY)) {
            if (!(entry instanceof Views2(var defView, var stateView))) continue;
            if (!defView.get().driven()) continue;

            var ws = stateView.get();
            stateView.set(new WheelState(ws.angularVelocity(), ws.suspensionCompression(), ws.steeringAngle(), torquePerWheel, ws.braking() || ctrl.braking(), ws.prevCompression(), ws.contactPointLocal()));
        }
    }
}
