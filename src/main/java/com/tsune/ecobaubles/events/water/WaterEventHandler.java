package com.tsune.ecobaubles.events.water;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.init.ModItems;
import com.tsune.ecobaubles.items.ItemRingSpringShield;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;

public class WaterEventHandler {

    // ── UUIDs for attribute modifiers ─────────────────────────────────────────
    private static final UUID SEA_GOD_SPEED_UUID = UUID.fromString("a0b1c2d3-0001-4000-8000-000000000001");
    private static final UUID TIDE_SPEED_UUID    = UUID.fromString("a0b1c2d3-0002-4000-8000-000000000002");
    private static final UUID ABYSS_DAMAGE_UUID  = UUID.fromString("a0b1c2d3-0003-4000-8000-000000000003");
    private static final UUID ABYSS_HEALTH_UUID  = UUID.fromString("a0b1c2d3-0004-4000-8000-000000000004");

    // ── SeaGod amulet ─────────────────────────────────────────────────────────
    private static final Map<UUID, Long> seaGodHealCD = new HashMap<>();

    // ── TideSurge amulet ──────────────────────────────────────────────────────
    private static final Map<UUID, Long> lastCombatTick = new HashMap<>();
    private static final Set<UUID> hasTideShield = new HashSet<>();

    // ── WaterRobe (水纹长袍) counter-attack ───────────────────────────────────
    private static final Map<UUID, Long> waterRobeCounterCD = new HashMap<>();

    // ── TorrentRing (激流勇进) ─────────────────────────────────────────────────
    private static final Map<UUID, Long> waterLockMap = new HashMap<>();
    private static final Map<UUID, Long> waterDebuffMap = new HashMap<>();
    private static final Map<UUID, Long> waterDrownTick = new HashMap<>();
    // locked position: entity UUID → [x, y, z]
    private static final Map<UUID, double[]> lockedPositions = new HashMap<>();

    // ── SpringShieldRing (泉御之戒) ───────────────────────────────────────────
    private static final Map<UUID, Long>  springShieldExpiry     = new HashMap<>(); // when immunity ends
    private static final Map<UUID, Float> springShieldStored     = new HashMap<>(); // damage absorbed
    private static final Map<UUID, Long>  springRepayStart       = new HashMap<>(); // when repayment started
    private static final Map<UUID, Float> springRepayTotal       = new HashMap<>(); // total to repay
    private static final Map<UUID, Long>  springRepayLastTick    = new HashMap<>(); // last repay tick

    public static void activateSpringShield(EntityPlayer player, long expiryTick) {
        UUID pid = player.getUniqueID();
        springShieldExpiry.put(pid, expiryTick);
        springShieldStored.put(pid, 0.0f);
        springRepayStart.remove(pid);
        springRepayTotal.remove(pid);
        springRepayLastTick.remove(pid);
    }

    // ── WaveRing (波光指环) ────────────────────────────────────────────────────
    private static final Map<UUID, Long> waveRingLastProc = new HashMap<>();

    // ── TidalBelt (潮汐腰带) ──────────────────────────────────────────────────
    private static boolean tidalBeltProcessing = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    public static boolean hasWaterSpirit(EntityPlayer player) {
        return hasBauble(player, ModItems.WATER_SPIRIT);
    }

    private static boolean hasBauble(EntityPlayer player, Item item) {
        IBaublesItemHandler h = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == item) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EntityWaterOrb helpers (called from EntityWaterOrb)
    // ─────────────────────────────────────────────────────────────────────────
    public static void applyWaterLock(World world, EntityLivingBase primary, double aoeRadius, boolean ws) {
        applyLockToEntity(world, primary, ws);
        List<EntityLivingBase> nearby = world.getEntitiesWithinAABB(EntityLivingBase.class,
                primary.getEntityBoundingBox().grow(aoeRadius));
        for (EntityLivingBase e : nearby) {
            if (e != primary && !(e instanceof EntityPlayer)) {
                applyLockToEntity(world, e, ws);
            }
        }
    }

    public static void applyWaterLockAtPos(World world, Vec3d pos, double radius, boolean ws) {
        AxisAlignedBB box = new AxisAlignedBB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        List<EntityLivingBase> nearby = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        for (EntityLivingBase e : nearby) {
            if (!(e instanceof EntityPlayer)) {
                applyLockToEntity(world, e, ws);
            }
        }
    }

    private static void applyLockToEntity(World world, EntityLivingBase entity, boolean ws) {
        long duration = ws ? 140L : 100L; // 水灵: 7s, 普通: 5s
        long expiry = world.getTotalWorldTime() + duration;
        waterLockMap.put(entity.getUniqueID(), expiry);
        waterDrownTick.put(entity.getUniqueID(), world.getTotalWorldTime());
        lockedPositions.put(entity.getUniqueID(), new double[]{entity.posX, entity.posY, entity.posZ});
        // Disable mob AI so pathfinding cannot override immobility
        if (entity instanceof EntityLiving) {
            ((EntityLiving) entity).setNoAI(true);
        }
        entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, (int) duration + 20, 127, false, false));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LivingHurtEvent
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurt(LivingHurtEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        DamageSource src = event.getSource();

        // ── SpringShieldRing: absorb all damage during immunity window ────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            if (hasBauble(vp, ModItems.SPRING_SHIELD_RING)) {
                UUID vid = vp.getUniqueID();
                Long shieldExpiry = springShieldExpiry.get(vid);
                long nowHurt = vp.world.getTotalWorldTime();
                if (shieldExpiry != null && nowHurt <= shieldExpiry) {
                    float stored = springShieldStored.getOrDefault(vid, 0.0f);
                    springShieldStored.put(vid, stored + event.getAmount());
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // ── SeaGod: 20% damage reduction ─────────────────────────────────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            if (hasBauble(vp, ModItems.SEA_GOD_AMULET)) {
                event.setAmount(event.getAmount() * 0.80f);
            }
        }

        // ── TideSurge: in water damage -20% + track combat ───────────────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            if (hasBauble(vp, ModItems.TIDE_SURGE_AMULET)) {
                if (vp.isInWater()) {
                    event.setAmount(event.getAmount() * 0.80f);
                }
                lastCombatTick.put(vp.getUniqueID(), vp.world.getTotalWorldTime());
                hasTideShield.remove(vp.getUniqueID());
            }
        }

        // ── WaterRobe: projectile -35% (-50% with 水灵); melee counter ────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            if (hasBauble(vp, ModItems.WATER_ROBE)) {
                boolean wsVp = hasWaterSpirit(vp);
                if (src.isProjectile()) {
                    event.setAmount(event.getAmount() * (wsVp ? 0.50f : 0.65f));
                } else if (!src.isMagicDamage() && !src.isFireDamage() && !src.isExplosion()
                        && src.getTrueSource() instanceof EntityLivingBase) {
                    // Melee counter: freeze attacker for 1s
                    EntityLivingBase attacker = (EntityLivingBase) src.getTrueSource();
                    long now = vp.world.getTotalWorldTime();
                    Long lastCounter = waterRobeCounterCD.get(vp.getUniqueID());
                    if (lastCounter == null || now - lastCounter >= 40L) {
                        waterRobeCounterCD.put(vp.getUniqueID(), now);
                        attacker.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 20, 127, false, false));
                        if (!(attacker instanceof EntityPlayer)) {
                            waterLockMap.put(attacker.getUniqueID(), now + 20L);
                            lockedPositions.put(attacker.getUniqueID(), new double[]{attacker.posX, attacker.posY, attacker.posZ});
                            if (attacker instanceof EntityLiving) {
                                ((EntityLiving) attacker).setNoAI(true);
                            }
                        }
                    }
                }
            }
        }

        // ── HealingRing: on-hit heal self + nearby 3 blocks for 5% maxHP ──────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            if (hasBauble(vp, ModItems.HEALING_RING)) {
                boolean wsVp = hasWaterSpirit(vp);
                float healPct = wsVp ? 0.08f : 0.05f;
                double healRange = wsVp ? 4.0 : 3.0;
                float healAmt = vp.getMaxHealth() * healPct;
                vp.heal(healAmt);
                List<EntityPlayer> allies = vp.world.getEntitiesWithinAABB(
                        EntityPlayer.class, vp.getEntityBoundingBox().grow(healRange));
                for (EntityPlayer ally : allies) {
                    if (ally != vp) ally.heal(ally.getMaxHealth() * healPct);
                }
            }
        }

        // ── Torrent post-debuff: entity attacks deal -20% damage ─────────────
        Entity trueSource = src.getTrueSource();
        if (trueSource instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) trueSource;
            Long debuffExpiry = waterDebuffMap.get(attacker.getUniqueID());
            if (debuffExpiry != null && victim.world.getTotalWorldTime() < debuffExpiry) {
                event.setAmount(event.getAmount() * 0.80f);
            }
        }

        // ── WaveRing: player attacks → slowness on target + heal nearby ───────
        if (trueSource instanceof EntityPlayer && victim != trueSource) {
            EntityPlayer attacker = (EntityPlayer) trueSource;
            if (hasBauble(attacker, ModItems.WAVE_RING)) {
                boolean wsA = hasWaterSpirit(attacker);
                long now = attacker.world.getTotalWorldTime();
                Long last = waveRingLastProc.get(attacker.getUniqueID());
                long waveCD = wsA ? 60L : 80L; // 水灵: 3s, 普通: 4s
                if (last == null || now - last >= waveCD) {
                    waveRingLastProc.put(attacker.getUniqueID(), now);
                    victim.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 0, false, true));
                    float healAmt = wsA ? 2.0f : 1.0f; // 水灵: 2点
                    List<EntityPlayer> allies = attacker.world.getEntitiesWithinAABB(
                            EntityPlayer.class, attacker.getEntityBoundingBox().grow(3.0));
                    for (EntityPlayer ally : allies) {
                        ally.heal(healAmt);
                    }
                }
            }
        }

        // ── AbyssHelmet: depth damage bonus (outgoing) ────────────────────────
        if (trueSource instanceof EntityPlayer) {
            EntityPlayer attacker = (EntityPlayer) trueSource;
            if (hasBauble(attacker, ModItems.ABYSS_HELMET) && attacker.isInWater()) {
                int depth = getWaterDepth(attacker);
                float bonus = (depth / 4) * 0.08f;
                if (bonus > 0) {
                    event.setAmount(event.getAmount() * (1.0f + bonus));
                }
            }
        }

    }

    // ─────────────────────────────────────────────────────────────────────────
    // LivingUpdateEvent (per-tick passives)
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            handleNonPlayerUpdate(event.getEntityLiving());
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;
        long now = player.world.getTotalWorldTime();
        UUID pid = player.getUniqueID();

        // ── SpringShieldRing: transition to repayment, then tick damage ────────
        if (hasBauble(player, ModItems.SPRING_SHIELD_RING)) {
            Long shieldExpiry = springShieldExpiry.get(pid);
            // Shield just expired → start repayment
            if (shieldExpiry != null && now > shieldExpiry && !springRepayStart.containsKey(pid)) {
                float total = springShieldStored.getOrDefault(pid, 0.0f);
                if (total > 0.0f) {
                    springRepayStart.put(pid, now);
                    springRepayTotal.put(pid, total);
                    springRepayLastTick.put(pid, now);
                }
                springShieldExpiry.remove(pid);
                springShieldStored.remove(pid);
            }
            // Repayment: deal stored/15 every second (20 ticks)
            Long repayStart = springRepayStart.get(pid);
            if (repayStart != null) {
                long elapsed = now - repayStart;
                int repayDuration = ItemRingSpringShield.REPAY_SECONDS * 20;
                if (elapsed >= repayDuration) {
                    springRepayStart.remove(pid);
                    springRepayTotal.remove(pid);
                    springRepayLastTick.remove(pid);
                } else {
                    Long lastTick = springRepayLastTick.get(pid);
                    if (lastTick != null && now - lastTick >= 20) {
                        float perSecond = springRepayTotal.getOrDefault(pid, 0.0f) / ItemRingSpringShield.REPAY_SECONDS;
                        player.attackEntityFrom(net.minecraft.util.DamageSource.MAGIC, perSecond);
                        springRepayLastTick.put(pid, now);
                    }
                }
            }
        } else {
            // Bauble removed mid-shield or mid-repayment: clear state
            springShieldExpiry.remove(pid);
            springShieldStored.remove(pid);
            springRepayStart.remove(pid);
            springRepayTotal.remove(pid);
            springRepayLastTick.remove(pid);
        }

        boolean hasSeaGod    = hasBauble(player, ModItems.SEA_GOD_AMULET);
        boolean hasTideSurge = hasBauble(player, ModItems.TIDE_SURGE_AMULET);
        boolean hasWaterRobe = hasBauble(player, ModItems.WATER_ROBE);
        boolean hasAbyss     = hasBauble(player, ModItems.ABYSS_HELMET);
        boolean hasPendant   = hasBauble(player, ModItems.WATER_HEART_PENDANT);
        boolean ws           = hasWaterSpirit(player); // 水灵增强

        // ── SeaGod ────────────────────────────────────────────────────────────
        if (hasSeaGod) {
            int regenAmp = player.isInWater() ? 1 : 0;
            PotionEffect currentRegen = player.getActivePotionEffect(MobEffects.REGENERATION);
            if (currentRegen == null || currentRegen.getAmplifier() < regenAmp || currentRegen.getDuration() < 40) {
                player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 60, regenAmp, false, false));
            }
            applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED, SEA_GOD_SPEED_UUID,
                    "seaGodSpeed", 0.20, 2, player.isInWater());

            Long lastHeal = seaGodHealCD.get(pid);
            if (lastHeal == null || now - lastHeal >= 6000L) {
                float triggerThreshold = ws ? 0.65f : 0.50f; // 水灵: 65%
                int absorptionDur = ws ? 400 : 300;           // 水灵: 20s
                boolean shouldTrigger = player.getHealth() < player.getMaxHealth() * triggerThreshold;
                if (!shouldTrigger) {
                    List<EntityPlayer> nearby = player.world.getEntitiesWithinAABB(
                            EntityPlayer.class, player.getEntityBoundingBox().grow(8.0));
                    for (EntityPlayer p : nearby) {
                        if (p != player && p.getHealth() < p.getMaxHealth() * triggerThreshold) {
                            shouldTrigger = true;
                            break;
                        }
                    }
                }
                if (shouldTrigger) {
                    seaGodHealCD.put(pid, now);
                    player.setHealth(player.getMaxHealth());
                    player.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, absorptionDur, 2, false, true));
                    List<EntityPlayer> nearby = player.world.getEntitiesWithinAABB(
                            EntityPlayer.class, player.getEntityBoundingBox().grow(8.0));
                    for (EntityPlayer p : nearby) {
                        if (p != player) {
                            p.setHealth(p.getMaxHealth());
                            p.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, absorptionDur, 2, false, true));
                        }
                    }
                }
            }
        } else {
            applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED, SEA_GOD_SPEED_UUID,
                    "seaGodSpeed", 0.20, 2, false);
        }

        // ── TideSurge ─────────────────────────────────────────────────────────
        if (hasTideSurge) {
            applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED, TIDE_SPEED_UUID,
                    "tideSurgeSpeed", 0.20, 2, player.isInWater());

            Long lastCombat = lastCombatTick.get(pid);
            long combatTimeout = ws ? 200L : 300L; // 水灵: 10s, 普通: 15s
            boolean outOfCombat = (lastCombat == null || now - lastCombat >= combatTimeout);
            if (outOfCombat && !hasTideShield.contains(pid)) {
                float shield = player.getMaxHealth() * (ws ? 0.30f : 0.20f); // 水灵: 30%
                if (player.getAbsorptionAmount() < shield) {
                    player.setAbsorptionAmount(shield);
                }
                hasTideShield.add(pid);
            } else if (!outOfCombat) {
                hasTideShield.remove(pid);
            }
        } else {
            applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED, TIDE_SPEED_UUID,
                    "tideSurgeSpeed", 0.20, 2, false);
            hasTideShield.remove(pid);
        }

        // ── AbyssHelmet ───────────────────────────────────────────────────────
        if (hasAbyss) {
            player.addPotionEffect(new PotionEffect(MobEffects.WATER_BREATHING, 40, 0, false, false));
            if (player.isInWater()) {
                player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 400, 0, false, false));
            }
            if (player.world.isRaining() && !player.isInWater()) {
                player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 60, 1, false, false));
            }
            // +20% max health (always active)
            applyOrRemoveModifier(player, SharedMonsterAttributes.MAX_HEALTH, ABYSS_HEALTH_UUID,
                    "abyssHealth", 0.20, 2, true);
            if (player.isInWater()) {
                int depth = getWaterDepth(player);
                double bonusPerLevel = ws ? 0.12 : 0.08; // 水灵: 每4格+12%
                double bonus = (depth / 4) * bonusPerLevel;
                applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_DAMAGE, ABYSS_DAMAGE_UUID,
                        "abyssDepth", bonus, 1, bonus > 0);
            } else {
                applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_DAMAGE, ABYSS_DAMAGE_UUID,
                        "abyssDepth", 0.0, 1, false);
            }
        } else {
            applyOrRemoveModifier(player, SharedMonsterAttributes.MAX_HEALTH, ABYSS_HEALTH_UUID,
                    "abyssHealth", 0.20, 2, false);
            applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_DAMAGE, ABYSS_DAMAGE_UUID,
                    "abyssDepth", 0.0, 1, false);
        }

        // ── WaterHeartPendant: regen overflow → absorption (vanilla regen skips ────
        //    heal() when at full HP, so we intercept here instead)
        if (hasPendant && player.getHealth() >= player.getMaxHealth()) {
            net.minecraft.potion.PotionEffect regenEffect = player.getActivePotionEffect(MobEffects.REGENERATION);
            if (regenEffect != null) {
                int interval = Math.max(1, 50 >> regenEffect.getAmplifier());
                if (regenEffect.getDuration() % interval == 0) {
                    float cap = player.getMaxHealth() * (ws ? 0.40f : 0.30f);
                    float current = player.getAbsorptionAmount();
                    if (current < cap) {
                        player.setAbsorptionAmount(Math.min(current + 1.0f, cap));
                    }
                }
            }
        }

        // ── WaterRobe: share regen when HP > 90% ─────────────────────────────
        float robeThreshold = ws ? 0.80f : 0.90f; // 水灵: 80%
        if (hasWaterRobe && player.getHealth() >= player.getMaxHealth() * robeThreshold) {
            List<EntityPlayer> nearby = player.world.getEntitiesWithinAABB(
                    EntityPlayer.class, player.getEntityBoundingBox().grow(5.0));
            for (EntityPlayer ally : nearby) {
                if (ally != player) {
                    ally.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 40, 0, false, false));
                }
            }
        }
    }

    private void handleNonPlayerUpdate(EntityLivingBase entity) {
        if (entity.world.isRemote) return;
        long now = entity.world.getTotalWorldTime();
        UUID eid = entity.getUniqueID();

        Long lockExpiry = waterLockMap.get(eid);
        if (lockExpiry != null) {
            if (now < lockExpiry) {
                // Teleport back to locked position each tick (in case of knockback etc.)
                double[] pos = lockedPositions.get(eid);
                if (pos != null) {
                    entity.setPosition(pos[0], pos[1], pos[2]);
                    entity.motionX = 0; entity.motionY = 0; entity.motionZ = 0;
                }
                // Drain damage every second
                Long lastDrown = waterDrownTick.get(eid);
                if (lastDrown == null || now - lastDrown >= 20L) {
                    waterDrownTick.put(eid, now);
                    entity.attackEntityFrom(DamageSource.DROWN, 1.0f);
                }
            } else {
                // Lock expired: re-enable AI, apply post-debuff
                waterLockMap.remove(eid);
                waterDrownTick.remove(eid);
                lockedPositions.remove(eid);
                if (entity instanceof EntityLiving) {
                    ((EntityLiving) entity).setNoAI(false);
                }
                waterDebuffMap.put(eid, now + 80L);
                entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 0, false, true));
            }
        }

        Long debuffExpiry = waterDebuffMap.get(eid);
        if (debuffExpiry != null && now >= debuffExpiry) {
            waterDebuffMap.remove(eid);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PotionEvent (潮汐腰带)
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onPotionAdded(PotionEvent.PotionAddedEvent event) {
        if (tidalBeltProcessing) return;
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (!hasBauble(player, ModItems.TIDAL_BELT)) return;

        PotionEffect added = event.getPotionEffect();
        boolean isBad = added.getPotion().isBadEffect();
        boolean ws = hasWaterSpirit(player);

        tidalBeltProcessing = true;
        try {
            if (isBad) {
                player.heal(ws ? 6.0f : 4.0f); // 水灵: 6点
                player.removePotionEffect(added.getPotion());
                int newDuration = (int) (added.getDuration() * 0.60f);
                if (newDuration > 0) {
                    player.addPotionEffect(new PotionEffect(added.getPotion(), newDuration,
                            added.getAmplifier(), added.getIsAmbient(), added.doesShowParticles()));
                }
            } else {
                float shareRatio = ws ? 0.50f : 0.30f; // 水灵: 50%
                int sharedDuration = (int) (added.getDuration() * shareRatio);
                if (sharedDuration > 0) {
                    List<EntityPlayer> nearby = player.world.getEntitiesWithinAABB(
                            EntityPlayer.class, player.getEntityBoundingBox().grow(8.0));
                    for (EntityPlayer ally : nearby) {
                        if (ally != player) {
                            ally.addPotionEffect(new PotionEffect(added.getPotion(), sharedDuration,
                                    added.getAmplifier(), added.getIsAmbient(), added.doesShowParticles()));
                        }
                    }
                }
            }
        } finally {
            tidalBeltProcessing = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LivingHealEvent (水心圣坠: overflow healing → absorption)
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        // Only intercept when at full HP
        if (player.getHealth() < player.getMaxHealth()) return;

        boolean hasPendant = hasBauble(player, ModItems.WATER_HEART_PENDANT);
        if (!hasPendant) {
            // Check if a nearby pendant wearer is within 8 blocks (their aura applies)
            List<EntityPlayer> nearby = player.world.getEntitiesWithinAABB(
                    EntityPlayer.class, player.getEntityBoundingBox().grow(8.0));
            boolean nearbyPendant = false;
            for (EntityPlayer p : nearby) {
                if (p != player && hasBauble(p, ModItems.WATER_HEART_PENDANT)) {
                    nearbyPendant = true;
                    break;
                }
            }
            if (!nearbyPendant) return;
        }

        boolean ws = hasWaterSpirit(player);
        float capRatio = ws ? 0.40f : 0.30f; // 水灵: 40%
        float maxAbsorption = player.getMaxHealth() * capRatio;
        float current = player.getAbsorptionAmount();
        float canAdd = maxAbsorption - current;
        event.setCanceled(true);
        if (canAdd > 0) {
            float toAdd = Math.min(event.getAmount(), canAdd);
            player.setAbsorptionAmount(current + toAdd);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private void applyOrRemoveModifier(EntityPlayer player, net.minecraft.entity.ai.attributes.IAttribute attr,
                                       UUID uuid, String name, double value, int operation, boolean apply) {
        IAttributeInstance inst = player.getEntityAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(uuid);
        if (apply && value != 0.0) {
            // Only (re-)apply if missing or value changed
            if (existing == null || Math.abs(existing.getAmount() - value) > 1e-9) {
                if (existing != null) inst.removeModifier(uuid);
                inst.applyModifier(new AttributeModifier(uuid, name, value, operation));
            }
        } else {
            if (existing != null) inst.removeModifier(uuid);
        }
    }

    private static int getWaterDepth(EntityPlayer player) {
        int depth = 0;
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY) + 1;
        int pz = (int) Math.floor(player.posZ);
        for (int y = py; y < py + 64; y++) {
            BlockPos pos = new BlockPos(px, y, pz);
            if (player.world.getBlockState(pos).getMaterial() == Material.WATER) {
                depth++;
            } else {
                break;
            }
        }
        return depth;
    }
}
