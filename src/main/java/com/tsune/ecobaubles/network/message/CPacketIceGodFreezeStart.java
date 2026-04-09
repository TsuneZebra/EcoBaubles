package com.tsune.ecobaubles.network.message;

import com.tsune.ecobaubles.client.IceGodHudOverlay;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class CPacketIceGodFreezeStart implements IMessage {

    private long expiryTick;
    private long totalDuration;

    public CPacketIceGodFreezeStart() {}

    public CPacketIceGodFreezeStart(long expiryTick, long totalDuration) {
        this.expiryTick = expiryTick;
        this.totalDuration = totalDuration;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        expiryTick = buf.readLong();
        totalDuration = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(expiryTick);
        buf.writeLong(totalDuration);
    }

    public static class Handler implements IMessageHandler<CPacketIceGodFreezeStart, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(CPacketIceGodFreezeStart message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() ->
                IceGodHudOverlay.setFreeze(message.expiryTick, message.totalDuration));
            return null;
        }
    }
}
