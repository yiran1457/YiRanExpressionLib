# YiRanExpressionLib

一个为 Minecraft NeoForge 模组设计的数学表达式解析与求值库，API 风格仿照 [exp4j](https://github.com/fasseg/exp4j)，底层使用 [fastutil](https://fastutil.di.unimi.it/) 消除基础类型的拆装箱。

求值热路径全程原始 `double`，无 `Integer`/`Double` 装箱、无 hash 查找；支持三元条件运算符的**惰性求值**。

- **Minecraft**: 1.21.1
- **NeoForge** (构建依赖)
- **Java**: 21
- **fastutil**: 8.5.12

## 特性

- 仿 exp4j 的流式 `ExpressionBuilder` API
- 全程原始 `double`：变量名在编译期解析为数组下标，求值时 `values[index]` 直访，无 map、无 hash
- `Operator` / `Function` 提供 `applyBinary` / `apply1` / `apply2` 等特化方法，热路径无 varargs `double[]` 分配
- 三元 `cond ? a : b` 惰性求值：只递归选中分支，未选分支（如 `1/x`）不被访问
- 自定义常量（`.constant(name, value)`），编译期折叠为字面量，与 `pi` / `e` 同等待遇
- 自定义函数 / 运算符，可覆盖同名内置项

## 快速开始

```java
import net.yiran.expressionlib.expr.Expression;
import net.yiran.expressionlib.expr.ExpressionBuilder;

Expression e = new ExpressionBuilder("3 * sin(x) - 2 * cos(y)")
        .variables("x", "y")
        .build();
double r = e.evaluate(1.0, 0.5);
```

## 求值方式

同一表达式有四种求值入口，按调用方是否持有变量值选择：

```java
Expression e = new ExpressionBuilder("sin(x) + y * z")
        .variables("x", "y", "z").build();

// 1. 按声明顺序传值，端到端无装箱（最常用的热路径）
e.evaluate(1.0, 2.0, 3.0);

// 2. 链式设值后无参求值
e.setVariable("x", 1.0).setVariable("y", 2.0).setVariable("z", 3.0);
e.evaluate();

// 3. 跳过校验的极速路径，缺失变量以 NaN 体现、不抛异常
//    适用于调用方已确保变量就绪、需最大性能的场景
e.setVariable("x", 1.0).setVariable("y", 2.0).setVariable("z", 3.0);
e.evaluateUnchecked();

// 4. 合并一个 Map（不修改实例字段）
e.evaluate(Map.of("x", 1.0, "y", 2.0, "z", 3.0));
```

| 入口 | 校验 | 适用 |
|---|---|---|
| `evaluate(double...)` | 数量检查 | 按顺序传值，最常用 |
| `evaluate()` | 缺失变量抛异常 | 已 `setVariable` 设值 |
| `evaluateUnchecked()` | 无 | 已确保变量就绪，最大性能 |
| `evaluate(Map)` | 缺失变量抛异常 | 合并外部 map |

## 常量

内置 `pi`、`e`。自定义常量在编译期折叠为字面量，不进变量表、求值时无需提供、不可被 `setVariable` 覆盖：

```java
Expression e = new ExpressionBuilder("sin(tau / 4)")   // sin(π/2) = 1
        .constant("tau", Math.PI * 2)
        .build();
e.evaluate();   // 1.0，不用传 tau
```

## 三元与惰性

`cond ? a : b` 右结合，条件 `0` 或 `NaN` 视为假。未选分支**不执行**：

```java
// x=0 时走 otherwise 分支，1/x 不被访问，不会除零
Expression e = new ExpressionBuilder("x != 0 ? 1/x : 0")
        .variable("x").build();
e.setVariable("x", 0);
e.evaluate();   // 0.0
```

嵌套三元与混合运算均支持：

```java
new ExpressionBuilder("x > 0 ? (x > 1 ? 2 : 1) : 0")
        .variable("x").build();
```

## 自定义函数

```java
// 通过函数式接口注册（0/1/2 参）
new ExpressionBuilder("clamp(x, 0, 1)")
        .variable("x")
        .function("clamp", (a, lo, hi) -> Math.max(lo, Math.min(hi, a)))  // 需 3 参：子类化
        .build();

// 单参 / 双参 / 无参
new ExpressionBuilder("square(x) + rnd()")
        .variable("x")
        .function("square", a -> a * a)
        .function("rnd", () -> Math.random())
        .build();
```

3 参及以上函数需子类化 `Function` 并重写 `apply(double...)`（或 `apply3` 走热路径特化）：

```java
Function clamp = new Function("clamp", 3) {
    @Override public double apply(double... a) {
        return Math.max(a[1], Math.min(a[2], a[0]));
    }
    @Override public double apply3(double a, double lo, double hi) {
        return Math.max(lo, Math.min(hi, a));
    }
};
new ExpressionBuilder("clamp(x, 0, 1)").variable("x").function(clamp).build();
```

## 自定义运算符

```java
Operator FACT = Operator.unary("!", 5, a -> {
    double r = 1;
    for (int i = 2; i <= (int) a; i++) r *= i;
    return r;
});
new ExpressionBuilder("x! + 1").variable("x").operator(FACT).build();
```

## 内置函数与运算符

**函数**：`sin cos tan asin acos atan atan2 sinh cosh tanh log(=ln) log10 log2 exp sqrt cbrt abs ceil floor round signum max min pow hypot random`

**运算符**：`+ - * / % ^` 及比较 `< > <= >= == !=`（比较成立返回 `1.0`，否则 `0.0`）。

**优先级**（数值越大越紧）：比较 `0` < 加减 `1` < 乘除模 `2` < 一元正负 `3` < 幂 `4`（右结合）。

> 注意：一元负号优先级低于幂，故 `-2^2 = -(2^2) = -4`（与 exp4j 不同）。

## 校验

```java
ValidationResult vr = e.validate(true);   // silent，不抛异常
if (!vr.isValid()) {
    // vr.missingVariables()
}
e.validate();   // 不可达错误时直接抛 IllegalArgumentException
```

## 性能

`sin(x) + cos(y) * z - x/y + log(x+1)`，500K 次预热后单次求值：

| 入口 | 单次 |
|---|---|
| `evaluate()` | ~65 ns |
| `evaluateUnchecked()` | ~50 ns |

热路径无装箱、无 hash、无临时数组分配。

## 线程安全

`setVariable` 写入实例字段数组；`evaluate(Map)` 与 `evaluate(double...)` 先复制再求值，并发求值互不影响。前提：不在求值同时调用 `setVariable`。无参 `evaluate()` / `evaluateUnchecked()` 直接读字段数组，适合变量已通过 `setVariable` 设定的单线程场景。

## 包结构

```
net.yiran.expressionlib
 └─ expr
    ├─ ExpressionBuilder      流式构建器（公共 API）
    ├─ Expression             编译后的可求值表达式（公共 API）
    ├─ Function / Functions   函数抽象与内置目录
    ├─ Operator / Operators   运算符抽象与内置目录
    ├─ ValidationResult       校验结果
    └─ internal
       ├─ Token / Tokenizer   词法
       ├─ AstBuilder          Pratt 解析器
       └─ AstNode             AST 求值（变量下标编译期解析）
```
