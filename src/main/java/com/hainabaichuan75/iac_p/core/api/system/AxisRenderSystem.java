package com.hainabaichuan75.iac_p.core.api.system;

import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.core.system.VehicleClientSystem;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

public class AxisRenderSystem implements VehicleClientSystem {
    @Override
    public void onTick(@NotNull ClientSubLevel subLevel, @NotNull List<PartBlockEntity> parts) {
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
            return;
        }
        Vector3f red = new Vector3f(1.0F, 0.0F, 0.0F);   // X - 红
        Vector3f green = new Vector3f(0.0F, 1.0F, 0.0F); // Y - 绿
        Vector3f blue = new Vector3f(0.0F, 0.0F, 1.0F);  // Z - 蓝
        ClientLevel level = subLevel.getLevel();
        for (PartBlockEntity part : parts) {
            Pose3d pose = part.worldPose();
            // 粒子效果
            for (double i = 0; i <= 1.5; i += 0.1) {

                Vector3d posX = pose.transformPosition(new Vector3d(i, 0, 0));
                Vector3d posY = pose.transformPosition(new Vector3d(0, i, 0));
                Vector3d posZ = pose.transformPosition(new Vector3d(0, 0, i));
                level.addParticle(new DustParticleOptions(red, .5F), posX.x, posX.y, posX.z, 0, 0, 0);
                level.addParticle(new DustParticleOptions(green, .5F), posY.x, posY.y, posY.z, 0, 0, 0);
                level.addParticle(new DustParticleOptions(blue, .5F), posZ.x, posZ.y, posZ.z, 0, 0, 0);
            }
        }
    }
}
