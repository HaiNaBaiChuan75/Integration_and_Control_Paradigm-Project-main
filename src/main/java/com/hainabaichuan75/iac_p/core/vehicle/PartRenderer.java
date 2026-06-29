package com.hainabaichuan75.iac_p.core.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class PartRenderer<BE extends VehiclePartBlockEntity & GeoAnimatable> extends GeoBlockRenderer<BE> {
    public static final Quaternionf ORIENTATION_CACHE = new Quaternionf();

    public PartRenderer(BlockEntityType<? extends BE> blockEntityType) {
        super(blockEntityType);
    }

    public PartRenderer(GeoModel<BE> model) {
        super(model);
    }

    @Override
    public void actuallyRender(PoseStack poseStack, BE animatable, BakedGeoModel model,
                               @Nullable RenderType renderType, MultiBufferSource bufferSource,
                               @Nullable VertexConsumer buffer, boolean isReRender, float partialTick,
                               int packedLight, int packedOverlay, int colour) {
        ORIENTATION_CACHE.set(animatable.orientation());
        poseStack.mulPose(ORIENTATION_CACHE);
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    @Override
    protected Direction getFacing(BE block) {
        return Direction.NORTH;
    }
}
