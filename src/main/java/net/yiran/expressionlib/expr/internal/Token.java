package net.yiran.expressionlib.expr.internal;

import net.yiran.expressionlib.expr.Operator;

/** 词法 token。{@code pi}/{@code e} 在词法阶段即归为 {@link ConstantToken}。 */
public sealed interface Token {
    enum Type { NUMBER, OPERATOR, FUNCTION, PAREN_OPEN, PAREN_CLOSE, COMMA, VARIABLE, CONSTANT,
                QUESTION, COLON, EOF }

    Type type();

    record NumberToken(double value) implements Token {
        @Override public Type type() { return Type.NUMBER; }
    }

    record OperatorToken(Operator operator) implements Token {
        @Override public Type type() { return Type.OPERATOR; }
    }

    record FunctionToken(String name) implements Token {
        @Override public Type type() { return Type.FUNCTION; }
    }

    record ParenOpenToken() implements Token {
        @Override public Type type() { return Type.PAREN_OPEN; }
    }

    record ParenCloseToken() implements Token {
        @Override public Type type() { return Type.PAREN_CLOSE; }
    }

    record CommaToken() implements Token {
        @Override public Type type() { return Type.COMMA; }
    }

    record VariableToken(String name) implements Token {
        @Override public Type type() { return Type.VARIABLE; }
    }

    record ConstantToken(String name, double value) implements Token {
        @Override public Type type() { return Type.CONSTANT; }
    }

    record QuestionToken() implements Token {
        @Override public Type type() { return Type.QUESTION; }
    }

    record ColonToken() implements Token {
        @Override public Type type() { return Type.COLON; }
    }

    record EofToken() implements Token {
        @Override public Type type() { return Type.EOF; }
    }
}
