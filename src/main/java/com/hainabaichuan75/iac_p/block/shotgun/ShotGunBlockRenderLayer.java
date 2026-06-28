package com.hainabaichuan75.iac_p.block.shotgun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class ShotGunBlockRenderLayer extends GeoRenderLayer<ShotGunBlockEntity> {
    public ShotGunBlockRenderLayer(GeoRenderer<ShotGunBlockEntity> entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public void preRender(PoseStack poseStack, ShotGunBlockEntity animatable, BakedGeoModel bakedModel,
                          @Nullable RenderType renderType, MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        partialTick = 1;
        System.out.println(partialTick);
        System.out.printf("%f, %f", animatable.pitch, animatable.yaw);
        float smoothYaw = (float) (animatable.prevYaw + (animatable.yaw - animatable.prevYaw) * partialTick);
        float smoothPitch = (float) (animatable.prevPitch + (animatable.pitch - animatable.prevPitch) * partialTick);
        System.out.printf("%f, %f", smoothPitch, smoothYaw);
        bakedModel.getBone("yaw").ifPresent(bone -> {
            bone.setRotY((float) Math.toRadians(smoothYaw));
        });

        bakedModel.getBone("pitch").ifPresent(bone -> {
            bone.setRotX((float) Math.toRadians(smoothPitch));
        });
    }

}
