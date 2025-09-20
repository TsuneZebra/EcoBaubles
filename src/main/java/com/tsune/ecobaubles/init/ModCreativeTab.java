package com.tsune.ecobaubles.init;

import com.tsune.ecobaubles.EcoBaubles;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public class ModCreativeTab extends CreativeTabs {

    public static final ModCreativeTab INSTANCE = new ModCreativeTab();

    public ModCreativeTab() {
        super(EcoBaubles.MODID);
    }

    @Override
    public ItemStack getTabIconItem() {
        return new ItemStack(ModItems.WIND_AMULET);
    }
}
