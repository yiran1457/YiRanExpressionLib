package net.yiran.expressionlib.expr;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * 内置运算符常量与构造工厂。优先级数值越大绑定越紧：比较 0、加减 1、乘除模 2、一元 3、幂 4（右结合）。
 * 一元负号优先级低于幂，故 {@code -2^2 = -4}（与 exp4j 不同）。
 */
public final class Operators {
    public static final int PRECEDENCE_ADDITION = 1;
    public static final int PRECEDENCE_SUBTRACTION = 1;
    public static final int PRECEDENCE_MULTIPLICATION = 2;
    public static final int PRECEDENCE_DIVISION = 2;
    public static final int PRECEDENCE_MODULO = 2;
    public static final int PRECEDENCE_UNARY_MINUS = 3;
    public static final int PRECEDENCE_UNARY_PLUS = 3;
    public static final int PRECEDENCE_POWER = 4;
    public static final int PRECEDENCE_COMPARISON = 0;

    public static final Operator ADD = binaryLeft("+", PRECEDENCE_ADDITION, Double::sum);
    public static final Operator SUB = binaryLeft("-", PRECEDENCE_SUBTRACTION, (a, b) -> a - b);
    public static final Operator MUL = binaryLeft("*", PRECEDENCE_MULTIPLICATION, (a, b) -> a * b);
    public static final Operator DIV = binaryLeft("/", PRECEDENCE_DIVISION, (a, b) -> a / b);
    public static final Operator MOD = binaryLeft("%", PRECEDENCE_MODULO, (a, b) -> a % b);
    public static final Operator POW = binaryRight("^", PRECEDENCE_POWER, Math::pow);
    public static final Operator NEG = unary("-", PRECEDENCE_UNARY_MINUS, a -> -a);
    public static final Operator POS = unary("+", PRECEDENCE_UNARY_PLUS, a -> a);

    /** 比较运算符：成立返回 1.0，否则 0.0（与三元 {@code 0}/{@code NaN} 为假一致）。 */
    public static final Operator GT = binaryLeft(">", PRECEDENCE_COMPARISON, (a, b) -> a > b ? 1.0 : 0.0);
    public static final Operator LT = binaryLeft("<", PRECEDENCE_COMPARISON, (a, b) -> a < b ? 1.0 : 0.0);
    public static final Operator GE = binaryLeft(">=", PRECEDENCE_COMPARISON, (a, b) -> a >= b ? 1.0 : 0.0);
    public static final Operator LE = binaryLeft("<=", PRECEDENCE_COMPARISON, (a, b) -> a <= b ? 1.0 : 0.0);
    public static final Operator EQ = binaryLeft("==", PRECEDENCE_COMPARISON, (a, b) -> a == b ? 1.0 : 0.0);
    public static final Operator NE = binaryLeft("!=", PRECEDENCE_COMPARISON, (a, b) -> a != b ? 1.0 : 0.0);

    private Operators() {
    }

    public static Operator binaryLeft(String symbol, int precedence, DoubleBinaryOperator op) {
        return new Operator(symbol, precedence, 2, true) {
            @Override public double apply(double... args) { return op.applyAsDouble(args[0], args[1]); }
            @Override public double applyBinary(double a, double b) { return op.applyAsDouble(a, b); }
        };
    }

    public static Operator binaryRight(String symbol, int precedence, DoubleBinaryOperator op) {
        return new Operator(symbol, precedence, 2, false) {
            @Override public double apply(double... args) { return op.applyAsDouble(args[0], args[1]); }
            @Override public double applyBinary(double a, double b) { return op.applyAsDouble(a, b); }
        };
    }

    public static Operator unary(String symbol, int precedence, DoubleUnaryOperator op) {
        return new Operator(symbol, precedence, 1, false) {
            @Override public double apply(double... args) { return op.applyAsDouble(args[0]); }
            @Override public double applyUnary(double a) { return op.applyAsDouble(a); }
        };
    }
}
