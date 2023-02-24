package com.mineclay.predicate;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

public class EvalTest {

    @Test
    public void run() throws Exception {
        String expression = "arg.call arg1";
        try (GroovyClassLoader cl = new GroovyClassLoader()) {
            CompilationUnit unit = new CompilationUnit(null, new GroovyCodeSource(expression, "test", GroovyShell.DEFAULT_CODE_BASE).getCodeSource(), cl);
            unit.compile(Phases.CLASS_GENERATION);
            SourceUnit su = unit.addSource("test", expression);
            int goalPhase = Phases.CLASS_GENERATION;
            unit.compile(goalPhase);
            ModuleNode ast = su.getAST();
            for (Statement stmt : ast.getStatementBlock().getStatements()) {
                findVariable(((ReturnStatement) stmt).getExpression(), ac -> {
                    System.out.println("ac = " + ac);
                });
            }
        }
        System.out.println("done");
    }

    private void findVariable(Expression exp, Consumer<String> acceptor) {
        if (exp instanceof MethodCallExpression) {
            MethodCallExpression methodCallExpression = (MethodCallExpression) exp;
            findVariable(methodCallExpression.getObjectExpression(), acceptor);
            findVariable(methodCallExpression.getArguments(), acceptor);
        }
        if (exp instanceof VariableExpression) {
            VariableExpression variableExpression = (VariableExpression) exp;
            acceptor.accept(variableExpression.getName());
        }
        if (exp instanceof BinaryExpression) {
            BinaryExpression binaryExpression = (BinaryExpression) exp;
            findVariable(binaryExpression.getLeftExpression(), acceptor);
            findVariable(binaryExpression.getRightExpression(), acceptor);
        }
        if (exp instanceof ArgumentListExpression) {
            ArgumentListExpression argumentListExpression = (ArgumentListExpression) exp;
            for (Expression expression : argumentListExpression.getExpressions()) {
                findVariable(expression, acceptor);
            }
        }
    }
}
