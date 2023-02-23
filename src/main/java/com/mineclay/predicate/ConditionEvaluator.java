package com.mineclay.predicate;

import groovy.lang.GroovyShell;

import java.util.Map;

public class ConditionEvaluator {
    final String expression;

    public ConditionEvaluator(String expression) {
        this.expression = expression;
    }

    ScriptBase script;

    public void compile(GroovyShell runtime) {
        script = (ScriptBase) runtime.parse(expression);

        script.setPropertyInterceptor(name -> {
            Map<String, Object> binding = arg.get();
            if (binding == null) return null;
            if (binding.containsKey(name)) {
                return binding.get(name);
            }
            return null;
        });
    }

    final ThreadLocal<Map<String, Object>> arg = new ThreadLocal<>();

    public Object evaluate(Map<String, Object> binding) {
        arg.set(binding);
        try {
            return script.run();
        } finally {
            arg.remove();
        }
    }
}
