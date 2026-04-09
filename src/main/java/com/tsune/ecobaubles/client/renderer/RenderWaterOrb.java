package com.tsune.ecobaubles.client.renderer;

import com.tsune.ecobaubles.entity.EntityWaterOrb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.init.Items;
import net.minecraftforge.fml.client.registry.IRenderFactory;

import javax.annotation.Nullable;

public class RenderWaterOrb extends RenderSnowball<EntityWaterOrb> {

    public RenderWaterOrb(RenderManager renderManager) {
        super(renderManager, Items.SPLASH_POTION, Minecraft.getMinecraft().getRenderItem());
    }

    @Override
    public void doRender(EntityWaterOrb entity, double x, double y, double z, float entityYaw, float partialTicks) {
        // Invisible — the water ball is shown entirely via DRIP_WATER particles spawned server-side
    }

    public static class Factory implements IRenderFactory<EntityWaterOrb> {
        @Override
        public Render<? super EntityWaterOrb> createRenderFor(RenderManager manager) {
            return new RenderWaterOrb(manager);
        }
    }
}
