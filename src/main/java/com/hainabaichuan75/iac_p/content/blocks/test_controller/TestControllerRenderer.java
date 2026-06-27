package com.hainabaichuan75.iac_p.content.blocks.test_controller;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * TestControllerRenderer —— GeckoLib 测试方块渲染器。
 * <p>
 * 使用 {@link TestControllerModel} 加载手写的 geo.json 模型。
 * 覆写 {@link #getRenderBoundingBox} 返回足够大的包围盒，
 * 防止 SubLevel 变换后被 frustum culling 裁切。
 */
public class TestControllerRenderer extends GeoBlockRenderer<TestControllerBlockEntity> {

    public TestControllerRenderer(BlockEntityRendererProvider.Context ctx) {
        super(new TestControllerModel());
    }

    @Override
    public AABB getRenderBoundingBox(TestControllerBlockEntity blockEntity) {
        // 返回一个足够大的包围盒（6×6×6），确保 SubLevel 坐标变换后不会被错误裁切
        return AABB.ofSize(blockEntity.getBlockPos().getCenter(), 6, 6, 6);
    }
}
