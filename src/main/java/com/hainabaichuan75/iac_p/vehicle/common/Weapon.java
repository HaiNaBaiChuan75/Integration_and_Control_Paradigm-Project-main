package com.hainabaichuan75.iac_p.vehicle.common;

import com.hainabaichuan75.iac_p.vehicle.api.Module;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3dc;

/**
 * 武器抽象。
 * <p>
 * 子类实现具体的开火行为。
 * 云台数学见 {@link com.hainabaichuan75.iac_p.vehicle.GimbalMath}。
 */
public abstract class Weapon extends Module {

    int ammo;
    double yaw;
    double pitch;

    protected Weapon(BlockEntity be) { super(be); }

    @Override public String componentName() { return "weapon"; }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("ammo", ammo);
        tag.putDouble("yaw", yaw);
        tag.putDouble("pitch", pitch);
    }

    @Override
    public void load(CompoundTag tag) {
        ammo = tag.getInt("ammo");
        yaw = tag.getDouble("yaw");
        pitch = tag.getDouble("pitch");
    }

    public abstract void fire(Level level, Vector3dc target, double yaw, double pitch);

    public int ammo() { return ammo; }
    public double yaw() { return yaw; }
    public double pitch() { return pitch; }

    public void setYaw(double yaw) { this.yaw = yaw; setChanged(); }
    public void setPitch(double pitch) { this.pitch = pitch; setChanged(); }
}
