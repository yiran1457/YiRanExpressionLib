package net.yiran.expressionlib.expr.internal;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.yiran.expressionlib.expr.Function;
import net.yiran.expressionlib.expr.Operator;

/**
 * 编译后的表达式 AST。三元 {@link Conditional} 惰性：只递归选中分支；变量查找走原始
 * {@code double[]}（变量名编译期解析为下标），全程无拆装箱、无 hash。
 *
 * <p>二元运算分两类节点：12 个内置运算符走特化节点（{@link AddNode} 等，直接内联算术，
 * 消除 {@code Operator.applyBinary → DoubleBinaryOperator.applyAsDouble} 的多态派发链）；
 * 用户自定义运算符仍走通用 {@link BinaryNode}。两者都实现 {@link BinaryAstNode}，遍历时归一处理。
 */
public sealed interface AstNode {
    double evaluate(double[] variables);

    static java.util.Set<String> collectVariables(AstNode node) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        collectVariables(node, names);
        return names;
    }

    private static void collectVariables(AstNode node, java.util.Set<String> names) {
        if (node instanceof VariableNode) {
            names.add(((VariableNode) node).name);
        } else if (node instanceof BinaryAstNode binary) {
            collectVariables(binary.left(), names);
            collectVariables(binary.right(), names);
        } else if (node instanceof UnaryNode) {
            collectVariables(((UnaryNode) node).operand, names);
        } else if (node instanceof FunctionNode) {
            for (AstNode argument : ((FunctionNode) node).arguments) {
                collectVariables(argument, names);
            }
        } else if (node instanceof Conditional) {
            Conditional conditional = (Conditional) node;
            collectVariables(conditional.cond, names);
            collectVariables(conditional.then, names);
            collectVariables(conditional.otherwise, names);
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

    /**
     * 二元节点公共接口：内置特化节点与用户通用 {@link BinaryNode} 都实现之，
     * 供 {@link #collectVariables}/{@link #bindIndices} 归一遍历左/右子树。
     */
    sealed interface BinaryAstNode extends AstNode permits BinaryNode, AddNode, SubNode, MulNode,
            DivNode, ModNode, PowNode, GtNode, LtNode, GeNode, LeNode, EqNode, NeNode {
        AstNode left();
        AstNode right();
    }

    /** 通用二元节点（用户自定义运算符用）。内置运算符在 {@code build} 时改走特化节点。 */
    record BinaryNode(Operator operator, AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return operator.applyBinary(a, b);
        }
    }

    record AddNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a + b;
        }
    }

    record SubNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a - b;
        }
    }

    record MulNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a * b;
        }
    }

    record DivNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a / b;
        }
    }

    record ModNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a % b;
        }
    }

    record PowNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return Math.pow(a, b);
        }
    }

    record GtNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a > b ? 1.0 : 0.0;
        }
    }

    record LtNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a < b ? 1.0 : 0.0;
        }
    }

    record GeNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a >= b ? 1.0 : 0.0;
        }
    }

    record LeNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a <= b ? 1.0 : 0.0;
        }
    }

    record EqNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a == b ? 1.0 : 0.0;
        }
    }

    record NeNode(AstNode left, AstNode right) implements BinaryAstNode {
        @Override
        public double evaluate(double[] variables) {
            double a = left.evaluate(variables);
            double b = right.evaluate(variables);
            return a != b ? 1.0 : 0.0;
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
        if (node instanceof VariableNode) {
            VariableNode variable = (VariableNode) node;
            variable.index = nameToIndex.getInt(variable.name);
        } else if (node instanceof BinaryAstNode) {
            BinaryAstNode binary = (BinaryAstNode) node;
            bindIndices(binary.left(), nameToIndex);
            bindIndices(binary.right(), nameToIndex);
        } else if (node instanceof UnaryNode) {
            bindIndices(((UnaryNode) node).operand, nameToIndex);
        } else if (node instanceof FunctionNode) {
            for (AstNode argument : ((FunctionNode) node).arguments) {
                bindIndices(argument, nameToIndex);
            }
        } else if (node instanceof Conditional) {
            Conditional conditional = (Conditional) node;
            bindIndices(conditional.cond, nameToIndex);
            bindIndices(conditional.then, nameToIndex);
            bindIndices(conditional.otherwise, nameToIndex);
        }
    }
}
