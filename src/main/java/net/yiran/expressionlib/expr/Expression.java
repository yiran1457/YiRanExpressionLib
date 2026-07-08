package net.yiran.expressionlib.expr;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.yiran.expressionlib.expr.internal.AstNode;

/**
 * 编译后的可求值表达式。求值走原始 {@code double[]}（变量名编译期解析为下标），全程无拆装箱；
 * 三元 {@code cond ? a : b} 惰性，只递归选中分支。
 */
public final class Expression {
    private final String original;
    private final String[] variableNames;
    private final Object2IntOpenHashMap<String> nameToIndex;
    private final String[] referencedVariables;
    private final double[] values;
    private final AstNode ast;

    Expression(String original, String[] variableNames, String[] referencedVariables,
               Object2IntOpenHashMap<String> nameToIndex, AstNode ast) {
        this.original = original;
        this.variableNames = variableNames;
        this.nameToIndex = nameToIndex;
        this.referencedVariables = referencedVariables;
        this.values = new double[variableNames.length];
        java.util.Arrays.fill(this.values, Double.NaN);
        AstNode.bindIndices(ast, nameToIndex);
        this.ast = ast;
    }

    /** 用已通过 {@link #setVariable} 设定的变量求值，缺失变量抛 {@link IllegalArgumentException}。 */
    public double evaluate() {
        verifyAllPresent();
        return ast.evaluate(values);
    }

    /**
     * 跳过校验的热路径求值，缺失变量以 NaN 体现、不抛异常。适用于调用方已确保变量就绪、
     * 需要最大性能的场景。
     */
    public double evaluateUnchecked() {
        return ast.evaluate(values);
    }

    /** 合并入复制的值数组后求值（不修改实例字段）。 */
    public double evaluate(Map<String, Double> vars) {
        double[] merged = values.clone();
        vars.forEach((k, v) -> {
            int idx = nameToIndex.getInt(k);
            if (idx >= 0) {
                merged[idx] = v.doubleValue();
            }
        });
        verifyAllPresent();
        return ast.evaluate(merged);
    }

    /** 按 {@link #getVariableNames()} 声明顺序传入值，端到端无装箱。 */
    public double evaluate(double... values) {
        if (values.length != variableNames.length) {
            throw new IllegalArgumentException("Expected " + variableNames.length
                    + " values but got " + values.length);
        }
        return ast.evaluate(values);
    }

    public Set<String> getVariableNames() {
        return Set.of(variableNames);
    }

    /** 链式设定变量值。 */
    public Expression setVariable(String name, double value) {
        int idx = nameToIndex.getInt(name);
        if (idx < 0) {
            throw new IllegalArgumentException("Unknown variable: " + name);
        }
        values[idx] = value;
        return this;
    }

    /** 清空通过 {@link #setVariable} 设定的变量（重置为 NaN）。 */
    public Expression clearVariables() {
        java.util.Arrays.fill(values, Double.NaN);
        return this;
    }

    /**
     * 校验表达式。{@code silent=true} 返回结果不抛异常；{@code silent=false} 在首个错误时抛
     * {@link IllegalArgumentException}。未知函数/运算符已在 build 时拒绝。
     */
    public ValidationResult validate(boolean silent) {
        Set<String> missing = new LinkedHashSet<>();
        for (String name : referencedVariables) {
            if (!nameToIndex.containsKey(name)) {
                missing.add(name);
            }
        }
        ValidationResult result = new ValidationResult(missing, Set.of(), Set.of());
        if (!silent && !result.isValid()) {
            throw new IllegalArgumentException("Validation failed: missing variables " + missing);
        }
        return result;
    }

    public ValidationResult validate() {
        return validate(false);
    }

    public String getExpressionString() {
        return original;
    }

    private void verifyAllPresent() {
        for (String name : referencedVariables) {
            if (!nameToIndex.containsKey(name)) {
                throw new IllegalArgumentException("Unknown variable: " + name);
            }
        }
    }
}
