package com.hainabaichuan75.iac_p.mixin;

import com.hainabaichuan75.iac_p.Config;
import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.client.ClientMountHandler;
import com.hainabaichuan75.iac_p.entity.IACPSeatEntity;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 轨道摄像机 Mixin —— 骑乘载具或座位时摄像机跟随 SubLevel 焦点。
 * <p>
 * <b>设计思路：</b>
 * <p>
 * 原版 {@link Camera#setup} 已在第三人称下提供轨道摄像机行为——偏航/俯仰来自玩家鼠标，
 * 摄像机围绕实体旋转。本 Mixin 仅将焦点从玩家实体重定向到 SubLevel 结构中心，
 * 并应用配置距离/高度偏移。F5 视角切换（第一→第三人称后→第三人称前）保持正常工作，
 * 第一人称时本 Mixin 直接跳过。
 * <p>
 * 相比旧的独立轨道摄像机状态（低通滤波器、帧间差分追踪），本方案：
 * <ul>
 *   <li>无需独立维护 yaw/pitch 状态——直接复用原版 Camera 的 yRot/xRot（来自鼠标）</li>
 *   <li>无需低通滤波器——原版 mouse look → entity → camera 链路不存在服务端覆盖</li>
 *   <li>F5 切换自然工作，第三人称前/后自动处理</li>
 * </ul>
 * <p>
 * 检测条件：
 * <ul>
 *   <li>旧 mount 系统（CockpitBlock）：{@link ClientMountHandler#isMounted()}</li>
 *   <li>IACPSeatEntity 骑乘（BaseCabinBlock）——<b>跳过</b>，seat 自身管理位姿</li>
 * </ul>
 */
@Mixin(Camera.class)
public class CameraMixin {

    @Shadow
    private void setPosition(Vec3 position) {
    }

    @Shadow
    private void setRotation(float yRot, float xRot) {
    }

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    @Inject(method = "setup", at = @At("TAIL"))
    private void iacp$afterCameraSetup(
            BlockGetter level, Entity entity,
            boolean thirdPerson, boolean inverseView,
            float partialTick, CallbackInfo ci
    ) {
        try {
            // 第一人称不干预——F5 第一人称保持原版行为
            if (!thirdPerson) return;

            ClientSubLevel targetSubLevel = resolveClientSubLevel(entity);
            if (targetSubLevel == null) return;

            Pose3dc renderPose = targetSubLevel.renderPose(partialTick);
            if (renderPose == null) return;

            var renderPos = renderPose.position();
            BoundingBox3dc bbox = targetSubLevel.boundingBox();

            // 计算 SubLevel 焦点位置
            double focusY;
            if (Config.CAMERA_ADAPTIVE_HEIGHT.get()) {
                double halfHeight = (bbox.maxY() - bbox.minY()) * 0.5;
                focusY = renderPos.y() + halfHeight + 1.0 + Config.CAMERA_HEIGHT_OFFSET.get();
            } else {
                focusY = renderPos.y() + Config.CAMERA_HEIGHT_OFFSET.get();
            }
            double focusX = renderPos.x();
            double focusZ = renderPos.z();

            double distance = Config.CAMERA_DISTANCE.get();
            if (Config.CAMERA_ADAPTIVE_DISTANCE.get()) {
                double lenX = bbox.maxX() - bbox.minX();
                double lenY = bbox.maxY() - bbox.minY();
                double lenZ = bbox.maxZ() - bbox.minZ();
                double longestSide = Math.max(lenX, Math.max(lenY, lenZ));
                distance += longestSide / 2.0;
            }

            // 哨兵摄像机模式：冻结位置，锁定焦点
            if (ClientMountHandler.isCameraStationary()) {
                Vec3 frozenPos = ClientMountHandler.getStationaryCameraPos();
                if (frozenPos != null) {
                    this.setPosition(frozenPos);
                    double lookX = focusX - frozenPos.x;
                    double lookY = focusY - frozenPos.y;
                    double lookZ = focusZ - frozenPos.z;
                    double horizontalDist = Math.sqrt(lookX * lookX + lookZ * lookZ);
                    float lookPitch = (float) -Mth.atan2(lookY, Math.max(horizontalDist, 1e-4)) * Mth.RAD_TO_DEG;
                    float lookYaw = horizontalDist < 1e-4
                            ? entity.getYRot()
                            : (float) Mth.atan2(lookZ, lookX) * Mth.RAD_TO_DEG - 90.0F;
                    this.setRotation(lookYaw, lookPitch);
                    return;
                }
            }

            // ── 使用原版摄像机偏航/俯仰 ──
            // Camera.setup() 已通过 entity.getViewYRot()/getViewXRot() 设置 yRot/xRot，
            // 其值来自玩家鼠标控制，直接复用。
            float yaw = this.yRot;
            float pitch = this.xRot;

            // 第三人称正面：翻转方向使摄像机位于前方
            if (inverseView) {
                yaw += 180.0F;
                pitch = -pitch;
            }

            // 计算轨道位置：焦点 + 方向 × 距离
            double dySign = Config.CAMERA_INVERT_Y.get() ? -1.0 : 1.0;
            double dx = Mth.sin(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD) * distance;
            double dy = dySign * Mth.sin(pitch * Mth.DEG_TO_RAD) * distance;
            double dz = -Mth.cos(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD) * distance;

            Vec3 cameraPos = new Vec3(focusX + dx, focusY + dy, focusZ + dz);
            this.setPosition(cameraPos);

            // 计算朝向焦点的旋转
            double lookX = focusX - cameraPos.x;
            double lookY = focusY - cameraPos.y;
            double lookZ = focusZ - cameraPos.z;
            double horizontalDist = Math.sqrt(lookX * lookX + lookZ * lookZ);

            float lookPitch = (float) -Mth.atan2(lookY, Math.max(horizontalDist, 1e-4)) * Mth.RAD_TO_DEG;
            float lookYaw = horizontalDist < 1e-4
                    ? yaw
                    : (float) Mth.atan2(lookZ, lookX) * Mth.RAD_TO_DEG - 90.0F;

            this.setRotation(lookYaw, lookPitch);
        } catch (Exception e) {
            IACP.LOGGER.error("[CameraMixin] 轨道摄像机异常: {}", e.getMessage());
        }
    }

    /**
     * 解析当前应跟踪的客户端 SubLevel。
     * <p>
     * 优先顺序：旧 mount 系统 → IACPSeatEntity 骑乘。
     */
    private static ClientSubLevel resolveClientSubLevel(Entity entity) {
        // 旧 mount 系统
        if (ClientMountHandler.isMounted()) {
            return ClientMountHandler.getMountedClientSubLevel();
        }

        // IACPSeatEntity 骑乘 —— 跳过轨道摄像机
        // seat 实体自己管理位姿跟随（followSubLevelPose + positionRider 全量变换），
        // CameraMixin 不应干涉。原版 F5 模式围绕骑乘者实体做轨道已足够。
        if (entity instanceof Player && entity.getVehicle() instanceof IACPSeatEntity) {
            return null;
        }

        return null;
    }
}
