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
        requireExpression();

        Map<String, Function> allFunctions = mergeFunctions();
        Map<String, Operator> allOperators = mergeOperators();
        validateDefinitions(allFunctions);

        AstNode ast = compile(allFunctions, allOperators);
        Set<String> referencedVariables = AstNode.collectVariables(ast);
        String[] names = buildVariableNames(referencedVariables);
        Object2IntOpenHashMap<String> nameToIndex = indexVariables(names);

        return new Expression(
                expression,
                names,
                referencedVariables.toArray(new String[0]),
                nameToIndex,
                ast);
    }

    private void requireExpression() {
        if (expression.isBlank()) {
            throw new IllegalArgumentException("Expression is empty");
        }
    }

    private Map<String, Function> mergeFunctions() {
        Map<String, Function> allFunctions = Functions.builtins();
        allFunctions.putAll(functions);
        return allFunctions;
    }

    private Map<String, Operator> mergeOperators() {
        Map<String, Operator> allOperators = new LinkedHashMap<>();
        allOperators.put("+", Operators.ADD);
        allOperators.put("-", Operators.SUB);
        allOperators.put("*", Operators.MUL);
        allOperators.put("/", Operators.DIV);
        allOperators.put("%", Operators.MOD);
        allOperators.put("^", Operators.POW);
        allOperators.put(">", Operators.GT);
        allOperators.put("<", Operators.LT);
        allOperators.put(">=", Operators.GE);
        allOperators.put("<=", Operators.LE);
        allOperators.put("==", Operators.EQ);
        allOperators.put("!=", Operators.NE);
        allOperators.putAll(operators);
        return allOperators;
    }

    private AstNode compile(Map<String, Function> allFunctions,
                            Map<String, Operator> allOperators) {
        var tokens = Tokenizer.tokenize(
                expression,
                allOperators,
                allFunctions.keySet(),
                constants);
        return AstBuilder.build(tokens, allFunctions);
    }

    private String[] buildVariableNames(Set<String> referencedVariables) {
        Set<String> ordered = new LinkedHashSet<>(variableNames);
        ordered.addAll(referencedVariables);
        return ordered.toArray(new String[0]);
    }

    private static Object2IntOpenHashMap<String> indexVariables(String[] names) {
        Object2IntOpenHashMap<String> nameToIndex = new Object2IntOpenHashMap<>(names.length);
        nameToIndex.defaultReturnValue(-1);
        for (int i = 0; i < names.length; i++) {
            nameToIndex.put(names[i], i);
        }
        return nameToIndex;
    }

    private void validateDefinitions(Map<String, Function> allFunctions) {
        validateUserFunctions();
        validateConstants(allFunctions);
        validateVariables(allFunctions, constants.keySet());
    }

    private void validateUserFunctions() {
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
