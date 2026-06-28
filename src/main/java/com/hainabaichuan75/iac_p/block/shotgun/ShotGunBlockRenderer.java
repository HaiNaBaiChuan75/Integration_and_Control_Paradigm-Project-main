package com.hainabaichuan75.iac_p.block.shotgun;

import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class ShotGunBlockRenderer extends GeoBlockRenderer<ShotGunBlockEntity> {

    public ShotGunBlockRenderer() {
        super(new ShotGunBlockModel());
        addRenderLayer(new ShotGunBlockRenderLayer(this));
    }

}
