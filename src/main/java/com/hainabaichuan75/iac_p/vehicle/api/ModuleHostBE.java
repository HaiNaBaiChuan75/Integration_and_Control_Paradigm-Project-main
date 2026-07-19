package com.hainabaichuan75.iac_p.vehicle.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 载具方块实体基类（可选便利抽象）。
 * <p>
 * 子类在 field 初始化时用 {@link #add} 注册 Module：
 * <pre>{@code
 * public class CannonBE extends ModuleHostBE {
 *     public final BarrelModule barrel = add(new BarrelModule(this));
 *     public final BreechModule breech = add(new BreechModule(this));
 *     ...
 * }
 * }</pre>
 */
public abstract class ModuleHostBE extends BlockEntity implements ModuleHost {

    private final List<Module> moduleList = new ArrayList<>();

    protected ModuleHostBE(@NotNull BlockEntityType<?> type, @NotNull BlockPos pos, @NotNull BlockState state) {
        super(type, pos, state);
    }

    protected <M extends Module> M add(M module) {
        moduleList.add(module);
        return module;
    }

    @Override
    public List<Module> modules() {
        return moduleList;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveComponents(tag);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadComponents(tag);
    }
}
