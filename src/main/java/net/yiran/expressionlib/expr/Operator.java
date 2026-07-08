package net.yiran.expressionlib.expr;

/**
 * 运算符。子类化并重写 {@link #apply(double...)}；一元/二元运算符另可重写
 * {@link #applyUnary} / {@link #applyBinary} 走热路径特化以消除数组分配。
 */
public abstract class Operator {
    private final String symbol;
    private final int precedence;
    private final int numOperands;
    private final boolean leftAssociative;

    protected Operator(String symbol, int precedence, int numOperands, boolean leftAssociative) {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Operator symbol must be non-empty");
        }
        if (numOperands != 1 && numOperands != 2) {
            throw new IllegalArgumentException("Operator numOperands must be 1 or 2, got " + numOperands);
        }
        this.symbol = symbol;
        this.precedence = precedence;
        this.numOperands = numOperands;
        this.leftAssociative = leftAssociative;
    }

    public final String getSymbol() {
        return symbol;
    }

    public final int getPrecedence() {
        return precedence;
    }

    public final int getNumOperands() {
        return numOperands;
    }

    public final boolean isLeftAssociative() {
        return leftAssociative;
    }

    /** 通用入口。热路径优先用 {@link #applyUnary}/{@link #applyBinary}。 */
    public abstract double apply(double... args);

    /** 一元热路径，默认回退 {@link #apply(double...)}；子类可重写以消除数组分配。 */
    public double applyUnary(double a) {
        return apply(a);
    }

    /** 二元热路径，默认回退 {@link #apply(double...)}；子类可重写以消除数组分配。 */
    public double applyBinary(double a, double b) {
        return apply(a, b);
    }
}
