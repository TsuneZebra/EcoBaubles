package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.capability.IPlayerCooldown;
import com.tsune.ecobaubles.capability.PlayerCooldownProvider;
import com.tsune.ecobaubles.events.water.WaterEventHandler;
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

public class ItemRingSpringShield extends Item implements IBauble, IActiveAbility {

    public static final String TAG_COOLDOWN_TICK = "spring_shield_ring_cd";
    public static final int COOLDOWN_TICKS   = 60 * 20; // 60s
    public static final int SHIELD_TICKS     = 10 * 20; // 10s immunity
    public static final int REPAY_SECONDS    = 15;       // 15s repayment

    public ItemRingSpringShield(String name) {
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
        NBTTagCompound nbt = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        long now = player.world.getTotalWorldTime();
        long lastUsed = nbt.getLong(TAG_COOLDOWN_TICK);
        boolean ws = WaterEventHandler.hasWaterSpirit(player);
        int cd = ws ? 45 * 20 : COOLDOWN_TICKS;
        if (now - lastUsed < cd) return;

        nbt.setLong(TAG_COOLDOWN_TICK, now);
        stack.setTagCompound(nbt);

        IPlayerCooldown cap = player.getCapability(PlayerCooldownProvider.COOLDOWN_CAP, null);
        if (cap != null) cap.setGlobalCooldown(now + cd);

        WaterEventHandler.activateSpringShield(player, now + SHIELD_TICKS);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(I18n.format("item.spring_shield_ring.desc"));
        if (worldIn != null) {
            NBTTagCompound nbt = stack.getTagCompound();
            long lastUsed = nbt != null ? nbt.getLong(TAG_COOLDOWN_TICK) : 0L;
            boolean ws = WaterEventHandler.hasWaterSpirit(worldIn instanceof net.minecraft.world.WorldServer
                    ? null : null); // client-side only shows static CD
            long cd = COOLDOWN_TICKS;
            long remain = (lastUsed + cd) - worldIn.getTotalWorldTime();
            if (remain > 0) {
                tooltip.add(I18n.format("item.spring_shield_ring.cooldown"));
            } else {
                tooltip.add(I18n.format("item.spring_shield_ring.ready"));
            }
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "\u00a79" + super.getItemStackDisplayName(stack);
    }
}
