package com.tsune.ecobaubles.entity;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.events.water.WaterEventHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

public class EntityWaterOrb extends EntityThrowable {

    public EntityWaterOrb(World world) {
        super(world);
        this.setSize(0.75F, 0.75F);
    }

    public EntityWaterOrb(World world, EntityLivingBase thrower) {
        super(world, thrower);
        this.setSize(0.75F, 0.75F);
    }

    @Override
    protected float getGravityVelocity() {
        return 0.02f;
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        if (!this.world.isRemote) {
            boolean ws = false;
            if (this.getThrower() instanceof EntityPlayer) {
                ws = WaterEventHandler.hasWaterSpirit((EntityPlayer) this.getThrower());
            }
            if (result.typeOfHit == RayTraceResult.Type.ENTITY) {
                net.minecraft.entity.Entity hit = result.entityHit;
                if (hit instanceof EntityLivingBase && hit != this.getThrower()) {
                    WaterEventHandler.applyWaterLock(this.world, (EntityLivingBase) hit, 2.0, ws);
                }
            } else {
                WaterEventHandler.applyWaterLockAtPos(this.world, result.hitVec, 2.5, ws);
            }
        }
        this.setDead();
    }
}
