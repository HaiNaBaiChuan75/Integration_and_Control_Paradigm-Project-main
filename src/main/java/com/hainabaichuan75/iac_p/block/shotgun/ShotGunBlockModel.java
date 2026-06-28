package com.hainabaichuan75.iac_p.block.shotgun;

import com.hainabaichuan75.iac_p.IACP;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ShotGunBlockModel extends GeoModel<ShotGunBlockEntity> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(IACP.MODID, "geo/shotgun_turret.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(IACP.MODID, "textures" +
                                                                                                      "/shotgun_turret.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(IACP.MODID, "animations" +
                                                                                                        "/shotgun_turret.animation.json");

    @Override
    public ResourceLocation getModelResource(ShotGunBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ShotGunBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ShotGunBlockEntity animatable) {
        return ANIMATION;
    }
}
