package com.tsune.ecobaubles.events;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.init.ModItems;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ForgeEventHandler {

    private static final String COOLDOWN_TAG = "skyfeather_cooldown";
    private static final int COOLDOWN_TICKS = 9600; // 8 minutes * 60 seconds * 20 ticks/second
    
    // Wind Attraction Ring - Continuous attraction system
    private static final Map<UUID, AttractionData> activeAttractions = new HashMap<>();
    private static final int ATTRACTION_DURATION = 30; // 1.5 seconds (30 ticks)
    private static final double ATTRACTION_RANGE = 8.0D;
    private static final double ATTRACTION_FORCE = 0.1D; // Reduced force for continuous effect
    
    // Crack Wind Ring - Bow speed and arrow effects
    private static final UUID CRACK_WIND_BOW_SPEED_UUID = UUID.fromString("12345678-1234-1234-1234-123456789012");
    private static final double BOW_SPEED_BONUS = 0.20; // 20% faster bow speed
    private static final double ARROW_SPEED_BONUS = 0.20; // 20% faster arrow speed
    private static final double CHAIN_DAMAGE_RANGE = 3.0D; // 3 block radius for chain damage
    private static final double CHAIN_DAMAGE_MULTIPLIER = 0.20; // 20% of original damage
    
    // Wind Shadow Belt - Sprint buff and wind marks system
    private static final Map<UUID, WindShadowData> windShadowPlayers = new HashMap<>();
    private static final int SPRINT_DURATION_TICKS = 200; // 10 seconds * 20 ticks/second
    private static final int WIND_MARK_INTERVAL = 140; // 7 seconds * 20 ticks/second
    private static final int MAX_WIND_MARKS = 5;
    private static final double WIND_MARK_SPEED_BONUS = 0.03; // 3% per mark
    private static final double WIND_SHIELD_DAMAGE_REDUCTION = 0.15; // 15% per mark
    private static final double DODGE_CHANCE = 0.15; // 15% dodge chance during wind state
    
    // Wind Crown - Wind shield system
    private static final Map<UUID, WindCrownData> windCrownPlayers = new HashMap<>();
    private static final double WIND_SHIELD_PERCENTAGE = 0.50; // 50% of max health
    private static final double DAMAGE_ABSORPTION = 0.40; // 40% damage absorption
    private static final double PROJECTILE_ABSORPTION = 0.70; // 70% projectile damage absorption
    private static final int SHIELD_REGEN_DELAY = 800; // 40 seconds * 20 ticks/second
    private static final double KNOCKBACK_RANGE = 5.0D; // 5 block radius for knockback
    private static final float KNOCKBACK_FORCE = 1.5F; // Knockback force when shield breaks
    
    // Data class to track attraction effects
    private static class AttractionData {
        public final double centerX, centerY, centerZ;
        public final World world;
        public int remainingTicks;
        
        public AttractionData(double x, double y, double z, World world, int duration) {
            this.centerX = x;
            this.centerY = y;
            this.centerZ = z;
            this.world = world;
            this.remainingTicks = duration;
        }
    }
    
    // Data class to track Wind Shadow Belt effects
    private static class WindShadowData {
        public int sprintTicks = 0; // Ticks spent sprinting continuously
        public int windMarks = 0; // Current wind marks (0-5)
        public int lastWindMarkTime = 0; // Last time a wind mark was added
        public boolean inWindState = false; // Currently in wind state
        public int windStateTicks = 0; // Ticks remaining in wind state
        public int windShield = 0; // Current wind shield value
    }
    
    // Data class to track Wind Crown effects
    private static class WindCrownData {
        public float maxWindShield = 0.0F; // Maximum wind shield value (50% of max health)
        public float currentWindShield = 0.0F; // Current wind shield value
        public int lastDamageTime = 0; // Last time player took damage
        public boolean shieldActive = false; // Whether wind shield is currently active
    }

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
        boolean hasWindShadowBelt = false;
        boolean hasWindCrown = false;

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
                } else if (stack.getItem() == ModItems.WIND_SHADOW_BELT) {
                    hasWindShadowBelt = true;
                } else if (stack.getItem() == ModItems.WIND_CROWN) {
                    hasWindCrown = true;
                }
            }
        }
        
        // Handle Wind Crown effects
        if (hasWindCrown) {
            handleWindCrownDamage(event, player, world);
        }
        
        // Handle Wind Shadow Belt effects
        if (hasWindShadowBelt) {
            handleWindShadowBeltDamage(event, player, world);
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

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        
        // Process active attractions
        activeAttractions.entrySet().removeIf(entry -> {
            AttractionData data = entry.getValue();
            data.remainingTicks--;
            
            if (data.remainingTicks <= 0) {
                return true; // Remove expired attraction
            }
            
            // Apply attraction force to nearby hostile mobs
            applyAttractionForce(data);
            return false; // Keep this attraction
        });
        
        // Process Wind Crown shield regeneration
        processWindCrownShieldRegeneration(event.world);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
        World world = player.world;

        if (world.isRemote) {
            return;
        }

        // Check if player has Wind Attraction Ring
        IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
        boolean hasWindAttractionRing = false;
        
        for (int i = 0; i < baublesHandler.getSlots(); i++) {
            ItemStack stack = baublesHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ModItems.WIND_ATTRACTION_RING) {
                hasWindAttractionRing = true;
                break;
            }
        }

        if (hasWindAttractionRing) {
            startWindAttraction(event, player, world);
        }
    }

    private void startWindAttraction(LivingDeathEvent event, EntityPlayer player, World world) {
        EntityLivingBase deadEntity = event.getEntityLiving();
        double deathX = deadEntity.posX;
        double deathY = deadEntity.posY;
        double deathZ = deadEntity.posZ;
        
        // Create unique ID for this attraction effect
        UUID attractionId = UUID.randomUUID();
        
        // Start the attraction effect
        activeAttractions.put(attractionId, new AttractionData(deathX, deathY, deathZ, world, ATTRACTION_DURATION));
        
        // Play sound effect
        world.playSound(null, deathX, deathY, deathZ, SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.PLAYERS, 0.5F, 1.2F);
        
        // Spawn particle effects (if on client side, this will be handled by the client)
        if (world.isRemote) {
            // Particle effects would be spawned here on client side
        }
    }
    
    private void applyAttractionForce(AttractionData data) {
        World world = data.world;
        double centerX = data.centerX;
        double centerY = data.centerY;
        double centerZ = data.centerZ;
        
        // Find nearby hostile mobs
        List<EntityLivingBase> nearbyEntities = world.getEntitiesWithinAABB(
            EntityLivingBase.class, 
            new net.minecraft.util.math.AxisAlignedBB(
                centerX - ATTRACTION_RANGE, centerY - ATTRACTION_RANGE, centerZ - ATTRACTION_RANGE,
                centerX + ATTRACTION_RANGE, centerY + ATTRACTION_RANGE, centerZ + ATTRACTION_RANGE
            )
        );
        
        for (EntityLivingBase entity : nearbyEntities) {
            // Only attract hostile mobs (not passive animals or villagers)
            if (entity.isCreatureType(EnumCreatureType.MONSTER, false)) {
                // Calculate direction from entity to attraction center
                double deltaX = centerX - entity.posX;
                double deltaY = centerY - entity.posY;
                double deltaZ = centerZ - entity.posZ;
                double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                
                if (distance > 0.1D && distance <= ATTRACTION_RANGE) {
                    // Normalize direction and apply continuous attraction force
                    double normalizedX = deltaX / distance;
                    double normalizedY = deltaY / distance;
                    double normalizedZ = deltaZ / distance;
                    
                    // Apply force (reduced for continuous effect)
                    entity.motionX += normalizedX * ATTRACTION_FORCE;
                    entity.motionY += normalizedY * ATTRACTION_FORCE * 0.3D; // Much reduced vertical force
                    entity.motionZ += normalizedZ * ATTRACTION_FORCE;
                    entity.velocityChanged = true;
                }
            }
        }
    }
    
    // ===== CRACK WIND RING FUNCTIONALITY =====
    
    /**
     * Apply bow speed bonus when player has Crack Wind Ring equipped
     */
    @SubscribeEvent
    public void onPlayerUpdate(LivingUpdateEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            applyCrackWindRingEffects(player);
        }
    }
    
    /**
     * Handle arrow speed boost when arrow is spawned
     */
    @SubscribeEvent
    public void onArrowSpawn(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof EntityArrow) {
            EntityArrow arrow = (EntityArrow) event.getEntity();
            if (arrow.shootingEntity instanceof EntityPlayer) {
                EntityPlayer shooter = (EntityPlayer) arrow.shootingEntity;
                if (hasCrackWindRing(shooter)) {
                    // Boost arrow speed by 20%
                    arrow.motionX *= (1.0 + ARROW_SPEED_BONUS);
                    arrow.motionY *= (1.0 + ARROW_SPEED_BONUS);
                    arrow.motionZ *= (1.0 + ARROW_SPEED_BONUS);
                }
            }
        }
    }
    
    /**
     * Handle arrow speed and chain damage when arrow hits target
     */
    @SubscribeEvent
    public void onArrowHit(LivingHurtEvent event) {
        if (event.getSource().getImmediateSource() instanceof EntityArrow) {
            EntityArrow arrow = (EntityArrow) event.getSource().getImmediateSource();
            if (arrow.shootingEntity instanceof EntityPlayer) {
                EntityPlayer shooter = (EntityPlayer) arrow.shootingEntity;
                if (hasCrackWindRing(shooter)) {
                    // Apply chain damage
                    applyChainDamage(event, shooter, arrow);
                }
            }
        }
    }
    
    private void applyCrackWindRingEffects(EntityPlayer player) {
        if (hasCrackWindRing(player)) {
            // Apply bow speed bonus
            IAttributeInstance bowSpeedAttribute = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
            if (bowSpeedAttribute != null) {
                AttributeModifier modifier = bowSpeedAttribute.getModifier(CRACK_WIND_BOW_SPEED_UUID);
                if (modifier == null) {
                    modifier = new AttributeModifier(CRACK_WIND_BOW_SPEED_UUID, "Crack Wind Bow Speed", BOW_SPEED_BONUS, 1);
                    bowSpeedAttribute.applyModifier(modifier);
                }
            }
        } else {
            // Remove bow speed bonus if ring not equipped
            IAttributeInstance bowSpeedAttribute = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
            if (bowSpeedAttribute != null) {
                AttributeModifier modifier = bowSpeedAttribute.getModifier(CRACK_WIND_BOW_SPEED_UUID);
                if (modifier != null) {
                    bowSpeedAttribute.removeModifier(modifier);
                }
            }
        }
    }
    
    private boolean hasCrackWindRing(EntityPlayer player) {
        IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < baublesHandler.getSlots(); i++) {
            ItemStack stack = baublesHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ModItems.CRACK_WIND_RING) {
                return true;
            }
        }
        return false;
    }
    
    private void applyChainDamage(LivingHurtEvent event, EntityPlayer shooter, EntityArrow arrow) {
        EntityLivingBase hitEntity = event.getEntityLiving();
        float originalDamage = event.getAmount();
        float chainDamage = originalDamage * (float) CHAIN_DAMAGE_MULTIPLIER;
        
        if (chainDamage > 0) {
            // Find entities behind the hit target
            Vec3d arrowDirection = arrow.getLook(1.0F);
            Vec3d hitPos = new Vec3d(hitEntity.posX, hitEntity.posY + hitEntity.height / 2, hitEntity.posZ);
            
            // Get entities in range behind the target
            AxisAlignedBB searchBox = new AxisAlignedBB(
                hitPos.x - CHAIN_DAMAGE_RANGE, hitPos.y - CHAIN_DAMAGE_RANGE, hitPos.z - CHAIN_DAMAGE_RANGE,
                hitPos.x + CHAIN_DAMAGE_RANGE, hitPos.y + CHAIN_DAMAGE_RANGE, hitPos.z + CHAIN_DAMAGE_RANGE
            );
            
            List<EntityLivingBase> nearbyEntities = hitEntity.world.getEntitiesWithinAABB(
                EntityLivingBase.class, searchBox
            );
            
            for (EntityLivingBase entity : nearbyEntities) {
                if (entity != hitEntity && entity != shooter) {
                    // Check if entity is behind the hit target (in the direction of arrow)
                    Vec3d entityPos = new Vec3d(entity.posX, entity.posY + entity.height / 2, entity.posZ);
                    Vec3d toEntity = entityPos.subtract(hitPos).normalize();
                    
                    // If dot product is positive, entity is in the same direction as arrow
                    if (arrowDirection.dotProduct(toEntity) > 0) {
                        // Apply chain damage
                        entity.attackEntityFrom(DamageSource.causePlayerDamage(shooter), chainDamage);
                        
                        // Play hit sound
                        entity.world.playSound(null, entity.posX, entity.posY, entity.posZ, 
                            SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.5F, 1.2F);
                    }
                }
            }
        }
    }
    
    // ===== WIND SHADOW BELT FUNCTIONALITY =====
    
    /**
     * Handle Wind Shadow Belt damage reduction and dodge
     */
    private void handleWindShadowBeltDamage(LivingHurtEvent event, EntityPlayer player, World world) {
        UUID playerId = player.getUniqueID();
        WindShadowData data = windShadowPlayers.get(playerId);
        
        if (data == null) {
            data = new WindShadowData();
            windShadowPlayers.put(playerId, data);
        }
        
        // Check for dodge during wind state
        if (data.inWindState && world.rand.nextDouble() < DODGE_CHANCE) {
            event.setCanceled(true);
            world.playSound(null, player.posX, player.posY, player.posZ, 
                SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.PLAYERS, 0.5F, 1.5F);
            return;
        }
        
        // Convert wind marks to shield and apply damage reduction
        if (data.windMarks > 0) {
            data.windShield = data.windMarks; // Convert all wind marks to shield
            float damageReduction = (float) (data.windShield * WIND_SHIELD_DAMAGE_REDUCTION);
            float newDamage = Math.max(0, event.getAmount() - damageReduction);
            event.setAmount(newDamage);
            
            // Consume wind shield and reset wind marks
            data.windShield = 0;
            data.windMarks = 0; // Reset wind marks after consumption
            world.playSound(null, player.posX, player.posY, player.posZ, 
                SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.PLAYERS, 0.3F, 1.0F);
        }
    }
    
    /**
     * Update Wind Shadow Belt effects on player update
     */
    @SubscribeEvent
    public void onPlayerUpdateWindShadow(LivingUpdateEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            updateWindShadowBeltEffects(player);
        }
    }
    
    private void updateWindShadowBeltEffects(EntityPlayer player) {
        if (player.world.isRemote) {
            return;
        }
        
        UUID playerId = player.getUniqueID();
        WindShadowData data = windShadowPlayers.get(playerId);
        
        if (data == null) {
            data = new WindShadowData();
            windShadowPlayers.put(playerId, data);
        }
        
        // Check if player has Wind Shadow Belt
        boolean hasBelt = hasWindShadowBelt(player);
        
        if (!hasBelt) {
            // Remove effects if belt not equipped
            if (data.inWindState) {
                data.inWindState = false;
                data.windStateTicks = 0;
            }
            data.sprintTicks = 0;
            data.windMarks = 0;
            data.windShield = 0;
            return;
        }
        
        // Update sprint tracking
        if (player.isSprinting()) {
            data.sprintTicks++;
            
            // Check if sprinted for 10 seconds
            if (data.sprintTicks >= SPRINT_DURATION_TICKS && !data.inWindState) {
                activateWindState(player, data);
            }
        } else {
            data.sprintTicks = 0;
        }
        
        // Update wind state duration
        if (data.inWindState) {
            data.windStateTicks--;
            if (data.windStateTicks <= 0) {
                data.inWindState = false;
                data.windStateTicks = 0;
                
                // Remove 30% speed bonus when wind state ends
                IAttributeInstance movementSpeed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
                UUID windStateSpeedUUID = UUID.fromString("87654321-4321-4321-4321-210987654322");
                AttributeModifier existingModifier = movementSpeed.getModifier(windStateSpeedUUID);
                if (existingModifier != null) {
                    movementSpeed.removeModifier(existingModifier);
                }
            }
        }
        
        // Update wind marks
        int currentTime = (int) player.world.getTotalWorldTime();
        if (currentTime - data.lastWindMarkTime >= WIND_MARK_INTERVAL) {
            if (data.windMarks < MAX_WIND_MARKS) {
                data.windMarks++;
                data.lastWindMarkTime = currentTime;
                
                // Play sound for wind mark
                player.world.playSound(null, player.posX, player.posY, player.posZ, 
                    SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.PLAYERS, 0.2F, 1.0F);
            }
        }
        
        // Apply wind mark speed bonus
        applyWindMarkSpeedBonus(player, data);
        
        // Note: Wind marks are converted to shield only when taking damage
        // This conversion happens in handleWindShadowBeltDamage method
    }
    
    private void activateWindState(EntityPlayer player, WindShadowData data) {
        data.inWindState = true;
        data.windStateTicks = 200; // 10 seconds
        
        // Apply wind state effects - 30% movement speed increase
        IAttributeInstance movementSpeed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        UUID windStateSpeedUUID = UUID.fromString("87654321-4321-4321-4321-210987654322");
        
        // Remove existing modifier
        AttributeModifier existingModifier = movementSpeed.getModifier(windStateSpeedUUID);
        if (existingModifier != null) {
            movementSpeed.removeModifier(existingModifier);
        }
        
        // Apply 30% speed bonus
        movementSpeed.applyModifier(new AttributeModifier(
            windStateSpeedUUID,
            "wind_state_speed_bonus",
            0.30, // 30% movement speed increase
            2 // MULTIPLY_TOTAL
        ));
        
        player.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 200, 0)); // Jump Boost I for 10 seconds
        
        // Play activation sound
        player.world.playSound(null, player.posX, player.posY, player.posZ, 
            SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }
    
    private void applyWindMarkSpeedBonus(EntityPlayer player, WindShadowData data) {
        if (data.windMarks > 0) {
            IAttributeInstance movementSpeed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            UUID windMarkSpeedUUID = UUID.fromString("87654321-4321-4321-4321-210987654321");
            
            // Remove existing modifier
            AttributeModifier existingModifier = movementSpeed.getModifier(windMarkSpeedUUID);
            if (existingModifier != null) {
                movementSpeed.removeModifier(existingModifier);
            }
            
            // Apply new modifier based on wind marks
            double speedBonus = data.windMarks * WIND_MARK_SPEED_BONUS;
            movementSpeed.applyModifier(new AttributeModifier(
                windMarkSpeedUUID,
                "wind_mark_speed_bonus",
                speedBonus,
                2 // MULTIPLY_TOTAL
            ));
        } else {
            // Remove modifier if no wind marks
            IAttributeInstance movementSpeed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            UUID windMarkSpeedUUID = UUID.fromString("87654321-4321-4321-4321-210987654321");
            AttributeModifier existingModifier = movementSpeed.getModifier(windMarkSpeedUUID);
            if (existingModifier != null) {
                movementSpeed.removeModifier(existingModifier);
            }
        }
    }
    
    private boolean hasWindShadowBelt(EntityPlayer player) {
        IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < baublesHandler.getSlots(); i++) {
            ItemStack stack = baublesHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ModItems.WIND_SHADOW_BELT) {
                return true;
            }
        }
        return false;
    }
    
    // Wind Crown damage handling
    private void handleWindCrownDamage(LivingHurtEvent event, EntityPlayer player, World world) {
        UUID playerUUID = player.getUniqueID();
        WindCrownData data = windCrownPlayers.get(playerUUID);
        
        if (data == null) {
            data = new WindCrownData();
            windCrownPlayers.put(playerUUID, data);
        }
        
        // Initialize wind shield if not active and not on cooldown
        if (!data.shieldActive) {
            long currentTime = world.getTotalWorldTime();
            if (currentTime - data.lastDamageTime >= SHIELD_REGEN_DELAY) {
                data.maxWindShield = player.getMaxHealth() * (float)WIND_SHIELD_PERCENTAGE;
                data.currentWindShield = data.maxWindShield;
                data.shieldActive = true;
            }
        }
        
        // Calculate damage absorption based on damage source
        double absorptionRate = DAMAGE_ABSORPTION;
        if (isProjectileDamage(event.getSource())) {
            absorptionRate = PROJECTILE_ABSORPTION;
        }
        
        float absorbedDamage = (float)(event.getAmount() * absorptionRate);
        float remainingDamage = event.getAmount() - absorbedDamage;
        
        // Apply damage to wind shield first
        if (data.currentWindShield > 0) {
            if (absorbedDamage >= data.currentWindShield) {
                // Wind shield breaks
                float excessDamage = absorbedDamage - data.currentWindShield;
                data.currentWindShield = 0;
                data.shieldActive = false;
                
                // Set last damage time when shield breaks to start cooldown
                data.lastDamageTime = (int)world.getTotalWorldTime();
                
                // Apply remaining damage to player
                event.setAmount(remainingDamage + excessDamage);
                
                // Trigger knockback effect
                triggerWindShieldBreak(player, world);
            } else {
                // Wind shield absorbs damage
                data.currentWindShield -= absorbedDamage;
                event.setAmount(remainingDamage);
                
                // Update last damage time
                data.lastDamageTime = (int)world.getTotalWorldTime();
            }
        } else {
            // No shield active, just update last damage time
            data.lastDamageTime = (int)world.getTotalWorldTime();
        }
    }
    
    private boolean isProjectileDamage(DamageSource source) {
        return source.isProjectile() || source.getImmediateSource() instanceof EntityArrow;
    }
    
    private void triggerWindShieldBreak(EntityPlayer player, World world) {
        // Knockback nearby entities
        List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, 
            player.getEntityBoundingBox().grow(KNOCKBACK_RANGE));
        
        for (EntityLivingBase entity : entities) {
            if (entity != player) {
                entity.knockBack(player, KNOCKBACK_FORCE, 
                    (float)(player.posX - entity.posX), (float)(player.posZ - entity.posZ));
            }
        }
        
        // Play sound effect
        world.playSound(null, player.posX, player.posY, player.posZ, 
            SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.PLAYERS, 1.2F, 0.8F);
    }
    
    private void processWindCrownShieldRegeneration(World world) {
        long currentTime = world.getTotalWorldTime();
        
        // Process all players with wind crown data
        windCrownPlayers.entrySet().removeIf(entry -> {
            UUID playerUUID = entry.getKey();
            WindCrownData data = entry.getValue();
            
            // Find the player in the world
            EntityPlayer player = world.getPlayerEntityByUUID(playerUUID);
            if (player == null) {
                return true; // Remove data for players not in this world
            }
            
            // Check if player has wind crown - if not, keep data but don't process regeneration
            if (!hasWindCrown(player)) {
                return false; // Keep data for cooldown tracking, but don't process regeneration
            }
            
            // Check if shield needs regeneration
            if (!data.shieldActive && currentTime - data.lastDamageTime >= SHIELD_REGEN_DELAY) {
                // Regenerate wind shield
                data.maxWindShield = player.getMaxHealth() * (float)WIND_SHIELD_PERCENTAGE;
                data.currentWindShield = data.maxWindShield;
                data.shieldActive = true;
                
                // Play regeneration sound
                world.playSound(null, player.posX, player.posY, player.posZ, 
                    SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.8F, 1.2F);
            }
            
            return false; // Keep this data
        });
    }
    
    private boolean hasWindCrown(EntityPlayer player) {
        IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < baublesHandler.getSlots(); i++) {
            ItemStack stack = baublesHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ModItems.WIND_CROWN) {
                return true;
            }
        }
        return false;
    }
}
