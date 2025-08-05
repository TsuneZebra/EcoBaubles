package com.tsune.ecobaubles;

import com.tsune.ecobaubles.capability.IPlayerCooldown;
import com.tsune.ecobaubles.capability.PlayerCooldown;
import com.tsune.ecobaubles.capability.PlayerCooldownStorage;
import com.tsune.ecobaubles.client.Keybinds;
import com.tsune.ecobaubles.client.renderer.RenderWindArrow;
import com.tsune.ecobaubles.entity.EntityWindArrow;
import com.tsune.ecobaubles.events.CapabilityHandler;
import com.tsune.ecobaubles.events.ForgeEventHandler;
import com.tsune.ecobaubles.network.PacketHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;

@Mod(modid = EcoBaubles.MODID, name = EcoBaubles.NAME, version = EcoBaubles.VERSION, dependencies = "required-after:baubles@[1.5.2,)")
public class EcoBaubles {

    public static final String MODID = "ecobaubles";
    public static final String NAME = "????";
    public static final String VERSION = "1.0";
    private static int entityId = 0;

    @Mod.Instance
    public static EcoBaubles instance;

    public static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        MinecraftForge.EVENT_BUS.register(new ForgeEventHandler());
        MinecraftForge.EVENT_BUS.register(new CapabilityHandler());
        CapabilityManager.INSTANCE.register(IPlayerCooldown.class, new PlayerCooldownStorage(), PlayerCooldown::new);
        
        PacketHandler.registerMessages();

        // Register entity
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "wind_arrow"), EntityWindArrow.class, "wind_arrow", entityId++, this, 64, 1, true);
    }

    @Mod.EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        Keybinds.register();
        MinecraftForge.EVENT_BUS.register(new Keybinds());
        
        // Register entity renderer
        RenderingRegistry.registerEntityRenderingHandler(EntityWindArrow.class, new RenderWindArrow.Factory());
    }
}
