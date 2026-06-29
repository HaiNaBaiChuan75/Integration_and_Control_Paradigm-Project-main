package com.hainabaichuan75.iac_p.block.base_cabin;

import com.hainabaichuan75.iac_p.core.part.PartBlockEntity;
import com.hainabaichuan75.iac_p.registry.IACPBlockEntities;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class BaseCabinBlockEntity extends PartBlockEntity implements GeoAnimatable {
    private static final Int2ObjectMap<Quaterniondc> ORIENTATIONS = Util.make(new Int2ObjectOpenHashMap<>(8), map -> {
        for (int i = 0; i < 8; i++) {
            map.put(i, new Quaterniond().rotateY(Math.toRadians(-i * 45)));
        }
    });

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private byte facingIndex;   // 0-7, 对应 ORIENTATIONS 中的键

    public BaseCabinBlockEntity(BlockPos pos, BlockState state) {
        super(IACPBlockEntities.BASE_CABIN.get(), pos, state);
    }

    public void setFacingIndex(int index) {
        this.facingIndex = (byte) (index & 7);
        setChanged();
    }

    @Override
    public @NotNull Quaterniondc orientation() {
        return ORIENTATIONS.get(facingIndex);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 无动画
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return level != null ? level.getGameTime() : 0;
    }

    /* ==================== NBT 持久化 ==================== */

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByte("facingIndex", facingIndex);
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        facingIndex = tag.getByte("facingIndex");
    }

    /* ==================== 网络同步 ==================== */

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putByte("facingIndex", facingIndex);
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
        facingIndex = tag.getByte("facingIndex");
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.@NotNull Provider lookupProvider) {
        handleUpdateTag(pkt.getTag(), lookupProvider);
    }
}
