package com.mineclay.predicate;

import groovy.lang.Binding;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class PredicatePlugin extends JavaPlugin {

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
        service.addMethod(NameMethod.class);

        Bukkit.getScheduler().runTaskTimer(this, service::rebuild, 20, 20);

        Objects.requireNonNull(getCommand("predicate")).setExecutor((sender, command, label, args) -> {
            if (args.length < 1) return false;
            String target = args[0];
            Player targetPlayer = Bukkit.getPlayerExact(target);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "player not found");
                return true;
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
                try {
                    if (service.test(targetPlayer, expr)) {
                        if (sender instanceof Player) {
                            sender.sendMessage(ChatColor.GREEN + "test passed");
                        }
                    } else {
                        if (sender instanceof Player) {
                            sender.sendMessage(ChatColor.GRAY + "test failed");
                        }
                        return true;
                    }
                } catch (Throwable e) {
                    sender.sendMessage(ChatColor.RED + e.toString());
                    return true;
                }
            }
            Bukkit.dispatchCommand(sender, commandToRun);
            return true;
        });

        addMethod(BuiltinMethods.class);
        if (getServer().getPluginManager().isPluginEnabled("CircleLink-bukkit")) {
            addMethod(CircleLinkMethods.class);
        }
    }

    @Override
    public void onDisable() {
    }

    public void addMethod(Class<? extends PredicateMethodBase> methodSource) {
        service.addMethod(methodSource);
    }

    public boolean test(Player player, String line) {
        return service.test(player, line);
    }
}
