package com.tsune.ecobaubles.capability;

public class PlayerCooldown implements IPlayerCooldown {
    private long globalCooldown = 0;

    @Override
    public long getGlobalCooldown() {
        return this.globalCooldown;
    }

    @Override
    public void setGlobalCooldown(long cooldown) {
        this.globalCooldown = cooldown;
    }
}
