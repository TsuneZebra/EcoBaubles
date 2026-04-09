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

public class ItemThunderSpirit extends Item implements IBauble {

    public ItemThunderSpirit(String name) {
        setUnlocalizedName(name);
        setRegistryName(name);
        setCreativeTab(ModCreativeTab.INSTANCE);
        setMaxStackSize(1);
        ModItems.ITEMS.add(this);
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.TRINKET;
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
        tooltip.add(I18n.format("item.thunder_spirit.desc"));
        tooltip.add("");
        tooltip.add(I18n.format("item.thunder_spirit.effect_title"));
        tooltip.add(I18n.format("item.thunder_spirit.thunder_god_boost"));
        tooltip.add(I18n.format("item.thunder_spirit.static_amulet_boost"));
        tooltip.add(I18n.format("item.thunder_spirit.thunder_crack_boost"));
        tooltip.add(I18n.format("item.thunder_spirit.lightning_chain_boost"));
        tooltip.add(I18n.format("item.thunder_spirit.electrify_ring_boost"));
        tooltip.add(I18n.format("item.thunder_spirit.thunder_pulse_boost"));
        tooltip.add(I18n.format("item.thunder_spirit.thunder_eye_boost"));
        tooltip.add(I18n.format("item.thunder_spirit.thunder_armor_boost"));
        tooltip.add(I18n.format("item.thunder_spirit.thunder_bolt_boost"));
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "§5" + super.getItemStackDisplayName(stack);
    }
}