package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.events.ice.IceEventHandler;
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

public class ItemAmuletIceGod extends Item implements IBauble {

    public ItemAmuletIceGod(String name) {
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
        tooltip.add(I18n.format("item.ice_god_amulet.desc"));
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && mc.world != null) {
            long lastTrigger = IceEventHandler.getIceGodLastTrigger(mc.player.getUniqueID());
            long now = mc.world.getTotalWorldTime();
            long cdDuration = 720L * 20L; // default (non-spirit) cooldown in ticks
            if (lastTrigger < 0 || now - lastTrigger >= cdDuration) {
                tooltip.add("\u00a7a\u51b0\u51bb: \u5df2\u5c31\u7eea");
            } else {
                long remaining = cdDuration - (now - lastTrigger);
                tooltip.add(String.format("\u00a7c\u51b0\u51bb: \u51b7\u5374\u4e2d (%.1fs)", remaining / 20.0f));
            }
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "§b" + super.getItemStackDisplayName(stack);
    }
}