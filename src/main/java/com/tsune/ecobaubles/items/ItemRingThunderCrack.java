package com.tsune.ecobaubles.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.tsune.ecobaubles.events.thunder.ThunderEventHandler;
import com.tsune.ecobaubles.init.ModCreativeTab;
import com.tsune.ecobaubles.init.ModItems;
import com.tsune.ecobaubles.item.special.IActiveAbility;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import com.tsune.ecobaubles.capability.IPlayerCooldown;
import com.tsune.ecobaubles.capability.PlayerCooldownProvider;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ItemRingThunderCrack extends Item implements IBauble, IActiveAbility {

    public static final int COOLDOWN_TICKS        = 45 * 20; // 45s
    public static final int COOLDOWN_TICKS_SPIRIT = 30 * 20; // 30s with 雷灵

    // Stores the CD actually used on last activation so getCooldownTicks() returns the right value
    private static int lastCooldownUsed = COOLDOWN_TICKS;

    public ItemRingThunderCrack(String name) {
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
    public int getCooldownTicks() { return lastCooldownUsed; }

    @Override
    public void useAbility(EntityPlayer player, ItemStack stack) {
        if (player.world.isRemote) return;
        long now = player.world.getTotalWorldTime();
        UUID pid = player.getUniqueID();

        if (ThunderEventHandler.isCrackRingOnCooldown(pid, now)) return;

        boolean spirit = ThunderEventHandler.hasThunderSpirit(player);
        float dashDamage = spirit ? 18.0f : 12.0f;
        int cooldown = spirit ? COOLDOWN_TICKS_SPIRIT : COOLDOWN_TICKS;
        lastCooldownUsed = cooldown;

        // Horizontal look direction, normalized
        Vec3d look = player.getLookVec();
        double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
        if (hLen < 1e-6) hLen = 1.0;
        double dirX = look.x / hLen;
        double dirZ = look.z / hLen;

        double startX = player.posX;
        double startY = player.posY;
        double startZ = player.posZ;
        double endX = startX + dirX * 7.0;
        double endZ = startZ + dirZ * 7.0;

        // Collect enemies along dash path using ray intercept (~1.5 block wide)
        AxisAlignedBB pathAABB = new AxisAlignedBB(
                Math.min(startX, endX) - 1.5, startY - 0.5,
                Math.min(startZ, endZ) - 1.5,
                Math.max(startX, endX) + 1.5, startY + 2.5,
                Math.max(startZ, endZ) + 1.5);
        List<EntityLivingBase> candidates = player.world.getEntitiesWithinAABB(EntityLivingBase.class, pathAABB);
        Set<EntityLivingBase> toHit = new LinkedHashSet<>();
        Vec3d rayStart = new Vec3d(startX, startY + 1.0, startZ);
        Vec3d rayEnd   = new Vec3d(endX,   startY + 1.0, endZ);
        for (EntityLivingBase e : candidates) {
            if (e == player) continue;
            if (e instanceof EntityPlayer) continue;
            if (!(e instanceof IMob)) continue;
            AxisAlignedBB eBox = e.getEntityBoundingBox().grow(1.0);
            if (eBox.calculateIntercept(rayStart, rayEnd) != null) {
                toHit.add(e);
            }
        }

        // Spawn particles along the dash trail BEFORE teleporting so the player dashes through them
        if (player.world instanceof WorldServer) {
            WorldServer ws = (WorldServer) player.world;
            int trailSteps = 14; // one point every 0.5 blocks
            for (int k = 0; k <= trailSteps; k++) {
                double t = (double) k / trailSteps;
                double px = startX + dirX * 7.0 * t;
                double pz = startZ + dirZ * 7.0 * t;
                // CRIT_MAGIC (blue sparks) + FIREWORKS_SPARK (bright) for a visible lightning trail
                ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, true,
                    px, startY + 1.0, pz, 5, 0.1, 0.4, 0.1, 0.08);
                ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, true,
                    px, startY + 1.0, pz, 3, 0.05, 0.3, 0.05, 0.05);
            }
        }

        // Set BOTH cooldowns BEFORE dealing damage so onLivingDeath kill-reductions apply correctly
        ThunderEventHandler.setCrackRingCooldown(pid, now + cooldown);
        IPlayerCooldown cap = player.getCapability(PlayerCooldownProvider.COOLDOWN_CAP, null);
        if (cap != null) cap.setGlobalCooldown(now + cooldown);

        // Dash: teleport player to end position
        player.setPositionAndUpdate(endX, startY, endZ);

        // Player-attributed magic damage source so getTrueSource() returns the player (enables kill credit)
        DamageSource crackDamage = new EntityDamageSource("magic", player)
                .setMagicDamage().setDamageBypassesArmor();

        // Deal damage + electrify 2s to all enemies hit along path
        for (EntityLivingBase e : toHit) {
            e.attackEntityFrom(crackDamage, dashDamage);
            ThunderEventHandler.electrify(e, 40L); // 2s
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(I18n.format("item.thunder_crack_ring.passive"));
        tooltip.add(I18n.format("item.thunder_crack_ring.desc"));
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "§5" + super.getItemStackDisplayName(stack);
    }
}