package com.tsune.ecobaubles.events.water;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.init.ModItems;
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

    // ── SeaGod amulet ─────────────────────────────────────────────────────────
    private static final Map<UUID, Long> seaGodHealCD = new HashMap<>();

    // ── TideSurge amulet ──────────────────────────────────────────────────────
    private static final Map<UUID, Long> lastCombatTick = new HashMap<>();
    private static final Set<UUID> hasTideShield = new HashSet<>();

    // ── HealingRing ───────────────────────────────────────────────────────────
    private static final Map<UUID, Long> healingRingLastTick = new HashMap<>();

    // ── TorrentRing (激流勇进) ─────────────────────────────────────────────────
    private static final Map<UUID, Long> waterLockMap = new HashMap<>();
    private static final Map<UUID, Long> waterDebuffMap = new HashMap<>();
    private static final Map<UUID, Long> waterDrownTick = new HashMap<>();
    // locked position: entity UUID → [x, y, z]
    private static final Map<UUID, double[]> lockedPositions = new HashMap<>();

    // ── WaveRing (波光指环) ────────────────────────────────────────────────────
    private static final Map<UUID, Long> waveRingLastProc = new HashMap<>();

    // ── TidalBelt (潮汐腰带) ──────────────────────────────────────────────────
    private static boolean tidalBeltProcessing = false;

    // ── WaterHeartPendant (水心圣坠) ──────────────────────────────────────────
    private static final Map<UUID, Long> waterHeartCD = new HashMap<>();

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

        // ── SeaGod: 80% damage reduction ─────────────────────────────────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            if (hasBauble(vp, ModItems.SEA_GOD_AMULET)) {
                event.setAmount(event.getAmount() * 0.20f);
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

        // ── WaterRobe: projectile -35% (-50% with 水灵) ───────────────────────
        if (victim instanceof EntityPlayer && src.isProjectile()) {
            EntityPlayer vp = (EntityPlayer) victim;
            if (hasBauble(vp, ModItems.WATER_ROBE)) {
                boolean wsVp = hasWaterSpirit(vp);
                event.setAmount(event.getAmount() * (wsVp ? 0.50f : 0.65f));
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
                long waveCD = wsA ? 100L : 140L; // 水灵: 5s, 普通: 7s
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

        // ── WaterHeartPendant: attack entity with HP < 25% ───────────────────
        if (trueSource instanceof EntityPlayer && victim != trueSource) {
            EntityPlayer attacker = (EntityPlayer) trueSource;
            if (hasBauble(attacker, ModItems.WATER_HEART_PENDANT)) {
                boolean wsA = hasWaterSpirit(attacker);
                long now = attacker.world.getTotalWorldTime();
                Long last = waterHeartCD.get(attacker.getUniqueID());
                long heartCD = wsA ? 300L : 400L; // 水灵: 15s, 普通: 20s
                if ((last == null || now - last >= heartCD)
                        && victim.getHealth() < victim.getMaxHealth() * 0.25f) {
                    waterHeartCD.put(attacker.getUniqueID(), now);
                    float missingHp = victim.getMaxHealth() - victim.getHealth();
                    float dmgRatio = wsA ? 0.35f : 0.25f; // 水灵: 35%
                    float waveDmg = Math.min(missingHp * dmgRatio, 50.0f);
                    float healRatio = wsA ? 0.60f : 0.40f; // 水灵: 60%
                    float totalDmg = 0;
                    List<EntityLivingBase> targets = attacker.world.getEntitiesWithinAABB(
                            EntityLivingBase.class, victim.getEntityBoundingBox().grow(3.0));
                    if (!targets.contains(victim)) targets.add(victim);
                    Set<UUID> done = new HashSet<>();
                    for (EntityLivingBase t : targets) {
                        if (done.contains(t.getUniqueID()) || t == attacker || t instanceof EntityPlayer) continue;
                        done.add(t.getUniqueID());
                        t.attackEntityFrom(DamageSource.MAGIC, waveDmg);
                        t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 127, false, false));
                        totalDmg += waveDmg;
                    }
                    attacker.heal(totalDmg * healRatio);
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

        boolean hasSeaGod    = hasBauble(player, ModItems.SEA_GOD_AMULET);
        boolean hasTideSurge = hasBauble(player, ModItems.TIDE_SURGE_AMULET);
        boolean hasHealing   = hasBauble(player, ModItems.HEALING_RING);
        boolean hasWaterRobe = hasBauble(player, ModItems.WATER_ROBE);
        boolean hasAbyss     = hasBauble(player, ModItems.ABYSS_HELMET);
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

        // ── HealingRing: every 5s heal nearby players ─────────────────────────
        if (hasHealing) {
            Long lastHeal = healingRingLastTick.get(pid);
            if (lastHeal == null || now - lastHeal >= 100L) {
                healingRingLastTick.put(pid, now);
                float healPct = ws ? 0.08f : 0.05f; // 水灵: 8%
                double healRange = ws ? 4.0 : 3.0;  // 水灵: 4格
                player.heal(player.getMaxHealth() * healPct);
                List<EntityPlayer> nearby = player.world.getEntitiesWithinAABB(
                        EntityPlayer.class, player.getEntityBoundingBox().grow(healRange));
                for (EntityPlayer ally : nearby) {
                    if (ally != player) {
                        ally.heal(ally.getMaxHealth() * healPct);
                    }
                }
            }
        } else {
            healingRingLastTick.remove(pid);
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
            applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_DAMAGE, ABYSS_DAMAGE_UUID,
                    "abyssDepth", 0.0, 1, false);
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
