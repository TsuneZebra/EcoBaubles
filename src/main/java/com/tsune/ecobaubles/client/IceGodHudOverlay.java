package com.tsune.ecobaubles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class IceGodHudOverlay {

    private static long expiryTick = 0;
    private static long totalDuration = 0;

    /** Called from the packet handler (main thread) when the freeze triggers. */
    public static void setFreeze(long expiry, long duration) {
        expiryTick = expiry;
        totalDuration = duration;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        long now = mc.world.getTotalWorldTime();
        if (totalDuration <= 0 || now >= expiryTick) return;

        long remaining = expiryTick - now;
        float progress = Math.max(0f, Math.min(1f, (float) remaining / totalDuration));

        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();

        // Bar geometry — top-center, compact size
        int barW = 100;
        int barH = 5;
        int barX = (screenW - barW) / 2;
        int barY = 20;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        // Dark translucent background panel (covers label + bar)
        Gui.drawRect(barX - 2, barY - 12, barX + barW + 2, barY + barH + 2, 0x99001133);

        // 1-px light-blue border around bar
        Gui.drawRect(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF66CCFF);

        // Empty trough (dark navy)
        Gui.drawRect(barX, barY, barX + barW, barY + barH, 0xFF0A1428);

        // Ice-blue fill
        int fillW = (int) (barW * progress);
        if (fillW > 0) {
            Gui.drawRect(barX, barY, barX + fillW, barY + barH, 0xFF00BFFF);
        }

        // Label — "寒极神魄 X.Xs" centered above bar
        // Gui.drawRect re-enables texture2D internally, so font rendering is safe here
        String label = String.format("\u5bd2\u6781\u795e\u9b44 %.1fs", remaining / 20.0f);
        float textX = (screenW - mc.fontRenderer.getStringWidth(label)) / 2.0f;
        mc.fontRenderer.drawStringWithShadow(label, textX, barY - 10, 0xFF88EEFF);

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
