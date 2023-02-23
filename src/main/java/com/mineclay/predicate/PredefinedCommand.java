package com.mineclay.predicate;

import groovy.lang.GroovyShell;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

public class PredefinedCommand {
    final String label;
    final String condition;
    final String xargs;
    final List<String> args;
    final String delim;
    final boolean async;
    final String help;

    final ConditionEvaluator evaluator;

    public PredefinedCommand(String label, String condition, String xargs, List<String> args, String delim, boolean async, String help) {
        this.label = label;
        this.condition = condition;
        this.xargs = xargs;
        this.args = args;
        this.delim = delim;
        this.async = async;
        this.help = help;

        if (this.condition != null) {
            evaluator = new ConditionEvaluator(condition);
        } else {
            evaluator = null;
        }
    }

    static PredefinedCommand parse(String label, ConfigurationSection section) {
        String condition = section.getString("condition");
        String xargs = section.getString("xargs");
        List<String> args = section.getStringList("args");
        if (args == null) args = new ArrayList<>();
        String delim = section.getString("delim", "then");
        boolean async = section.getBoolean("async", false);
        String help = section.getString("help");
        return new PredefinedCommand(label, condition, xargs, args, delim, async, help);
    }

    public void compile(GroovyShell runtime) {
        if (evaluator != null) {
            evaluator.compile(runtime);
        }
    }

    static class ConditionReference {
        final PredefinedCondition condition;
        String arg;

        ConditionReference(PredefinedCondition condition) {
            this.condition = condition;
        }
    }

    public void registerCommand(PluginCommand command) {
        command.setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                Map<String, String> argMap = new LinkedHashMap<>();
                StringBuilder payloadBuilder = null;
                List<ConditionReference> conditions = new ArrayList<>();
                ConditionReference currentCondition = null;
                Boolean asyncExplicit = null;
                boolean payload = false;
                for (int i = 0; i < args.length; i++) {
                    // extract declared args
                    if (i < PredefinedCommand.this.args.size()) {
                        argMap.put(PredefinedCommand.this.args.get(i), args[i]);
                        continue;
                    }

                    String content = args[i];

                    // append payload
                    if (payload) {
                        if (payloadBuilder == null) {
                            payloadBuilder = new StringBuilder(content);
                        } else {
                            payloadBuilder.append(" ").append(content);
                        }
                        continue;
                    }

                    // begin of payload
                    if (content.equals(delim)) {
                        payload = true;
                        continue;
                    }

                    // set condition arg
                    if (currentCondition != null) {
                        if (currentCondition.condition != null) {
                            currentCondition.arg = content;
                            currentCondition = null;
                        } else {
                            // if condition is null, it's not a predefined condition
                            if (currentCondition.arg == null) currentCondition.arg = content;
                            else currentCondition.arg += " " + content;
                        }
                        continue;
                    }

                    // start condition
                    if (content.equalsIgnoreCase("predicate")) {
                        currentCondition = new ConditionReference(null);
                        conditions.add(currentCondition);
                        asyncExplicit = false;
                        continue;
                    }
                    if (content.equalsIgnoreCase("predicateAsync")) {
                        currentCondition = new ConditionReference(null);
                        conditions.add(currentCondition);
                        asyncExplicit = true;
                        continue;
                    }
                    PredefinedCondition condition = PredicatePlugin.inst().getPredefinedCondition(content);
                    if (condition != null) {
                        currentCondition = new ConditionReference(condition);
                        conditions.add(currentCondition);
                        if (!condition.hasArg) currentCondition = null;
                        continue;
                    }
                    sender.sendMessage("unknown condition: " + content);
                    if (help != null) {
                        sender.sendMessage(help);
                    }
                    return true;
                }

                boolean async = asyncExplicit == null ? PredefinedCommand.this.async : asyncExplicit;

                String exp = buildCommand(async, argMap, conditions);
                if (payloadBuilder == null) {
                    if (help != null) {
                        sender.sendMessage(help);
                    } else {
                        sender.sendMessage("empty payload");
                    }
                    return true;
                }
                String redirect;
                if (xargs != null) {
                    redirect = xargs + " " + exp;
                } else {
                    redirect = exp;
                }
                redirect += payloadBuilder.toString();

                String finalRedirect = redirect;
                Runnable dispatch = () -> {
                    PredicatePlugin.inst().getLogger().log(Level.INFO, "dispatching command /" + finalRedirect);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalRedirect);
                };

                Supplier<Boolean> cond;
                if (evaluator != null) {
                    //noinspection unchecked
                    cond = () -> (boolean) evaluator.evaluate((Map<String, Object>) ((Map<?, ?>) argMap));
                } else {
                    cond = () -> true;
                }
                if (async) {
                    PredicatePlugin.inst().getServer().getScheduler().runTaskAsynchronously(PredicatePlugin.inst(), () -> {
                        if (cond.get()) {
                            Bukkit.getScheduler().runTask(PredicatePlugin.inst(), dispatch);
                        }
                    });
                } else {
                    if (cond.get()) {
                        dispatch.run();
                    }
                }
                return true;
            }

            String buildCommand(boolean async, Map<String, String> argMap, List<ConditionReference> conditionReferences) {
                StringBuilder builder = new StringBuilder(async ? "predicate:predicateAsync" : "predicate:predicate");
                if (args.contains("player")) {
                    builder.append(argMap.get("player "));
                } else {
                    builder.append(" server ");
                }
                boolean and = false;
                for (ConditionReference ref : conditionReferences) {
                    if (and) builder.append("&& ");
                    else and = true;

                    if (ref.condition == null) {
                        // wild conditions (predicate or predicateAsync)
                        builder.append(ref.arg).append(" ");
                    } else {
                        if (ref.condition.hasArg) {
                            builder.append(ref.condition.name).append("(\"").append(ref.arg).append("\") ");
                        } else {
                            builder.append(ref.condition.name).append("() ");
                        }
                    }
                }
                builder.append("then ");
                return builder.toString();
            }
        });

        command.setTabCompleter((sender, cmd, label, args) -> {
            boolean payload = false;
            StringBuilder payloadBuilder = null;
            ConditionReference currentCondition = null;

            Supplier<List<String>> completor = null;

            PayloadCompletor payloadCompletor = new PayloadCompletor();

            for (int i = 0; i < args.length; i++) {
                // extract declared args
                if (i < PredefinedCommand.this.args.size()) {
                    completor = null;
                    continue;
                }

                String content = args[i];

                // append payload
                if (payload) {
                    if (payloadBuilder == null) {
                        payloadBuilder = new StringBuilder(content);
                        payloadCompletor.payloadBuilder = payloadBuilder;
                    } else {
                        payloadBuilder.append(" ").append(content);
                    }
                    completor = payloadCompletor;
                    continue;
                }

                // begin of payload
                if (content.equals(delim)) {
                    payload = true;
                    completor = payloadCompletor;
                    continue;
                }

                // set condition arg
                if (currentCondition != null) {
                    if (currentCondition.condition != null) {
                        currentCondition.arg = content;
                        currentCondition = null;
                    } else {
                        // if condition is null, it's not a predefined condition
                        if (currentCondition.arg == null) currentCondition.arg = content;
                        else currentCondition.arg += " " + content;
                    }
                    completor = null;
                    continue;
                }

                // start condition
                completor = () -> {
                    List<String> list = new ArrayList<>();
                    list.add("predicate");
                    list.add("predicateAsync");
                    list.add(delim);
                    PredicatePlugin.inst().getPredefinedConditions().stream().map(c -> c.name).forEach(list::add);
                    return list;
                };

                PredefinedCondition condition = PredicatePlugin.inst().getPredefinedCondition(content);
                if (condition != null) {
                    currentCondition = new ConditionReference(condition);
                    if (!condition.hasArg) currentCondition = null;
                }
            }
            if (completor == null) return null;
            return completor.get();
        });
    }

    private static class PayloadCompletor implements Supplier<List<String>> {

        StringBuilder payloadBuilder = null;

        final SimpleCommandMap commandMap;

        {
            try {
                commandMap = (SimpleCommandMap) Bukkit.getServer().getClass().getDeclaredMethod("getCommandMap").invoke(Bukkit.getServer());
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<String> get() {
            return commandMap.tabComplete(Bukkit.getConsoleSender(), payloadBuilder.toString());
        }
    }
}
