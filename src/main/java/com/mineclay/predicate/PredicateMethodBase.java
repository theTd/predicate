package com.mineclay.predicate;

import org.bukkit.entity.Player;

/**
 * must have a default open constructor
 * any public method will be exposed
 */
public class PredicateMethodBase {

    final static ThreadLocal<Player> PLAYER_THREAD_LOCAL = new ThreadLocal<>();

    protected Player getPlayer() {
        return PLAYER_THREAD_LOCAL.get();
    }

    /**
     * extend this to expose dynamic properties
     *
     * @param key dynamic property name
     * @return dynamic property value
     */
    protected Object getProperty(String key) {
        return null;
    }
}
