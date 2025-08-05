package com.tsune.ecobaubles.item.special;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public interface IActiveAbility {
    void useAbility(EntityPlayer player, ItemStack stack);
}
