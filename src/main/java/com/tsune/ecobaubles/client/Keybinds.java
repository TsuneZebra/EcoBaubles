package com.tsune.ecobaubles.client;

import com.tsune.ecobaubles.network.PacketHandler;
import com.tsune.ecobaubles.network.message.SPacketUseAbility;
import com.tsune.ecobaubles.network.message.SPacketTogglePassive;
import com.tsune.ecobaubles.network.message.SPacketWindCharmJump;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class Keybinds {
    public static KeyBinding abilityKey;
    public static KeyBinding togglePassiveKey;
    private static boolean jumpKeyPressed = false;

    public static void register() {
        abilityKey = new KeyBinding("key.ecobaubles.ability", Keyboard.KEY_G, "key.categories.ecobaubles");
        togglePassiveKey = new KeyBinding("key.ecobaubles.toggle_passive", Keyboard.KEY_R, "key.categories.ecobaubles");
        ClientRegistry.registerKeyBinding(abilityKey);
        ClientRegistry.registerKeyBinding(togglePassiveKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (abilityKey.isPressed()) {
            PacketHandler.INSTANCE.sendToServer(new SPacketUseAbility());
        }
        if (togglePassiveKey.isPressed()) {
            PacketHandler.INSTANCE.sendToServer(new SPacketTogglePassive());
        }
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null && mc.world != null) {
                // Check if jump key is pressed
                boolean jumpPressed = mc.gameSettings.keyBindJump.isKeyDown();
                
                // If jump key was just pressed (not held), send wind charm jump request
                if (jumpPressed && !jumpKeyPressed) {
                    PacketHandler.INSTANCE.sendToServer(new SPacketWindCharmJump());
                }
                
                jumpKeyPressed = jumpPressed;
            }
        }
    }
}
