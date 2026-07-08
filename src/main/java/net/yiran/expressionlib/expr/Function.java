package net.yiran.expressionlib.expr;

/**
 * 自定义函数。子类化并重写 {@link #apply(double...)}；0-3 参函数另可重写
 * {@link #apply0}/{@link #apply1}/{@link #apply2}/{@link #apply3} 走热路径特化以消除数组分配。
 * 不支持变参（{@code numArguments < 0} 时 build 抛异常）。
 */
public abstract class Function {
    private final String name;
    private final int numArguments;

    protected Function(String name, int numArguments) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Function name must be non-empty");
        }
        this.name = name;
        this.numArguments = numArguments;
    }

    public final String getName() {
        return name;
    }

    public final int getNumArguments() {
        return numArguments;
    }

    /** 通用入口。热路径优先用 {@link #apply0}/{@link #apply1}/{@link #apply2}。 */
    public abstract double apply(double... args);

    /** 0 参热路径，默认回退 {@link #apply(double...)}；子类可重写以消除数组分配。 */
    public double apply0() {
        return apply();
    }

    /** 单参热路径，默认回退 {@link #apply(double...)}；子类可重写以消除数组分配。 */
    public double apply1(double a) {
        return apply(a);
    }

    /** 双参热路径，默认回退 {@link #apply(double...)}；子类可重写以消除数组分配。 */
    public double apply2(double a, double b) {
        return apply(a, b);
    }

    /** 3 参热路径，默认回退 {@link #apply(double...)}；子类可重写以消除数组分配。 */
    public double apply3(double a, double b, double c) {
        return apply(a, b, c);
    }
}
