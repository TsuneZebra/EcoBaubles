package com.tsune.ecobaubles.entity;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.events.water.WaterEventHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

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
    public void onUpdate() {
        super.onUpdate();
        // Emit DRIP_WATER particle cluster every tick (server → all clients)
        if (!this.world.isRemote && this.world instanceof WorldServer) {
            WorldServer ws = (WorldServer) this.world;
            ws.spawnParticle(EnumParticleTypes.DRIP_WATER, true,
                this.posX, this.posY, this.posZ, 6, 0.18, 0.18, 0.18, 0.0);
        }
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
