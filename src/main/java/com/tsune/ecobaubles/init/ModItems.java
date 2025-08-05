package com.tsune.ecobaubles.init;

import com.tsune.ecobaubles.items.ItemAmuletSkyfeather;
import com.tsune.ecobaubles.items.ItemAmuletWind;
import com.tsune.ecobaubles.items.ItemRingWind;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.List;

public class ModItems {
    public static final List<Item> ITEMS = new ArrayList<>();

    // Items
    public static final Item WIND_AMULET = new ItemAmuletWind("wind_amulet");
    public static final Item SKYFEATHER_AMULET = new ItemAmuletSkyfeather("skyfeather_amulet");
    public static final Item WIND_RING = new ItemRingWind("wind_ring");
}
