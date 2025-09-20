package com.tsune.ecobaubles.client.renderer;

import com.tsune.ecobaubles.EcoBaubles;
import com.tsune.ecobaubles.entity.EntityWindArrow;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderArrow;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.IRenderFactory;

import javax.annotation.Nullable;

public class RenderWindArrow extends RenderArrow<EntityWindArrow> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(EcoBaubles.MODID, "entity/wind_arrow.png");

    public RenderWindArrow(RenderManager renderManagerIn) {
        super(renderManagerIn);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityWindArrow entity) {
        return TEXTURE;
    }
    
    @Override
    public void doRender(EntityWindArrow entity, double x, double y, double z, float entityYaw, float partialTicks) {
        this.bindEntityTexture(entity);
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }
    
    public static class Factory implements IRenderFactory<EntityWindArrow> {
        @Override
        public Render<? super EntityWindArrow> createRenderFor(RenderManager manager) {
            return new RenderWindArrow(manager);
        }
    }
}
