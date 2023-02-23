package com.mineclay.predicate;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

class PredicateMethodInstance {
    String name;
    Class<?> source;
    Method method;
    PredicateMethodBase base;
    JavaPlugin providingPlugin;
}
