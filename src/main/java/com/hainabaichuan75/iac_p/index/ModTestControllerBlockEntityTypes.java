package com.hainabaichuan75.iac_p.index;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.content.blocks.test_controller.TestControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 测试 Controller 的 BlockEntityType 注册。
 * <p>
 * 与普通 BE 分开，避免与现有注册中心循环依赖。
 */
public class ModTestControllerBlockEntityTypes {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IACP.MODID);

    public static final Supplier<BlockEntityType<TestControllerBlockEntity>> TEST_CONTROLLER =
            BLOCK_ENTITY_TYPES.register("test_controller",
                    () -> BlockEntityType.Builder.of(
                            TestControllerBlockEntity::new,
                            ModBlocks.TEST_CONTROLLER.get()
                    ).build(null));
}
