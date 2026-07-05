package com.hainabaichuan75.iac_p.block.shotgun;

import com.hainabaichuan75.iac_p.ecs.part.PartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * ShotGunBlockRenderer —— 霰弹枪炮塔的 PartRenderer。
 * <p>
 * 在 {@link PartRenderer#actuallyRender} 应用 orientation 之后，
 * 拦截骨骼渲染注入动态 yaw/pitch 旋转。
 */
public class ShotGunBlockRenderer extends PartRenderer<ShotGunBlockEntity> {

    public ShotGunBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        super(new ShotGunBlockModel());
    }

    @Override
    public AABB getRenderBoundingBox(ShotGunBlockEntity blockEntity) {
        return AABB.ofSize(blockEntity.getBlockPos().getCenter(), 12, 12, 12);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ShotGunBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        float realPartialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        if ("yaw".equals(bone.getName())) {
            float yawRad = (float) Math.toRadians(animatable.getRenderYaw(realPartialTick));
            bone.setRotY(yawRad);
            bone.setRotX(0);
            bone.setRotZ(0);
        } else if ("pitch".equals(bone.getName())) {
            float pitchRad = (float) Math.toRadians(animatable.getRenderPitch(realPartialTick));
            bone.setRotX(pitchRad);
            bone.setRotY(0);
            bone.setRotZ(0);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }
}
