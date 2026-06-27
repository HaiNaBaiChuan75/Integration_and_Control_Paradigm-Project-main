package com.hainabaichuan75.iac_p.content.blocks.test_controller;

import com.hainabaichuan75.iac_p.IACP;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * TestControllerModel —— GeckoLib 测试方块模型。
 * <p>
 * 模型: {@code assets/iac_p/geo/block/test_controller.geo.json}
 * 纹理: {@code assets/iac_p/textures/block/test_controller.png}
 * 动画: {@code assets/iac_p/animations/block/test_controller.animation.json}
 * <p>
 * DefaultedBlockGeoModel 会自动在 {@code geo/} 后插入 {@code block/}，
 * 所以构造参数只需传 {@code "test_controller"} 即可。
 */
public class TestControllerModel extends DefaultedBlockGeoModel<TestControllerBlockEntity> {

    public TestControllerModel() {
        super(ResourceLocation.fromNamespaceAndPath(IACP.MODID, "test_controller"));
    }
}
