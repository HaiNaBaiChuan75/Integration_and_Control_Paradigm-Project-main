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

    @Inject(method = "setup", at = @At("TAIL"))
    private void iacp$vehicleCameraSetup(
            BlockGetter level, Entity entity,
            boolean thirdPerson, boolean inverseView,
            float partialTick, CallbackInfo ci
    ) {
        try {
            // 仅骑乘 IACPSeatEntity 且启用了载具摄像机模式时激活
            VehicleCameraMode mode = ClientMountHandler.getVehicleCameraMode();
            if (mode == null) return;
            if (!(entity instanceof Player && entity.getVehicle() instanceof IACPSeatEntity seat))
                return;

            // 获取座位实体的 SubLevel
            var homePos = seat.getHomePos();
            if (homePos == null) return;

            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;

            var subLevel = Sable.HELPER.getContaining(mc.level,
                    new Vector3d(homePos.getX() + 0.5, homePos.getY() + 0.5, homePos.getZ() + 0.5));
            if (!(subLevel instanceof ClientSubLevel clientSl)) return;

            Pose3dc pose = clientSl.renderPose(partialTick);
            if (pose == null) return;

            Vector3dc pos = pose.position();
            Vector3d localFwd = new Vector3d(0, 0, 1);
            Vector3d localUp = new Vector3d(0, 1, 0);
            Vector3d worldFwd = pose.transformNormal(localFwd, new Vector3d());
            Vector3d worldUp = pose.transformNormal(localUp, new Vector3d());

            // 计算载具偏航（水平）
            float vehicleYaw = (float) Math.toDegrees(Math.atan2(-worldFwd.x(), worldFwd.z()));

            // 从鼠标获取轨道角度
            float mouseYaw = this.yRot;
            float mousePitch = this.xRot;

            // 第三人称正面翻转
            if (inverseView) {
                mouseYaw += 180.0F;
                mousePitch = -mousePitch;
            }

            // 计算相对于载具偏航的轨道角度
            float relativeYaw = mouseYaw - vehicleYaw;
            double pitchRad = mousePitch * Mth.DEG_TO_RAD;
            double yawRad = relativeYaw * Mth.DEG_TO_RAD;

            // 局部空间的轨道偏移（载具坐标系）
            double dx = DEFAULT_DISTANCE * Mth.sin((float) yawRad) * Mth.cos((float) pitchRad);
            double dy = DEFAULT_DISTANCE * Mth.sin((float) pitchRad) + DEFAULT_HEIGHT;
            double dz = -DEFAULT_DISTANCE * Mth.cos((float) yawRad) * Mth.cos((float) pitchRad);

            Vector3d localOffset = new Vector3d(dx, dy, dz);

            Vector3d worldCamPos = new Vector3d();

            switch (mode) {
                case STRUCTURE_FIXED -> {
                    // ── 全量位姿变换 ──
                    // 局部偏移通过 SubLevel 的完整位移+旋转四元数变换到世界空间。
                    // 载具俯仰/侧倾时，摄像机位置随之旋转，画面与车体刚性连接。
                    pose.transformPosition(localOffset, worldCamPos);

                    // 摄像机朝向：指向载具中心
                    setLookAt(worldCamPos,
                            pos.x(), pos.y(), pos.z(),
                            (float) worldUp.x(), (float) worldUp.y(), (float) worldUp.z());
                }
                case DIRECTION_STABILIZED -> {
                    // ── 方向稳定变换 ──
                    // 从 SubLevel 位姿中提取位移 + 偏航旋转（移除俯仰/侧倾）。
                    // 摄像机始终水平，地平线保持稳定。
                    Vector3d translation = new Vector3d(pos);
                    // rotateY(正角度) 将局部向后(-Z)旋转到载具偏航方向，
                    // 与 Minecraft 偏航约定一致（yaw=0 → 面向 -Z = 南）。
                    float yawRadOnly = vehicleYaw * Mth.DEG_TO_RAD;
                    Quaterniond yawOnly = new Quaterniond().rotateY(yawRadOnly);

                    // 仅用偏航旋转局部偏移
                    Vector3d yawOffset = yawOnly.transform(new Vector3d(localOffset));
                    worldCamPos.set(translation).add(yawOffset);

                    // 摄像机朝向：指向载具中心，使用世界朝上
                    setLookAt(worldCamPos,
                            pos.x(), pos.y(), pos.z(),
                            0.0f, 1.0f, 0.0f);
                }
            }

            this.setPosition(new Vec3(worldCamPos.x(), worldCamPos.y(), worldCamPos.z()));

        } catch (Exception e) {
            IACP.LOGGER.error("[VehicleCameraMixin] 载具摄像机异常: {}", e.getMessage());
        }
    }

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
