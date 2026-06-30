package com.hainabaichuan75.iac_p.content.blocks.shotgun;

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
 * ShotGunBlockRenderer —— 霰弹枪炮塔的 GeckoLib 渲染器。
 * <p>
 * <b>核心职责</b>：
 * <ol>
 * <li>在 {@link #renderRecursively} 中拦截骨骼渲染，在 GeckoLib 动画系统</li>
 * <li>将 BE 中存储的目标角度通过 {@link GeoBone#setRotY(float)} /
 *     {@link GeoBone#setRotX(float)} 注入骨骼，</li>
 * </ol>
 * <p>
 * <b>骨骼层级</b>：
 * <pre>
 * base (根)
 *   └── yaw (绕 Y 轴旋转，偏航)
 *       └── yaw_ani (动画承载)
 *           └── pitch (绕 X 轴旋转，俯仰)
 *               └── pitch_ani (动画承载)
 *                   └── 炮管几何体
 * </pre>
 */
public class ShotGunBlockRenderer extends GeoBlockRenderer<ShotGunBlockEntity> {

    public ShotGunBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        super(new ShotGunBlockModel());
    }

    @Override
    public AABB getRenderBoundingBox(ShotGunBlockEntity blockEntity) {
        // 返回足够大的包围盒，防止 SubLevel 坐标变换后被 frustum culling 裁切
        return AABB.ofSize(blockEntity.getBlockPos().getCenter(), 12, 12, 12);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ShotGunBlockEntity animatable, GeoBone bone,
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
