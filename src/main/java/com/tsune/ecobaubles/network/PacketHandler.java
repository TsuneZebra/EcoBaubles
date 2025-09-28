package com.tsune.ecobaubles.network;

import com.tsune.ecobaubles.EcoBaubles;
import com.tsune.ecobaubles.network.message.CPacketCooldownFeedback;
import com.tsune.ecobaubles.network.message.SPacketUseAbility;
import com.tsune.ecobaubles.network.message.SPacketWindCharmJump;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class PacketHandler {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(EcoBaubles.MODID);
    private static int id = 0;

    public static void registerMessages() {
        // Register messages here
        INSTANCE.registerMessage(SPacketUseAbility.Handler.class, SPacketUseAbility.class, id++, Side.SERVER);
        INSTANCE.registerMessage(CPacketCooldownFeedback.Handler.class, CPacketCooldownFeedback.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(SPacketWindCharmJump.Handler.class, SPacketWindCharmJump.class, id++, Side.SERVER);
    }
}
