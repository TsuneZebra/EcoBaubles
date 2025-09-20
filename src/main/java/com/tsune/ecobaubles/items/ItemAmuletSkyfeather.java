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
        tooltip.add(I18n.format("item.skyfeather_amulet.desc"));

        NBTTagCompound nbt = stack.getTagCompound();
        
        if (worldIn != null && nbt != null && nbt.hasKey("skyfeather_cooldown")) {
            long cooldown = nbt.getLong("skyfeather_cooldown");
            long remaining = (cooldown + 200) - worldIn.getTotalWorldTime();
            if (remaining > 0) {
                tooltip.add(I18n.format("item.skyfeather_amulet.cooldown"));
            } else {
                 tooltip.add(I18n.format("item.skyfeather_amulet.ready"));
            }
        } else {
            tooltip.add(I18n.format("item.skyfeather_amulet.ready"));
        }
    }
}
