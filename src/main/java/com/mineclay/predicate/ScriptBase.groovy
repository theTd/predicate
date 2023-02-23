package com.mineclay.predicate

import org.bukkit.Bukkit
import org.bukkit.Server

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

abstract class ScriptBase extends Script {
    final Server server = Bukkit.server
    PropertyExtractor propertyExtractor;
    PropertyInterceptor propertyInterceptor;

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

    def propertyMissing(String name) {
        if (propertyInterceptor != null) {
            def fromInterceptor = propertyInterceptor.getProperty(name)
            if (fromInterceptor != null) {
                return fromInterceptor
            }
        }
        def find = findProperty(name)
        if (find != null) {
            return find
        }
    }

    // method delegation target
    @SuppressWarnings('GrMethodMayBeStatic')
    Object findProperty(String propertyName) {
        return null
    }
}
