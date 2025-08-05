package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.init.ModItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ItemAmuletWind extends Item implements IBauble {

    public ItemAmuletWind(String name) {
        setUnlocalizedName(name);
        setRegistryName(name);
        setCreativeTab(CreativeTabs.MISC); // We can create a custom creative tab later

        ModItems.ITEMS.add(this);
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.AMULET;
    }
}
