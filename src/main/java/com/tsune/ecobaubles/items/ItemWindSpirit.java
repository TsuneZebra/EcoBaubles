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

public class ItemWindSpirit extends Item implements IBauble {
    public ItemWindSpirit(String name) {
        setUnlocalizedName(name);
        setRegistryName(name);
        setCreativeTab(ModCreativeTab.INSTANCE);
        setMaxStackSize(1);

        ModItems.ITEMS.add(this);
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.TRINKET; // 任意部位，使用护身符槽位
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(I18n.format("item.wind_spirit.desc"));
        tooltip.add("");
        tooltip.add(I18n.format("item.wind_spirit.effect_title"));
        tooltip.add(I18n.format("item.wind_spirit.wind_amulet_boost"));
        tooltip.add(I18n.format("item.wind_spirit.skyfeather_boost"));
        tooltip.add(I18n.format("item.wind_spirit.wind_ring_boost"));
        tooltip.add(I18n.format("item.wind_spirit.wind_attraction_boost"));
        tooltip.add(I18n.format("item.wind_spirit.crack_wind_boost"));
        tooltip.add(I18n.format("item.wind_spirit.wind_shadow_belt_boost"));
        tooltip.add(I18n.format("item.wind_spirit.wind_crown_boost"));
        tooltip.add(I18n.format("item.wind_spirit.wind_shield_echo_boost"));
        tooltip.add(I18n.format("item.wind_spirit.wind_charm_boost"));
    }
}
