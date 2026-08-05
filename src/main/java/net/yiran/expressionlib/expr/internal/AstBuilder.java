package net.yiran.expressionlib.expr.internal;

import java.util.List;
import java.util.Map;

import net.yiran.expressionlib.expr.Function;
import net.yiran.expressionlib.expr.Operator;
import net.yiran.expressionlib.expr.Operators;

/**
 * Pratt（precedence climbing）递归下降解析器，直接产出 {@link AstNode}。一元 {@code -}/{@code +}
 * 按上下文重分类为 {@link Operators#NEG}/{@link Operators#POS}；三元 {@code cond ? a : b} 右结合，
 * 惰性由 {@link AstNode.Conditional} 求值时只访问选中分支保证。
 *
 * <p>{@code build()} 末尾跑一轮 {@link #simplify(AstNode)} 自底向上常量折叠/代数化简——构建期一次性
 * 成本，收益随每次 {@code evaluate()} 复合（节点数永久减少、派发永久消除）。仅做语义保持的安全规则，
 * 严格保留 IEEE-754（NaN 传播、{@code 0*NaN=NaN}、{@code Inf-Inf=NaN}、{@code pow} 的 1-ULP 行为）。
 */
public final class AstBuilder {
    private final List<Token> tokens;
    private final Map<String, Function> functions;
    private int pos = 0;

    /** EOF 复用单例：原 {@code peek()} 每次末尾都 {@code new EofToken()}，解析期高频浪费。 */
    private static final Token EOF = new Token.EofToken();

    private AstBuilder(List<Token> tokens, Map<String, Function> functions) {
        this.tokens = tokens;
        this.functions = functions;
    }

    public static AstNode build(List<Token> tokens, Map<String, Function> functions) {
        AstBuilder p = new AstBuilder(tokens, functions);
        AstNode node = p.parseExpression(0);
        if (!p.isAtEnd()) {
            throw new IllegalArgumentException("Unexpected token: " + p.peek());
        }
        return simplify(node);
    }

    private AstNode parseExpression(int minPrec) {
        AstNode left = parseUnary();
        while (true) {
            Token t = peek();
            if (!(t instanceof Token.OperatorToken)) {
                break;
            }
            Operator op = ((Token.OperatorToken) t).operator();
            int prec = op.getPrecedence();
            if (prec < minPrec) {
                break;
            }
            advance();
            int nextMin = op.isLeftAssociative() ? prec + 1 : prec;
            AstNode right = parseExpression(nextMin);
            left = binaryNode(op, left, right);
        }
        if (minPrec == 0 && peek() instanceof Token.QuestionToken) {
            advance();
            AstNode then = parseExpression(0);
            expectColon();
            AstNode otherwise = parseExpression(0);
            left = new AstNode.Conditional(left, then, otherwise);
        }
        return left;
    }

    private AstNode parseUnary() {
        Token t = peek();
        if (t instanceof Token.OperatorToken && isUnaryContext()) {
            Operator op = ((Token.OperatorToken) t).operator();
            String sym = op.getSymbol();
            if ("-".equals(sym) || "+".equals(sym)) {
                advance();
                Operator unary = "-".equals(sym) ? Operators.NEG : Operators.POS;
                AstNode operand = parseExpression(Operators.PRECEDENCE_UNARY_MINUS);
                return new AstNode.UnaryNode(unary, operand);
            }
        }
        return parsePower();
    }

    private AstNode parsePower() {
        AstNode base = parsePrimary();
        if (peek() instanceof Token.OperatorToken
                && "^".equals(((Token.OperatorToken) peek()).operator().getSymbol())) {
            Operator op = ((Token.OperatorToken) peek()).operator();
            advance();
            AstNode exponent = parseUnary();
            return binaryNode(op, base, exponent);
        }
        return base;
    }

    private AstNode parsePrimary() {
        Token t = peek();
        if (t instanceof Token.NumberToken) {
            advance();
            return new AstNode.NumberNode(((Token.NumberToken) t).value());
        }
        if (t instanceof Token.ConstantToken c) {
            advance();
            return new AstNode.NumberNode(c.value());
        }
        if (t instanceof Token.VariableToken) {
            advance();
            return new AstNode.VariableNode(((Token.VariableToken) t).name());
        }
        if (t instanceof Token.FunctionToken fn) {
            advance();
            return parseFunctionCall(fn);
        }
        if (t instanceof Token.ParenOpenToken) {
            advance();
            AstNode inner = parseExpression(0);
            expect(Token.ParenCloseToken.class, "')'");
            return inner;
        }
        throw new IllegalArgumentException("Unexpected token: " + t);
    }

    private AstNode parseFunctionCall(Token.FunctionToken fn) {
        Function f = functions.get(fn.name());
        if (f == null) {
            throw new IllegalArgumentException("Unknown function: " + fn.name());
        }
        expect(Token.ParenOpenToken.class, "'('");
        int argc = f.getNumArguments();
        AstNode[] args = new AstNode[argc];
        for (int i = 0; i < argc; i++) {
            if (i > 0) {
                expect(Token.CommaToken.class, "','");
            }
            args[i] = parseExpression(0);
        }
        expect(Token.ParenCloseToken.class, "')'");
        return new AstNode.FunctionNode(f, args);
    }

    private void expectColon() {
        if (!(peek() instanceof Token.ColonToken)) {
            throw new IllegalArgumentException("Expected ':' in ternary but found: " + peek());
        }
        advance();
    }

    private void expect(Class<? extends Token> type, String desc) {
        if (!type.isInstance(peek())) {
            throw new IllegalArgumentException("Expected " + desc + " but found: " + peek());
        }
        advance();
    }

    private boolean isUnaryContext() {
        if (pos == 0) return true;
        Token prev = tokens.get(pos - 1);
        return prev instanceof Token.OperatorToken
                || prev instanceof Token.ParenOpenToken
                || prev instanceof Token.CommaToken
                || prev instanceof Token.QuestionToken
                || prev instanceof Token.ColonToken;
    }

    private Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : EOF;
    }

    private void advance() {
        if (pos < tokens.size()) pos++;
    }

    private boolean isAtEnd() {
        return pos >= tokens.size();
    }

    /**
     * 内置运算符走特化节点（直接内联算术，消除 {@code Operator.applyBinary → DoubleBinaryOperator}
     * 多态派发链）；用户自定义运算符仍走通用 {@link AstNode.BinaryNode}。
     */
    private static AstNode binaryNode(Operator op, AstNode left, AstNode right) {
        if (op == Operators.ADD) return new AstNode.AddNode(left, right);
        if (op == Operators.SUB) return new AstNode.SubNode(left, right);
        if (op == Operators.MUL) return new AstNode.MulNode(left, right);
        if (op == Operators.DIV) return new AstNode.DivNode(left, right);
        if (op == Operators.MOD) return new AstNode.ModNode(left, right);
        if (op == Operators.POW) return new AstNode.PowNode(left, right);
        if (op == Operators.GT) return new AstNode.GtNode(left, right);
        if (op == Operators.LT) return new AstNode.LtNode(left, right);
        if (op == Operators.GE) return new AstNode.GeNode(left, right);
        if (op == Operators.LE) return new AstNode.LeNode(left, right);
        if (op == Operators.EQ) return new AstNode.EqNode(left, right);
        if (op == Operators.NE) return new AstNode.NeNode(left, right);
        return new AstNode.BinaryNode(op, left, right);
    }

    // ───────────────────────── build 期化简 ─────────────────────────
    // 自底向上：先化简子节点，再对当前节点套规则。所有规则严格保持 IEEE-754 语义。
    private static AstNode simplify(AstNode node) {
        if (node instanceof AstNode.BinaryAstNode b) {
            AstNode left = simplify(b.left());
            AstNode right = simplify(b.right());
            return simplifyBinary(b, left, right);
        }
        if (node instanceof AstNode.UnaryNode) {
            AstNode.UnaryNode unary = (AstNode.UnaryNode) node;
            AstNode operand = simplify(unary.operand());
            return simplifyUnary(unary.operator(), operand);
        }
        if (node instanceof AstNode.FunctionNode f) {
            AstNode[] args = f.arguments();
            boolean changed = false;
            for (int i = 0; i < args.length; i++) {
                AstNode s = simplify(args[i]);
                if (s != args[i]) {
                    if (!changed) {
                        args = args.clone();
                        changed = true;
                    }
                    args[i] = s;
                }
            }
            return changed ? new AstNode.FunctionNode(f.function(), args) : f;
        }
        if (node instanceof AstNode.Conditional c) {
            AstNode cond = simplify(c.cond());
            AstNode then = simplify(c.then());
            AstNode otherwise = simplify(c.otherwise());
            // 常量条件：按求值期同一谓词（0 或 NaN 为假）选分支，消除死分支。
            if (cond instanceof AstNode.NumberNode) {
                double cv = ((AstNode.NumberNode) cond).value();
                return (cv == 0.0 || Double.isNaN(cv)) ? otherwise : then;
            }
            if (cond == c.cond() && then == c.then() && otherwise == c.otherwise()) {
                return c;
            }
            return new AstNode.Conditional(cond, then, otherwise);
        }
        return node; // NumberNode / VariableNode
    }

    /** 代数恒等式：仅在安全（保留 NaN/Inf/有符号零）时应用。 */
    private static AstNode simplifyBinary(AstNode.BinaryAstNode node, AstNode left, AstNode right) {
        boolean lConst = left instanceof AstNode.NumberNode;
        boolean rConst = right instanceof AstNode.NumberNode;

        // 常量折叠：双常量 → 直接算。对特化节点与通用 BinaryNode 都用 applyBinary 求一次，
        // 结果为精确 IEEE-754（与运行期逐次求值一致）。
        if (lConst && rConst) {
            double a = ((AstNode.NumberNode) left).value();
            double b = ((AstNode.NumberNode) right).value();
            return new AstNode.NumberNode(binaryValue(node, a, b));
        }

        if (node instanceof AstNode.AddNode) {
            if (rConst && isZero(((AstNode.NumberNode) right).value())) return left; // x+0 -> x
            if (lConst && isZero(((AstNode.NumberNode) left).value())) return right; // 0+x -> x
        } else if (node instanceof AstNode.SubNode) {
            // x-0 -> x：仅当常量为 +0.0。若为 -0.0，x-(-0.0)=x+0.0 会翻转 -0.0→+0.0（符号变化，
            // 1/结果 ±Inf 可观测），故 -0.0 时不折叠、留待运行期计算。
            if (rConst && isPosZero(((AstNode.NumberNode) right).value())) return left;
        } else if (node instanceof AstNode.MulNode) {
            if (rConst && isOne(((AstNode.NumberNode) right).value())) return left; // x*1 -> x
            if (lConst && isOne(((AstNode.NumberNode) left).value())) return right; // 1*x -> x
            // 注意：x*0/0*x 绝不折叠为 0——未设变量以 NaN 体现，0*NaN 必须保持 NaN。
        } else if (node instanceof AstNode.DivNode) {
            if (rConst && isOne(((AstNode.NumberNode) right).value())) return left; // x/1 -> x
        } else if (node instanceof AstNode.PowNode) {
            if (rConst) {
                double e = ((AstNode.NumberNode) right).value();
                if (isZero(e)) return new AstNode.NumberNode(1.0); // x^0 -> 1（含 NaN 底：Math.pow(NaN,0)=1.0，与运行期一致）
                if (isOne(e)) return left; // x^1 -> x
            }
            // 不做 pow(x,2)->x*x：Math.pow 与 x*x 有 1-ULP 差异，破坏 bit-一致性。
        }

        return rebuildBinary(node, left, right);
    }

    private static AstNode simplifyUnary(Operator op, AstNode operand) {
        if (op == Operators.NEG) {
            // 常量取负；-(-x) -> x（消除双重否定）。
            if (operand instanceof AstNode.NumberNode) {
                return new AstNode.NumberNode(-((AstNode.NumberNode) operand).value());
            }
            if (operand instanceof AstNode.UnaryNode
                    && ((AstNode.UnaryNode) operand).operator() == Operators.NEG) {
                return ((AstNode.UnaryNode) operand).operand();
            }
        } else if (op == Operators.POS) {
            // +x -> x；+常量 -> 常量。
            return operand;
        }
        return new AstNode.UnaryNode(op, operand);
    }

    /** 双常量折叠时按节点类型取值（特化节点直算，通用节点走 applyBinary）。 */
    private static double binaryValue(AstNode.BinaryAstNode node, double a, double b) {
        if (node instanceof AstNode.AddNode) return a + b;
        if (node instanceof AstNode.SubNode) return a - b;
        if (node instanceof AstNode.MulNode) return a * b;
        if (node instanceof AstNode.DivNode) return a / b;
        if (node instanceof AstNode.ModNode) return a % b;
        if (node instanceof AstNode.PowNode) return Math.pow(a, b);
        if (node instanceof AstNode.GtNode) return a > b ? 1.0 : 0.0;
        if (node instanceof AstNode.LtNode) return a < b ? 1.0 : 0.0;
        if (node instanceof AstNode.GeNode) return a >= b ? 1.0 : 0.0;
        if (node instanceof AstNode.LeNode) return a <= b ? 1.0 : 0.0;
        if (node instanceof AstNode.EqNode) return a == b ? 1.0 : 0.0;
        if (node instanceof AstNode.NeNode) return a != b ? 1.0 : 0.0;
        if (node instanceof AstNode.BinaryNode) {
            return ((AstNode.BinaryNode) node).operator().applyBinary(a, b);
        }
        throw new IllegalStateException("Unknown binary node: " + node.getClass());
    }

    /** 化简后子节点若变化，用同类型重建（保留特化/通用区分）。 */
    private static AstNode rebuildBinary(AstNode.BinaryAstNode node, AstNode left, AstNode right) {
        if (left == node.left() && right == node.right()) {
            return node;
        }
        if (node instanceof AstNode.AddNode) return new AstNode.AddNode(left, right);
        if (node instanceof AstNode.SubNode) return new AstNode.SubNode(left, right);
        if (node instanceof AstNode.MulNode) return new AstNode.MulNode(left, right);
        if (node instanceof AstNode.DivNode) return new AstNode.DivNode(left, right);
        if (node instanceof AstNode.ModNode) return new AstNode.ModNode(left, right);
        if (node instanceof AstNode.PowNode) return new AstNode.PowNode(left, right);
        if (node instanceof AstNode.GtNode) return new AstNode.GtNode(left, right);
        if (node instanceof AstNode.LtNode) return new AstNode.LtNode(left, right);
        if (node instanceof AstNode.GeNode) return new AstNode.GeNode(left, right);
        if (node instanceof AstNode.LeNode) return new AstNode.LeNode(left, right);
        if (node instanceof AstNode.EqNode) return new AstNode.EqNode(left, right);
        if (node instanceof AstNode.NeNode) return new AstNode.NeNode(left, right);
        if (node instanceof AstNode.BinaryNode) {
            return new AstNode.BinaryNode(((AstNode.BinaryNode) node).operator(), left, right);
        }
        throw new IllegalStateException("Unknown binary node: " + node.getClass());
    }

    /** 加法的零元：+0.0 与 -0.0 均可（x±0 不改变 x 的符号，-0.0 例外见 SubNode）。 */
    private static boolean isZero(double v) { return v == 0.0; }
    /** 正零（排除 -0.0）：仅 SubNode 的 x-0 规则用之，避免 x-(-0.0) 翻转符号。 */
    private static boolean isPosZero(double v) {
        return v == 0.0 && Double.doubleToRawLongBits(v) == 0L;
    }
    private static boolean isOne(double v) { return v == 1.0; }
}
