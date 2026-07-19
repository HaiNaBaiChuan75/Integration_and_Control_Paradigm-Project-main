package com.hainabaichuan75.iac_p.vehicle.common;

import com.hainabaichuan75.iac_p.vehicle.api.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 引擎。
 */
public class Engine extends Component {

    double rpm;
    double torqueOutput;

    public Engine(BlockEntity be) { super(be); }

    @Override public String componentName() { return "engine"; }

    @Override
    public void save(CompoundTag tag) {
        tag.putDouble("rpm", rpm);
        tag.putDouble("torque", torqueOutput);
    }

    @Override
    public void load(CompoundTag tag) {
        rpm = tag.getDouble("rpm");
        torqueOutput = tag.getDouble("torque");
    }

    public double tick(double throttle, double dt) {
        rpm = Math.clamp(rpm + throttle * 100 - rpm * 0.02, 0, 3000);
        torqueOutput = rpm * 0.15;
        setChanged();
        return torqueOutput;
    }

    public double rpm() { return rpm; }
    public double torqueOutput() { return torqueOutput; }
}
