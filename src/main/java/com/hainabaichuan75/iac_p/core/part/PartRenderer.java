package com.hainabaichuan75.iac_p.core.part;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 基于 {@link PartBlockEntity} 的 GeckoLib 渲染器基类。
 * <p>
 * 在渲染时自动应用 {@link PartBlockEntity#orientation()} 的朝向偏移，
 * 使得部件的视觉旋转与物理/逻辑朝向保持一致。
 *
 * @param <BE> 部件 BE 类型，需同时实现 {@link PartBlockEntity} 和 {@link GeoAnimatable}
 */
public class PartRenderer<BE extends PartBlockEntity & GeoAnimatable> extends GeoBlockRenderer<BE> {

    public static final Quaternionf REUSE_QUAT = new Quaternionf();

    public PartRenderer(@NotNull GeoModel<BE> model) {
        super(model);
    }

    @Override
    public void actuallyRender(@NotNull PoseStack poseStack, @NotNull BE animatable, @NotNull BakedGeoModel model,
                               @Nullable RenderType renderType, @NotNull MultiBufferSource bufferSource,
                               @Nullable VertexConsumer buffer, boolean isReRender, float partialTick,
                               int packedLight, int packedOverlay, int colour) {
        REUSE_QUAT.set(animatable.orientation());
        poseStack.mulPose(REUSE_QUAT);
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    @Override
    protected Direction getFacing(BE block) {
        return Direction.NORTH;
    }
}

