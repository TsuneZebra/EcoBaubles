package com.tsune.ecobaubles.network.message;

import com.tsune.ecobaubles.client.util.ClientUtils;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class CPacketCooldownFeedback implements IMessage {

    private long cooldownRemaining;

    public CPacketCooldownFeedback() {
    }

    public CPacketCooldownFeedback(long cooldownRemaining) {
        this.cooldownRemaining = cooldownRemaining;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.cooldownRemaining = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.cooldownRemaining);
    }

    public static class Handler implements IMessageHandler<CPacketCooldownFeedback, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(CPacketCooldownFeedback message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() ->
                    ClientUtils.showCooldownFeedback(message.cooldownRemaining));
            return null;
        }
    }
}
