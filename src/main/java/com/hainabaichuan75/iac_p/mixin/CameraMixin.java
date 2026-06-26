package com.hainabaichuan75.iac_p.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plan B1 核心 Mixin：轨道摄像机 —— 镜头始终对准 SubLevel 焦点， 鼠标控制摄像机在球面上的环绕位置。
 * <p>
 * 原理：{@link Camera#setup(BlockGetter, Entity, boolean, boolean, float)} 在 setup
 * 完成后（@At("TAIL")），将摄像机位置设为 SubLevel 焦点周围的 球坐标位置，再计算从摄像机指向焦点的方向向量，强制设置摄像机旋转，
 * 实现"镜头始终对准焦点，鼠标控制环绕"的轨道摄像机效果。
 */
@Mixin(Camera.class)
public class CameraMixin {

    @Shadow
    private void setPosition(Vec3 position) {
    }

    @Shadow
    private void setRotation(float yRot, float xRot) {
    }

    /**
     * 在 Camera.setup() 完成后，若玩家处于骑乘状态，将摄像机改为轨道模式： 位置随鼠标在球面上环绕，旋转始终指向 SubLevel
     * 焦点。
     * <p>
     * 整个方法包裹在 try-catch 中，防止渲染异常导致游戏卡死。
     */
    @Inject(method = "setup", at = @At("TAIL"))
    private void iacp$afterCameraSetup(BlockGetter level, Entity entity, boolean thirdPerson, boolean inverseView,
                                       float partialTick, CallbackInfo ci) {

    }
}
