package com.tsune.ecobaubles.events.fire;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.init.ModItems;
import com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye;
import com.tsune.ecobaubles.items.ItemRingExplosion;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;

/**
 * 火系全部饰品事件处理（含火灵增强）
 *
 * 火灵增强一览：
 *  炎魔之眼  — 狂战士最大增幅 +15%（50%→65%），不屈持续 +3s（7s→10s）
 *  炎魔核心  — 灼烧 +3s（5s→8s），点燃冷却减半（30s→15s）
 *  爆炎戒    — 冷却 -60s（180s→120s），自伤比例 80%→60%
 *  灰烬指环  — 触发阈值 50%→65%，火焰伤害 20%→35%
 *  灼魂戒    — 持续 +6s（14s→20s），吸取上限 5→8
 *  熔火之心腰带 — 最大生命 25%→40%，岩浆回复 1→2 HP/s
 *  余烬面具  — 注视点燃 6s→4s，注视引爆 5s→3s
 *  狂热披风  — 光环范围 2→3 格，击杀爆炸伤害 10→15，冷却 30s→20s
 *  爆炎吊坠  — 触发阈值 55%→40%，爆炸伤害 7→12
 */
public class FireEventHandler {

    // ── FlameDemonEye attribute UUIDs ──────────────────────────────────────────
    private static final UUID MOD_UUID_ATTACK  = UUID.fromString("a1c1a3c2-6f3f-4b83-9a7e-2b8f9f2b7c01");
    private static final UUID MOD_UUID_SPEED   = UUID.fromString("b2d2b4e4-0c6d-4f95-a2fc-7c2b0b3c8d12");

    // ── LavaHeart / FlameDemonCore attribute UUIDs ─────────────────────────────
    private static final UUID LAVA_HEART_HP_UUID = UUID.fromString("c3e3c5f6-1d7e-4a06-b3fd-8d3c1c4d9e23");
    private static final UUID CORE_SPEED_UUID    = UUID.fromString("d5f5e7b9-3f90-4c28-d5bf-ae5e3f6f1045");

    // ── FlameDemonCore: per-player → (target UUID → last ignite tick) ──────────
    private static final Map<UUID, Map<UUID, Long>> coreIgniteCooldowns = new HashMap<>();

    // ── FlameDemonCore: custom burn tracking (entity UUID → FlameCoreData) ─────
    private static final Map<UUID, FlameCoreData> flameCoreTargets = new HashMap<>();

    static class FlameCoreData {
        long lastDamageTick;
        long expiryTick;
        FlameCoreData(long now, boolean fireSpirit) {
            lastDamageTick = now;
            expiryTick = now + (fireSpirit ? 160 : 100); // 8s or 5s
        }
    }

    // ── AshRing: per-player last proc tick ────────────────────────────────────
    private static final Map<UUID, Long> ashRingLastProc = new HashMap<>();

    // ── SoulBurnRing: active chains (bearer player UUID → SoulChainData) ──────
    private static final Map<UUID, SoulChainData> soulChains = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> soulBurnCooldowns = new HashMap<>();
    private static final Set<UUID> soulChainedEntities = new HashSet<>();

    static class SoulChainData {
        UUID targetId;
        long startTick;
        long lastDrainTick;
        SoulChainData(UUID targetId, long now) {
            this.targetId   = targetId;
            this.startTick  = now;
            this.lastDrainTick = now;
        }
    }

    // ── EmberMask: stare tracking ──────────────────────────────────────────────
    private static final Map<UUID, UUID> stareTargetMap = new HashMap<>();
    private static final Map<UUID, Long> stareStartMap  = new HashMap<>();

    // ── FerventCloak: aura + kill explosion ───────────────────────────────────
    private static final Set<UUID> cloakBurnedEntities = new HashSet<>();
    private static final Map<UUID, Long> cloakKillExplosionCD = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  hasFireSpirit helper
    // ─────────────────────────────────────────────────────────────────────────
    public static boolean hasFireSpirit(EntityPlayer player) {
        IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack s = baubles.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == ModItems.FIRE_SPIRIT) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  onLivingHurt
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {

        // ── Attacker is a player ───────────────────────────────────────────
        if (event.getSource().getTrueSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
            IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
            EntityLivingBase target = event.getEntityLiving();
            boolean fs = !player.world.isRemote && hasFireSpirit(player);

            for (int i = 0; i < baubles.getSlots(); i++) {
                ItemStack s = baubles.getStackInSlot(i);
                if (s.isEmpty()) continue;

                // ── FlameDemonEye: toggle ──────────────────────────────────
                if (s.getItem() == ModItems.FLAME_DEMON_EYE) {
                    NBTTagCompound nbt = s.hasTagCompound() ? s.getTagCompound() : new NBTTagCompound();
                    boolean toggled = nbt.getBoolean(ItemAmuletFlameDemonEye.TAG_TOGGLE);
                    if (toggled) {
                        float original = event.getAmount();
                        float boosted  = original * 1.5f;
                        event.setAmount(boosted);
                        if (!player.world.isRemote) {
                            long now        = player.world.getTotalWorldTime();
                            long unyieldEnd = nbt.getLong(ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK);
                            boolean undying = now < unyieldEnd;
                            float selfDamage = boosted * 0.35f;
                            if (undying) {
                                float max = Math.max(0f, player.getHealth() - 1.0f + 0.0001f);
                                if (selfDamage > max) selfDamage = max;
                            }
                            if (selfDamage > 0f) {
                                player.attackEntityFrom(DamageSource.ON_FIRE, selfDamage);
                            }
                        }
                    }
                }

                // ── FlameDemonCore: ignite on attack ───────────────────────
                if (s.getItem() == ModItems.FLAME_DEMON_CORE && !player.world.isRemote) {
                    UUID pid = player.getUniqueID();
                    UUID tid = target.getUniqueID();
                    long now = player.world.getTotalWorldTime();
                    long igniteCooldown = fs ? 300L : 600L; // 火灵: 15s, 普通: 30s
                    Map<UUID, Long> perTarget = coreIgniteCooldowns.computeIfAbsent(pid, k -> new HashMap<>());
                    Long last = perTarget.get(tid);
                    if (last == null || now - last >= igniteCooldown) {
                        target.setFire(fs ? 8 : 5);
                        perTarget.put(tid, now);
                        flameCoreTargets.put(tid, new FlameCoreData(now, fs));
                    }
                }

                // ── ExplosionPendant: big-hit explosion ────────────────────
                if (s.getItem() == ModItems.EXPLOSION_PENDANT && !player.world.isRemote) {
                    float dmg = event.getAmount();
                    float threshold = fs ? 0.40f : 0.55f;  // 火灵: 40%
                    float explodeDmg = fs ? 12.0f : 7.0f;  // 火灵: 12点
                    if (dmg > target.getMaxHealth() * threshold) {
                        double x = target.posX, y = target.posY, z = target.posZ;
                        List<EntityLivingBase> nearby = player.world.getEntitiesWithinAABB(
                            EntityLivingBase.class,
                            new AxisAlignedBB(x - 3, y - 3, z - 3, x + 3, y + 3, z + 3));
                        for (EntityLivingBase nb : nearby) {
                            if (nb != player && !(nb instanceof EntityPlayer)) {
                                nb.attackEntityFrom(DamageSource.MAGIC, explodeDmg);
                                nb.setFire(3);
                            }
                        }
                        player.world.playSound(null, x, y, z,
                            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS,
                            2.0f, 1.1f + player.world.rand.nextFloat() * 0.2f);
                    }
                }
            }
        }

        // ── Victim is a player ────────────────────────────────────────────
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer hurtPlayer = (EntityPlayer) event.getEntityLiving();
            IBaublesItemHandler baubles2 = BaublesApi.getBaublesHandler(hurtPlayer);
            boolean fs = !hurtPlayer.world.isRemote && hasFireSpirit(hurtPlayer);

            for (int i = 0; i < baubles2.getSlots(); i++) {
                ItemStack s = baubles2.getStackInSlot(i);
                if (s.isEmpty()) continue;

                // ── FlameDemonEye: undying trigger ─────────────────────────
                if (s.getItem() == ModItems.FLAME_DEMON_EYE) {
                    NBTTagCompound nbt = s.hasTagCompound() ? s.getTagCompound() : new NBTTagCompound();
                    long now  = hurtPlayer.world.getTotalWorldTime();
                    long end  = nbt.getLong(ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK);
                    if (now < end) {
                        float health     = hurtPlayer.getHealth();
                        float maxAllowed = Math.max(0f, health - 1.0f + 0.0001f);
                        if (event.getAmount() > maxAllowed) event.setAmount(maxAllowed);
                    } else {
                        float health = hurtPlayer.getHealth();
                        float post   = health - event.getAmount();
                        long last    = nbt.getLong(ItemAmuletFlameDemonEye.TAG_COOLDOWN_TICK);
                        boolean onCD = now - last < ItemAmuletFlameDemonEye.UNYIELD_COOLDOWN_TICKS;
                        if (!onCD && post <= 0.0f) {
                            float maxAllowed = Math.max(0f, health - 1.0f + 0.0001f);
                            if (event.getAmount() > maxAllowed) event.setAmount(maxAllowed);
                            int unyieldDur = fs ? 10 * 20 : ItemAmuletFlameDemonEye.UNYIELD_DURATION_TICKS;
                            nbt.setLong(ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK, now + unyieldDur);
                            nbt.setLong(ItemAmuletFlameDemonEye.TAG_COOLDOWN_TICK, now);
                            s.setTagCompound(nbt);
                        }
                    }
                }

                // ── SoulBurnRing: create chain when player is hit ──────────
                if (s.getItem() == ModItems.SOUL_BURN_RING && !hurtPlayer.world.isRemote) {
                    Entity trueSource = event.getSource().getTrueSource();
                    if (trueSource instanceof EntityLivingBase && !(trueSource instanceof EntityPlayer)) {
                        EntityLivingBase attacker = (EntityLivingBase) trueSource;
                        UUID pid = hurtPlayer.getUniqueID();
                        UUID aid = attacker.getUniqueID();
                        long now = hurtPlayer.world.getTotalWorldTime();
                        Map<UUID, Long> perTarget = soulBurnCooldowns.computeIfAbsent(pid, k -> new HashMap<>());
                        Long lastChain = perTarget.get(aid);
                        if (lastChain == null || now - lastChain >= 1800) { // 90s
                            soulChains.put(pid, new SoulChainData(aid, now));
                            soulChainedEntities.add(aid);
                            perTarget.put(aid, now);
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  onLivingDeath
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        // ── FlameDemonEye undying ──────────────────────────────────────────
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            if (!player.world.isRemote) {
                IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
                for (int i = 0; i < baubles.getSlots(); i++) {
                    ItemStack s = baubles.getStackInSlot(i);
                    if (!s.isEmpty() && s.getItem() == ModItems.FLAME_DEMON_EYE) {
                        NBTTagCompound nbt = s.hasTagCompound() ? s.getTagCompound() : new NBTTagCompound();
                        long now = player.world.getTotalWorldTime();
                        long end = nbt.getLong(ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK);
                        if (now < end) {
                            event.setCanceled(true);
                            if (player.getHealth() < 1.0f) player.setHealth(1.0f);
                        }
                        break;
                    }
                }
            }
        }

        // ── FerventCloak: kill explosion ───────────────────────────────────
        if (!event.getEntityLiving().world.isRemote
                && event.getSource().getTrueSource() instanceof EntityPlayer) {
            EntityPlayer killer = (EntityPlayer) event.getSource().getTrueSource();
            IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(killer);
            boolean fs = hasFireSpirit(killer);
            for (int i = 0; i < baubles.getSlots(); i++) {
                ItemStack s = baubles.getStackInSlot(i);
                if (!s.isEmpty() && s.getItem() == ModItems.FERVENT_CLOAK) {
                    long now = killer.world.getTotalWorldTime();
                    long cdTicks = fs ? 400L : 600L;  // 火灵: 20s, 普通: 30s
                    Long lastExp = cloakKillExplosionCD.get(killer.getUniqueID());
                    if (lastExp == null || now - lastExp >= cdTicks) {
                        EntityLivingBase dying = event.getEntityLiving();
                        double x = dying.posX, y = dying.posY, z = dying.posZ;
                        float expDmg = fs ? 15.0f : 10.0f;
                        List<EntityLivingBase> nearby = killer.world.getEntitiesWithinAABB(
                            EntityLivingBase.class,
                            new AxisAlignedBB(x - 4, y - 4, z - 4, x + 4, y + 4, z + 4));
                        for (EntityLivingBase nb : nearby) {
                            if (nb != killer && nb != dying) {
                                nb.attackEntityFrom(DamageSource.MAGIC, expDmg);
                                nb.setFire(2);
                            }
                        }
                        killer.world.playSound(null, x, y, z,
                            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS,
                            2.0f, 1.0f + killer.world.rand.nextFloat() * 0.2f);
                        cloakKillExplosionCD.put(killer.getUniqueID(), now);
                    }
                    break;
                }
            }
        }

        // ── Clean up per-entity tracking on death ─────────────────────────
        UUID deadId = event.getEntityLiving().getUniqueID();
        soulChainedEntities.remove(deadId);
        flameCoreTargets.remove(deadId);
        cloakBurnedEntities.remove(deadId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  onLivingHeal
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        UUID id = event.getEntityLiving().getUniqueID();
        if (soulChainedEntities.contains(id)) {
            event.setCanceled(true);
            return;
        }
        if (cloakBurnedEntities.contains(id)) {
            event.setAmount(event.getAmount() * 0.9f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  onPlayerUpdate
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onPlayerUpdate(LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
        long now = player.world.getTotalWorldTime();

        boolean hasFlameDemonEye  = false;
        boolean hasFlameDemonCore = false;
        boolean hasExplosionRing  = false;
        boolean hasAshRing        = false;
        boolean hasSoulBurnRing   = false;
        boolean hasLavaHeart      = false;
        boolean hasEmberMask      = false;
        boolean hasFerventCloak   = false;
        boolean fs                = false; // hasFireSpirit

        ItemStack flameEye = ItemStack.EMPTY;
        ItemStack expRing  = ItemStack.EMPTY;

        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack s = baubles.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (s.getItem() == ModItems.FLAME_DEMON_EYE)  { hasFlameDemonEye  = true; flameEye = s; }
            if (s.getItem() == ModItems.FLAME_DEMON_CORE) { hasFlameDemonCore = true; }
            if (s.getItem() == ModItems.EXPLOSION_RING)   { hasExplosionRing  = true; expRing  = s; }
            if (s.getItem() == ModItems.ASH_RING)         { hasAshRing        = true; }
            if (s.getItem() == ModItems.SOUL_BURN_RING)   { hasSoulBurnRing   = true; }
            if (s.getItem() == ModItems.LAVA_HEART_BELT)  { hasLavaHeart      = true; }
            if (s.getItem() == ModItems.EMBER_MASK)       { hasEmberMask      = true; }
            if (s.getItem() == ModItems.FERVENT_CLOAK)    { hasFerventCloak   = true; }
            if (s.getItem() == ModItems.FIRE_SPIRIT)      { fs                = true; }
        }

        // ── FlameDemonEye ──────────────────────────────────────────────────
        if (hasFlameDemonEye && !flameEye.isEmpty()) {
            NBTTagCompound nbt = flameEye.hasTagCompound() ? flameEye.getTagCompound() : new NBTTagCompound();
            float max = player.getMaxHealth();
            float cur = player.getHealth();
            float missingRatio = Math.max(0f, (max - cur) / max);
            // 火灵: 攻击最大增幅 65%，移速 28%；普通: 50% / 20%
            double atkBonus = (fs ? 0.65D : 0.50D) * missingRatio;
            double spdBonus = (fs ? 0.28D : 0.20D) * missingRatio;

            IAttributeInstance atk = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
            IAttributeInstance spd = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            if (atk != null) {
                AttributeModifier m = atk.getModifier(MOD_UUID_ATTACK);
                if (m != null) atk.removeModifier(m);
                if (atkBonus > 0) atk.applyModifier(new AttributeModifier(MOD_UUID_ATTACK, "flame_eye_missing_hp_attack", atkBonus, 2));
            }
            if (spd != null) {
                AttributeModifier m = spd.getModifier(MOD_UUID_SPEED);
                if (m != null) spd.removeModifier(m);
                if (spdBonus > 0) spd.applyModifier(new AttributeModifier(MOD_UUID_SPEED, "flame_eye_missing_hp_speed", spdBonus, 2));
            }

            long last       = nbt.getLong(ItemAmuletFlameDemonEye.TAG_COOLDOWN_TICK);
            long unyieldEnd = nbt.getLong(ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK);
            int unyieldDur  = fs ? 10 * 20 : ItemAmuletFlameDemonEye.UNYIELD_DURATION_TICKS;

            if (player.getHealth() <= 1.0f) {
                boolean onCD   = now - last < ItemAmuletFlameDemonEye.UNYIELD_COOLDOWN_TICKS;
                boolean active = now < unyieldEnd;
                if (!onCD && !active) {
                    nbt.setLong(ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK, now + unyieldDur);
                    nbt.setLong(ItemAmuletFlameDemonEye.TAG_COOLDOWN_TICK, now);
                    flameEye.setTagCompound(nbt);
                }
            }
            if (now < nbt.getLong(ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK)) {
                if (player.getHealth() < 1.0f) player.setHealth(1.0f);
            }
        } else {
            IAttributeInstance atk = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
            IAttributeInstance spd = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            if (atk != null) { AttributeModifier m = atk.getModifier(MOD_UUID_ATTACK); if (m != null) atk.removeModifier(m); }
            if (spd != null) { AttributeModifier m = spd.getModifier(MOD_UUID_SPEED);  if (m != null) spd.removeModifier(m); }
        }

        // ── FlameDemonCore ────────────────────────────────────────────────
        if (hasFlameDemonCore) {
            if (now % 200 == 0 || !player.isPotionActive(MobEffects.FIRE_RESISTANCE)) {
                player.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 300, 0, false, false));
            }
            boolean inLava     = player.isInLava();
            boolean onSoulSand = player.world.getBlockState(player.getPosition().down()).getBlock() == Blocks.SOUL_SAND;
            IAttributeInstance spd = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            if (spd != null) {
                AttributeModifier existing = spd.getModifier(CORE_SPEED_UUID);
                if (inLava || onSoulSand) {
                    if (existing == null) spd.applyModifier(new AttributeModifier(CORE_SPEED_UUID, "flame_core_lava_speed", 0.20, 2));
                } else {
                    if (existing != null) spd.removeModifier(existing);
                }
            }
        } else {
            IAttributeInstance spd = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            if (spd != null) { AttributeModifier m = spd.getModifier(CORE_SPEED_UUID); if (m != null) spd.removeModifier(m); }
        }

        // ── ExplosionRing: fuse check ──────────────────────────────────────
        if (hasExplosionRing && !expRing.isEmpty()) {
            NBTTagCompound nbt = expRing.hasTagCompound() ? expRing.getTagCompound() : new NBTTagCompound();
            long armedTick = nbt.getLong(ItemRingExplosion.TAG_ARMED_TICK);
            if (armedTick > 0 && now - armedTick >= ItemRingExplosion.FUSE_TICKS) {
                float selfRatio = fs ? 0.60f : 0.80f; // 火灵: 60%，普通: 80%
                float selfDmg   = player.getMaxHealth() * selfRatio;
                player.attackEntityFrom(DamageSource.MAGIC, selfDmg);

                List<EntityLivingBase> targets = player.world.getEntitiesWithinAABB(
                    EntityLivingBase.class, player.getEntityBoundingBox().grow(4.0));
                float aoe = player.getMaxHealth() * 1.80f;
                for (EntityLivingBase t : targets) {
                    if (t != player) t.attackEntityFrom(DamageSource.MAGIC, aoe);
                }
                player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS,
                    4.0f, 0.9f + player.world.rand.nextFloat() * 0.2f);
                nbt.setLong(ItemRingExplosion.TAG_ARMED_TICK, 0);
                expRing.setTagCompound(nbt);
            }
        }

        // ── AshRing ───────────────────────────────────────────────────────
        if (hasAshRing) {
            float hp    = player.getHealth();
            float maxHp = player.getMaxHealth();
            float hpThreshold = fs ? 0.65f : 0.50f; // 火灵: 65%
            if (hp < maxHp * hpThreshold) {
                Long lastProc = ashRingLastProc.get(player.getUniqueID());
                if (lastProc == null || now - lastProc >= 600) { // 30s
                    // 伤害 = 自身已损生命值 × 倍率（火灵: 130%，普通: 100%）
                    float missingHp = maxHp - hp;
                    float dmgMultiplier = fs ? 1.30f : 1.00f;
                    float damage = missingHp * dmgMultiplier;
                    List<EntityLivingBase> nearby = player.world.getEntitiesWithinAABB(
                        EntityLivingBase.class, player.getEntityBoundingBox().grow(6.0));
                    for (EntityLivingBase t : nearby) {
                        if (t != player) {
                            t.attackEntityFrom(DamageSource.ON_FIRE, damage);
                            t.setFire(2);
                        }
                    }
                    ashRingLastProc.put(player.getUniqueID(), now);
                }
            }
        }

        // ── SoulBurnRing: drain tick ───────────────────────────────────────
        if (hasSoulBurnRing) {
            SoulChainData chain = soulChains.get(player.getUniqueID());
            if (chain != null) {
                List<EntityLivingBase> entities = player.world.getEntitiesWithinAABB(
                    EntityLivingBase.class, player.getEntityBoundingBox().grow(25.0));
                EntityLivingBase chainTarget = null;
                for (EntityLivingBase e : entities) {
                    if (e.getUniqueID().equals(chain.targetId)) { chainTarget = e; break; }
                }

                long chainDuration = fs ? 400L : 280L; // 火灵: 20s, 普通: 14s
                boolean expired    = now - chain.startTick >= chainDuration;
                boolean outOfRange = chainTarget == null || player.getDistance(chainTarget) > 20.0;

                if (expired || outOfRange) {
                    soulChains.remove(player.getUniqueID());
                    soulChainedEntities.remove(chain.targetId);
                } else {
                    if (now - chain.lastDrainTick >= 40) { // every 2s
                        float drainCap = fs ? 8.0f : 5.0f; // 火灵: 上限 8
                        float drain = Math.min(chainTarget.getHealth() * 0.03f, drainCap);
                        chainTarget.attackEntityFrom(DamageSource.MAGIC, drain);
                        float missing = player.getMaxHealth() - player.getHealth();
                        if (missing > 0) {
                            float heal = Math.min(drain, missing);
                            player.heal(heal);
                            float overflow = drain - heal;
                            if (overflow > 0) player.setAbsorptionAmount(player.getAbsorptionAmount() + overflow);
                        } else {
                            player.setAbsorptionAmount(player.getAbsorptionAmount() + drain);
                        }
                        chain.lastDrainTick = now;
                    }
                }
            }
        } else {
            SoulChainData chain = soulChains.remove(player.getUniqueID());
            if (chain != null) soulChainedEntities.remove(chain.targetId);
        }

        // ── LavaHeart belt ─────────────────────────────────────────────────
        if (hasLavaHeart) {
            IAttributeInstance maxHpAttr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
            if (maxHpAttr != null) {
                double hpBonus = fs ? 0.40 : 0.25; // 火灵: +40%, 普通: +25%
                AttributeModifier existing = maxHpAttr.getModifier(LAVA_HEART_HP_UUID);
                // Re-apply every tick so fire spirit toggle takes effect immediately
                if (existing != null) maxHpAttr.removeModifier(existing);
                maxHpAttr.applyModifier(new AttributeModifier(LAVA_HEART_HP_UUID, "lava_heart_max_hp", hpBonus, 1));
            }
            if (player.isInLava()) {
                if (now % 200 == 0 || !player.isPotionActive(MobEffects.RESISTANCE)) {
                    player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 300, 0, false, false));
                }
                // Lava regen: 火灵 2 HP/s, 普通 1 HP/s
                if (now % 20 == 0) {
                    float regenAmt = fs ? 2.0f : 1.0f;
                    float cur = player.getHealth();
                    float max = player.getMaxHealth();
                    if (cur < max) player.setHealth(Math.min(cur + regenAmt, max));
                }
            } else {
                if (player.isPotionActive(MobEffects.RESISTANCE)) {
                    player.removePotionEffect(MobEffects.RESISTANCE);
                }
            }
        } else {
            IAttributeInstance maxHpAttr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
            if (maxHpAttr != null) {
                AttributeModifier existing = maxHpAttr.getModifier(LAVA_HEART_HP_UUID);
                if (existing != null) maxHpAttr.removeModifier(existing);
            }
        }

        // ── EmberMask: night vision + stare ───────────────────────────────
        if (hasEmberMask) {
            if (now % 200 == 0 || !player.isPotionActive(MobEffects.NIGHT_VISION)) {
                player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 300, 0, false, false));
            }
            if (now % 4 == 0) {
                EntityLivingBase lookTarget = getLookTarget(player, 8.0);
                UUID pid = player.getUniqueID();
                if (lookTarget instanceof IMob) {
                    UUID currentTarget = stareTargetMap.get(pid);
                    if (!lookTarget.getUniqueID().equals(currentTarget)) {
                        stareTargetMap.put(pid, lookTarget.getUniqueID());
                        stareStartMap.put(pid, now);
                    } else {
                        Long startTick = stareStartMap.get(pid);
                        if (startTick != null) {
                            long elapsed = now - startTick;
                            long detonateThresh = fs ? 60L  : 100L; // 火灵: 3s, 普通: 5s
                            long igniteThresh   = fs ? 80L  : 120L; // 火灵: 4s, 普通: 6s
                            // 引爆优先（需目标在燃烧）
                            if (elapsed >= detonateThresh && lookTarget.isBurning()) {
                                int fireTicks = getRemainingFireTicks(lookTarget);
                                float dmg = Math.max(1.0f, (float) Math.ceil(fireTicks / 20.0));
                                lookTarget.attackEntityFrom(DamageSource.MAGIC, dmg);
                                lookTarget.extinguish();
                                stareStartMap.put(pid, now);
                            } else if (elapsed >= igniteThresh) {
                                lookTarget.setFire(10);
                                stareStartMap.put(pid, now);
                            }
                        }
                    }
                } else {
                    stareTargetMap.remove(pid);
                    stareStartMap.remove(pid);
                }
            }
        } else {
            UUID pid = player.getUniqueID();
            stareTargetMap.remove(pid);
            stareStartMap.remove(pid);
        }

        // ── FerventCloak: burn aura ────────────────────────────────────────
        if (hasFerventCloak) {
            double auraRange = fs ? 3.0 : 2.0; // 火灵: 3格
            List<EntityLivingBase> nearby = player.world.getEntitiesWithinAABB(
                EntityLivingBase.class, player.getEntityBoundingBox().grow(auraRange));
            Set<UUID> currentAura = new HashSet<>();
            for (EntityLivingBase e : nearby) {
                if (e != player) {
                    currentAura.add(e.getUniqueID());
                    cloakBurnedEntities.add(e.getUniqueID());
                    if (now % 20 == 0) e.attackEntityFrom(DamageSource.ON_FIRE, 1.0f);
                }
            }
            cloakBurnedEntities.removeIf(id -> !currentAura.contains(id));
        } else {
            cloakBurnedEntities.clear();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  onEntityUpdate  (FlameDemonCore custom burn damage for non-players)
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onEntityUpdate(LivingUpdateEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) return;
        if (event.getEntityLiving().world.isRemote) return;

        EntityLivingBase entity = event.getEntityLiving();
        UUID id = entity.getUniqueID();
        FlameCoreData data = flameCoreTargets.get(id);
        if (data == null) return;

        long now = entity.world.getTotalWorldTime();
        if (now > data.expiryTick) {
            flameCoreTargets.remove(id);
            return;
        }
        if (now - data.lastDamageTick >= 20) {
            entity.attackEntityFrom(DamageSource.ON_FIRE, entity.getMaxHealth() * 0.01f);
            data.lastDamageTick = now;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utility: server-side entity look-target detection
    // ─────────────────────────────────────────────────────────────────────────
    private static EntityLivingBase getLookTarget(EntityPlayer player, double range) {
        Vec3d eyes = player.getPositionEyes(1.0f);
        Vec3d look = player.getLookVec();
        Vec3d end  = eyes.addVector(look.x * range, look.y * range, look.z * range);

        AxisAlignedBB searchBox = new AxisAlignedBB(
            Math.min(eyes.x, end.x) - 1.0, Math.min(eyes.y, end.y) - 1.0, Math.min(eyes.z, end.z) - 1.0,
            Math.max(eyes.x, end.x) + 1.0, Math.max(eyes.y, end.y) + 1.0, Math.max(eyes.z, end.z) + 1.0);

        List<Entity> candidates = player.world.getEntitiesInAABBexcluding(
            player, searchBox,
            e -> e instanceof EntityLivingBase && e != player);

        EntityLivingBase best = null;
        double minDist = Double.MAX_VALUE;
        for (Entity e : candidates) {
            AxisAlignedBB aabb = e.getEntityBoundingBox().grow(0.3);
            RayTraceResult hit = aabb.calculateIntercept(eyes, end);
            if (hit != null) {
                double dist = eyes.squareDistanceTo(hit.hitVec);
                if (dist < minDist) {
                    minDist = dist;
                    best = (EntityLivingBase) e;
                }
            }
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utility: read remaining fire ticks via ReflectionHelper (handles obf)
    // ─────────────────────────────────────────────────────────────────────────
    private static int getRemainingFireTicks(EntityLivingBase entity) {
        try {
            return ReflectionHelper.getPrivateValue(Entity.class, entity, "fire", "field_70173_aa");
        } catch (Exception e) {
            return entity.isBurning() ? 100 : 0;
        }
    }
}
