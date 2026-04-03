package com.tsune.ecobaubles.init;

import com.tsune.ecobaubles.items.ItemAmuletSkyfeather;
import com.tsune.ecobaubles.items.ItemAmuletFlameDemonEye;
import com.tsune.ecobaubles.items.ItemAmuletFlameDemonCore;
import com.tsune.ecobaubles.items.ItemAmuletWind;
import com.tsune.ecobaubles.items.ItemBeltLavaHeart;
import com.tsune.ecobaubles.items.ItemBodyFerventCloak;
import com.tsune.ecobaubles.items.ItemCharmExplosionPendant;
import com.tsune.ecobaubles.items.ItemFireSpirit;
import com.tsune.ecobaubles.items.ItemHelmetEmberMask;
import com.tsune.ecobaubles.items.ItemRingAsh;
import com.tsune.ecobaubles.items.ItemRingCrackWind;
import com.tsune.ecobaubles.items.ItemRingExplosion;
import com.tsune.ecobaubles.items.ItemRingSoulBurn;
import com.tsune.ecobaubles.items.ItemRingWind;
import com.tsune.ecobaubles.items.ItemRingWindAttraction;
import com.tsune.ecobaubles.items.ItemWindShadowBelt;
import com.tsune.ecobaubles.items.ItemWindCrown;
import com.tsune.ecobaubles.items.ItemWindShieldEcho;
import com.tsune.ecobaubles.items.ItemWindCharm;
import com.tsune.ecobaubles.items.ItemWindSpirit;
// Water items
import com.tsune.ecobaubles.items.ItemAmuletSeaGod;
import com.tsune.ecobaubles.items.ItemAmuletTideSurge;
import com.tsune.ecobaubles.items.ItemRingHealing;
import com.tsune.ecobaubles.items.ItemRingTorrent;
import com.tsune.ecobaubles.items.ItemRingWave;
import com.tsune.ecobaubles.items.ItemHelmetAbyss;
import com.tsune.ecobaubles.items.ItemBeltTidal;
import com.tsune.ecobaubles.items.ItemBodyWaterRobe;
import com.tsune.ecobaubles.items.ItemCharmWaterHeart;
import com.tsune.ecobaubles.items.ItemWaterSpirit;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.List;

public class ModItems {
    public static final List<Item> ITEMS = new ArrayList<>();

    // Wind items
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

    // Fire items
    public static final Item FLAME_DEMON_EYE = new ItemAmuletFlameDemonEye("flame_demon_eye");
    public static final Item FLAME_DEMON_CORE = new ItemAmuletFlameDemonCore("flame_demon_core");
    public static final Item EXPLOSION_RING = new ItemRingExplosion("explosion_ring");
    public static final Item ASH_RING = new ItemRingAsh("ash_ring");
    public static final Item SOUL_BURN_RING = new ItemRingSoulBurn("soul_burn_ring");
    public static final Item LAVA_HEART_BELT = new ItemBeltLavaHeart("lava_heart_belt");
    public static final Item EMBER_MASK = new ItemHelmetEmberMask("ember_mask");
    public static final Item FERVENT_CLOAK = new ItemBodyFerventCloak("fervent_cloak");
    public static final Item EXPLOSION_PENDANT = new ItemCharmExplosionPendant("explosion_pendant");
    public static final Item FIRE_SPIRIT = new ItemFireSpirit("fire_spirit");

    // Water items
    public static final Item SEA_GOD_AMULET      = new ItemAmuletSeaGod("sea_god_amulet");
    public static final Item TIDE_SURGE_AMULET   = new ItemAmuletTideSurge("tide_surge_amulet");
    public static final Item HEALING_RING        = new ItemRingHealing("healing_ring");
    public static final Item TORRENT_RING        = new ItemRingTorrent("torrent_ring");
    public static final Item WAVE_RING           = new ItemRingWave("wave_ring");
    public static final Item ABYSS_HELMET        = new ItemHelmetAbyss("abyss_helmet");
    public static final Item TIDAL_BELT          = new ItemBeltTidal("tidal_belt");
    public static final Item WATER_ROBE          = new ItemBodyWaterRobe("water_robe");
    public static final Item WATER_HEART_PENDANT = new ItemCharmWaterHeart("water_heart_pendant");
    public static final Item WATER_SPIRIT        = new ItemWaterSpirit("water_spirit");
}
