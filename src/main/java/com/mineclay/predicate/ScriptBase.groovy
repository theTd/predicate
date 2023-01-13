package com.mineclay.predicate

import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.entity.Player

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

abstract class ScriptBase extends Script {
    private final ThreadLocal<Player> playerThreadLocal = new ThreadLocal<Player>()
    Server server = Bukkit.server

    void setPlayer(Player player) {
        this.playerThreadLocal.set(player)
    }

    <T> T sync(Closure<T> block) {
        if (Bukkit.primaryThread) {
            return block.call()
        } else {
            CompletableFuture<T> f = new CompletableFuture<>()
            server.scheduler.runTask(PredicatePlugin.inst()) {
                try {
                    f.complete(block.call())
                } catch (e) {
                    f.completeExceptionally(e)
                }
            }
            return f.get(10, TimeUnit.SECONDS)
        }
    }

    def propertyMissing(name) {
        def find = findProperty(name)
        if (find != null) {
            return find
        }
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Object findProperty(String propertyName) {
        return null
    }
}
