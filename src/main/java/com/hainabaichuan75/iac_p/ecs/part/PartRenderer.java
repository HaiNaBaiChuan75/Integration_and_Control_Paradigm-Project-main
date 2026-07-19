package com.hainabaichuan75.iac_p.ecs.part;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 基于 {@link Part} 的 GeckoLib 渲染器基类。
 * <p>
 * 在渲染时自动应用 {@link Part#orientation()} 的朝向偏移，
 * 使得部件的视觉旋转与物理/逻辑朝向保持一致。
 * 覆盖 {@code getFacing()} 固定返回 {@link Direction#NORTH}——朝向由四元数处理，不依赖方块朝向。
 *
 * @param <BE> 部件 BE 类型，需同时实现 {@link Part}、{@link GeoAnimatable}，并且是 {@link BlockEntity}
 */
@Deprecated(since = "1.0", forRemoval = true)
public class PartRenderer<BE extends BlockEntity & Part & GeoAnimatable> extends GeoBlockRenderer<BE> {

    /**
     * 渲染时复用的临时四元数。避免每帧创建新对象导致的 GC 压力。
     * <p>
     * <b>注意</b>：仅在渲染线程使用，Minecraft 渲染管线天然单线程，无需同步。
     */
    public static final Quaternionf REUSE_QUAT = new Quaternionf();

    /**
     * @param model 此 Part 对应的 GeckoLib 模型
     */
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

