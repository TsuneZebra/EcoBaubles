package com.tsune.ecobaubles.capability;

import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PlayerCooldownProvider implements ICapabilitySerializable<NBTBase> {

    @CapabilityInject(IPlayerCooldown.class)
    public static final Capability<IPlayerCooldown> COOLDOWN_CAP = null;

    private IPlayerCooldown instance = COOLDOWN_CAP.getDefaultInstance();

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == COOLDOWN_CAP;
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        return hasCapability(capability, facing) ? COOLDOWN_CAP.<T>cast(this.instance) : null;
    }

    @Override
    public NBTBase serializeNBT() {
        return COOLDOWN_CAP.getStorage().writeNBT(COOLDOWN_CAP, this.instance, null);
    }

    @Override
    public void deserializeNBT(NBTBase nbt) {
        COOLDOWN_CAP.getStorage().readNBT(COOLDOWN_CAP, this.instance, null, nbt);
    }
}
