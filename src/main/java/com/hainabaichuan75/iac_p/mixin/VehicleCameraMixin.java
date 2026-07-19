package com.hainabaichuan75.iac_p.mixin;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.client.ClientMountHandler;
import com.hainabaichuan75.iac_p.client.VehicleCameraMode;
import com.hainabaichuan75.iac_p.entity.IACPSeatEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 载具参照摄像机 Mixin —— 在骑乘 {@link IACPSeatEntity} 时提供两种以 SubLevel
 * 为参考系的额外摄像机模式（结构固定 / 方向稳定）。
 * <p>
 * 仅在 {@link ClientMountHandler#getVehicleCameraMode()} 不为 null 时激活。
 * 通过 F6 键循环切换，不替代 F5 的三模式。
 * <p>
 * 实现方式：在 {@link Camera#setup} {@code @TAIL} 注入，覆盖标准第三人称的
 * 位置和朝向计算。偏航/俯仰仍来自鼠标。
 */
@Mixin(Camera.class)
public class VehicleCameraMixin {

    @Shadow
    private void setPosition(Vec3 position) {}

    @Shadow
    private void setRotation(float yRot, float xRot) {}

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    /** 摄像机到载具的默认距离 */
    private static final double DEFAULT_DISTANCE = 6.0;

    /** 摄像机在载具局部空间的默认高度偏移 */
    private static final double DEFAULT_HEIGHT = 2.5;


    /**
     * 设置摄像机旋转，使其从 {@code camPos} 看向 {@code targetPos}。
     */
    private void setLookAt(Vector3d camPos, double tx, double ty, double tz,
                            float upX, float upY, float upZ) {
        double lookX = tx - camPos.x();
        double lookY = ty - camPos.y();
        double lookZ = tz - camPos.z();
        double hDist = Math.sqrt(lookX * lookX + lookZ * lookZ);

        if (hDist < 1e-6) {
            this.setRotation(this.yRot, this.xRot);
            return;
        }

        float yaw = (float) (Mth.atan2(lookZ, lookX) * Mth.RAD_TO_DEG - 90.0F);
        float pitch = (float) (-Mth.atan2(lookY, hDist) * Mth.RAD_TO_DEG);
        this.setRotation(yaw, pitch);
    }
}
