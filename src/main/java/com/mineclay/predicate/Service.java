package com.mineclay.predicate;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.SneakyThrows;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import org.bukkit.entity.Player;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

class Service {
    private final Logger logger;
    private final Binding binding;

    Service(Logger logger, Binding binding) {
        this.logger = logger;
        this.binding = binding;
    }

    public synchronized void rebuild() {
        if (this.dirty) {
            scriptCache.invalidateAll();
            logger.log(Level.INFO, "rebuilding runtime");
            DynamicType.Builder<ScriptBase> b = new ByteBuddy().subclass(ScriptBase.class).name("com.mineclay.predicate.RuntimeScriptBase");
            for (PredicateMethodInstance methodInstance : methods.values()) {
                b = b.defineMethod(methodInstance.method.getName(), methodInstance.method.getReturnType(), methodInstance.method.getModifiers())
                        .withParameters(methodInstance.method.getParameterTypes())
                        .intercept(MethodDelegation.to(methodInstance.base));
            }
            PropertyExtractor propertyExtractor = new PropertyExtractor(bases);
            b = b.defineMethod("findProperty", Object.class, Modifier.PUBLIC)
                    .withParameters(String.class)
                    .intercept(MethodDelegation.to(propertyExtractor));

            ByteArrayClassLoader cl = new ByteArrayClassLoader(getClass().getClassLoader(), new LinkedHashMap<>());
            Class<? extends ScriptBase> loaded = b.make().load(cl, ClassLoadingStrategy.Default.INJECTION).getLoaded();

            CompilerConfiguration cfg = new CompilerConfiguration();

            cfg.setScriptBaseClass(loaded.getName());
            this.runtime = new GroovyShell(cl, binding, cfg);
            this.dirty = false;
        }
    }

    private final Map<String, PredicateMethodInstance> methods = new HashMap<>();
    private final List<PredicateMethodBase> bases = new ArrayList<>();

    boolean dirty = true;

    @SneakyThrows
    public void addMethod(Class<? extends PredicateMethodBase> methodSource) {
        PredicateMethodBase base = methodSource.getConstructor().newInstance();
        bases.add(base);
        for (Method method : methodSource.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                PredicateMethodInstance ctx = new PredicateMethodInstance();
                ctx.name = method.getName();
                ctx.source = methodSource;
                ctx.method = method;
                ctx.base = base;
                String signature = getMethodSignature(method);
                PredicateMethodInstance replace = methods.put(signature, ctx);
                if (replace != null) {
                    logger.log(Level.WARNING, "redefining method " + signature);
                } else {
                    logger.log(Level.INFO, "adding method " + signature);
                }
                dirty = true;
            }
        }
    }

    private static String getMethodSignature(Method method) {
        StringJoiner sj = new StringJoiner(",", method.getName() + "(", ")");
        for (Class<?> parameterType : method.getParameterTypes()) {
            sj.add(parameterType.getTypeName());
        }
        return sj.toString();
    }

    GroovyShell runtime;

    final Cache<String, ScriptBase> scriptCache = CacheBuilder.newBuilder().maximumSize(100).build();

    final ExecutorService compileThread = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("predicate-compile-thread").build());

    public <T> CompletableFuture<Supplier<T>> compile(Player player, String line, Class<T> returnType) {
        return CompletableFuture.supplyAsync(() -> {
            rebuild();
            try {
                return scriptCache.get(line, () -> (ScriptBase) runtime.parse(line));
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, compileThread).thenApply((base) -> () -> execute(base, player, returnType));
    }

    private <T> T execute(ScriptBase base, Player player, Class<T> returnType) {
        base.setPlayer(player);

        Object result;
        try {
            for (PredicateMethodInstance m : methods.values()) {
                m.base.playerThreadLocal.set(player);
            }

            result = base.run();
        } finally {
            for (PredicateMethodInstance m : methods.values()) {
                m.base.playerThreadLocal.set(null);
            }
            base.setPlayer(null);
        }

        return result == null ? null : returnType.cast(result);
    }
}
