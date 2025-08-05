package com.tsune.ecobaubles.capability;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;

public class PlayerCooldownStorage implements IStorage<IPlayerCooldown> {

    @Override
    public NBTBase writeNBT(Capability<IPlayerCooldown> capability, IPlayerCooldown instance, EnumFacing side) {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setLong("global_cooldown", instance.getGlobalCooldown());
        return compound;
    }

    @Override
    public void readNBT(Capability<IPlayerCooldown> capability, IPlayerCooldown instance, EnumFacing side, NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            instance.setGlobalCooldown(((NBTTagCompound) nbt).getLong("global_cooldown"));
        }
    }
}
