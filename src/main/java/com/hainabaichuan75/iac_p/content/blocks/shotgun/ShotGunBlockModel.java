package com.hainabaichuan75.iac_p.content.blocks.shotgun;

import com.hainabaichuan75.iac_p.IACP;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * ShotGunBlockModel —— 霰弹枪炮塔的 GeckoLib 模型定义。
 * <p>
 * 模型: {@code assets/iac_p/geo/shotgun_turret.geo.json}
 * 纹理: {@code assets/iac_p/textures/shotgun_turret.png}
 * 动画: {@code assets/iac_p/animations/shotgun_turret.animation.json}
 * <p>
 * 注意：资源文件不在 {@code block/} 子目录下，因此使用自定义 GeoModel
 * 显式指定路径，而非 DefaultedBlockGeoModel（后者自动插入 block/ 前缀）。
 */
public class ShotGunBlockModel extends GeoModel<ShotGunBlockEntity> {

    @Override
    public ResourceLocation getModelResource(ShotGunBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(IACP.MODID, "geo/shotgun_turret.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ShotGunBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(IACP.MODID, "textures/shotgun_turret.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShotGunBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(IACP.MODID, "animations/shotgun_turret.animation.json");
    }
}
