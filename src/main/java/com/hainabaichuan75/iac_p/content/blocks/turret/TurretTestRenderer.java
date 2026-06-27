package com.hainabaichuan75.iac_p.content.blocks.turret;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * TurretTestRenderer —— 炮塔测试方块的 GeckoLib 渲染器。
 * <p>
 * <b>核心职责</b>：
 * <ol>
 * <li>在 {@link #renderRecursively} 中拦截骨骼渲染，在 GeckoLib 动画系统</li>
 * <li>将 BE 中存储的目标角度通过 {@link GeoBone#setRotY(float)} /
 *     {@link GeoBone#setRotX(float)} 注入骨骼，</li>
 * <li>实现 Crossout 第5章的"动画解耦"——视觉旋转独立于物理 tick。</li>
 * </ol>
 * <p>
 * <b>关键设计</b>：必须在 {@code handleAnimations} 之后设置骨骼旋转，
 * 因此使用 {@link #renderRecursively} 而非 {@link #preRender}，
 * 因为前者在动画系统应用变换之后调用。
 * <p>
 * <b>骨骼层级</b>：
 * <pre>
 * base (根)
 *   └── yaw (绕 Y 轴旋转，偏航)
 *       └── pitch (绕 X 轴旋转，俯仰)
 * </pre>
 */
public class TurretTestRenderer extends GeoBlockRenderer<TurretTestBlockEntity> {

    public TurretTestRenderer(BlockEntityRendererProvider.Context ctx) {
        super(new TurretTestModel());
    }

    @Override
    public AABB getRenderBoundingBox(TurretTestBlockEntity blockEntity) {
        // 返回足够大的包围盒，防止 SubLevel 坐标变换后被 frustum culling 裁切
        return AABB.ofSize(blockEntity.getBlockPos().getCenter(), 12, 12, 12);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, TurretTestBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        // 在 GeckoLib 动画系统应用完变换之后，覆盖骨骼旋转
        if ("yaw".equals(bone.getName())) {
            float yawRad = (float) Math.toRadians(animatable.getRenderYaw(partialTick));
            bone.setRotY(yawRad);
            bone.setRotX(0);
            bone.setRotZ(0);
        } else if ("pitch".equals(bone.getName())) {
            float pitchRad = (float) Math.toRadians(animatable.getRenderPitch(partialTick));
            bone.setRotX(pitchRad);
            bone.setRotY(0);
            bone.setRotZ(0);
        }

        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }
}
