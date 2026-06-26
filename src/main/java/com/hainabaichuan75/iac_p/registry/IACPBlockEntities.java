package com.hainabaichuan75.iac_p.registry;

import com.hainabaichuan75.iac_p.IACP;
import com.hainabaichuan75.iac_p.block.base_cabin.BaseCabinBlockEntity;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.Type;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.datafix.fixes.References;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BaseCabinBlockEntity>> BASE_CABIN =
            BLOCK_ENTITIES.register("base_cabin", () -> BlockEntityType.Builder.of(BaseCabinBlockEntity::new,
                    IACPBlocks.BASE_CABIN.get()).build(DSL.remainderType()));
}
