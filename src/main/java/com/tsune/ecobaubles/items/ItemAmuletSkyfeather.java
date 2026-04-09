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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

public class ItemAmuletSkyfeather extends Item implements IBauble {
    
    private static final String COOLDOWN_TAG = "skyfeather_cooldown";
    private static final int COOLDOWN_TICKS = 9600; // 8 minutes * 60 seconds * 20 ticks/second

    public ItemAmuletSkyfeather(String name) {
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
        return EnumRarity.COMMON;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(I18n.format("item.skyfeather_amulet.desc"));

        NBTTagCompound nbt = stack.getTagCompound();
        
        if (worldIn != null && nbt != null && nbt.hasKey(COOLDOWN_TAG)) {
            long lastTriggerTime = nbt.getLong(COOLDOWN_TAG);
            long currentTime = worldIn.getTotalWorldTime();
            long remaining = (lastTriggerTime + COOLDOWN_TICKS) - currentTime;
            if (remaining > 0) {
                tooltip.add(I18n.format("item.skyfeather_amulet.cooldown"));
            } else {
                 tooltip.add(I18n.format("item.skyfeather_amulet.ready"));
            }
        } else {
            tooltip.add(I18n.format("item.skyfeather_amulet.ready"));
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "§3" + super.getItemStackDisplayName(stack);
    }
}