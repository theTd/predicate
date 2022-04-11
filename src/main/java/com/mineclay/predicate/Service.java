package com.mineclay.predicate;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.SneakyThrows;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import org.bukkit.entity.Player;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class Service {
    private final Logger logger;
    private final Binding binding;

    Service(Logger logger, Binding binding) {
        this.logger = logger;
        this.binding = binding;
    }

    /**
     * only run in server thread
     */
    public void rebuild() {
        if (this.dirty) {
            logger.log(Level.INFO, "rebuilding runtime");
            DynamicType.Builder<ScriptBase> b = new ByteBuddy().subclass(ScriptBase.class).name("com.mineclay.predicate.RuntimeScriptBase");
            for (PredicateMethodInstance methodInstance : methods.values()) {
                b = b.defineMethod(methodInstance.method.getName(), methodInstance.method.getReturnType(), methodInstance.method.getModifiers())
                        .intercept(MethodDelegation.to(methodInstance.base));
            }
            Class<?> loaded = b.make().load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION).getLoaded();
            CompilerConfiguration cfg = new CompilerConfiguration();

            cfg.setScriptBaseClass(loaded.getName());
            this.runtime = new GroovyShell(getClass().getClassLoader(), binding, cfg);
            this.dirty = false;
        }
    }

    private final Map<String, PredicateMethodInstance> methods = new HashMap<>();

    boolean dirty = true;

    @SneakyThrows
    public void addMethod(Class<? extends PredicateMethodBase> methodSource) {
        for (Method method : methodSource.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                PredicateMethodInstance ctx = new PredicateMethodInstance();
                ctx.name = method.getName();
                ctx.source = methodSource;
                ctx.method = method;
                ctx.base = methodSource.getConstructor().newInstance();
                PredicateMethodInstance replace = methods.put(method.getName(), ctx);
                if (replace != null) {
                    logger.log(Level.WARNING, "redefining method " + method.getName());
                }
                dirty = true;
            }
        }
    }

    GroovyShell runtime;

    @SneakyThrows
    public boolean test(Player player, String line) {
        rebuild();

        ScriptBase base = (ScriptBase) runtime.parse(line);
        base.setPlayer(player);

        Object result;
        try {
            for (PredicateMethodInstance m : methods.values()) {
                m.base.player = player;
            }

            result = base.run();
        } finally {
            for (PredicateMethodInstance m : methods.values()) {
                m.base.player = null;
            }
        }

        assert result instanceof Boolean;
        return (boolean) result;
    }
}
