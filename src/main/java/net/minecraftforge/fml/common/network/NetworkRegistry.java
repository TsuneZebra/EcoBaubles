/*
 * Minecraft Forge
 * Copyright (c) 2016-2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.common.network;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.AttributeKey;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.network.INetHandler;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.network.FMLOutboundHandler.OutboundTarget;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.common.network.internal.NetworkModHolder;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;

/**
 * @author cpw
 *
 */
public enum NetworkRegistry
{
    INSTANCE;
    private EnumMap<Side,Map<String,FMLEmbeddedChannel>> channels = Maps.newEnumMap(Side.class);
    private Map<ModContainer, NetworkModHolder> registry = Maps.newHashMap();
    private Map<ModContainer, IGuiHandler> serverGuiHandlers = Maps.newHashMap();
    private Map<ModContainer, IGuiHandler> clientGuiHandlers = Maps.newHashMap();

    /**
     * Set in the {@link ChannelHandlerContext}
     */
    public static final AttributeKey<String> FML_CHANNEL = AttributeKey.valueOf("fml:channelName");
    public static final AttributeKey<Side> CHANNEL_SOURCE = AttributeKey.valueOf("fml:channelSource");
    public static final AttributeKey<ModContainer> MOD_CONTAINER = AttributeKey.valueOf("fml:modContainer");
    public static final AttributeKey<INetHandler> NET_HANDLER = AttributeKey.valueOf("fml:netHandler");
    public static final AttributeKey<Boolean> FML_MARKER = AttributeKey.valueOf("fml:hasMarker");

    // Version 1: ServerHello only contains this value as a byte
    // Version 2: ServerHello additionally contains a 4 byte (int) dimension for the logging in client
    public static final byte FML_PROTOCOL = 2;

    private NetworkRegistry()
    {
        channels.put(Side.CLIENT, Maps.<String,FMLEmbeddedChannel>newConcurrentMap());
        channels.put(Side.SERVER, Maps.<String,FMLEmbeddedChannel>newConcurrentMap());
    }

    /**
     * Workaround for classloader mismatch: EnumMap.get() uses key.getClass() == keyType identity check.
     * If Side was loaded by two different classloaders the check fails and get() returns null.
     * Fall back to ordinal-based lookup in that case.
     */
    private Map<String, FMLEmbeddedChannel> getChannelMap(Side side)
    {
        Map<String, FMLEmbeddedChannel> map = channels.get(side);
        if (map != null)
        {
            return map;
        }
        // Ordinal-based fallback: iterate the EnumMap entries and match by ordinal
        for (Map.Entry<Side, Map<String, FMLEmbeddedChannel>> entry : channels.entrySet())
        {
            if (entry.getKey().ordinal() == side.ordinal())
            {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Represents a target point for the ALLROUNDPOINT target.
     *
     * @author cpw
     *
     */
    public static class TargetPoint {
        /**
         * A target point
         * @param dimension The dimension to target
         * @param x The X coordinate
         * @param y The Y coordinate
         * @param z The Z coordinate
         * @param range The range
         */
        public TargetPoint(int dimension, double x, double y, double z, double range)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.range = range;
            this.dimension = dimension;
        }
        public final double x;
        public final double y;
        public final double z;
        public final double range;
        public final int dimension;
    }

    public EnumMap<Side,FMLEmbeddedChannel> newChannel(String name, ChannelHandler... handlers)
    {
        Map<String,FMLEmbeddedChannel> clientMap = getChannelMap(Side.CLIENT);
        Map<String,FMLEmbeddedChannel> serverMap = getChannelMap(Side.SERVER);
        if ((clientMap != null && clientMap.containsKey(name)) || (serverMap != null && serverMap.containsKey(name)) || name.startsWith("MC|") || name.startsWith("\u0001") || name.startsWith("FML"))
        {
            throw new RuntimeException("That channel is already registered");
        }
        EnumMap<Side,FMLEmbeddedChannel> result = Maps.newEnumMap(Side.class);

        for (Side side : Side.values())
        {
            FMLEmbeddedChannel channel = new FMLEmbeddedChannel(name, side, handlers);
            Map<String,FMLEmbeddedChannel> sideMap = getChannelMap(side);
            if (sideMap != null)
            {
                sideMap.put(name, channel);
            }
            result.put(side, channel);
        }
        return result;
    }

    /**
     * Construct a new {@link SimpleNetworkWrapper} for the channel.
     *
     * @param name The name of the channel
     * @return A {@link SimpleNetworkWrapper} for handling this channel
     */
    public SimpleNetworkWrapper newSimpleChannel(String name)
    {
        return new SimpleNetworkWrapper(name);
    }
    /**
     * Construct a new {@link FMLEventChannel} for the channel.
     *
     * @param name The name of the channel
     * @return An {@link FMLEventChannel} for handling this channel
     */
    public FMLEventChannel newEventDrivenChannel(String name)
    {
        return new FMLEventChannel(name);
    }
    /**
     * INTERNAL Create a new channel pair with the specified name and channel handlers.
     * This is used internally in forge and FML
     *
     * @param container The container to associate the channel with
     * @param name The name for the channel
     * @param handlers Some {@link ChannelHandler} for the channel
     * @return an {@link EnumMap} of the pair of channels. keys are {@link Side}. There will always be two entries.
     */
    public EnumMap<Side,FMLEmbeddedChannel> newChannel(ModContainer container, String name, ChannelHandler... handlers)
    {
        Map<String,FMLEmbeddedChannel> clientMap = getChannelMap(Side.CLIENT);
        Map<String,FMLEmbeddedChannel> serverMap = getChannelMap(Side.SERVER);
        if ((clientMap != null && clientMap.containsKey(name)) || (serverMap != null && serverMap.containsKey(name)) || name.startsWith("MC|") || name.startsWith("\u0001") || (name.startsWith("FML") && !("FML".equals(container.getModId()))))
        {
            throw new RuntimeException("That channel is already registered");
        }
        EnumMap<Side,FMLEmbeddedChannel> result = Maps.newEnumMap(Side.class);

        for (Side side : Side.values())
        {
            FMLEmbeddedChannel channel = new FMLEmbeddedChannel(container, name, side, handlers);
            Map<String,FMLEmbeddedChannel> sideMap = getChannelMap(side);
            if (sideMap != null)
            {
                sideMap.put(name, channel);
            }
            result.put(side, channel);
        }
        return result;
    }

    public FMLEmbeddedChannel getChannel(String name, Side source)
    {
        Map<String,FMLEmbeddedChannel> sideMap = getChannelMap(source);
        return sideMap != null ? sideMap.get(name) : null;
    }
    /**
     * Register an {@link IGuiHandler} for the supplied mod object.
     *
     * @param mod The mod to handle GUIs for
     * @param handler A handler for creating GUI related objects
     */
    public void registerGuiHandler(Object mod, IGuiHandler handler)
    {
        ModContainer mc = FMLCommonHandler.instance().findContainerFor(mod);
        if (mc == null)
        {
            FMLLog.log.error("Mod of type {} attempted to register a gui network handler during a construction phase", mod.getClass().getName());
            throw new RuntimeException("Invalid attempt to create a GUI during mod construction. Use an EventHandler instead");
        }
        serverGuiHandlers.put(mc, handler);
        clientGuiHandlers.put(mc, handler);
    }

    /**
     * INTERNAL method for accessing the Gui registry
     * @param mc Mod Container
     * @param player Player
     * @param modGuiId guiId
     * @param world World
     * @param x X coord
     * @param y Y coord
     * @param z Z coord
     * @return The server side GUI object (An instance of {@link Container})
     */
    @Nullable
    public Container getRemoteGuiContainer(ModContainer mc, EntityPlayerMP player, int modGuiId, World world, int x, int y, int z)
    {
        IGuiHandler handler = serverGuiHandlers.get(mc);

        if (handler != null)
        {
            return (Container)handler.getServerGuiElement(modGuiId, player, world, x, y, z);
        }
        else
        {
            return null;
        }
    }

    /**
     * INTERNAL method for accessing the Gui registry
     * @param mc Mod Container
     * @param player Player
     * @param modGuiId guiId
     * @param world World
     * @param x X coord
     * @param y Y coord
     * @param z Z coord
     * @return The client side GUI object (An instance of {@link net.minecraft.client.gui.Gui})
     */
    @Nullable
    public Object getLocalGuiContainer(ModContainer mc, EntityPlayer player, int modGuiId, World world, int x, int y, int z)
    {
        IGuiHandler handler = clientGuiHandlers.get(mc);
        return handler.getClientGuiElement(modGuiId, player, world, x, y, z);
    }

    /**
     * Is there a channel with this name on this side?
     * @param channelName The name
     * @param source the side
     * @return if there's a channel
     */
    public boolean hasChannel(String channelName, Side source)
    {
        Map<String,FMLEmbeddedChannel> sideMap = getChannelMap(source);
        return sideMap != null && sideMap.containsKey(channelName);
    }

    /**
     * INTERNAL method for registering a mod as a network capable thing
     * @param fmlModContainer The fml mod container
     * @param clazz a class
     * @param remoteVersionRange the acceptable remote range
     * @param asmHarvestedData internal data
     */
    public void register(ModContainer fmlModContainer, Class<?> clazz, @Nullable String remoteVersionRange, ASMDataTable asmHarvestedData)
    {
        NetworkModHolder networkModHolder = new NetworkModHolder(fmlModContainer, clazz, remoteVersionRange, asmHarvestedData);
        registry.put(fmlModContainer, networkModHolder);
        networkModHolder.testVanillaAcceptance();
    }

    public boolean isVanillaAccepted(Side from)
    {
        return registry.values().stream()
                .allMatch(mod -> mod.acceptsVanilla(from));
    }

    public Collection<String> getRequiredMods(Side from)
    {
        return registry.values().stream()
                .filter(mod -> !mod.acceptsVanilla(from))
                .map(mod -> mod.getContainer().getName())
                .sorted()
                .collect(Collectors.toList());
    }

    public Map<ModContainer,NetworkModHolder> registry()
    {
        return ImmutableMap.copyOf(registry);
    }

    /**
     * All the valid channel names for a side
     * @param side the side
     * @return the set of channel names
     */
    public Set<String> channelNamesFor(Side side)
    {
        Map<String,FMLEmbeddedChannel> sideMap = getChannelMap(side);
        return sideMap != null ? sideMap.keySet() : java.util.Collections.<String>emptySet();
    }

    /**
     * INTERNAL fire a handshake to all channels
     * @param networkDispatcher The dispatcher firing
     * @param origin which side the dispatcher is on
     */
    public void fireNetworkHandshake(NetworkDispatcher networkDispatcher, Side origin)
    {
        NetworkHandshakeEstablished handshake = new NetworkHandshakeEstablished(networkDispatcher, networkDispatcher.getNetHandler(), origin);
        Map<String,FMLEmbeddedChannel> sideMap = getChannelMap(origin);
        if (sideMap != null)
        {
            for (FMLEmbeddedChannel channel : sideMap.values())
            {
                channel.attr(FMLOutboundHandler.FML_MESSAGETARGET).set(OutboundTarget.DISPATCHER);
                channel.attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(networkDispatcher);
                channel.pipeline().fireUserEventTriggered(handshake);
            }
        }
    }

    public void cleanAttributes()
    {
        channels.values().forEach(map -> map.values().forEach(FMLEmbeddedChannel::cleanAttributes));
    }
}
