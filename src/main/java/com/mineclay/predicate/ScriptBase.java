package com.mineclay.predicate;

import groovy.lang.Script;
import org.bukkit.entity.Player;

public abstract class ScriptBase extends Script {
    Player player;

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public void println() {
        super.println();
    }
}
