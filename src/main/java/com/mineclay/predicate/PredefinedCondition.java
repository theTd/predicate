package com.mineclay.predicate;

import groovy.lang.GroovyShell;

import java.util.Collections;

public class PredefinedCondition {
    final String name;
    final String expression;
    boolean hasArg = false;

    final ConditionEvaluator evaluator;

    public PredefinedCondition(String name, String expression) {
        this.name = name;
        this.expression = expression;
        evaluator = new ConditionEvaluator(expression);
    }

    public void compile(GroovyShell runtime) {
        evaluator.compile(runtime);
        hasArg = ASTHelper.findVariableUsage(expression, "arg");
    }

    @SuppressWarnings("unused") // method delegation target
    public Object evaluate(Object arg) {
        return evaluator.evaluate(Collections.singletonMap("arg", arg));
    }

    @SuppressWarnings("unused") // method delegation target
    public Object evaluate() {
        return evaluator.evaluate(null);
    }
}
