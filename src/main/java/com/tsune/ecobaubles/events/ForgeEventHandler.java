package com.tsune.ecobaubles.events;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.init.ModItems;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.SharedMonsterAttributes;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

public class ForgeEventHandler {

    private static final String COOLDOWN_TAG = "skyfeather_cooldown";
    private static final int COOLDOWN_TICKS = 9600; // 8 minutes * 60 seconds * 20 ticks/second

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        World world = player.world;

        if (world.isRemote) {
            return;
        }

        IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
        ItemStack amuletStack = ItemStack.EMPTY;
        int amuletSlot = -1;

        for (int i = 0; i < baublesHandler.getSlots(); i++) {
            ItemStack stack = baublesHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (stack.getItem() == ModItems.WIND_AMULET && event.getAmount() > 6.0F) {
                    handleWindAmulet(player, world);
                    return; // Wind Amulet triggered, stop processing for this event
                } else if (stack.getItem() == ModItems.SKYFEATHER_AMULET) {
                    amuletStack = stack;
                    amuletSlot = i;
                    // Don't break, we might find wind amulet first
                }
            }
        }
        
        // Handle Skyfeather Amulet logic if found and damage is lethal
        if (!amuletStack.isEmpty() && player.getHealth() - event.getAmount() <= 0) {
            handleSkyfeatherAmulet(event, player, world, amuletStack);
        }
    }

    private void handleWindAmulet(EntityPlayer player, World world) {
        double range = 5.0D;
        List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, player.getEntityBoundingBox().grow(range));
        for (EntityLivingBase entity : entities) {
            if (entity != player) {
                entity.knockBack(player, 1.0F, player.posX - entity.posX, player.posZ - entity.posZ);
            }
        }
        player.addPotionEffect(new PotionEffect(MobEffects.SPEED, 60, 1));
        world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.8F, 1.0F);
    }

    private void handleSkyfeatherAmulet(LivingHurtEvent event, EntityPlayer player, World world, ItemStack amuletStack) {
        NBTTagCompound nbt = amuletStack.hasTagCompound() ? amuletStack.getTagCompound() : new NBTTagCompound();
        long lastTriggerTime = nbt.getLong(COOLDOWN_TAG);

        if (world.getTotalWorldTime() - lastTriggerTime < COOLDOWN_TICKS) {
            return; // Amulet is on cooldown
        }

        // --- Skyfeather's Grace Triggered ---
        event.setCanceled(true);
        player.setHealth(1.0F);
        
        // Set cooldown
        nbt.setLong(COOLDOWN_TAG, world.getTotalWorldTime());
        amuletStack.setTagCompound(nbt);

        // Apply effects
        player.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 160, 4)); // Absorption V (level 4) for 8s
        player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 160, 1)); // Regeneration II (level 1) for 8s
        player.addPotionEffect(new PotionEffect(MobEffects.SPEED, 160, 2)); // Speed III (level 2) for 8s
        player.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 160, 4)); // Jump Boost V (level 4) for 8s
        player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 160, 0)); // Resistance I (level 0) for 8s

        // Launch logic - check for blocks in 8 blocks above
        boolean hasBlockAbove = false;
        for (int i = 1; i <= 8; i++) {
            if (world.getBlockState(player.getPosition().up(i)).isFullCube()) {
                hasBlockAbove = true;
                break;
            }
        }
        
        if (hasBlockAbove) {
            // Strong knockback if blocked
            double range = 6.0D;
            List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, player.getEntityBoundingBox().grow(range));
             for (EntityLivingBase entity : entities) {
                if (entity != player) {
                    entity.knockBack(player, 2.5F, player.posX - entity.posX, player.posZ - entity.posZ);
                    // Add 20 damage to knocked back entities
                    entity.attackEntityFrom(DamageSource.causePlayerDamage(player), 20.0F);
                }
            }
        } else {
            // Launch upwards
            Vec3d lookVec = player.getLookVec();
            player.motionX = lookVec.x * 0.1;
            player.motionY = 2.2; // Strong upward launch
            player.motionZ = lookVec.z * 0.1;
            player.velocityChanged = true;
        }
        
        world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public void onPlayerJump(LivingJumpEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
            for (int i = 0; i < baublesHandler.getSlots(); i++) {
                if (baublesHandler.getStackInSlot(i).getItem() == ModItems.SKYFEATHER_AMULET) {
                    player.motionY += 0.2D; // Add extra jump height
                    return;
                }
            }
        }
    }
    
    @SubscribeEvent
    public void onPlayerFall(LivingFallEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            
            // Check if player has Skyfeather Amulet for permanent fall damage reduction
            IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
            boolean hasSkyfeather = false;
            for (int i = 0; i < baublesHandler.getSlots(); i++) {
                if (baublesHandler.getStackInSlot(i).getItem() == ModItems.SKYFEATHER_AMULET) {
                    hasSkyfeather = true;
                    break;
                }
            }
            
            if (hasSkyfeather) {
                // Reduce fall damage by 60%
                event.setDamageMultiplier(0.4F);
            }
            
            // Check if player has the specific Absorption V from our amulet (death protection effect)
            PotionEffect absorption = player.getActivePotionEffect(MobEffects.ABSORPTION);
            if (absorption != null && absorption.getAmplifier() == 4) {
                // Cancel fall damage completely during death protection
                event.setCanceled(true);
            }
        }
    }
    
    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            
            if (player.world.isRemote) {
                return;
            }
            
            // Check if player has Skyfeather Amulet for permanent speed boost
            IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
            boolean hasSkyfeather = false;
            for (int i = 0; i < baublesHandler.getSlots(); i++) {
                if (baublesHandler.getStackInSlot(i).getItem() == ModItems.SKYFEATHER_AMULET) {
                    hasSkyfeather = true;
                    break;
                }
            }
            
            if (hasSkyfeather) {
                // Apply permanent speed boost (20% movement speed increase)
                IAttributeInstance movementSpeed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
                UUID speedModifierUUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
                if (movementSpeed.getModifier(speedModifierUUID) == null) {
                    // Add 20% speed boost using MULTIPLY_TOTAL operation
                    movementSpeed.applyModifier(new AttributeModifier(
                        speedModifierUUID,
                        "skyfeather_speed_boost",
                        0.2D,
                        2
                    ));
                }
            } else {
                // Remove speed boost when amulet is not equipped
                IAttributeInstance movementSpeed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
                UUID speedModifierUUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
                AttributeModifier speedModifier = movementSpeed.getModifier(speedModifierUUID);
                if (speedModifier != null) {
                    movementSpeed.removeModifier(speedModifier);
                }
            }
        }
    }
}
