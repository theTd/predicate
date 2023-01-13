package com.mineclay.predicate;

import org.bukkit.entity.Player;

/**
 * must have a default open constructor
 * any public method will be exposed
 */
public class PredicateMethodBase {

    final ThreadLocal<Player> playerThreadLocal = new ThreadLocal<>();

    protected Player getPlayer() {
        return playerThreadLocal.get();
    }

    /**
     * extend this to expose dynamic properties
     */
    protected Object getProperty(String key) {
        return null;
    }
}
