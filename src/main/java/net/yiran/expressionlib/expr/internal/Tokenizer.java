package net.yiran.expressionlib.expr.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yiran.expressionlib.expr.Operator;

/**
 * 词法分析器，单遍索引扫描。标识符若是函数名或后随 {@code (} 则归为函数，否则为变量；
 * {@code pi}/{@code e} 为保留常量。运算符按符号最长匹配。
 */
public final class Tokenizer {
    private Tokenizer() {
    }

    public static List<Token> tokenize(String s, Map<String, Operator> operators,
                                       Set<String> functionNames, Map<String, Double> constants) {
        List<Token> tokens = new ArrayList<>();
        String[] sortedOps = operators.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toArray(String[]::new);

        int i = 0;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);

            if (isWhitespace(c)) {
                i++;
                continue;
            }

            if (isDigit(c) || (c == '.' && i + 1 < len && isDigit(s.charAt(i + 1)))) {
                int start = i;
                boolean sawDot = false;
                boolean sawDigit = false;
                while (i < len) {
                    char d = s.charAt(i);
                    if (isDigit(d)) {
                        sawDigit = true;
                        i++;
                    } else if (d == '.' && !sawDot) {
                        sawDot = true;
                        i++;
                    } else {
                        break;
                    }
                }
                if (i < len && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    if (!sawDigit) {
                        throw new IllegalArgumentException("Malformed number at index " + start);
                    }
                    int j = i + 1;
                    if (j < len && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
                        j++;
                    }
                    if (j >= len || !isDigit(s.charAt(j))) {
                        throw new IllegalArgumentException("Malformed number at index " + start
                                + ": exponent has no digits");
                    }
                    i = j;
                    while (i < len && isDigit(s.charAt(i))) {
                        i++;
                    }
                }
                String num = s.substring(start, i);
                double value;
                try {
                    value = Double.parseDouble(num);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Malformed number at index " + start + ": " + num, e);
                }
                tokens.add(new Token.NumberToken(value));
                continue;
            }

            if (isIdentStart(c)) {
                int start = i;
                do {
                    i++;
                } while (i < len && isIdentPart(s.charAt(i)));
                String name = s.substring(start, i);
                if (name.equals("pi")) {
                    tokens.add(new Token.ConstantToken("pi", Math.PI));
                } else if (name.equals("e")) {
                    tokens.add(new Token.ConstantToken("e", Math.E));
                } else if (constants != null && constants.containsKey(name)) {
                    tokens.add(new Token.ConstantToken(name, constants.get(name)));
                } else if (functionNames.contains(name) || nextNonWhitespaceIs(s, i)) {
                    tokens.add(new Token.FunctionToken(name));
                } else {
                    tokens.add(new Token.VariableToken(name));
                }
                continue;
            }

            if (c == '(') { tokens.add(new Token.ParenOpenToken()); i++; continue; }
            if (c == ')') { tokens.add(new Token.ParenCloseToken()); i++; continue; }
            if (c == ',') { tokens.add(new Token.CommaToken()); i++; continue; }
            if (c == '?') { tokens.add(new Token.QuestionToken()); i++; continue; }
            if (c == ':') { tokens.add(new Token.ColonToken()); i++; continue; }

            String matched = null;
            for (String sym : sortedOps) {
                if (s.startsWith(sym, i)) {
                    matched = sym;
                    break;
                }
            }
            if (matched == null) {
                throw new IllegalArgumentException("Unexpected character '" + c + "' at index " + i);
            }
            tokens.add(new Token.OperatorToken(operators.get(matched)));
            i += matched.length();
        }
        return tokens;
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }

    private static boolean nextNonWhitespaceIs(String s, int from) {
        int j = from;
        int len = s.length();
        while (j < len && isWhitespace(s.charAt(j))) {
            j++;
        }
        return j < len && s.charAt(j) == '(';
    }
}
