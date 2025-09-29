package com.tsune.ecobaubles.init;

import com.tsune.ecobaubles.items.ItemAmuletSkyfeather;
import com.tsune.ecobaubles.items.ItemAmuletWind;
import com.tsune.ecobaubles.items.ItemRingCrackWind;
import com.tsune.ecobaubles.items.ItemRingWind;
import com.tsune.ecobaubles.items.ItemRingWindAttraction;
import com.tsune.ecobaubles.items.ItemWindShadowBelt;
import com.tsune.ecobaubles.items.ItemWindCrown;
import com.tsune.ecobaubles.items.ItemWindShieldEcho;
import com.tsune.ecobaubles.items.ItemWindCharm;
import com.tsune.ecobaubles.items.ItemWindSpirit;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.List;

public class ModItems {
    public static final List<Item> ITEMS = new ArrayList<>();

    // Items
    public static final Item WIND_AMULET = new ItemAmuletWind("wind_amulet");
    public static final Item SKYFEATHER_AMULET = new ItemAmuletSkyfeather("skyfeather_amulet");
    public static final Item WIND_RING = new ItemRingWind("wind_ring");
    public static final Item WIND_ATTRACTION_RING = new ItemRingWindAttraction("wind_attraction_ring");
    public static final Item CRACK_WIND_RING = new ItemRingCrackWind("crack_wind_ring");
    public static final Item WIND_SHADOW_BELT = new ItemWindShadowBelt("wind_shadow_belt");
    public static final Item WIND_CROWN = new ItemWindCrown("wind_crown");
    public static final Item WIND_SHIELD_ECHO = new ItemWindShieldEcho("wind_shield_echo");
    public static final Item WIND_CHARM = new ItemWindCharm("wind_charm");
    public static final Item WIND_SPIRIT = new ItemWindSpirit("wind_spirit");
}
