package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.capability.IPlayerCooldown;
import com.tsune.ecobaubles.capability.PlayerCooldownProvider;
import com.tsune.ecobaubles.events.fire.FireEventHandler;
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

public class ItemRingExplosion extends Item implements IBauble, IActiveAbility {

    public static final String TAG_ARMED_TICK = "explosion_ring_armed";
    public static final String TAG_COOLDOWN_TICK = "explosion_ring_cd";
    public static final int COOLDOWN_TICKS = 180 * 20; // 180s
    public static final int FUSE_TICKS = 3 * 20;       // 3s

    public ItemRingExplosion(String name) {
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
        // 火灵: 冷却 120s, 普通: 180s
        int cd = FireEventHandler.hasFireSpirit(player) ? 120 * 20 : COOLDOWN_TICKS;
        if (now - lastUsed < cd) return;
        long armed = nbt.getLong(TAG_ARMED_TICK);
        if (armed > 0) return; // already armed
        nbt.setLong(TAG_ARMED_TICK, now);
        nbt.setLong(TAG_COOLDOWN_TICK, now);
        stack.setTagCompound(nbt);
        IPlayerCooldown cap = player.getCapability(PlayerCooldownProvider.COOLDOWN_CAP, null);
        if (cap != null) cap.setGlobalCooldown(now + cd);
        player.setFire(4); // visual fuse fire
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(I18n.format("item.explosion_ring.desc"));

        if (worldIn != null) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt != null) {
                long armed = nbt.getLong(TAG_ARMED_TICK);
                if (armed > 0) {
                    tooltip.add(I18n.format("item.explosion_ring.armed"));
                    return;
                }
                long lastUsed = nbt.getLong(TAG_COOLDOWN_TICK);
                long remain = (lastUsed + COOLDOWN_TICKS) - worldIn.getTotalWorldTime();
                // Note: tooltip shows base cooldown; actual cooldown may be shorter with Fire Spirit
                if (remain > 0) {
                    tooltip.add(I18n.format("item.explosion_ring.cooldown"));
                } else {
                    tooltip.add(I18n.format("item.explosion_ring.ready"));
                }
            } else {
                tooltip.add(I18n.format("item.explosion_ring.ready"));
            }
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "§4" + super.getItemStackDisplayName(stack);
    }
}