package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.init.ModItems;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
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

    public ItemAmuletSkyfeather(String name) {
        setUnlocalizedName(name);
        setRegistryName(name);
        setCreativeTab(CreativeTabs.MISC);
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
        tooltip.add("§b含有风神的意识的残留，在危急时刻会发挥出极大的作用");

        NBTTagCompound nbt = stack.getTagCompound();
        
        if (worldIn != null && nbt != null && nbt.hasKey("skyfeather_cooldown")) {
            long cooldown = nbt.getLong("skyfeather_cooldown");
            long remaining = (cooldown + 12000) - worldIn.getTotalWorldTime();
            if (remaining > 0) {
                tooltip.add("§c神的意识正在重新汇聚");
            } else {
                 tooltip.add("§a准备就绪");
            }
        } else {
            tooltip.add("§a准备就绪");
        }
    }
}
