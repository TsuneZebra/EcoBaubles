package com.tsune.ecobaubles.network.message;

import com.tsune.ecobaubles.events.ForgeEventHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class SPacketWindCharmJump implements IMessage {

    public SPacketWindCharmJump() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<SPacketWindCharmJump, IMessage> {
        @Override
        public IMessage onMessage(SPacketWindCharmJump message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            WorldServer world = player.getServerWorld();

            world.addScheduledTask(() -> {
                // Check if player can perform wind charm jump
                if (ForgeEventHandler.canWindCharmJump(player)) {
                    ForgeEventHandler.performWindCharmJump(player);
                }
            });
            return null;
        }
    }
}
