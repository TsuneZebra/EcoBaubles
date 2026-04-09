package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.events.thunder.ThunderEventHandler;
import com.tsune.ecobaubles.init.ModCreativeTab;
import com.tsune.ecobaubles.init.ModItems;
import net.minecraft.client.Minecraft;
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

public class ItemAmuletThunderGod extends Item implements IBauble {

    public ItemAmuletThunderGod(String name) {
        setUnlocalizedName(name);
        setRegistryName(name);
        setCreativeTab(ModCreativeTab.INSTANCE);
        setMaxStackSize(1);
        ModItems.ITEMS.add(this);
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.AMULET;
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
        tooltip.add(I18n.format("item.thunder_god_amulet.desc"));
        net.minecraft.client.entity.EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            java.util.UUID pid = player.getUniqueID();
            int charges = ThunderEventHandler.getThunderCharges(pid);
            boolean ready = ThunderEventHandler.isThunderStrikeReady(pid);
            if (ready) {
                tooltip.add("\u00a76\u96f7\u80fd: " + charges + " \u5c42 \u00a7c[\u5c31\u7eea\u4e00\u51fb!]");
            } else {
                tooltip.add("\u00a7e\u96f7\u80fd: " + charges + " \u5c42");
            }
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "§5" + super.getItemStackDisplayName(stack);
    }
}