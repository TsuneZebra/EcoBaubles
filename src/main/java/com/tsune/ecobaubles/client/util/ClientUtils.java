package com.tsune.ecobaubles.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class ClientUtils {

    public static void showCooldownFeedback(long remainingTicks) {
        if (Minecraft.getMinecraft().player != null) {
            double remainingSeconds = remainingTicks / 20.0;
            String feedback = String.format("冷却中: %.1fs", remainingSeconds);
            Minecraft.getMinecraft().player.sendStatusMessage(new TextComponentString(TextFormatting.RED + feedback), true);
        }
    }
}
