package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.entity.EntityWindArrow;
import com.tsune.ecobaubles.init.ModItems;
import com.tsune.ecobaubles.item.special.IActiveAbility;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.init.SoundEvents;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

public class ItemRingWind extends Item implements IBauble, IActiveAbility {

    public ItemRingWind(String name) {
        setUnlocalizedName(name);
        setRegistryName(name);
        setCreativeTab(CreativeTabs.MISC);
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

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add("§7按[G]发射一根穿透一切的风箭。");
        tooltip.add("§7(共享冷却 60s)");
    }

    @Override
    public void useAbility(EntityPlayer player, ItemStack stack) {
        World world = player.world;
        if (!world.isRemote) {
            EntityWindArrow arrow = new EntityWindArrow(world, player);
            arrow.shoot(player, player.rotationPitch, player.rotationYaw, 0.0F, 3.0F, 1.0F);
            world.spawnEntity(arrow);
            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }
}
