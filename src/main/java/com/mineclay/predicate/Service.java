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
import org.bukkit.plugin.java.JavaPlugin;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
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
            Set<String> acknowledged = new HashSet<>();
            for (PredicateMethodInstance methodInstance : methods.values()) {
                if (acknowledged.add(getMethodSignature(methodInstance.method))) {
                    b = b.defineMethod(methodInstance.method.getName(), methodInstance.method.getReturnType(), methodInstance.method.getModifiers())
                            .withParameters(methodInstance.method.getParameterTypes())
                            .intercept(MethodDelegation.to(methodInstance.base));
                } else {
                    logger.log(Level.WARNING, "duplicate method: " + getMethodSignature(methodInstance.method));
                }
            }
            for (PredefinedCondition predef : predefinedConditions.values()) {
                if (acknowledged.add(predef.name + "()")) {
                    b = b.defineMethod(predef.name, Object.class, Modifier.PUBLIC)
                            .intercept(MethodDelegation.to(predef));
                } else {
                    logger.log(Level.WARNING, "duplicate method: " + predef.name + "()");
                }
                if (acknowledged.add(predef.name + "(" + Object.class.getTypeName() + ")")) {
                    b = b.defineMethod(predef.name, Object.class, Modifier.PUBLIC)
                            .withParameters(Object.class)
                            .intercept(MethodDelegation.to(predef));
                } else {
                    logger.log(Level.WARNING, "duplicate method: " + predef.name + "(" + Object.class.getTypeName() + ")");
                }
            }
            this.propertyExtractor = new PropertyExtractor(bases);
            b = b.defineMethod("findProperty", Object.class, Modifier.PUBLIC)
                    .withParameters(String.class)
                    .intercept(MethodDelegation.to(propertyExtractor));

            ByteArrayClassLoader cl = new ByteArrayClassLoader(getClass().getClassLoader(), new LinkedHashMap<>());
            Class<? extends ScriptBase> loaded = b.make().load(cl, ClassLoadingStrategy.Default.INJECTION).getLoaded();

            CompilerConfiguration cfg = new CompilerConfiguration();

            cfg.setScriptBaseClass(loaded.getName());
            this.runtime = new GroovyShell(cl, binding, cfg);
            for (PredefinedCondition predef : predefinedConditions.values()) {
                predef.compile(runtime);
            }
            for (PredefinedCommand predef : predefinedCommands) {
                predef.compile(runtime);
            }

            this.dirty = false;
        }
    }

    private final Map<String, PredicateMethodInstance> methods = new HashMap<>();
    final Map<String, PredefinedCondition> predefinedConditions = new HashMap<>();
    private final List<PredicateMethodBase> bases = new ArrayList<>();

    boolean dirty = true;

    public void removeMethods(Predicate<PredicateMethodInstance> filter) {
        dirty = methods.values().removeIf(filter);
    }

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
                ctx.providingPlugin = JavaPlugin.getProvidingPlugin(methodSource);
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

    public void addPredefinedCondition(PredefinedCondition predef) {
        if (predefinedConditions.put(predef.name, predef) != null) {
            logger.log(Level.WARNING, "redefining predefined condition " + predef.name);
        }
        dirty = true;
    }

    public void clearPredefinedConditions() {
        predefinedConditions.clear();
        dirty = true;
    }

    final List<PredefinedCommand> predefinedCommands = new ArrayList<>();

    public void addPredefinedCommands(PredefinedCommand predef) {
        predefinedCommands.add(predef);
        if (runtime != null) {
            predef.compile(runtime);
        }
    }

    public void clearPredefinedCommands() {
        predefinedCommands.clear();
    }

    private static String getMethodSignature(Method method) {
        StringJoiner sj = new StringJoiner(",", method.getName() + "(", ")");
        for (Class<?> parameterType : method.getParameterTypes()) {
            sj.add(parameterType.getTypeName());
        }
        return sj.toString();
    }

    GroovyShell runtime;
    PropertyExtractor propertyExtractor;

    final Cache<String, ScriptBase> scriptCache = CacheBuilder.newBuilder().maximumSize(100).build();

    final ExecutorService compileThread = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("predicate-compile-thread").build());

    public <T> CompletableFuture<Supplier<T>> compile(Player player, String line, Class<T> returnType) {
        return compile(player, line, returnType, null);
    }

    @SneakyThrows
    public ScriptBase compile(String line) {
        rebuild();
        return scriptCache.get(line, () -> {
            ScriptBase compile = (ScriptBase) runtime.parse(line);
            compile.setPropertyExtractor(propertyExtractor);
            return compile;
        });
    }

    public <T> CompletableFuture<Supplier<T>> compile(Player player, String line, Class<T> returnType, PropertyInterceptor interceptor) {
        return CompletableFuture.supplyAsync(() -> compile(line), compileThread).thenApply((base) -> () -> execute(base, player, returnType, interceptor));
    }

    private <T> T execute(ScriptBase base, Player player, Class<T> returnType, PropertyInterceptor interceptor) {
        base.getPropertyExtractor().getPropertyInterceptor().set(interceptor);
        PredicateMethodBase.PLAYER_THREAD_LOCAL.set(player);

        Object result;
        try {
            result = base.run();
        } finally {
            PredicateMethodBase.PLAYER_THREAD_LOCAL.set(null);
            base.getPropertyExtractor().getPropertyInterceptor().set(null);
        }

        return result == null ? null : returnType.cast(result);
    }
}
