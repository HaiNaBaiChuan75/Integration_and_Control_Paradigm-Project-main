package com.hainabaichuan75.iac_p.vehicle.common;

import com.hainabaichuan75.iac_p.vehicle.api.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3dc;

/**
 * 轮子。
 */
public class Wheel extends Component {

    double angularVelocity;
    double suspensionCompression;
    double steeringAngle;
    double torque;

    transient Vector3dc contactPointLocal;

    public Wheel(BlockEntity be) { super(be); }

    @Override public String componentName() { return "wheel"; }

    @Override
    public void save(CompoundTag tag) {
        tag.putDouble("angVel", angularVelocity);
        tag.putDouble("suspComp", suspensionCompression);
        tag.putDouble("steer", steeringAngle);
        tag.putDouble("torque", torque);
    }

    @Override
    public void load(CompoundTag tag) {
        angularVelocity = tag.getDouble("angVel");
        suspensionCompression = tag.getDouble("suspComp");
        steeringAngle = tag.getDouble("steer");
        torque = tag.getDouble("torque");
    }

    public void applyTorque(double torque) {
        this.torque = torque;
        setChanged();
    }

    public double angularVelocity() { return angularVelocity; }
    public double suspensionCompression() { return suspensionCompression; }
    public double steeringAngle() { return steeringAngle; }
    public double torque() { return torque; }
}
