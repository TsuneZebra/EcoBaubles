package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.init.ModCreativeTab;
import com.tsune.ecobaubles.init.ModItems;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

public class ItemBeltLavaHeart extends Item implements IBauble {

    public static final java.util.UUID UUID_MAX_HP = java.util.UUID.fromString("c3e3c5f6-1d7e-4a06-b3fd-8d3c1c4d9e23");

    public ItemBeltLavaHeart(String name) {
        setUnlocalizedName(name);
        setRegistryName(name);
        setCreativeTab(ModCreativeTab.INSTANCE);
        setMaxStackSize(1);
        ModItems.ITEMS.add(this);
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.BELT;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.RARE;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(I18n.format("item.lava_heart_belt.desc"));
    }
}
