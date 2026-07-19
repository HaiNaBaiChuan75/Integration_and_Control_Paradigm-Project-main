package com.hainabaichuan75.iac_p.vehicle.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3dc;

/**
 * 机枪。
 */
public class MachineGun extends Weapon {

    int heat;

    public MachineGun(BlockEntity be) { super(be); }

    @Override public String componentName() { return "machine_gun"; }

    @Override
    public void save(CompoundTag tag) {
        super.save(tag);
        tag.putInt("heat", heat);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        heat = tag.getInt("heat");
    }

    @Override
    public void fire(Level level, Vector3dc target, double yaw, double pitch) {
        if (ammo <= 0) return;
        ammo--;
        heat = Math.min(heat + 1, 100);
        setChanged();
    }
}
