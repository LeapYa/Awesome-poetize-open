package com.ld.poetry.service.ai.tools;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorToolsTest {

    private final CalculatorTools calculatorTools = new CalculatorTools();

    @Test
    void shouldCalculateArithmeticExpression() {
        BigDecimal result = calculatorTools.evaluateExpression("(2 + 3) * 4 - 5 / 2");

        assertEquals("17.5", result.stripTrailingZeros().toPlainString());
    }

    @Test
    void shouldSupportFunctionsAndConstants() {
        BigDecimal result = calculatorTools.evaluateExpression("pow(2, 8) + sqrt(16) + abs(-3) + floor(pi)");

        assertEquals("266", result.stripTrailingZeros().toPlainString());
    }

    @Test
    void shouldReturnReadableErrorForInvalidExpression() {
        String result = calculatorTools.calculate("1 + unknown(2)");

        assertTrue(result.contains("表达式无效"));
        assertTrue(result.contains("不支持的函数"));
    }

    @Test
    void shouldRejectDivisionByZero() {
        String result = calculatorTools.calculate("10 / 0");

        assertTrue(result.contains("表达式无效"));
        assertTrue(result.contains("除数不能为 0"));
    }

    @Test
    void shouldSupportTrigonometricAndLogFunctions() {
        assertEquals(0.5, calculatorTools.evaluateExpression("sin(pi/6)").doubleValue(), 1e-9);
        assertEquals(1.0, calculatorTools.evaluateExpression("log(e)").doubleValue(), 1e-9);
        assertEquals(3.0, calculatorTools.evaluateExpression("log2(8)").doubleValue(), 1e-9);
        assertEquals(0.5, calculatorTools.evaluateExpression("sin(radians(30))").doubleValue(), 1e-9);
        assertEquals(30.0, calculatorTools.evaluateExpression("degrees(pi/6)").doubleValue(), 1e-9);
    }

    @Test
    void shouldRejectInvalidDomainArguments() {
        assertTrue(calculatorTools.calculate("asin(2)").contains("必须在 -1 到 1 之间"));
        assertTrue(calculatorTools.calculate("ln(0)").contains("必须为正数"));
        assertTrue(calculatorTools.calculate("fact(2.5)").contains("非负整数"));
    }

    @Test
    void shouldSupportCombinatoricsAndStatistics() {
        assertEquals("120", calculatorTools.evaluateExpression("fact(5)").stripTrailingZeros().toPlainString());
        assertEquals("120", calculatorTools.evaluateExpression("comb(10, 3)").stripTrailingZeros().toPlainString());
        assertEquals("20", calculatorTools.evaluateExpression("perm(5, 2)").stripTrailingZeros().toPlainString());
        assertEquals("10", calculatorTools.evaluateExpression("sum(1,2,3,4)").stripTrailingZeros().toPlainString());
        assertEquals("2.5", calculatorTools.evaluateExpression("avg(1,2,3,4)").stripTrailingZeros().toPlainString());
        assertEquals("2.5", calculatorTools.evaluateExpression("median(4,1,3,2)").stripTrailingZeros().toPlainString());
        assertEquals(Math.sqrt(2.0 / 3.0),
                calculatorTools.evaluateExpression("std(1,2,3)").doubleValue(), 1e-12);
    }

    @Test
    void shouldSupportRadixLiterals() {
        assertEquals("256", calculatorTools.evaluateExpression("0xff + 1").stripTrailingZeros().toPlainString());
        assertEquals("5", calculatorTools.evaluateExpression("0b101").stripTrailingZeros().toPlainString());
        assertEquals("15", calculatorTools.evaluateExpression("0o17").stripTrailingZeros().toPlainString());
        assertEquals("-255", calculatorTools.evaluateExpression("-0xff").stripTrailingZeros().toPlainString());
    }

    @Test
    void shouldSupportNumericalCalculus() {
        assertEquals(9.0, calculatorTools.integrateExpression("x^2", 0, 3).doubleValue(), 1e-9);
        assertEquals(2.0, calculatorTools.integrateExpression("sin(x)", 0, Math.PI).doubleValue(), 1e-9);
        assertEquals(Math.PI, calculatorTools.integrateExpression("4/(1+x^2)", 0, 1).doubleValue(), 1e-9);
        assertEquals(12.0, calculatorTools.differentiateExpression("x^3", 2).doubleValue(), 1e-6);
        assertEquals(1.0, calculatorTools.differentiateExpression("sin(x)", 0).doubleValue(), 1e-6);
    }
}
