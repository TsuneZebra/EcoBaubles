package com.tsune.ecobaubles.network.message;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.tsune.ecobaubles.capability.IPlayerCooldown;
import com.tsune.ecobaubles.capability.PlayerCooldownProvider;
import com.tsune.ecobaubles.init.ModItems;
import com.tsune.ecobaubles.item.special.IActiveAbility;
import com.tsune.ecobaubles.network.PacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class SPacketUseAbility implements IMessage {

    public SPacketUseAbility() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<SPacketUseAbility, IMessage> {
        @Override
        public IMessage onMessage(SPacketUseAbility message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            WorldServer world = player.getServerWorld();

            world.addScheduledTask(() -> {
                IPlayerCooldown cooldown = player.getCapability(PlayerCooldownProvider.COOLDOWN_CAP, null);
                if (cooldown == null) return;

                long currentTime = world.getTotalWorldTime();
                if (currentTime < cooldown.getGlobalCooldown()) {
                    // Player is on cooldown, send feedback to client
                    long remaining = cooldown.getGlobalCooldown() - currentTime;
                    PacketHandler.INSTANCE.sendTo(new CPacketCooldownFeedback(remaining), player);
                    return;
                }

                IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
                for (int i = 0; i < baubles.getSlots(); i++) {
                    ItemStack stack = baubles.getStackInSlot(i);
                    Item item = stack.getItem();
                    if (item instanceof IActiveAbility) {
                        ((IActiveAbility) item).useAbility(player, stack);
                        
                        // Set the global cooldown
                        cooldown.setGlobalCooldown(world.getTotalWorldTime() + 1200); // 60s cooldown
                        return; // Found and used an ability, stop searching
                    }
                }
            });
            return null;
        }
    }
}
