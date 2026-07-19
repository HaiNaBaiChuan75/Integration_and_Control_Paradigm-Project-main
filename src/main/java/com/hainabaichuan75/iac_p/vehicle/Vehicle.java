package com.hainabaichuan75.iac_p.vehicle;

import com.hainabaichuan75.iac_p.vehicle.common.Engine;
import com.hainabaichuan75.iac_p.vehicle.common.Weapon;
import com.hainabaichuan75.iac_p.vehicle.common.Wheel;

import java.util.List;

/**
 * 载具编排 —— 每 tick 构造的栈对象，不缓存。
 * <p>
 * 从 SubLevel 收集部件引用，执行跨组件协调。
 */
public record Vehicle(Engine engine, List<Wheel> wheels, Weapon weapon) {

    public void tick(double throttle, double dt) {
        if (engine != null) {
            engine.tick(throttle, dt);
            double torque = engine.torqueOutput();
            if (wheels != null) {
                wheels.forEach(w -> w.applyTorque(torque));
            }
        }
    }

    public boolean isValid() {
        return engine != null || (wheels != null && !wheels.isEmpty());
    }
}
