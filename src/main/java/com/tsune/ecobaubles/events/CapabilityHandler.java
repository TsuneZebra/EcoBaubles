package com.tsune.ecobaubles.events;

import com.tsune.ecobaubles.EcoBaubles;
import com.tsune.ecobaubles.capability.PlayerCooldownProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class CapabilityHandler {
    public static final ResourceLocation COOLDOWN_CAP = new ResourceLocation(EcoBaubles.MODID, "cooldown");

    @SubscribeEvent
    public void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(COOLDOWN_CAP, new PlayerCooldownProvider());
        }
    }
}
