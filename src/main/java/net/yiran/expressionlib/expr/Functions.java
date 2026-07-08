package net.yiran.expressionlib.expr;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;

/**
 * 内置函数目录与构造工厂。内置：{@code sin cos tan asin acos atan atan2 sinh cosh tanh
 * log(=ln) log10 log2 exp sqrt cbrt abs ceil floor round signum max min pow hypot random}。
 */
public final class Functions {
    private Functions() {
    }

    static Map<String, Function> builtins() {
        Map<String, Function> m = new HashMap<>();
        m.put("sin", unary("sin", Math::sin));
        m.put("cos", unary("cos", Math::cos));
        m.put("tan", unary("tan", Math::tan));
        m.put("asin", unary("asin", Math::asin));
        m.put("acos", unary("acos", Math::acos));
        m.put("atan", unary("atan", Math::atan));
        m.put("atan2", binary("atan2", Math::atan2));
        m.put("sinh", unary("sinh", Math::sinh));
        m.put("cosh", unary("cosh", Math::cosh));
        m.put("tanh", unary("tanh", Math::tanh));
        m.put("log", unary("log", Math::log));
        m.put("log10", unary("log10", Math::log10));
        m.put("log2", unary("log2", x -> Math.log(x) / Math.log(2)));
        m.put("exp", unary("exp", Math::exp));
        m.put("sqrt", unary("sqrt", Math::sqrt));
        m.put("cbrt", unary("cbrt", Math::cbrt));
        m.put("abs", unary("abs", Math::abs));
        m.put("ceil", unary("ceil", Math::ceil));
        m.put("floor", unary("floor", Math::floor));
        m.put("round", unary("round", x -> (double) Math.round(x)));
        m.put("signum", unary("signum", Math::signum));
        m.put("max", binary("max", Math::max));
        m.put("min", binary("min", Math::min));
        m.put("pow", binary("pow", Math::pow));
        m.put("hypot", binary("hypot", Math::hypot));
        m.put("random", nullary("random", Math::random));
        return m;
    }

    /** 注册 0 参函数。 */
    public static Function nullary(String name, DoubleSupplier f) {
        return new Function(name, 0) {
            @Override public double apply(double... args) { return f.getAsDouble(); }
            @Override public double apply0() { return f.getAsDouble(); }
        };
    }

    /** 注册单参函数。 */
    public static Function unary(String name, DoubleUnaryOperator f) {
        return new Function(name, 1) {
            @Override public double apply(double... args) { return f.applyAsDouble(args[0]); }
            @Override public double apply1(double a) { return f.applyAsDouble(a); }
        };
    }

    /** 注册双参函数。 */
    public static Function binary(String name, DoubleBinaryOperator f) {
        return new Function(name, 2) {
            @Override public double apply(double... args) { return f.applyAsDouble(args[0], args[1]); }
            @Override public double apply2(double a, double b) { return f.applyAsDouble(a, b); }
        };
    }
}
