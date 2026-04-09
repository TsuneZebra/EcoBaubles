package com.tsune.ecobaubles.events.ice;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.init.ModItems;
import com.tsune.ecobaubles.network.PacketHandler;
import com.tsune.ecobaubles.network.message.CPacketIceGodFreezeStart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;

/**
 * Ice element bauble event handler.
 *
 * Ice Spirit boost summary:
 *  寒极神魄  — aura 10→12 blocks, freeze 12s→15s (240→300 ticks), cd 720s→600s
 *  霜蚀护符  — trigger 30%→45%, duration 3s→4s (60→80 ticks), dps 1.5→2
 *  霜刃指环  — damage 2→3, slow duration 3s→4s (60→80 ticks)
 *  寒狱之戒  — freeze 3s→5s (60→100 ticks), break dmg 3+(2n)→5+(3n), cd 40s→30s
 *  霜怨指环  — store ratio 60%→80%, trigger threshold 40%→50%
 *  寒域腰带  — max cold 60→80, slowness amplifier per range block +1
 *  冰魄之冠  — speed 15%→20%, attack 60→40 ticks cd, damage 4→6
 *  冰殇披甲  — armor +15→+20, toughness +5→+8
 *  极寒之心  — trigger 10%→15%, freeze 60→80 ticks, regen 4→6 HP/s
 */
public class IceEventHandler {

    // ── Attribute UUIDs ───────────────────────────────────────────────────────
    private static final UUID ICE_ARMOR_UUID      = UUID.fromString("c0d1e2f3-0002-4000-8000-000000000002");
    private static final UUID ICE_TOUGHNESS_UUID  = UUID.fromString("c0d1e2f3-0003-4000-8000-000000000003");
    private static final UUID ICE_CROWN_SPEED_UUID = UUID.fromString("c0d1e2f3-0004-4000-8000-000000000004");

    // ── 寒极神魄 ──────────────────────────────────────────────────────────────
    private static final Map<UUID, Long> iceGodCD = new HashMap<>();
    // world freeze: player UUID → expiry tick
    private static final Map<UUID, Long> worldFreezeExpiry = new HashMap<>();
    // world freeze: player UUID → set of frozen mob UUIDs
    private static final Map<UUID, Set<UUID>> worldFrozenMobs = new HashMap<>();

    // ── 霜蚀护符 ──────────────────────────────────────────────────────────────
    // mob UUID → FrostErosionData
    private static final Map<UUID, FrostErosionData> frostErosionTargets = new HashMap<>();

    static class FrostErosionData {
        long expiry;
        long lastDamageTick;
        boolean isEnhanced; // ice spirit
        FrostErosionData(long now, boolean is) {
            expiry = now + (is ? 80L : 60L);
            lastDamageTick = now;
            isEnhanced = is;
        }
    }

    // ── 霜刃指环 ──────────────────────────────────────────────────────────────
    // mob UUID → expiry tick (slowness debuff)
    private static final Map<UUID, Long> frostBladeSlowed = new HashMap<>();

    // ── 寒狱之戒 ──────────────────────────────────────────────────────────────
    // mob UUID → freeze expiry
    private static final Map<UUID, Long> icePrisonFrozenMobs = new HashMap<>();
    // mob UUID → count of mobs frozen in same cast (for break damage)
    private static final Map<UUID, Integer> icePrisonCastCount = new HashMap<>();
    // mob UUID → whether the cast was enhanced by ice spirit
    private static final Map<UUID, Boolean> icePrisonEnhanced = new HashMap<>();

    // ── 霜怨指环 ──────────────────────────────────────────────────────────────
    private static final Map<UUID, Float> frostGrudgeStored = new HashMap<>();
    private static final Map<UUID, Long> frostGrudgeBurstCD = new HashMap<>();

    // ── 寒域腰带 ──────────────────────────────────────────────────────────────
    private static final Map<UUID, Float> coldMeterMap = new HashMap<>();
    // 自行记录上一tick位置，prevPosX在LivingUpdateEvent前已被更新无法使用
    private static final Map<UUID, double[]> frostBeltLastPos = new HashMap<>();

    // ── 冰魄之冠 ──────────────────────────────────────────────────────────────
    private static final Map<UUID, Long> iceCrownAttackCD = new HashMap<>();

    // ── 冰殇披甲 ──────────────────────────────────────────────────────────────
    // players that currently have active ice armor (not shattered)
    private static final Set<UUID> hasIceArmor = new HashSet<>();
    // attacker UUID → expiry (2s debuff window)
    private static final Map<UUID, Long> iceArmorDebuffMap = new HashMap<>();

    // ── 极寒之心 ──────────────────────────────────────────────────────────────
    // player UUID → freeze expiry
    private static final Map<UUID, Long> frozenPlayersMap = new HashMap<>();
    // player UUID → frozen position [x, y, z]
    private static final Map<UUID, double[]> frozenPositions = new HashMap<>();
    // player UUID → last heal tick
    private static final Map<UUID, Long> frozenLastHealTick = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean hasIceSpirit(EntityPlayer player) {
        return hasBauble(player, ModItems.ICE_SPIRIT);
    }

    private static boolean hasBauble(EntityPlayer player, Item item) {
        IBaublesItemHandler h = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == item) return true;
        }
        return false;
    }

    /**
     * Called from ItemRingIcePrison.useAbility to freeze nearby mobs.
     */
    public static void activateIcePrison(EntityPlayer player, boolean isEnhanced) {
        World world = player.world;
        long now = world.getTotalWorldTime();
        long freezeDuration = isEnhanced ? 100L : 60L; // 5s or 3s

        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(
                EntityLivingBase.class, player.getEntityBoundingBox().grow(5.0));

        int count = 0;
        List<UUID> frozenUUIDs = new ArrayList<>();
        for (EntityLivingBase e : targets) {
            if (e == player) continue;
            if (!(e instanceof IMob)) continue;
            frozenUUIDs.add(e.getUniqueID());
            count++;
        }

        // Store cast count for each frozen mob
        for (UUID uid : frozenUUIDs) {
            icePrisonFrozenMobs.put(uid, now + freezeDuration);
            icePrisonCastCount.put(uid, count);
            icePrisonEnhanced.put(uid, isEnhanced);
        }

        // Apply freeze to entities
        for (EntityLivingBase e : targets) {
            if (e == player) continue;
            if (!frozenUUIDs.contains(e.getUniqueID())) continue;
            if (e instanceof EntityLiving) {
                ((EntityLiving) e).setNoAI(true);
            }
            e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, (int) freezeDuration + 20, 127, false, false));
        }
    }

    private static void breakIcePrison(EntityLivingBase mob) {
        UUID uid = mob.getUniqueID();
        Long expiry = icePrisonFrozenMobs.get(uid);
        if (expiry == null) return;

        icePrisonFrozenMobs.remove(uid);
        // Restore AI
        if (mob instanceof EntityLiving) {
            ((EntityLiving) mob).setNoAI(false);
        }
        // Deal break damage
        Integer count = icePrisonCastCount.getOrDefault(uid, 1);
        Boolean enhanced = icePrisonEnhanced.getOrDefault(uid, false);
        float dmg = enhanced ? (5.0f + 3.0f * count) : (3.0f + 2.0f * count);
        icePrisonCastCount.remove(uid);
        icePrisonEnhanced.remove(uid);
        mob.attackEntityFrom(DamageSource.MAGIC, dmg);
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

    private static boolean isSnowBiome(World world, EntityPlayer player) {
        net.minecraft.world.biome.Biome biome = world.getBiome(new BlockPos(player));
        String name = biome.getBiomeName().toLowerCase();
        float temp = biome.getDefaultTemperature();
        return name.contains("frozen") || name.contains("ice") || name.contains("snowy")
                || name.contains("snow") || temp <= 0.15f;
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

        // ── 极寒之心: immunity while frozen ───────────────────────────────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            UUID pid = vp.getUniqueID();
            if (frozenPlayersMap.containsKey(pid)) {
                // Player is currently frozen → cancel all damage
                event.setCanceled(true);
                return;
            }
        }

        // ── 寒极神魄: lethal hit → world freeze trigger ───────────────────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            UUID pid = vp.getUniqueID();
            if (hasBauble(vp, ModItems.ICE_GOD_AMULET)) {
                boolean is = hasIceSpirit(vp);
                float hp = vp.getHealth();
                float dmg = event.getAmount();
                if (hp - dmg <= 0) {
                    // Would be lethal
                    Long lastCD = iceGodCD.get(pid);
                    long cdDuration = is ? 600L * 20L : 720L * 20L;
                    if (lastCD == null || now - lastCD >= cdDuration) {
                        // Trigger: survive at 1 HP
                        iceGodCD.put(pid, now);
                        event.setAmount(hp - 1.0f); // leave player at 1 HP
                        // Initiate world freeze
                        long freezeTicks = is ? 300L : 240L; // 15s or 12s
                        worldFreezeExpiry.put(pid, now + freezeTicks);
                        // Find mobs to freeze
                        double aoeRadius = is ? 32.0 : 32.0;
                        Set<UUID> frozenSet = new HashSet<>();
                        List<EntityLivingBase> nearby = vp.world.getEntitiesWithinAABB(
                                EntityLivingBase.class, vp.getEntityBoundingBox().grow(aoeRadius));
                        for (EntityLivingBase e : nearby) {
                            if (e == vp || e instanceof EntityPlayer) continue;
                            frozenSet.add(e.getUniqueID());
                            if (e instanceof EntityLiving) {
                                ((EntityLiving) e).setNoAI(true);
                            }
                            e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,
                                    (int) freezeTicks + 20, 127, false, false));
                        }
                        worldFrozenMobs.put(pid, frozenSet);
                        // Sound + HUD progress bar
                        if (!vp.world.isRemote) {
                            vp.world.playSound(null, vp.posX, vp.posY, vp.posZ,
                                    SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                                    SoundCategory.PLAYERS, 1.0f, 0.7f);
                            if (vp instanceof EntityPlayerMP) {
                                PacketHandler.INSTANCE.sendTo(
                                        new CPacketIceGodFreezeStart(now + freezeTicks, freezeTicks),
                                        (EntityPlayerMP) vp);
                            }
                        }
                    }
                }
            }
        }

        // ── 霜蚀护符: 30% chance counter on attacker ──────────────────────────
        if (victim instanceof EntityPlayer && trueSource instanceof EntityLivingBase) {
            EntityPlayer vp = (EntityPlayer) victim;
            if (hasBauble(vp, ModItems.FROST_EROSION_AMULET)) {
                boolean is = hasIceSpirit(vp);
                float triggerChance = is ? 0.45f : 0.30f;
                if (vp.world.rand.nextFloat() < triggerChance) {
                    EntityLivingBase attacker = (EntityLivingBase) trueSource;
                    int slowDuration = is ? 80 : 60; // 4s or 3s
                    attacker.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, slowDuration, 1, false, true));
                    frostErosionTargets.put(attacker.getUniqueID(), new FrostErosionData(now, is));
                }
            }
        }

        // ── 霜刃指环: damage reduction for debuffed attackers ─────────────────
        if (trueSource instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) trueSource;
            Long slowExpiry = frostBladeSlowed.get(attacker.getUniqueID());
            if (slowExpiry != null && now < slowExpiry) {
                event.setAmount(event.getAmount() * 0.80f);
            }
        }

        // ── 霜刃指环: on attack deal extra magic damage + apply slow ──────────
        if (trueSource instanceof EntityPlayer && victim != trueSource && !(victim instanceof EntityPlayer)) {
            EntityPlayer attacker = (EntityPlayer) trueSource;
            if (hasBauble(attacker, ModItems.FROST_BLADE_RING)) {
                boolean is = hasIceSpirit(attacker);
                float extraDmg = is ? 3.0f : 2.0f;
                int slowDuration = is ? 80 : 60; // 4s or 3s
                victim.attackEntityFrom(DamageSource.MAGIC, extraDmg);
                victim.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, slowDuration, 0, false, true));
                frostBladeSlowed.put(victim.getUniqueID(), now + slowDuration);
            }
        }

        // ── 寒狱之戒: break freeze on damage ─────────────────────────────────
        if (!(victim instanceof EntityPlayer)) {
            UUID vid = victim.getUniqueID();
            if (icePrisonFrozenMobs.containsKey(vid)) {
                breakIcePrison(victim);
            }
        }

        // ── 霜怨指环: accumulate stored damage ───────────────────────────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            UUID pid = vp.getUniqueID();
            if (hasBauble(vp, ModItems.FROST_GRUDGE_RING)) {
                boolean is = hasIceSpirit(vp);
                float storeRatio = is ? 0.80f : 0.60f;
                float stored = frostGrudgeStored.getOrDefault(pid, 0.0f);
                frostGrudgeStored.put(pid, stored + event.getAmount() * storeRatio);
            }
        }

        // ── 冰殇披甲: attacker debuff + debuffed attacker reduction ───────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            if (hasBauble(vp, ModItems.ICE_ARMOR_BODY)) {
                if (trueSource instanceof EntityLivingBase) {
                    EntityLivingBase attacker = (EntityLivingBase) trueSource;
                    // Debuff the attacker for 2s
                    iceArmorDebuffMap.put(attacker.getUniqueID(), now + 40L);
                }
            }
        }
        // Debuffed attacker reduction (they deal -20% damage)
        if (trueSource instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) trueSource;
            Long debuffExpiry = iceArmorDebuffMap.get(attacker.getUniqueID());
            if (debuffExpiry != null && now < debuffExpiry) {
                event.setAmount(event.getAmount() * 0.80f);
            }
        }

        // ── 极寒之心: 10% chance freeze self ─────────────────────────────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            UUID pid = vp.getUniqueID();
            if (hasBauble(vp, ModItems.ICY_HEART_PENDANT) && !frozenPlayersMap.containsKey(pid)) {
                boolean is = hasIceSpirit(vp);
                float triggerChance = is ? 0.15f : 0.10f;
                if (vp.world.rand.nextFloat() < triggerChance) {
                    long freezeTicks = is ? 80L : 60L; // 4s or 3s
                    frozenPlayersMap.put(pid, now + freezeTicks);
                    frozenPositions.put(pid, new double[]{vp.posX, vp.posY, vp.posZ});
                    frozenLastHealTick.put(pid, now);
                    vp.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, (int) freezeTicks + 20, 255, false, false));
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // ── 冰魄之冠: biome damage reduction ──────────────────────────────────
        if (victim instanceof EntityPlayer) {
            EntityPlayer vp = (EntityPlayer) victim;
            if (hasBauble(vp, ModItems.ICE_SOUL_CROWN) && isSnowBiome(vp.world, vp)) {
                event.setAmount(event.getAmount() * 0.90f);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LivingUpdateEvent
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
        boolean is = hasIceSpirit(player);

        boolean hasIceGod     = hasBauble(player, ModItems.ICE_GOD_AMULET);
        boolean hasFrostBelt  = hasBauble(player, ModItems.FROST_DOMAIN_BELT);
        boolean hasIceCrown   = hasBauble(player, ModItems.ICE_SOUL_CROWN);
        boolean hasIceArmorB  = hasBauble(player, ModItems.ICE_ARMOR_BODY);
        boolean hasFrostGrudge = hasBauble(player, ModItems.FROST_GRUDGE_RING);
        boolean hasIcyHeart   = hasBauble(player, ModItems.ICY_HEART_PENDANT);

        // ── 寒极神魄: aura – slow nearby hostiles each tick ───────────────────
        if (hasIceGod) {
            double auraRange = is ? 12.0 : 10.0;
            List<EntityLivingBase> nearby = player.world.getEntitiesWithinAABB(
                    EntityLivingBase.class, player.getEntityBoundingBox().grow(auraRange));
            for (EntityLivingBase e : nearby) {
                if (e == player) continue;
                if (!(e instanceof IMob)) continue;
                e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 3, 0, false, false));
            }
        }

        // ── 极寒之心: while frozen ────────────────────────────────────────────
        if (frozenPlayersMap.containsKey(pid)) {
            long freezeExpiry = frozenPlayersMap.get(pid);
            if (now < freezeExpiry) {
                // Lock position
                double[] pos = frozenPositions.get(pid);
                if (pos != null) {
                    player.setPosition(pos[0], pos[1], pos[2]);
                    player.motionX = 0;
                    player.motionY = 0;
                    player.motionZ = 0;
                }
                // Apply immobilizing slowness
                player.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 3, 255, false, false));
                // Heal every 20 ticks
                Long lastHeal = frozenLastHealTick.get(pid);
                if (lastHeal == null || now - lastHeal >= 20L) {
                    frozenLastHealTick.put(pid, now);
                    float healAmount = is ? 3.0f : 2.0f; // 6 HP/s or 4 HP/s (2 = 1 tick = 1 HP... 20 ticks = 4 HP)
                    player.heal(healAmount);
                }
            } else {
                // Freeze expired: release
                frozenPlayersMap.remove(pid);
                frozenPositions.remove(pid);
                frozenLastHealTick.remove(pid);
                // Knockback + Slowness II to nearby
                List<EntityLivingBase> closeBy = player.world.getEntitiesWithinAABB(
                        EntityLivingBase.class, player.getEntityBoundingBox().grow(3.0));
                for (EntityLivingBase e : closeBy) {
                    if (e == player) continue;
                    double dx = e.posX - player.posX;
                    double dz = e.posZ - player.posZ;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist < 0.01) { dx = 1; dz = 0; dist = 1; }
                    dx /= dist;
                    dz /= dist;
                    e.motionX += dx * 1.5;
                    e.motionY += 0.3;
                    e.motionZ += dz * 1.5;
                    e.isAirBorne = true;
                    e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 1, false, true));
                }
            }
        } else if (hasIcyHeart) {
            // Nothing to maintain while not frozen
        }

        // ── 霜怨指环: burst check ─────────────────────────────────────────────
        if (hasFrostGrudge) {
            float stored = frostGrudgeStored.getOrDefault(pid, 0.0f);
            if (stored > 0) {
                float threshold = is ? 0.50f : 0.40f;
                Long lastBurst = frostGrudgeBurstCD.get(pid);
                boolean onCD = lastBurst != null && now - lastBurst < 300L;
                if (!onCD && player.getHealth() < player.getMaxHealth() * threshold) {
                    // Find nearby enemies
                    List<EntityLivingBase> enemies = player.world.getEntitiesWithinAABB(
                            EntityLivingBase.class, player.getEntityBoundingBox().grow(5.0));
                    List<EntityLivingBase> validEnemies = new ArrayList<>();
                    for (EntityLivingBase e : enemies) {
                        if (e == player || e instanceof EntityPlayer) continue;
                        if (!(e instanceof IMob)) continue;
                        validEnemies.add(e);
                    }
                    int count = Math.max(1, validEnemies.size());
                    float dmgEach = stored / count;
                    for (EntityLivingBase e : validEnemies) {
                        e.attackEntityFrom(DamageSource.MAGIC, dmgEach);
                    }
                    frostGrudgeStored.put(pid, 0.0f);
                    frostGrudgeBurstCD.put(pid, now);
                }
            }
        } else {
            frostGrudgeStored.remove(pid);
        }

        // ── 寒域腰带: cold meter update ───────────────────────────────────────
        if (hasFrostBelt) {
            float maxCold = is ? 80.0f : 60.0f;
            float coldBefore = coldMeterMap.getOrDefault(pid, 0.0f);
            float cold = coldBefore;
            double[] lastPos = frostBeltLastPos.get(pid);
            double dx = lastPos != null ? player.posX - lastPos[0] : 0.0;
            double dz = lastPos != null ? player.posZ - lastPos[1] : 0.0;
            frostBeltLastPos.put(pid, new double[]{player.posX, player.posZ});
            double speed = Math.sqrt(dx * dx + dz * dz);
            if (speed < 0.05) {
                cold += 0.25f; // 5/20 per tick = +5/s
            } else {
                cold -= (float) Math.min(speed * 10.0, 4.0) / 20.0f;
            }
            cold = Math.max(0.0f, Math.min(maxCold, cold));
            coldMeterMap.put(pid, cold);

            // Snow-like particles within aura range every 4 ticks
            if (now % 4 == 0 && player.world instanceof WorldServer) {
                WorldServer ws = (WorldServer) player.world;
                java.util.Random rand = player.world.rand;
                for (int i = 0; i < 3; i++) {
                    double angle = rand.nextDouble() * Math.PI * 2;
                    double r = rand.nextDouble() * 4.0;
                    double px = player.posX + r * Math.cos(angle);
                    double pz = player.posZ + r * Math.sin(angle);
                    double py = player.posY + 0.5 + rand.nextDouble() * 2.0;
                    // DRIP_WATER: tiny falling droplets, naturally drifts downward
                    ws.spawnParticle(EnumParticleTypes.DRIP_WATER,
                            true, px, py, pz,
                            1, 0.0, 0.0, 0.0, 0.0);
                }
            }

            // Every 20 ticks: apply effects
            if (now % 20 == 0) {
                // Mobs within 4 blocks: Slowness based on cold level
                int slowAmp = (int) (cold / 20.0f); // 0=Slowness I, 1=II, 2=III
                List<EntityLivingBase> mobs4 = player.world.getEntitiesWithinAABB(
                        EntityLivingBase.class, player.getEntityBoundingBox().grow(4.0));
                for (EntityLivingBase e : mobs4) {
                    if (e == player || e instanceof EntityPlayer) continue;
                    if (!(e instanceof IMob)) continue;
                    if (slowAmp >= 0) {
                        e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 25, slowAmp, false, false));
                    }
                }

                // Mobs within 3 blocks: cold damage
                float coldDmg = cold / 8.0f;
                if (coldDmg >= 1.0f) {
                    List<EntityLivingBase> mobs3 = player.world.getEntitiesWithinAABB(
                            EntityLivingBase.class, player.getEntityBoundingBox().grow(3.0));
                    for (EntityLivingBase e : mobs3) {
                        if (e == player || e instanceof EntityPlayer) continue;
                        e.attackEntityFrom(DamageSource.MAGIC, coldDmg);
                    }
                }

                // Mobs within 8 blocks but beyond 3 blocks: Blindness (reduced detection)
                List<EntityLivingBase> mobs8 = player.world.getEntitiesWithinAABB(
                        EntityLivingBase.class, player.getEntityBoundingBox().grow(8.0));
                for (EntityLivingBase e : mobs8) {
                    if (e == player || e instanceof EntityPlayer) continue;
                    if (!(e instanceof IMob)) continue;
                    double distSq = e.getDistanceSq(player);
                    if (distSq > 9.0) { // beyond 3 blocks
                        e.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 25, 0, false, false));
                    }
                }
            }
        } else {
            coldMeterMap.remove(pid);
        }

        // ── 冰魄之冠: biome speed + auto attack ──────────────────────────────
        if (hasIceCrown) {
            boolean inSnow = isSnowBiome(player.world, player);
            double speedBonus = is ? 0.20 : 0.15;
            applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED, ICE_CROWN_SPEED_UUID,
                    "iceCrownSpeed", speedBonus, 2, inSnow);

            // Auto attack: every 60 ticks (3s) or 40 ticks (2s) with ice spirit
            long attackInterval = is ? 40L : 60L;
            Long lastAttack = iceCrownAttackCD.get(pid);
            if (lastAttack == null || now - lastAttack >= attackInterval) {
                // Find nearest hostile mob within 8 blocks
                List<EntityLivingBase> mobs = player.world.getEntitiesWithinAABB(
                        EntityLivingBase.class, player.getEntityBoundingBox().grow(8.0));
                EntityLivingBase nearest = null;
                double nearestDist = Double.MAX_VALUE;
                for (EntityLivingBase e : mobs) {
                    if (e == player || e instanceof EntityPlayer) continue;
                    if (!(e instanceof IMob)) continue;
                    double d = e.getDistanceSq(player);
                    if (d < nearestDist) {
                        nearestDist = d;
                        nearest = e;
                    }
                }
                if (nearest != null) {
                    iceCrownAttackCD.put(pid, now);
                    float dmg = is ? 6.0f : 4.0f;
                    nearest.attackEntityFrom(DamageSource.MAGIC, dmg);
                    nearest.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 0, false, true));
                    // Play snowball sound at head position
                    player.world.playSound(null, player.posX, player.posY + player.getEyeHeight(), player.posZ,
                            SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.5f, 1.0f);
                    // Particle trail + impact burst
                    if (player.world instanceof WorldServer) {
                        WorldServer ws = (WorldServer) player.world;
                        Vec3d start = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
                        Vec3d end   = new Vec3d(nearest.posX, nearest.posY + nearest.height * 0.5, nearest.posZ);
                        Vec3d dir   = end.subtract(start).normalize();
                        double dist = start.distanceTo(end);
                        // Trail: small ice-magic sparks every 0.5 blocks
                        for (double d = 0.5; d < dist; d += 0.5) {
                            ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC,
                                    true,
                                    start.x + dir.x * d,
                                    start.y + dir.y * d,
                                    start.z + dir.z * d,
                                    1, 0.03, 0.03, 0.03, 0.02);
                        }
                        // Impact burst at target
                        ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC,
                                true,
                                nearest.posX, nearest.posY + nearest.height * 0.5, nearest.posZ,
                                8, 0.2, 0.2, 0.2, 0.1);
                    }
                }
            }
        } else {
            applyOrRemoveModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED, ICE_CROWN_SPEED_UUID,
                    "iceCrownSpeed", 0.0, 2, false);
            iceCrownAttackCD.remove(pid);
        }

        // ── 冰殇披甲: armor modifiers + shatter/restore ───────────────────────
        if (hasIceArmorB) {
            float hp = player.getHealth();
            float maxHp = player.getMaxHealth();
            boolean currentlyHasArmor = hasIceArmor.contains(pid);

            float shatterThreshold = 0.35f;
            float shatterDmgRatio  = 0.40f;

            if (!currentlyHasArmor) {
                // Check if HP restored above 60%
                if (hp >= maxHp * 0.60f) {
                    hasIceArmor.add(pid);
                    applyArmorModifiers(player, is, true);
                }
            } else {
                // Ice armor is active — re-apply each tick so spirit toggle takes effect
                applyArmorModifiers(player, is, true);

                // Check for shatter
                if (hp < maxHp * shatterThreshold) {
                    // SHATTER!
                    hasIceArmor.remove(pid);
                    applyArmorModifiers(player, is, false);
                    float missingHp = maxHp - hp;
                    float shatterDmg = missingHp * shatterDmgRatio;
                    List<EntityLivingBase> nearby4 = player.world.getEntitiesWithinAABB(
                            EntityLivingBase.class, player.getEntityBoundingBox().grow(4.0));
                    for (EntityLivingBase e : nearby4) {
                        if (e == player) continue;
                        e.attackEntityFrom(DamageSource.MAGIC, shatterDmg);
                    }
                    player.attackEntityFrom(DamageSource.MAGIC, shatterDmg);
                }
            }
        } else {
            if (hasIceArmor.contains(pid)) {
                hasIceArmor.remove(pid);
                applyArmorModifiers(player, is, false);
            }
        }

        // Clean up expired debuff entries
        iceArmorDebuffMap.entrySet().removeIf(e -> now >= e.getValue());
    }

    private void applyArmorModifiers(EntityPlayer player, boolean iceSpirit, boolean apply) {
        // base: +15 armor / +5 toughness; ice spirit: +20 armor / +8 toughness
        double armorBonus     = iceSpirit ? 20.0 : 15.0;
        double toughnessBonus = iceSpirit ? 8.0  : 5.0;

        IAttributeInstance armorInst = player.getEntityAttribute(SharedMonsterAttributes.ARMOR);
        if (armorInst != null) {
            AttributeModifier existing = armorInst.getModifier(ICE_ARMOR_UUID);
            if (existing != null) armorInst.removeModifier(ICE_ARMOR_UUID);
            if (apply) {
                armorInst.applyModifier(new AttributeModifier(ICE_ARMOR_UUID, "iceArmorBonus", armorBonus, 0));
            }
        }
        IAttributeInstance toughnessInst = player.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS);
        if (toughnessInst != null) {
            AttributeModifier existing = toughnessInst.getModifier(ICE_TOUGHNESS_UUID);
            if (existing != null) toughnessInst.removeModifier(ICE_TOUGHNESS_UUID);
            if (apply) {
                toughnessInst.applyModifier(new AttributeModifier(ICE_TOUGHNESS_UUID, "iceToughnessBonus", toughnessBonus, 0));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handleNonPlayerUpdate
    // ─────────────────────────────────────────────────────────────────────────
    private void handleNonPlayerUpdate(EntityLivingBase entity) {
        if (entity.world.isRemote) return;
        long now = entity.world.getTotalWorldTime();
        UUID eid = entity.getUniqueID();

        // ── 寒极神魄: maintain world freeze ───────────────────────────────────
        for (Map.Entry<UUID, Set<UUID>> entry : worldFrozenMobs.entrySet()) {
            UUID playerUUID = entry.getKey();
            Set<UUID> frozenSet = entry.getValue();
            if (!frozenSet.contains(eid)) continue;
            Long expiry = worldFreezeExpiry.get(playerUUID);
            if (expiry == null) {
                frozenSet.remove(eid);
                continue;
            }
            if (now < expiry) {
                // Re-apply freeze
                if (entity instanceof EntityLiving) {
                    ((EntityLiving) entity).setNoAI(true);
                }
                entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 5, 127, false, false));
            } else {
                // Freeze expired: restore AI
                frozenSet.remove(eid);
                if (frozenSet.isEmpty()) {
                    worldFrozenMobs.remove(playerUUID);
                    worldFreezeExpiry.remove(playerUUID);
                }
                if (entity instanceof EntityLiving) {
                    ((EntityLiving) entity).setNoAI(false);
                }
            }
            break;
        }

        // ── 霜蚀护符: periodic drown damage ──────────────────────────────────
        FrostErosionData erosionData = frostErosionTargets.get(eid);
        if (erosionData != null) {
            if (now < erosionData.expiry) {
                if (now - erosionData.lastDamageTick >= 20L) {
                    erosionData.lastDamageTick = now;
                    float dmg = erosionData.isEnhanced ? 2.0f : 1.5f;
                    entity.attackEntityFrom(DamageSource.DROWN, dmg);
                }
            } else {
                frostErosionTargets.remove(eid);
            }
        }

        // ── 寒狱之戒: maintain frozen state ───────────────────────────────────
        Long prisonExpiry = icePrisonFrozenMobs.get(eid);
        if (prisonExpiry != null) {
            if (now < prisonExpiry) {
                if (entity instanceof EntityLiving) {
                    ((EntityLiving) entity).setNoAI(true);
                }
                entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 5, 127, false, false));
            } else {
                // Expired: restore + deal break damage
                breakIcePrison(entity);
            }
        }
    }

    // ── Public getters for tooltip display (single-player JVM sharing) ───────
    public static float getColdMeter(UUID pid) {
        return coldMeterMap.getOrDefault(pid, 0.0f);
    }

    /** Returns the world tick when the ice-god freeze was last triggered, or -1 if never. */
    public static long getIceGodLastTrigger(UUID pid) {
        Long val = iceGodCD.get(pid);
        return val == null ? -1L : val;
    }
}
