package net.yiran.expressionlib.expr;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;

import net.yiran.expressionlib.expr.internal.AstBuilder;
import net.yiran.expressionlib.expr.internal.AstNode;
import net.yiran.expressionlib.expr.internal.Tokenizer;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * 流式表达式构建器（仿 exp4j）。用户提供的函数/运算符会覆盖同名内置项。
 * <pre>{@code
 * Expression e = new ExpressionBuilder("3 * sin(x) - 2 * cos(y)")
 *     .variables("x", "y").build();
 * double r = e.evaluate(1.0, 0.5);
 * }</pre>
 */
public final class ExpressionBuilder {
    private final String expression;
    private final Set<String> variableNames = new LinkedHashSet<>();
    private final Map<String, Function> functions = new HashMap<>();
    private final Map<String, Operator> operators = new HashMap<>();
    private final Map<String, Double> constants = new LinkedHashMap<>();

    public ExpressionBuilder(String expression) {
        this.expression = Objects.requireNonNull(expression, "expression");
    }

    public ExpressionBuilder variables(String... names) {
        Collections.addAll(variableNames, names);
        return this;
    }

    public ExpressionBuilder variable(String name) {
        variableNames.add(name);
        return this;
    }

    public ExpressionBuilder functions(Function... fns) {
        for (Function f : fns) {
            functions.put(f.getName(), f);
        }
        return this;
    }

    public ExpressionBuilder function(Function f) {
        functions.put(f.getName(), f);
        return this;
    }

    /** 注册无参函数（等价于 {@link Functions#nullary}）。 */
    public ExpressionBuilder function(String name, DoubleSupplier op) {
        functions.put(name, Functions.nullary(name, op));
        return this;
    }

    /** 注册单参函数（等价于 {@link Functions#unary}）。 */
    public ExpressionBuilder function(String name, DoubleUnaryOperator op) {
        functions.put(name, Functions.unary(name, op));
        return this;
    }

    /** 注册双参函数（等价于 {@link Functions#binary}）。 */
    public ExpressionBuilder function(String name, DoubleBinaryOperator op) {
        functions.put(name, Functions.binary(name, op));
        return this;
    }

    public ExpressionBuilder operator(Operator... ops) {
        for (Operator o : ops) {
            operators.put(o.getSymbol(), o);
        }
        return this;
    }

    /**
     * 注册自定义常量。编译期折叠为字面量，与 {@code pi}/{@code e} 同等待遇：不进变量表、
     * 求值时无需提供、不可被 {@link Expression#setVariable} 覆盖。常量名不得与变量名或函数名冲突。
     */
    public ExpressionBuilder constant(String name, double value) {
        if ("pi".equals(name) || "e".equals(name)) {
            throw new IllegalArgumentException("Constant name '" + name + "' is reserved");
        }
        constants.put(name, value);
        return this;
    }

    /** 批量注册常量。 */
    public ExpressionBuilder constants(Map<String, Double> consts) {
        consts.forEach(this::constant);
        return this;
    }

    /** 编译表达式并返回可求值的 {@link Expression}。 */
    public Expression build() {
        if (expression.isBlank()) {
            throw new IllegalArgumentException("Expression is empty");
        }

        Map<String, Function> mergedFunctions = Functions.builtins();
        mergedFunctions.putAll(functions);
        Map<String, Operator> mergedOperators = builtinOperators();
        mergedOperators.putAll(operators);

        validateUserFunctions(mergedFunctions);
        validateConstants(mergedFunctions);
        validateVariables(mergedFunctions, constants.keySet());

        var tokens = Tokenizer.tokenize(expression, mergedOperators, mergedFunctions.keySet(), constants);
        AstNode ast = AstBuilder.build(tokens, mergedFunctions);

        Set<String> referenced = collectVariables(ast);
        Set<String> ordered = new LinkedHashSet<>(variableNames);
        ordered.addAll(referenced);
        String[] names = ordered.toArray(new String[0]);
        String[] referencedArr = referenced.toArray(new String[0]);

        Object2IntOpenHashMap<String> nameToIndex = new Object2IntOpenHashMap<>(names.length);
        nameToIndex.defaultReturnValue(-1);
        for (int i = 0; i < names.length; i++) {
            nameToIndex.put(names[i], i);
        }

        return new Expression(expression, names, referencedArr, nameToIndex, ast);
    }

    private static Set<String> collectVariables(AstNode node) {
        return AstNode.collectVariables(node);
    }

    private Map<String, Operator> builtinOperators() {
        Map<String, Operator> m = new LinkedHashMap<>();
        m.put("+", Operators.ADD);
        m.put("-", Operators.SUB);
        m.put("*", Operators.MUL);
        m.put("/", Operators.DIV);
        m.put("%", Operators.MOD);
        m.put("^", Operators.POW);
        m.put(">", Operators.GT);
        m.put("<", Operators.LT);
        m.put(">=", Operators.GE);
        m.put("<=", Operators.LE);
        m.put("==", Operators.EQ);
        m.put("!=", Operators.NE);
        return m;
    }

    private void validateUserFunctions(Map<String, Function> merged) {
        for (Function f : functions.values()) {
            if (f.getNumArguments() < 0) {
                throw new IllegalArgumentException("Variadic functions are not supported: " + f.getName());
            }
        }
    }

    private void validateConstants(Map<String, Function> merged) {
        for (String name : constants.keySet()) {
            if (merged.containsKey(name)) {
                throw new IllegalArgumentException("Constant name '" + name + "' conflicts with a function");
            }
        }
    }

    private void validateVariables(Map<String, Function> merged, Set<String> constantNames) {
        for (String name : variableNames) {
            if (name.equals("pi") || name.equals("e")) {
                throw new IllegalArgumentException("Variable name '" + name + "' is reserved");
            }
            if (merged.containsKey(name)) {
                throw new IllegalArgumentException("Variable name '" + name + "' conflicts with a function");
            }
            if (constantNames.contains(name)) {
                throw new IllegalArgumentException("Variable name '" + name + "' conflicts with a constant");
            }
        }
    }
}
