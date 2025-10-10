package com.tsune.ecobaubles.events.fire;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.init.ModItems;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 火系事件处理：炎魔之眼
 * - 常驻被动（狂战士）：按生命缺失比例提升攻击和移速；临界触发不屈1，4s，CD 300s
 * - 可开关被动（玩火自焚）：开启时对敌伤害+50%，并反弹80%到自己
 */
public class FireEventHandler {

    private static final java.util.UUID MOD_UUID_ATTACK = java.util.UUID.fromString("a1c1a3c2-6f3f-4b83-9a7e-2b8f9f2b7c01");
    private static final java.util.UUID MOD_UUID_SPEED = java.util.UUID.fromString("b2d2b4e4-0c6d-4f95-a2fc-7c2b0b3c8d12");

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        // 攻击者是玩家时：处理炎魔之眼的增伤与自伤
        if (event.getSource().getTrueSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
            IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);

            ItemStack flameEye = ItemStack.EMPTY;
            for (int i = 0; i < baubles.getSlots(); i++) {
                ItemStack s = baubles.getStackInSlot(i);
                if (!s.isEmpty() && s.getItem() == ModItems.FLAME_DEMON_EYE) {
                    flameEye = s;
                    break;
                }
            }

            if (!flameEye.isEmpty()) {
                // 可开关被动：增加对敌伤害并自伤（打印测试信息）
                if (event.getEntityLiving() instanceof EntityLivingBase) {
                    NBTTagCompound nbt = flameEye.hasTagCompound() ? flameEye.getTagCompound() : new NBTTagCompound();
                    boolean toggled = nbt.getBoolean(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_TOGGLE);
                    if (toggled) {
                        float original = event.getAmount();
                        float boosted = original * 1.5f; // +50% 对敌伤害
                        event.setAmount(boosted);

                        // 反弹35%到自己（真实伤害，避免循环）
                        float selfDamage = boosted * 0.35f;
                        // 仅在服务端执行自伤，避免客户端提前显示死亡界面
                        if (!player.world.isRemote) {
                            // 如果不屈正在生效，则不让自伤把生命压到 1 以下之下触发客户端死亡
                            long now = player.world.getTotalWorldTime();
                            long unyieldEnd = nbt.getLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK);
                            boolean undyingActive = now < unyieldEnd;
                            if (undyingActive) {
                                float maxAllowed = Math.max(0f, player.getHealth() - 1.0f + 0.0001f);
                                if (selfDamage > maxAllowed) {
                                    selfDamage = maxAllowed;
                                }
                            }
                            if (selfDamage > 0f) {
                                player.attackEntityFrom(DamageSource.ON_FIRE, selfDamage);
                            }
                        }

                        System.out.println(String.format("[FlameEye] Toggle ON: original=%.2f, boosted=%.2f, self=%.2f", original, boosted, selfDamage));
                    }
                }
            }
        }
        // 不屈期间与首次触发的伤害处理：当受击者是玩家时生效
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer hurtPlayer = (EntityPlayer) event.getEntityLiving();
            IBaublesItemHandler baubles2 = BaublesApi.getBaublesHandler(hurtPlayer);
            for (int i = 0; i < baubles2.getSlots(); i++) {
                ItemStack s = baubles2.getStackInSlot(i);
                if (!s.isEmpty() && s.getItem() == ModItems.FLAME_DEMON_EYE) {
                    NBTTagCompound nbt = s.hasTagCompound() ? s.getTagCompound() : new NBTTagCompound();
                    long now = hurtPlayer.world.getTotalWorldTime();
                    long end = nbt.getLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK);
                    if (now < end) {
                        // 已在不屈：不让生命降到 1 以下
                        float health = hurtPlayer.getHealth();
                        float maxAllowed = Math.max(0f, health - 1.0f + 0.0001f);
                        if (event.getAmount() > maxAllowed) {
                            event.setAmount(maxAllowed);
                        }
                    } else {
                        // 首次触发：如果本次伤害会把生命打到 <= 0，且不在冷却，则立刻激活不屈并把生命保留到 1
                        float health = hurtPlayer.getHealth();
                        float post = health - event.getAmount();
                        long last = nbt.getLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_COOLDOWN_TICK);
                        boolean onCooldown = now - last < com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.UNYIELD_COOLDOWN_TICKS;
                        if (!onCooldown && post <= 0.0f) {
                            float maxAllowed = Math.max(0f, health - 1.0f + 0.0001f);
                            if (event.getAmount() > maxAllowed) {
                                event.setAmount(maxAllowed);
                            }
                            // 立即启动不屈
                            nbt.setLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK, now + com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.UNYIELD_DURATION_TICKS);
                            nbt.setLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_COOLDOWN_TICK, now);
                            s.setTagCompound(nbt);
                        }
                    }
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack s = baubles.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == ModItems.FLAME_DEMON_EYE) {
                NBTTagCompound nbt = s.hasTagCompound() ? s.getTagCompound() : new NBTTagCompound();
                long now = player.world.getTotalWorldTime();
                long end = nbt.getLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK);
                if (now < end) {
                    // During Undying: cancel death and keep player at 1 HP
                    event.setCanceled(true);
                    if (player.getHealth() < 1.0f) {
                        player.setHealth(1.0f);
                    }
                }
                break;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerUpdate(LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
        ItemStack flameEye = ItemStack.EMPTY;
        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack s = baubles.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == ModItems.FLAME_DEMON_EYE) {
                flameEye = s;
                break;
            }
        }
        if (flameEye.isEmpty()) return;

        NBTTagCompound nbt = flameEye.hasTagCompound() ? flameEye.getTagCompound() : new NBTTagCompound();

        // 常驻被动：按生命缺失比例，使用属性修饰符（打印测试信息）
        float max = player.getMaxHealth();
        float cur = player.getHealth();
        float missingRatio = Math.max(0f, (max - cur) / max); // 0..1
        // 每损失10%生命：+5%攻击、+2%移速 => 每 1.0 缺失 -> +50%攻击, +20%移速
        double attackBonus = 0.50D * missingRatio; // 乘法修饰
        double speedBonus = 0.20D * missingRatio;  // 乘法修饰

        IAttributeInstance atk = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        IAttributeInstance spd = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (atk != null) {
            AttributeModifier m = atk.getModifier(MOD_UUID_ATTACK);
            if (m != null) atk.removeModifier(m);
            if (attackBonus > 0) {
                atk.applyModifier(new AttributeModifier(MOD_UUID_ATTACK, "flame_eye_missing_hp_attack", attackBonus, 2)); // MULTIPLY_TOTAL
            }
        }
        if (spd != null) {
            AttributeModifier m2 = spd.getModifier(MOD_UUID_SPEED);
            if (m2 != null) spd.removeModifier(m2);
            if (speedBonus > 0) {
                spd.applyModifier(new AttributeModifier(MOD_UUID_SPEED, "flame_eye_missing_hp_speed", speedBonus, 2));
            }
        }

        // 不屈1（1点血触发，4s，CD 300s）
        long now = player.world.getTotalWorldTime();
        long last = nbt.getLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_COOLDOWN_TICK);
        long unyieldEnd = nbt.getLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK);

        if (player.getHealth() <= 1.0f) {
            boolean onCooldown = now - last < com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.UNYIELD_COOLDOWN_TICKS;
            boolean active = now < unyieldEnd;
            if (!onCooldown && !active) {
                nbt.setLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK, now + com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.UNYIELD_DURATION_TICKS);
                nbt.setLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_COOLDOWN_TICK, now);
                flameEye.setTagCompound(nbt);
            }
        }

        // 生效期间：仅维持生命值不低于 1
        if (now < nbt.getLong(com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye.TAG_UNYIELD_END_TICK)) {
            if (player.getHealth() < 1.0f) {
                player.setHealth(1.0f);
            }
        }
    }
}



