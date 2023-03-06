package com.mineclay.predicate;

import com.mineclay.config.CircleConfig;
import groovy.lang.Binding;
import groovy.lang.GString;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class PredicatePlugin extends JavaPlugin implements Listener {

    private static PredicatePlugin inst;
    private final Binding binding = new Binding();
    final Service service = new Service(getLogger(), binding);

    {
        inst = this;
    }

    public static PredicatePlugin inst() {
        return inst;
    }

    @Override
    public void onEnable() {
        binding.setVariable("server", getServer());

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, service::rebuild, 20, 20);

        CommandExecutor cmd = (sender, command, label, args) -> {
            if (args.length < 1) return false;
            String target = args[0];
            Player targetPlayer;
            if (target.equalsIgnoreCase("server")) {
                targetPlayer = null;
            } else {
                targetPlayer = Bukkit.getPlayerExact(target);
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "player not found");
                    return true;
                }
            }

            boolean concatCmd = false;
            StringBuilder expressionBuilder = new StringBuilder();
            StringBuilder commandBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                String s = args[i];
                if (s.equals("then")) {
                    concatCmd = true;
                } else {
                    (concatCmd ? commandBuilder : expressionBuilder).append(s).append(" ");
                }
            }
            if (commandBuilder.length() == 0) return false;
            commandBuilder.deleteCharAt(commandBuilder.length() - 1);
            String commandToRun = commandBuilder.toString();
            if (expressionBuilder.length() != 0) {
                expressionBuilder.deleteCharAt(expressionBuilder.length() - 1);
                String expr = expressionBuilder.toString();
                boolean async = label.toLowerCase().contains("async");
                boolean template = commandToRun.contains("$");
                CompletableFuture<Supplier<Boolean>> screen = service.compile(targetPlayer, expr, Boolean.class);
                CompletableFuture<Supplier<String>> cmdFuture;
                if (template) {
                    cmdFuture = screen.thenCompose(sBool -> service.compile(targetPlayer, "\"\"\"" + commandToRun + "\"\"\"", GString.class)
                            .thenApply(sCmd -> () -> {
                                if (sBool.get()) {
                                    return sCmd.get().toString();
                                } else {
                                    return null;
                                }
                            }));
                } else {
                    cmdFuture = screen.thenApply(sBool -> () -> {
                        if (sBool.get()) {
                            return commandToRun;
                        } else {
                            return null;
                        }
                    });
                }

                cmdFuture.thenAccept(sCmd -> {
                    Runnable r = () -> {
                        String finalCmd = sCmd.get();
                        if (finalCmd != null) {
                            if (sender instanceof Player) {
                                sender.sendMessage(ChatColor.GREEN + "test passed");
                            }
                            if (async) {
                                Bukkit.getScheduler().runTask(PredicatePlugin.inst(), () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
                            } else {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                            }
                        } else {
                            if (sender instanceof Player) {
                                sender.sendMessage(ChatColor.GRAY + "test failed");
                            }
                        }
                    };
                    if (async) {
                        Bukkit.getScheduler().runTaskAsynchronously(this, r);
                    } else {
                        Bukkit.getScheduler().runTask(this, r);
                    }
                }).exceptionally((ex) -> {
                    sender.sendMessage(ChatColor.RED + "" + ex.getCause());
                    return null;
                });
                return true;
            }
            Bukkit.dispatchCommand(sender, commandToRun);
            return true;
        };
        Objects.requireNonNull(getCommand("predicate")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("predicateAsync")).setExecutor(cmd);

        addMethod(BuiltinMethods.class);

        Bukkit.getPluginManager().registerEvents(this, this);

        if (getConfig().isConfigurationSection("commands")) {
            loadPredefinedCommands(getConfig().getConfigurationSection("commands"));
        }
        if (getConfig().isConfigurationSection("conditions")) {
            loadPredefinedConditions(getConfig().getConfigurationSection("conditions"));
        }

        if (getServer().getPluginManager().isPluginEnabled("CircleLink-bukkit")) {
            addMethod(CircleLinkMethods.class);
            CircleConfig.subscribe("com.mineclay.predicate", s -> Bukkit.getScheduler().runTask(this, () -> {
                for (PluginCommand loadedCommand : loadedCommands) {
                    loadedCommand.setExecutor(null);
                    loadedCommand.setTabCompleter(null);
                }
                loadedCommands.clear();
                service.clearPredefinedConditions();

                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new StringReader(s));

                if (cfg.isConfigurationSection("commands")) {
                    loadPredefinedCommands(cfg.getConfigurationSection("commands"));
                }
                if (getConfig().isConfigurationSection("conditions")) {
                    loadPredefinedConditions(cfg.getConfigurationSection("conditions"));
                }
            }), getDataFolder()).addInitialConfig("#empty config");
        }
    }

    List<PluginCommand> loadedCommands = new ArrayList<>();

    private void loadPredefinedCommands(ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            PluginCommand cmd = getOrCreateCommand(key);
            PredefinedCommand def = PredefinedCommand.parse(key, section.getConfigurationSection(key));
            def.registerCommand(cmd);
            loadedCommands.add(cmd);
            service.addPredefinedCommands(def);
        }
    }

    private void loadPredefinedConditions(ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            service.addPredefinedCondition(new PredefinedCondition(key, section.getString(key)));
        }
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(((Listener) this));
    }

    public void addMethod(Class<? extends PredicateMethodBase> methodSource) {
        service.addMethod(methodSource);
    }

    @SneakyThrows
    public boolean test(Player player, String line) {
        return service.compile(player, line, Boolean.class).get().get();
    }

    @SneakyThrows
    public String template(Player player, String template) {
        return service.compile(player, "\"\"\"" + template + "\"\"\"", GString.class).get().get().toString();
    }

    @EventHandler
    void autoRemoveMethod(PluginDisableEvent e) {
        service.removeMethods(m -> m.providingPlugin == e.getPlugin());
    }

    @SneakyThrows
    PluginCommand getOrCreateCommand(String command) {
        PluginCommand cmd = getCommand(command);
        if (cmd == null) {
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            cmd = constructor.newInstance(command, this);
            SimpleCommandMap cmdMap = (SimpleCommandMap) getServer().getClass().getDeclaredMethod("getCommandMap").invoke(getServer());
            cmdMap.register(getName(), cmd);
            try {
                getServer().getClass().getDeclaredMethod("syncCommands").invoke(getServer());
            } catch (NoSuchMethodException ignored) {
            }
        }
        return cmd;
    }

    public List<PredefinedCondition> getPredefinedConditions() {
        return new ArrayList<>(service.predefinedConditions.values());
    }

    public PredefinedCondition getPredefinedCondition(String name) {
        return service.predefinedConditions.get(name);
    }
}
