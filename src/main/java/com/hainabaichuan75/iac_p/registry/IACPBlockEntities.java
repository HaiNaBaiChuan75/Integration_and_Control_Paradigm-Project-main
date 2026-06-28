package com.hainabaichuan75.iac_p.registry;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlockEntity;
import com.hainabaichuan75.iac_p.block.shotgun.ShotGunBlockEntity;
import com.hainabaichuan75.iac_p.block.simplewheel.SimpleWheelBlockEntity;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.Type;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public abstract class IACPBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IACP.MODID);

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> create(String id,
                                                                                                         BlockEntityType.Builder<T> builder) {
        Type<?> type = Util.fetchChoiceType(References.BLOCK_ENTITY, id);
        return BLOCK_ENTITIES.register(id, () -> builder.build(type == null ? DSL.remainderType() : type));
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> create(String id,
                                                                                                         BlockEntityType.BlockEntitySupplier<? extends T> factory, Block... validBlocks) {
        return create(id, BlockEntityType.Builder.of(factory, validBlocks));
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BaseCabinBlockEntity>> BASE_CABIN =
            BLOCK_ENTITIES.register("base_cabin", () -> {
        // 在 lambda 中获取方块，此时方块已经注册
        Type<?> type = Util.fetchChoiceType(References.BLOCK_ENTITY, "base_cabin");
        return BlockEntityType.Builder.of(BaseCabinBlockEntity::new, IACPBlocks.BASE_CABIN.get()  // 延迟执行，安全
        ).build(type == null ? DSL.remainderType() : type);
    });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SimpleWheelBlockEntity>> SIMPLE_WHEEL =
            BLOCK_ENTITIES.register("simple_wheel", () -> {
        Type<?> type = Util.fetchChoiceType(References.BLOCK_ENTITY, "simple_wheel");
        return BlockEntityType.Builder.of(SimpleWheelBlockEntity::new, IACPBlocks.SIMPLE_WHEEL.get()).build(type == null ? DSL.remainderType() : type);
    });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShotGunBlockEntity>> SHOT_GUN =
            BLOCK_ENTITIES.register("shot_gun", () -> {
        Type<?> type = Util.fetchChoiceType(References.BLOCK_ENTITY, "shot_gun");
        return BlockEntityType.Builder.of(ShotGunBlockEntity::new, IACPBlocks.SHOT_GUN.get()).build(type == null ?
                DSL.remainderType() : type);
    });
}
