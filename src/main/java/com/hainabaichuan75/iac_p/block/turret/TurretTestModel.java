package com.hainabaichuan75.iac_p.block.turret;

import com.hainabaichuan75.iac_p.IACP;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * TurretTestModel —— 炮塔测试方块的 GeckoLib 模型定义。
 * <p>
 * 模型: {@code assets/iac_p/geo/block/turret_test.geo.json}
 * 纹理: {@code assets/iac_p/textures/block/turret_test.png}
 * 动画: {@code assets/iac_p/animations/block/turret_test.animation.json}
 * <p>
 * DefaultedBlockGeoModel 自动在 {@code geo/} 后插入 {@code block/}，
 * 构造参数 {@code "turret_test"} 匹配资源文件名。
 */
public class TurretTestModel extends DefaultedBlockGeoModel<TurretTestBlockEntity> {

    public TurretTestModel() {
        super(ResourceLocation.fromNamespaceAndPath(IACP.MODID, "turret_test"));
    }
}
