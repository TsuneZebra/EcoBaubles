package com.tsune.ecobaubles.events.thunder;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.capability.IPlayerCooldown;
import com.tsune.ecobaubles.capability.PlayerCooldownProvider;
import com.tsune.ecobaubles.init.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;

/**
 * Thunder element bauble event handler.
 */
public class ThunderEventHandler {

    // ── Electrify system ──────────────────────────────────────────────────────
    private static final Map<UUID, Long> electrifiedMap = new HashMap<>(); // entity UUID -> expiry tick
    private static final UUID ELECTRIFY_SPEED_UUID  = UUID.fromString("d1e2f3a4-0001-4000-8000-000000000001");
    private static final UUID ELECTRIFY_ATTACK_UUID = UUID.fromString("d1e2f3a4-0002-4000-8000-000000000002");

    // Track HP for heal suppression (感电之戒)
    private static final Map<UUID, Float> electrifiedLastHP = new HashMap<>();

    // ── 雷神之怒 ──────────────────────────────────────────────────────────────
    private static final Map<UUID, Integer> thunderCharges = new HashMap<>();
    private static final Set<UUID> thunderStrikeReady = new HashSet<>();
    private static final Map<UUID, Long> thunderLethalCD = new HashMap<>();

    // ── 闪链指环 ──────────────────────────────────────────────────────────────
    private static final Map<UUID, Long> lightningChainCD = new HashMap<>();

    // ── 雷脉腰带 ──────────────────────────────────────────────────────────────
    private static final Map<UUID, Float> staticMeterMap = new HashMap<>();
    private static final Map<UUID, double[]> thunderPulseLastPos = new HashMap<>();
    private static final UUID THUNDER_SPEED_UUID        = UUID.fromString("d1e2f3a4-0003-4000-8000-000000000003");
    private static final UUID THUNDER_PULSE_ATTACK_UUID = UUID.fromString("d1e2f3a4-0004-4000-8000-000000000004");

    // ── 雷眼 ──────────────────────────────────────────────────────────────────
    private static final Map<UUID, Long> thunderEyeLastScan = new HashMap<>();
    // Track lightning bolts we spawned to avoid re-redirecting
    private static final Set<UUID> redirectedLightning = new HashSet<>();

    // ── 霹雳坠 ───────────────────────────────────────────────────────────────
    private static final Map<UUID, Long> boltPendantCD = new HashMap<>();
    private static final Map<UUID, Long> boltPendantAttackBuffExpiry = new HashMap<>();
    private static final UUID BOLT_PENDANT_ATTACK_UUID = UUID.fromString("d1e2f3a4-0005-4000-8000-000000000005");

    // ── 雷霆之铠 ─────────────────────────────────────────────────────────────
    private static final UUID THUNDER_ARMOR_ATTACK_UUID          = UUID.fromString("d1e2f3a4-0006-4000-8000-000000000006");
    private static final UUID THUNDER_ARMOR_LIGHTNING_SPEED_UUID = UUID.fromString("d1e2f3a4-0007-4000-8000-000000000007");
    private static final UUID THUNDER_ARMOR_LIGHTNING_ATCK_UUID  = UUID.fromString("d1e2f3a4-0008-4000-8000-000000000008");
    private static final Map<UUID, Long> thunderArmorLightningBuffExpiry = new HashMap<>();

    // ── 裂雷戒 ───────────────────────────────────────────────────────────────
    private static final UUID THUNDER_CRACK_SPEED_UUID = UUID.fromString("d1e2f3a4-0009-4000-8000-000000000009");
    static final Map<UUID, Long> thunderCrackCooldownExpiry = new HashMap<>();

    // ── 感电之戒 ─────────────────────────────────────────────────────────────
    private static final UUID ELECTRIFY_RING_ATTACK_UUID = UUID.fromString("d1e2f3a4-000a-4000-8000-00000000000a");

    // ─────────────────────────────────────────────────────────────────────────
    // Public helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean hasThunderSpirit(EntityPlayer player) {
        return hasBauble(player, ModItems.THUNDER_SPIRIT);
    }

    public static boolean isElectrified(UUID entityId) {
        Long expiry = electrifiedMap.get(entityId);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /**
     * Electrify an entity for the given number of ticks.
     * Uses world time so it integrates with the tick-based system.
     */
    public static void electrify(EntityLivingBase entity, long durationTicks) {
        long now = entity.world.getTotalWorldTime();
        Long existing = electrifiedMap.get(entity.getUniqueID());
        long newExpiry = now + durationTicks;
        if (existing == null || existing < newExpiry) {
            electrifiedMap.put(entity.getUniqueID(), newExpiry);
        }
    }

    /** Check electrify using world time (tick-based). */
    private static boolean isElectrifiedTick(UUID entityId, long now) {
        Long expiry = electrifiedMap.get(entityId);
        return expiry != null && expiry > now;
    }

    private static boolean hasBauble(EntityPlayer player, Item item) {
        IBaublesItemHandler h = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == item) return true;
        }
        return false;
    }

    private static void applyOrRemoveModifier(EntityPlayer player,
                                               net.minecraft.entity.ai.attributes.IAttribute attr,
                                               UUID uuid, String name, double value, int operation, boolean apply) {
        IAttributeInstance inst = player.getEntityAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(uuid);
        if (apply && value != 0.0) {
            if (existing == null || Math.abs(existing.getAmount() - value) > 1e-9) {
                if (existing != null) inst.removeModifier(uuid);
                inst.applyModifier(new AttributeModifier(uuid, name, value, operation));
            }
        } else {
            if (existing != null) inst.removeModifier(uuid);
        }
    }

    private static void spawnLightning(World world, double x, double y, double z) {
        world.addWeatherEffect(new EntityLightningBolt(world, x, y, z, false));
    }

    /**
     * Chain lightning to up to {@code maxTargets} nearby enemies from {@code source},
     * dealing {@code damage} each (with optional decay factor per chain hop).
     */
    private static void chainLightning(World world, EntityLivingBase source,
                                        EntityLivingBase excludeFirst,
                                        float damage, float decayFactor,
                                        int maxTargets, double radius) {
        List<EntityLivingBase> candidates = world.getEntitiesWithinAABB(
                EntityLivingBase.class, source.getEntityBoundingBox().grow(radius));
        int count = 0;
        for (EntityLivingBase e : candidates) {
            if (count >= maxTargets) break;
            if (e == source || e == excludeFirst) continue;
            if (e instanceof EntityPlayer) continue;
            if (!(e instanceof IMob)) continue;
            spawnLightning(world, e.posX, e.posY, e.posZ);
            e.attackEntityFrom(DamageSource.MAGIC, damage);
            electrify(e, 200L); // 10s
            damage *= decayFactor;
            count++;
        }
    }

    /**
     * Activate 裂雷戒 (Thunder Crack Ring) old lightning ability — kept for reference,
     * now replaced by dash. This method is no longer called.
     */
    public static void activateThunderCrack(EntityPlayer player, Vec3d targetPos) {
        World world = player.world;
        boolean spirit = hasThunderSpirit(player);
        float baseDmg = spirit ? 28.0f : 20.0f;
        int chainTargets = spirit ? 4 : 3;

        spawnLightning(world, targetPos.x, targetPos.y, targetPos.z);

        List<EntityLivingBase> nearby = world.getEntitiesWithinAABB(
                EntityLivingBase.class,
                new AxisAlignedBB(targetPos.x - 2, targetPos.y - 2, targetPos.z - 2,
                        targetPos.x + 2, targetPos.y + 2, targetPos.z + 2));
        EntityLivingBase primary = null;
        double closestDist = Double.MAX_VALUE;
        for (EntityLivingBase e : nearby) {
            if (e == player) continue;
            double dist = e.getDistanceSq(targetPos.x, targetPos.y, targetPos.z);
            if (dist < closestDist) {
                closestDist = dist;
                primary = e;
            }
        }

        if (primary != null) {
            primary.attackEntityFrom(DamageSource.MAGIC, baseDmg);
            electrify(primary, 60L); // 3s
            float chainDmg = baseDmg * 0.60f;
            chainLightning(world, primary, primary, chainDmg, 0.60f, chainTargets, 4.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 裂雷戒: cooldown helpers (used by ItemRingThunderCrack)
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean isCrackRingOnCooldown(UUID playerId, long now) {
        Long expiry = thunderCrackCooldownExpiry.get(playerId);
        return expiry != null && now < expiry;
    }

    public static void setCrackRingCooldown(UUID playerId, long expiryTick) {
        thunderCrackCooldownExpiry.put(playerId, expiryTick);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EntityJoinWorldEvent: redirect natural lightning (雷眼)
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!(event.getEntity() instanceof EntityLightningBolt)) return;
        EntityLightningBolt bolt = (EntityLightningBolt) event.getEntity();
        UUID boltId = bolt.getUniqueID();
        if (redirectedLightning.contains(boltId)) {
            // This is our own redirected bolt – clean up tracking
            redirectedLightning.remove(boltId);
            return;
        }
        if (event.getWorld().isRemote) return;

        World world = event.getWorld();
        Vec3d boltPos = bolt.getPositionVector();

        // Find any player within 30 blocks who has 雷眼
        List<EntityPlayer> players = world.getEntitiesWithinAABB(
                EntityPlayer.class, new AxisAlignedBB(
                        boltPos.x - 30, boltPos.y - 30, boltPos.z - 30,
                        boltPos.x + 30, boltPos.y + 30, boltPos.z + 30));
        EntityPlayer owner = null;
        for (EntityPlayer p : players) {
            if (hasBauble(p, ModItems.THUNDER_EYE)) {
                owner = p;
                break;
            }
        }
        if (owner == null) return;
        boolean spirit = hasThunderSpirit(owner);
        float extraDmg = spirit ? 15.0f : 10.0f;

        // Find nearest hostile mob within 30 blocks of the bolt
        List<EntityLivingBase> mobs = world.getEntitiesWithinAABB(
                EntityLivingBase.class, new AxisAlignedBB(
                        boltPos.x - 30, boltPos.y - 30, boltPos.z - 30,
                        boltPos.x + 30, boltPos.y + 30, boltPos.z + 30));
        EntityLivingBase target = null;
        double closestDist = Double.MAX_VALUE;
        for (EntityLivingBase e : mobs) {
            if (!(e instanceof IMob)) continue;
            double dist = e.getDistanceSq(boltPos.x, boltPos.y, boltPos.z);
            if (dist < closestDist) {
                closestDist = dist;
                target = e;
            }
        }
        if (target == null) return;

        // Teleport the lightning bolt to target
        bolt.setPosition(target.posX, target.posY, target.posZ);
        redirectedLightning.add(boltId);
        target.attackEntityFrom(DamageSource.MAGIC, extraDmg);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LivingDeathEvent: 裂雷戒 cooldown reduction on kill
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        Entity killer = event.getSource().getTrueSource();
        if (!(killer instanceof EntityPlayer)) return;
        EntityPlayer killerPlayer = (EntityPlayer) killer;
        if (!hasBauble(killerPlayer, ModItems.THUNDER_CRACK_RING)) return;
        UUID pid = killerPlayer.getUniqueID();
        long now = killerPlayer.world.getTotalWorldTime();
        long reduction = 4 * 20L;

        // Reduce item-specific CD
        Long expiry = thunderCrackCooldownExpiry.get(pid);
        if (expiry != null) {
            thunderCrackCooldownExpiry.put(pid, Math.max(now, expiry - reduction));
        }

        // Reduce global ability lock
        IPlayerCooldown cap = killerPlayer.getCapability(PlayerCooldownProvider.COOLDOWN_CAP, null);
        if (cap != null) {
            cap.setGlobalCooldown(Math.max(now, cap.getGlobalCooldown() - reduction));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LivingHurtEvent
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurt(LivingHurtEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        DamageSource src = event.getSource();
        Entity trueSource = src.getTrueSource();
        long now = victim.world.getTotalWorldTime();
        UUID vid = victim.getUniqueID();

        // ── 感电之戒: amplify damage to electrified targets + suppress heal ──
        if (isElectrifiedTick(vid, now) && trueSource instanceof EntityPlayer) {
            EntityPlayer attacker = (EntityPlayer) trueSource;
            if (hasBauble(attacker, ModItems.ELECTRIFY_RING)) {
                boolean spirit = hasThunderSpirit(attacker);
                float bonus = spirit ? 0.25f : 0.15f;
                event.setAmount(event.getAmount() * (1.0f + bonus));
            }
        }

        // ── 雷神之怒: lethal hit → survive + consume charges + AoE ─────────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            UUID pid2 = vp.getUniqueID();
            if (hasBauble(vp, ModItems.THUNDER_GOD_AMULET)) {
                float hp = vp.getHealth();
                if (hp - event.getAmount() <= 0) {
                    Long lastCD = thunderLethalCD.get(pid2);
                    long cdDuration = 600L * 20L;
                    if (lastCD == null || now - lastCD >= cdDuration) {
                        thunderLethalCD.put(pid2, now);
                        // Survive at 1 HP
                        event.setAmount(hp - 1.0f);
                        // Consume all charges
                        boolean ts = hasThunderSpirit(vp);
                        int charges = thunderCharges.getOrDefault(pid2, 0);
                        thunderCharges.put(pid2, 0);
                        thunderStrikeReady.remove(pid2);
                        // Shield effects: Absorption II + Speed III, 5s
                        vp.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 100, 1, false, true));
                        vp.addPotionEffect(new PotionEffect(MobEffects.SPEED, 100, 2, false, true));
                        // AoE damage nearby enemies
                        float lethalMult = ts ? 0.7f : 0.5f;
                        float aoeDmg = charges * lethalMult;
                        if (aoeDmg > 0) {
                            List<EntityLivingBase> nearby = vp.world.getEntitiesWithinAABB(
                                    EntityLivingBase.class, vp.getEntityBoundingBox().grow(5.0));
                            for (EntityLivingBase e : nearby) {
                                if (e == vp || e instanceof EntityPlayer) continue;
                                if (!(e instanceof IMob)) continue;
                                e.attackEntityFrom(DamageSource.MAGIC, aoeDmg);
                            }
                        }
                        spawnLightning(vp.world, vp.posX, vp.posY, vp.posZ);
                    }
                }
            }
        }

        // Only process player attackers for the rest
        if (!(trueSource instanceof EntityPlayer)) {
            // ── 雷霆之铠: counter-attack on melee hit + lightning buff ──────────
            if (victim instanceof EntityPlayer) {
                EntityPlayer vp = (EntityPlayer) victim;
                if (hasBauble(vp, ModItems.THUNDER_ARMOR)) {
                    // Lightning hit → 5s: +100% move speed + attack speed
                    if (src.getDamageType().equals("lightningBolt")) {
                        thunderArmorLightningBuffExpiry.put(vp.getUniqueID(), now + 5 * 20L);
                    }
                    boolean isMelee = !src.isProjectile() && !src.isMagicDamage()
                            && !src.isExplosion() && trueSource instanceof EntityLivingBase;
                    if (isMelee) {
                        // 30% chance to counter-attack for 6 damage, chain to 3 enemies
                        if (vp.world.rand.nextFloat() < 0.30f) {
                            EntityLivingBase attacker = (EntityLivingBase) trueSource;
                            attacker.attackEntityFrom(DamageSource.MAGIC, 6.0f);
                            electrify(attacker, 40L); // 2s
                            // Chain to up to 3 nearby enemies, 20% decay per hop
                            chainLightning(vp.world, attacker, attacker, 6.0f * 0.80f, 0.80f, 3, 4.0);
                        }
                    }
                    // Explosion damage reduction
                    if (src.isExplosion()) {
                        event.setAmount(event.getAmount() * 0.60f);
                    }
                }
            }
            return;
        }

        EntityPlayer playerAttacker = (EntityPlayer) trueSource;
        UUID pid = playerAttacker.getUniqueID();
        boolean spirit = hasThunderSpirit(playerAttacker);

        // ── 雷神之怒: accumulate charges & trigger Thunder Strike ─────────────
        if (hasBauble(playerAttacker, ModItems.THUNDER_GOD_AMULET) && !(victim instanceof EntityPlayer)) {
            int maxCharges = spirit ? 80 : 60;
            int charges = thunderCharges.getOrDefault(pid, 0);

            if (thunderStrikeReady.contains(pid)) {
                // Thunder Strike: 40 damage, chain 10 targets, 15% decay (85% retain)
                thunderStrikeReady.remove(pid);
                thunderCharges.put(pid, 0);
                float strikeDmg = 40.0f;
                spawnLightning(victim.world, victim.posX, victim.posY, victim.posZ);
                victim.attackEntityFrom(DamageSource.MAGIC, strikeDmg);
                electrify(victim, 200L); // 10s
                float chainDmg = strikeDmg * 0.85f;
                chainLightning(victim.world, victim, victim, chainDmg, 0.85f, 10, 4.0);
            } else {
                charges = Math.min(maxCharges, charges + 1);
                thunderCharges.put(pid, charges);
                if (charges >= maxCharges) {
                    thunderStrikeReady.add(pid);
                }
            }
        }

        // ── 静电护符: 20% (or 40% in thunderstorm) chance to electrify ──────
        if (hasBauble(playerAttacker, ModItems.STATIC_AMULET) && !(victim instanceof EntityPlayer)) {
            boolean isThunderstorm = playerAttacker.world.isThundering();
            float baseChance = spirit ? 0.30f : 0.20f;
            float chance = isThunderstorm ? baseChance * 2.0f : baseChance;
            if (chance > 1.0f) chance = 1.0f;
            if (playerAttacker.world.rand.nextFloat() < chance) {
                long electrifyTicks = spirit ? 100L : 60L; // 5s or 3s
                electrify(victim, electrifyTicks);
                victim.attackEntityFrom(DamageSource.MAGIC, 3.0f);
            }
        }

        // ── 闪链指环: chain damage to 4 enemies, 20% decay per hop ──────────
        if (hasBauble(playerAttacker, ModItems.LIGHTNING_CHAIN_RING) && !(victim instanceof EntityPlayer)) {
            long chainCDTicks = spirit ? 40L : 60L; // 2s or 3s
            Long lastChain = lightningChainCD.get(pid);
            if (lastChain == null || now - lastChain >= chainCDTicks) {
                lightningChainCD.put(pid, now);
                int chainTargets = 4;
                float chainDmg = event.getAmount() * 0.80f;
                List<EntityLivingBase> candidates = victim.world.getEntitiesWithinAABB(
                        EntityLivingBase.class, victim.getEntityBoundingBox().grow(4.0));
                int count = 0;
                for (EntityLivingBase e : candidates) {
                    if (count >= chainTargets) break;
                    if (e == victim || e instanceof EntityPlayer) continue;
                    if (!(e instanceof IMob)) continue;
                    e.attackEntityFrom(DamageSource.MAGIC, chainDmg);
                    electrify(e, 20L); // 1s
                    chainDmg *= 0.80f; // 20% decay per hop
                    count++;
                }
            }
        }

        // ── 霹雳坠: on critical hit summon lightning + 3s attack speed buff ──
        if (hasBauble(playerAttacker, ModItems.THUNDER_BOLT_PENDANT) && !(victim instanceof EntityPlayer)) {
            // Crit detection: player must be falling
            boolean isCrit = playerAttacker.fallDistance > 0.0f
                    && !playerAttacker.isOnLadder()
                    && !playerAttacker.isInWater();
            if (isCrit) {
                long boltCDTicks = spirit ? 10L * 20L : 15L * 20L;
                Long lastBolt = boltPendantCD.get(pid);
                if (lastBolt == null || now - lastBolt >= boltCDTicks) {
                    boltPendantCD.put(pid, now);
                    // Grant +20% attack speed for 3s
                    boltPendantAttackBuffExpiry.put(pid, now + 3 * 20L);
                    boolean inRainOrWater = victim.isInWater()
                            || victim.world.isRainingAt(victim.getPosition());
                    float boltDmg;
                    if (inRainOrWater) {
                        boltDmg = spirit ? 18.0f : 12.0f;
                    } else {
                        boltDmg = spirit ? 12.0f : 8.0f;
                    }
                    spawnLightning(victim.world, victim.posX, victim.posY, victim.posZ);
                    victim.attackEntityFrom(DamageSource.MAGIC, boltDmg);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LivingUpdateEvent
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote) return;

        long now = entity.world.getTotalWorldTime();
        UUID eid = entity.getUniqueID();

        // ── Apply electrify effects to all living entities ────────────────────
        if (isElectrifiedTick(eid, now)) {
            entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 3, 0, false, false));
            if (entity instanceof EntityPlayer) {
                EntityPlayer ep = (EntityPlayer) entity;
                applyOrRemoveModifier(ep, SharedMonsterAttributes.ATTACK_SPEED,
                        ELECTRIFY_ATTACK_UUID, "electrify_attack_debuff", -0.15, 2, true);
            }
        } else {
            // Clean up expired electrify
            electrifiedMap.remove(eid);
            electrifiedLastHP.remove(eid);
            if (entity instanceof EntityPlayer) {
                EntityPlayer ep = (EntityPlayer) entity;
                IAttributeInstance inst = ep.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
                if (inst != null && inst.getModifier(ELECTRIFY_ATTACK_UUID) != null) {
                    inst.removeModifier(ELECTRIFY_ATTACK_UUID);
                }
            }
        }

        if (!(entity instanceof EntityPlayer)) {
            // ── 感电之戒: suppress healing of electrified non-player mobs ────
            if (isElectrifiedTick(eid, now)) {
                float currentHP = entity.getHealth();
                Float lastHP = electrifiedLastHP.get(eid);
                if (lastHP != null && currentHP > lastHP) {
                    List<EntityPlayer> nearbyPlayers = entity.world.getEntitiesWithinAABB(
                            EntityPlayer.class, entity.getEntityBoundingBox().grow(32));
                    boolean hasRingPlayer = false;
                    for (EntityPlayer p : nearbyPlayers) {
                        if (hasBauble(p, ModItems.ELECTRIFY_RING)) {
                            hasRingPlayer = true;
                            break;
                        }
                    }
                    if (hasRingPlayer) {
                        entity.setHealth(lastHP);
                    }
                }
                electrifiedLastHP.put(eid, entity.getHealth());
            }
            return;
        }

        // ─ Player-only updates ─────────────────────────────────────────────
        EntityPlayer player = (EntityPlayer) entity;
        UUID pid = player.getUniqueID();
        boolean spirit = hasThunderSpirit(player);

        boolean hasThunderEyeB    = hasBauble(player, ModItems.THUNDER_EYE);
        boolean hasThunderPulseB  = hasBauble(player, ModItems.THUNDER_PULSE_BELT);
        boolean hasThunderArmorB  = hasBauble(player, ModItems.THUNDER_ARMOR);
        boolean hasCrackRingB     = hasBauble(player, ModItems.THUNDER_CRACK_RING);
        boolean hasBoltPendantB   = hasBauble(player, ModItems.THUNDER_BOLT_PENDANT);
        boolean hasElectrifyRingB = hasBauble(player, ModItems.ELECTRIFY_RING);

        // ── 雷眼: periodic Glowing on nearby hostiles ──────────────────────
        if (hasThunderEyeB) {
            double scanRange = spirit ? 30.0 : 20.0;
            boolean isThunderstorm = player.world.isThundering();

            if (isThunderstorm) {
                // Continuous Glowing
                List<EntityLivingBase> mobs = player.world.getEntitiesWithinAABB(
                        EntityLivingBase.class, player.getEntityBoundingBox().grow(scanRange));
                for (EntityLivingBase e : mobs) {
                    if (!(e instanceof IMob)) continue;
                    e.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 3, 0, false, false));
                }
            } else {
                // Every 10s (200 ticks)
                Long lastScan = thunderEyeLastScan.get(pid);
                if (lastScan == null || now - lastScan >= 200L) {
                    thunderEyeLastScan.put(pid, now);
                    List<EntityLivingBase> mobs = player.world.getEntitiesWithinAABB(
                            EntityLivingBase.class, player.getEntityBoundingBox().grow(scanRange));
                    for (EntityLivingBase e : mobs) {
                        if (!(e instanceof IMob)) continue;
                        e.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 60, 0, false, true));
                    }
                }
            }
        } else {
            thunderEyeLastScan.remove(pid);
        }

        // ── 雷脉腰带: static meter update ─────────────────────────────────
        if (hasThunderPulseB) {
            float maxStatic = spirit ? 50.0f : 30.0f;
            float staticVal = staticMeterMap.getOrDefault(pid, 0.0f);

            double[] tpLastPos = thunderPulseLastPos.get(pid);
            double dx = tpLastPos != null ? player.posX - tpLastPos[0] : 0.0;
            double dz = tpLastPos != null ? player.posZ - tpLastPos[1] : 0.0;
            thunderPulseLastPos.put(pid, new double[]{player.posX, player.posZ});
            boolean isMoving = Math.sqrt(dx * dx + dz * dz) > 0.05;

            if (isMoving) {
                // +1 per second = +1/20 per tick
                staticVal += 1.0f / 20.0f;
            } else {
                // -2 per second = -2/20 per tick
                staticVal -= 2.0f / 20.0f;
            }
            staticVal = Math.max(0.0f, Math.min(maxStatic, staticVal));
            staticMeterMap.put(pid, staticVal);

            // +3% move speed and attack speed per 3 static
            double speedBonus = Math.floor(staticVal / 3.0f) * 0.03;
            applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED,
                    THUNDER_SPEED_UUID, "thunder_pulse_speed", speedBonus, 2, speedBonus > 0);
            applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_SPEED,
                    THUNDER_PULSE_ATTACK_UUID, "thunder_pulse_attack", speedBonus, 2, speedBonus > 0);
        } else {
            staticMeterMap.remove(pid);
            thunderPulseLastPos.remove(pid);
            applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED, THUNDER_SPEED_UUID, "thunder_pulse_speed", 0, 2, false);
            applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_SPEED, THUNDER_PULSE_ATTACK_UUID, "thunder_pulse_attack", 0, 2, false);
        }

        // ── 雷霆之铠: permanent +15% attack speed + lightning buff ────────────
        if (hasThunderArmorB) {
            applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_SPEED,
                    THUNDER_ARMOR_ATTACK_UUID, "thunder_armor_attack", 0.15, 2, true);
            Long lightningExpiry = thunderArmorLightningBuffExpiry.get(pid);
            boolean lightningBuff = lightningExpiry != null && now <= lightningExpiry;
            applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED,
                    THUNDER_ARMOR_LIGHTNING_SPEED_UUID, "thunder_armor_lightning_speed", 1.0, 2, lightningBuff);
            applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_SPEED,
                    THUNDER_ARMOR_LIGHTNING_ATCK_UUID, "thunder_armor_lightning_attack", 1.0, 2, lightningBuff);
        } else {
            applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_SPEED, THUNDER_ARMOR_ATTACK_UUID, "thunder_armor_attack", 0, 2, false);
            applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED, THUNDER_ARMOR_LIGHTNING_SPEED_UUID, "thunder_armor_lightning_speed", 0, 2, false);
            applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_SPEED, THUNDER_ARMOR_LIGHTNING_ATCK_UUID, "thunder_armor_lightning_attack", 0, 2, false);
            thunderArmorLightningBuffExpiry.remove(pid);
        }

        // ── 裂雷戒: passive +15% move speed ───────────────────────────────────
        applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED,
                THUNDER_CRACK_SPEED_UUID, "thunder_crack_speed", 0.15, 2, hasCrackRingB);

        // ── 霹雳坠: crit attack speed buff (+20% for 3s) ──────────────────────
        if (hasBoltPendantB) {
            Long attackExpiry = boltPendantAttackBuffExpiry.get(pid);
            boolean attackBuff = attackExpiry != null && now <= attackExpiry;
            applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_SPEED,
                    BOLT_PENDANT_ATTACK_UUID, "bolt_pendant_attack", 0.20, 2, attackBuff);
        } else {
            applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_SPEED, BOLT_PENDANT_ATTACK_UUID, "bolt_pendant_attack", 0, 2, false);
            boltPendantAttackBuffExpiry.remove(pid);
        }

        // ── 感电之戒: permanent +10% attack speed ────────────────────────────
        applyOrRemoveModifier(player, SharedMonsterAttributes.ATTACK_SPEED,
                ELECTRIFY_RING_ATTACK_UUID, "electrify_ring_attack", 0.10, 2, hasElectrifyRingB);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 雷脉腰带: melee hit → consume 3 static → 2 dmg + electrify 2s to attacker
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurtBelt(LivingHurtEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote) return;
        if (!(victim instanceof EntityPlayer)) return;
        EntityPlayer vp = (EntityPlayer) victim;
        UUID pid = vp.getUniqueID();

        DamageSource src = event.getSource();
        Entity trueSource = src.getTrueSource();
        boolean isMelee = !src.isProjectile() && !src.isMagicDamage()
                && !src.isExplosion() && trueSource instanceof EntityLivingBase;
        if (!isMelee) return;
        if (!hasBauble(vp, ModItems.THUNDER_PULSE_BELT)) return;

        float staticVal = staticMeterMap.getOrDefault(pid, 0.0f);
        if (staticVal < 3.0f) return;

        // Consume 3 static → deal 2 dmg + electrify 2s to attacker
        staticMeterMap.put(pid, staticVal - 3.0f);
        EntityLivingBase attacker = (EntityLivingBase) trueSource;
        attacker.attackEntityFrom(DamageSource.MAGIC, 2.0f);
        electrify(attacker, 40L); // 2s
    }

    // ── Public getters for tooltip display (single-player JVM sharing) ────────
    public static float getStaticMeter(UUID pid) {
        return staticMeterMap.getOrDefault(pid, 0.0f);
    }

    public static int getThunderCharges(UUID pid) {
        return thunderCharges.getOrDefault(pid, 0);
    }

    public static boolean isThunderStrikeReady(UUID pid) {
        return thunderStrikeReady.contains(pid);
    }
}
