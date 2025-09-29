package com.tsune.ecobaubles.entity;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.init.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

public class EntityWindArrow extends EntityArrow {

    public EntityWindArrow(World worldIn) {
        super(worldIn);
    }

    public EntityWindArrow(World worldIn, EntityLivingBase shooter) {
        super(worldIn, shooter);
        this.pickupStatus = PickupStatus.DISALLOWED;
    }

    @Override
    protected ItemStack getArrowStack() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHit(RayTraceResult raytraceResultIn) {
        Entity entity = raytraceResultIn.entityHit;
        if (entity != null && entity != this.shootingEntity) {
            if (entity instanceof EntityLivingBase) {
                EntityLivingBase livingEntity = (EntityLivingBase) entity;
                
                // 检测风灵饰品
                boolean hasWindSpirit = false;
                if (this.shootingEntity instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) this.shootingEntity;
                    IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
                    for (int i = 0; i < baublesHandler.getSlots(); i++) {
                        ItemStack stack = baublesHandler.getStackInSlot(i);
                        if (!stack.isEmpty() && stack.getItem() == ModItems.WIND_SPIRIT) {
                            hasWindSpirit = true;
                            break;
                        }
                    }
                }
                
                // 15 armor-piercing damage
                DamageSource armorPiercing = DamageSource.causeArrowDamage(this, this.shootingEntity).setDamageBypassesArmor();
                livingEntity.attackEntityFrom(armorPiercing, 15.0F);

                // 风灵增强：最大生命值伤害变为33%
                float maxHealthDamage = hasWindSpirit ? 0.33F : 0.25F;
                float trueDamage = Math.min(livingEntity.getMaxHealth() * maxHealthDamage, 200.0F);
                DamageSource trueDamageSource = DamageSource.causeArrowDamage(this, this.shootingEntity).setDamageIsAbsolute();
                livingEntity.attackEntityFrom(trueDamageSource, trueDamage);
            }
        }
        
        // Do not call super.onHit() to allow infinite piercing
        // Instead, just pass through blocks if it's a block hit
        if (raytraceResultIn.typeOfHit == RayTraceResult.Type.BLOCK) {
            // Arrow will just continue
        }
    }
    
    @Override
    public void onUpdate() {
        // 移除重力影响，使风箭没有下坠
        if (!this.inGround) {
            // 保持发射时的垂直速度，但移除重力影响
            // 不直接设置motionY为0，而是抵消重力
            this.motionY += 0.05D; // 抵消重力加速度
        }
        
        super.onUpdate();
        
        if (this.ticksExisted > 100) { // Arrow lives for 5 seconds
            this.setDead();
        }
    }
}
