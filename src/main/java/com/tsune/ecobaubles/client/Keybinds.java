package com.tsune.ecobaubles.client;

import com.tsune.ecobaubles.network.PacketHandler;
import com.tsune.ecobaubles.network.message.SPacketUseAbility;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class Keybinds {
    public static KeyBinding abilityKey;

    public static void register() {
        abilityKey = new KeyBinding("key.ecobaubles.ability", Keyboard.KEY_G, "key.categories.ecobaubles");
        ClientRegistry.registerKeyBinding(abilityKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (abilityKey.isPressed()) {
            PacketHandler.INSTANCE.sendToServer(new SPacketUseAbility());
        }
    }
}
