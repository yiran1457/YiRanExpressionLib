package net.yiran.expressionlib.expr.internal;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.yiran.expressionlib.expr.Function;
import net.yiran.expressionlib.expr.Operator;

/**
 * 编译后的表达式 AST。三元 {@link Conditional} 惰性：只递归选中分支；变量查找走原始
 * {@code double[]}（变量名编译期解析为下标），全程无拆装箱、无 hash。
 */
public sealed interface AstNode {
    double evaluate(double[] variables);

    static java.util.Set<String> collectVariables(AstNode node) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        collectVariables(node, names);
        return names;
    }

    private static void collectVariables(AstNode node, java.util.Set<String> names) {
        switch (node) {
            case VariableNode v -> names.add(v.name);
            case BinaryNode b -> { collectVariables(b.left, names); collectVariables(b.right, names); }
            case UnaryNode u -> collectVariables(u.operand, names);
            case FunctionNode f -> { for (AstNode a : f.arguments) collectVariables(a, names); }
            case Conditional c -> {
                collectVariables(c.cond, names);
                collectVariables(c.then, names);
                collectVariables(c.otherwise, names);
            }
            default -> { }
        }
    }

    record NumberNode(double value) implements AstNode {
        @Override
        public double evaluate(double[] variables) {
            return value;
        }
    }

    /**
     * 变量节点。{@code name} 供编译期收集/校验；{@code index} 在 AST 构建后、求值前由
     * {@link #bindIndices(AstNode, Object2IntOpenHashMap)} 按 {@code 名字→下标} 映射一次性解析。
     * 求值时只用 {@code index} 做 {@code variables[index]} 数组访问。
     */
    final class VariableNode implements AstNode {
        final String name;
        int index = -1;

        VariableNode(String name) { this.name = name; }

        public String name() { return name; }

        @Override
        public double evaluate(double[] variables) {
            return variables[index];
        }
    }

    record BinaryNode(Operator operator, AstNode left, AstNode right) implements AstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return operator.applyBinary(a, b);
        }
    }

    record UnaryNode(Operator operator, AstNode operand) implements AstNode {
        @Override
        public double evaluate(double[] variables) {
            return operator.applyUnary(operand.evaluate(variables));
        }
    }

    record FunctionNode(Function function, AstNode[] arguments) implements AstNode {
        @Override
        public double evaluate(double[] variables) {
            // 按参数个数走特化路径，消除 double[] 分配（绝大多数函数为 0-2 参）。
            int n = arguments.length;
            switch (n) {
                case 0: return function.apply0();
                case 1: return function.apply1(arguments[0].evaluate(variables));
                case 2: return function.apply2(arguments[0].evaluate(variables),
                                               arguments[1].evaluate(variables));
                case 3: return function.apply3(arguments[0].evaluate(variables),
                                               arguments[1].evaluate(variables),
                                               arguments[2].evaluate(variables));
                default: {
                    double[] args = new double[n];
                    for (int i = 0; i < n; i++) {
                        args[i] = arguments[i].evaluate(variables);
                    }
                    return function.apply(args);
                }
            }
        }
    }

    /**
     * 三元条件 {@code cond ? then : else}。惰性：只求值选中分支。
     * 条件 {@code 0} 或 {@code NaN} 视为假。
     */
    record Conditional(AstNode cond, AstNode then, AstNode otherwise) implements AstNode {
        @Override
        public double evaluate(double[] variables) {
            double c = cond.evaluate(variables);
            if (c == 0.0 || Double.isNaN(c)) {
                return otherwise.evaluate(variables);
            }
            return then.evaluate(variables);
        }
    }

    /** 构建后遍历 AST，按 {@code 名字→下标} 映射解析所有 {@link VariableNode} 的索引。 */
    static void bindIndices(AstNode node, Object2IntOpenHashMap<String> nameToIndex) {
        switch (node) {
            case VariableNode v -> v.index = nameToIndex.getInt(v.name);
            case BinaryNode b -> { bindIndices(b.left, nameToIndex); bindIndices(b.right, nameToIndex); }
            case UnaryNode u -> bindIndices(u.operand, nameToIndex);
            case FunctionNode f -> { for (AstNode a : f.arguments) bindIndices(a, nameToIndex); }
            case Conditional c -> {
                bindIndices(c.cond, nameToIndex);
                bindIndices(c.then, nameToIndex);
                bindIndices(c.otherwise, nameToIndex);
            }
            default -> { }
        }
    }
}
