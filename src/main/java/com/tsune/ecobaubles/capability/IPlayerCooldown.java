package com.tsune.ecobaubles.capability;

public interface IPlayerCooldown {
    long getGlobalCooldown();
    void setGlobalCooldown(long cooldown);
}
