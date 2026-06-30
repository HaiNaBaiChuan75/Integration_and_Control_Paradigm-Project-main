package com.hainabaichuan75.iac_p.block.base_cabin;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * BaseCabinBlockRenderer —— 基础座舱方块的 GeckoLib 渲染器。
 * <p>
 * 使用 {@link BaseCabinBlockModel} 驱动骨骼模型渲染，
 * 座舱本身为静态模型，不涉及动态骨骼旋转（区别于炮塔类方块）。
 */
public class BaseCabinBlockRenderer extends GeoBlockRenderer<BaseCabinBlockEntity> {

    public BaseCabinBlockRenderer(BlockEntityRendererProvider.Context ctx) {
        super(new BaseCabinBlockModel());
    }

    @Override
    public AABB getRenderBoundingBox(BaseCabinBlockEntity blockEntity) {
        // 返回足够大的包围盒，防止 SubLevel 坐标变换后被 frustum culling 裁切
        return AABB.ofSize(blockEntity.getBlockPos().getCenter(), 12, 12, 12);
    }
}
