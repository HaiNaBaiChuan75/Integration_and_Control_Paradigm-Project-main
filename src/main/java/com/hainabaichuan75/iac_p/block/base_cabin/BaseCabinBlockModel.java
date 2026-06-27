package com.hainabaichuan75.iac_p.block.base_cabin;

import com.hainabaichuan75.iac_p.IACP;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BaseCabinBlockModel extends GeoModel<BaseCabinBlockEntity> {

    @Override
    public ResourceLocation getModelResource(BaseCabinBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(IACP.MODID, "geo/base_cabin/cabin.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BaseCabinBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(IACP.MODID, "geo/base_cabin/cabin.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BaseCabinBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(IACP.MODID, "animations/base_cabin.animation.json");
    }
}
