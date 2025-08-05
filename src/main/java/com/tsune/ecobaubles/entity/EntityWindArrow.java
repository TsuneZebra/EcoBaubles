package com.tsune.ecobaubles.entity;

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
                
                // 15 armor-piercing damage
                DamageSource armorPiercing = DamageSource.causeArrowDamage(this, this.shootingEntity).setDamageBypassesArmor();
                livingEntity.attackEntityFrom(armorPiercing, 15.0F);

                // 25% max health true damage (up to 200)
                float trueDamage = Math.min(livingEntity.getMaxHealth() * 0.25F, 200.0F);
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
        super.onUpdate();
        if (this.ticksExisted > 100) { // Arrow lives for 5 seconds
            this.setDead();
        }
    }
}
