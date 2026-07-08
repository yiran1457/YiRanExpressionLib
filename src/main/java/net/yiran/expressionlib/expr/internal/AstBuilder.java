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
 */
public final class AstBuilder {
    private final List<Token> tokens;
    private final Map<String, Function> functions;
    private int pos = 0;

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
        return node;
    }

    private AstNode parseExpression(int minPrec) {
        AstNode left = parseUnary();
        while (true) {
            Token t = peek();
            if (!(t instanceof Token.OperatorToken(Operator op))) {
                break;
            }
            int prec = op.getPrecedence();
            if (prec < minPrec) {
                break;
            }
            advance();
            int nextMin = op.isLeftAssociative() ? prec + 1 : prec;
            AstNode right = parseExpression(nextMin);
            left = new AstNode.BinaryNode(op, left, right);
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
        if (t instanceof Token.OperatorToken(Operator op) && isUnaryContext()) {
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
        if (peek() instanceof Token.OperatorToken(Operator op) && "^".equals(op.getSymbol())) {
            advance();
            AstNode exponent = parseUnary();
            return new AstNode.BinaryNode(op, base, exponent);
        }
        return base;
    }

    private AstNode parsePrimary() {
        Token t = peek();
        if (t instanceof Token.NumberToken(double value)) {
            advance();
            return new AstNode.NumberNode(value);
        }
        if (t instanceof Token.ConstantToken c) {
            advance();
            return new AstNode.NumberNode(c.value());
        }
        if (t instanceof Token.VariableToken(String name)) {
            advance();
            return new AstNode.VariableNode(name);
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
        return pos < tokens.size() ? tokens.get(pos) : new Token.EofToken();
    }

    private void advance() {
        if (pos < tokens.size()) pos++;
    }

    private boolean isAtEnd() {
        return pos >= tokens.size();
    }
}
