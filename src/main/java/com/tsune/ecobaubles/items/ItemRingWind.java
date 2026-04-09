package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.capability.IPlayerCooldown;
import com.tsune.ecobaubles.capability.PlayerCooldownProvider;
import com.tsune.ecobaubles.events.ForgeEventHandler;
import com.tsune.ecobaubles.init.ModCreativeTab;
import com.tsune.ecobaubles.init.ModItems;
import com.tsune.ecobaubles.item.special.IActiveAbility;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

public class ItemRingWind extends Item implements IBauble, IActiveAbility {

    private static final String TAG_COOLDOWN = "wind_ring_cd";
    public static final int COOLDOWN_TICKS = 40 * 20; // 40s
    public static final int BUFF_TICKS = 2 * 20;       // 2s

    public ItemRingWind(String name) {
        setUnlocalizedName(name);
        setRegistryName(name);
        setCreativeTab(ModCreativeTab.INSTANCE);
        setMaxStackSize(1);
        ModItems.ITEMS.add(this);
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.RING;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.COMMON;
    }

    @Override
    public void useAbility(EntityPlayer player, ItemStack stack) {
        if (player.world.isRemote) return;
        long now = player.world.getTotalWorldTime();
        NBTTagCompound nbt = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        long lastUsed = nbt.getLong(TAG_COOLDOWN);
        if (now - lastUsed < COOLDOWN_TICKS) return;
        nbt.setLong(TAG_COOLDOWN, now);
        stack.setTagCompound(nbt);
        IPlayerCooldown cap = player.getCapability(PlayerCooldownProvider.COOLDOWN_CAP, null);
        if (cap != null) cap.setGlobalCooldown(now + COOLDOWN_TICKS);
        ForgeEventHandler.activateWindRingBuff(player, now + BUFF_TICKS);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(I18n.format("item.wind_ring.desc"));
        if (worldIn != null) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null) {
                long lastUsed = nbt.getLong(TAG_COOLDOWN);
                long remain = (lastUsed + COOLDOWN_TICKS) - worldIn.getTotalWorldTime();
                if (remain > 0) {
                    tooltip.add(I18n.format("item.wind_ring.cooldown"));
                } else {
                    tooltip.add(I18n.format("item.wind_ring.ready"));
                }
            } else {
                tooltip.add(I18n.format("item.wind_ring.ready"));
            }
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "§3" + super.getItemStackDisplayName(stack);
    }
}