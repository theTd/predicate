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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class ASTHelper {
    public static boolean findVariableUsage(String expression, String variableName) {
        try (GroovyClassLoader cl = new GroovyClassLoader()) {
            CompilationUnit unit = new CompilationUnit(null, new GroovyCodeSource(expression, "dummy", GroovyShell.DEFAULT_CODE_BASE).getCodeSource(), cl);
            unit.compile(Phases.CLASS_GENERATION);
            SourceUnit su = unit.addSource("dummy", expression);
            unit.compile(Phases.CLASS_GENERATION);
            ModuleNode ast = su.getAST();
            AtomicBoolean found = new AtomicBoolean(false);
            for (Statement stmt : ast.getStatementBlock().getStatements()) {
                findVariableUsages(((ReturnStatement) stmt).getExpression(), ac -> {
                    if (ac.equals(variableName)) {
                        found.set(true);
                        return false;
                    }
                    return true;
                });
            }
            return found.get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean findVariableUsages(Expression exp, Function<String, Boolean> acceptor) {
        if (exp instanceof MethodCallExpression) {
            MethodCallExpression methodCallExpression = (MethodCallExpression) exp;
            return findVariableUsages(methodCallExpression.getObjectExpression(), acceptor) &&
                    findVariableUsages(methodCallExpression.getArguments(), acceptor);
        } else if (exp instanceof VariableExpression) {
            VariableExpression variableExpression = (VariableExpression) exp;
            return acceptor.apply(variableExpression.getName());
        } else if (exp instanceof BinaryExpression) {
            BinaryExpression binaryExpression = (BinaryExpression) exp;
            return findVariableUsages(binaryExpression.getLeftExpression(), acceptor) &&
                    findVariableUsages(binaryExpression.getRightExpression(), acceptor);
        } else if (exp instanceof ArgumentListExpression) {
            ArgumentListExpression argumentListExpression = (ArgumentListExpression) exp;
            for (Expression expression : argumentListExpression.getExpressions()) {
                if (!findVariableUsages(expression, acceptor)) return false;
            }
        }
        return true;
    }
}
