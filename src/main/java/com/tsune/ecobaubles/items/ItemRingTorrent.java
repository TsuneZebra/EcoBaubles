package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.entity.EntityWaterOrb;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

public class ItemRingTorrent extends Item implements IBauble, IActiveAbility {

    public static final String TAG_COOLDOWN_TICK = "torrent_ring_cd";
    public static final int COOLDOWN_TICKS = 800; // 40s

    public ItemRingTorrent(String name) {
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
        return EnumRarity.RARE;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    public void useAbility(EntityPlayer player, ItemStack stack) {
        if (player.world.isRemote) return;
        NBTTagCompound nbt = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        long now = player.world.getTotalWorldTime();
        long lastUsed = nbt.getLong(TAG_COOLDOWN_TICK);
        // 水灵: 冷却 30s, 普通: 40s
        int cd = WaterEventHandler.hasWaterSpirit(player) ? 600 : COOLDOWN_TICKS;
        if (now - lastUsed < cd) return;

        nbt.setLong(TAG_COOLDOWN_TICK, now);
        stack.setTagCompound(nbt);

        World world = player.world;
        EntityWaterOrb orb = new EntityWaterOrb(world, player);

        // Launch in the direction the player is looking
        Vec3d look = player.getLookVec();
        orb.shoot(look.x, look.y, look.z, 2.0f, 1.0f);
        world.spawnEntity(orb);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(I18n.format("item.torrent_ring.desc"));

        if (worldIn != null) {
            NBTTagCompound nbt = stack.getTagCompound();
            long lastUsed = nbt != null ? nbt.getLong(TAG_COOLDOWN_TICK) : 0L;
            long remain = (lastUsed + COOLDOWN_TICKS) - worldIn.getTotalWorldTime();
            if (remain > 0) {
                tooltip.add(I18n.format("item.torrent_ring.cooldown"));
            } else {
                tooltip.add(I18n.format("item.torrent_ring.ready"));
            }
        }
    }
}
