package com.mineclay.predicate;

import groovy.lang.Binding;
import groovy.lang.GString;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class PredicatePlugin extends JavaPlugin implements Listener {

    private static PredicatePlugin inst;
    private final Binding binding = new Binding();
    private final Service service = new Service(getLogger(), binding);

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
//
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
        if (getServer().getPluginManager().isPluginEnabled("CircleLink-bukkit")) {
            addMethod(CircleLinkMethods.class);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
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
}
