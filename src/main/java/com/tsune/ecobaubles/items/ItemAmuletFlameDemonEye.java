package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.init.ModCreativeTab;
import com.tsune.ecobaubles.init.ModItems;
import com.tsune.ecobaubles.item.special.IActiveAbility;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
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

public class ItemAmuletFlameDemonEye extends Item implements IBauble, IActiveAbility {

    private static final java.util.UUID MOD_UUID_ATTACK = java.util.UUID.fromString("a1c1a3c2-6f3f-4b83-9a7e-2b8f9f2b7c01");
    private static final java.util.UUID MOD_UUID_SPEED = java.util.UUID.fromString("b2d2b4e4-0c6d-4f95-a2fc-7c2b0b3c8d12");

    public static final String TAG_TOGGLE = "flame_eye_toggle"; // 是否开启“玩火自焚”
    public static final String TAG_UNYIELD_END_TICK = "flame_eye_unyield_end"; // 不屈结束刻
    public static final String TAG_COOLDOWN_TICK = "flame_eye_cooldown"; // 不屈冷却起始刻
    public static final int UNYIELD_DURATION_TICKS = 7 * 20; // 7s
	public static final int UNYIELD_COOLDOWN_TICKS = 300 * 20; // 10s (for testing)

    public ItemAmuletFlameDemonEye(String name) {
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
        tooltip.add(I18n.format("item.flame_demon_eye.desc"));

        NBTTagCompound nbt = stack.getTagCompound();
        boolean toggle = nbt != null && nbt.getBoolean(TAG_TOGGLE);
        tooltip.add(I18n.format(toggle ? "item.flame_demon_eye.toggle_on" : "item.flame_demon_eye.toggle_off"));

        if (worldIn != null && nbt != null && nbt.hasKey(TAG_COOLDOWN_TICK)) {
            long last = nbt.getLong(TAG_COOLDOWN_TICK);
            long remain = (last + UNYIELD_COOLDOWN_TICKS) - worldIn.getTotalWorldTime();
            if (remain > 0) {
                tooltip.add(I18n.format("item.flame_demon_eye.cooldown"));
            } else {
                tooltip.add(I18n.format("item.flame_demon_eye.ready"));
            }
        } else {
            tooltip.add(I18n.format("item.flame_demon_eye.ready"));
        }
    }

    @Override
    public void useAbility(EntityPlayer player, ItemStack stack) {
        // 保持与全局能力键解耦：此处不做任何事，改由 R 键单独切换
    }

    public static void useToggle(ItemStack stack) {
        NBTTagCompound nbt = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        boolean toggled = nbt.getBoolean(TAG_TOGGLE);
        nbt.setBoolean(TAG_TOGGLE, !toggled);
        stack.setTagCompound(nbt);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "§4" + super.getItemStackDisplayName(stack);
    }
}