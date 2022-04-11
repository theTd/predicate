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
            for (String s : args) {
                if (concatCmd) {
                    commandBuilder.append(s).append(" ");
                } else {
                    if (s.equals("then")) {
                        concatCmd = true;
                    } else {
                        expressionBuilder.append(s).append(" ");
                    }
                }
            }
            if (commandBuilder.length() == 0) return false;
            commandBuilder.deleteCharAt(commandBuilder.length() - 1);
            String commandToRun = commandBuilder.toString();
            if (expressionBuilder.length() != 0) {
                expressionBuilder.deleteCharAt(expressionBuilder.length() - 1);
                String expr = expressionBuilder.toString();
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
            }
            Bukkit.dispatchCommand(sender, commandToRun);
            return true;
        });
    }

    @Override
    public void onDisable() {
    }
}
