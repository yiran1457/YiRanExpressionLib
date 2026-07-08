package net.yiran.expressionlib.expr;

import java.util.Set;

/**
 * 表达式校验结果。
 *
 * @param missingVariables  求值时缺失的变量名集合
 * @param unknownFunctions  表达式中引用的未注册函数名集合
 * @param unknownOperators  表达式中引用的未注册运算符符号集合
 */
public record ValidationResult(Set<String> missingVariables,
                               Set<String> unknownFunctions,
                               Set<String> unknownOperators) {
    public boolean isValid() {
        return missingVariables.isEmpty()
                && unknownFunctions.isEmpty()
                && unknownOperators.isEmpty();
    }

    public static ValidationResult success() {
        return new ValidationResult(Set.of(), Set.of(), Set.of());
    }
}
