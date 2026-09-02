package com.ld.poetry.service.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * 安全的本地计算器工具，避免依赖脚本引擎执行任意代码。
 */
@Service
public class CalculatorTools {

    private static final int MAX_EXPRESSION_LENGTH = 500;
    private static final int DIVISION_SCALE = 12;
    private static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    private static final int SIMPSON_INTERVALS = 2000;
    private static final int MAX_COMBINATORIAL_INPUT = 10000;

    @Tool(description = "计算数学表达式。运算符：+ - * / % ^、括号、负数；常量：pi、e；进制字面量：0x/0b/0o。"
            + "函数：sqrt、abs、round、floor、ceil、pow、max、min、sin、cos、tan、asin、acos、atan（弧度制）、"
            + "sinh、cosh、tanh、ln、log、log10、log2、exp、radians（度转弧度）、degrees（弧度转度）、"
            + "fact（阶乘）、comb（组合数）、perm（排列数）、sum、avg、median、var、std（总体方差/标准差）。"
            + "max、min 及统计函数支持任意多个参数")
    public String calculate(
            @ToolParam(description = "数学表达式，例如 (2+3)*4、sin(pi/6)、pow(2,10)、max(3,7,12,5)、comb(10,3)、0xff+1") String expression) {
        if (expression == null || expression.isBlank()) {
            return "表达式不能为空。";
        }

        try {
            BigDecimal result = evaluateExpression(expression);
            return "计算结果：" + expression.trim() + " = " + formatNumber(result) + "。";
        } catch (IllegalArgumentException ex) {
            return "表达式无效：" + ex.getMessage()
                    + "。请检查函数名与语法，支持三角/对数/统计等函数与 0x/0b/0o 进制字面量。";
        }
    }

    @Tool(description = "数值积分（复合辛普森法）：计算表达式在区间 [下限, 上限] 上的定积分，自变量用 x 表示")
    public String integral(
            @ToolParam(description = "被积表达式，自变量用 x 表示，例如 sin(x)+x^2") String expression,
            @ToolParam(description = "积分下限") double lower,
            @ToolParam(description = "积分上限") double upper) {
        try {
            return "积分结果：∫[" + lower + ", " + upper + "] (" + expression.trim() + ") dx ≈ "
                    + formatNumber(integrateExpression(expression, lower, upper)) + "。";
        } catch (IllegalArgumentException ex) {
            return "积分失败：" + ex.getMessage() + "。请检查表达式语法与积分区间（区间内可能存在奇异点）。";
        }
    }

    @Tool(description = "数值求导（中心差分法）：计算表达式在某点的导数值，自变量用 x 表示")
    public String derivative(
            @ToolParam(description = "目标表达式，自变量用 x 表示，例如 x^3+2*x") String expression,
            @ToolParam(description = "求导点 x0") double point) {
        try {
            return "求导结果：d/dx (" + expression.trim() + ") 在 x=" + point + " 处 ≈ "
                    + formatNumber(differentiateExpression(expression, point)) + "。";
        } catch (IllegalArgumentException ex) {
            return "求导失败：" + ex.getMessage() + "。请检查表达式语法与求导点。";
        }
    }

    BigDecimal evaluateExpression(String expression) {
        return evaluateExpression(expression, null);
    }

    BigDecimal evaluateExpression(String expression, Map<String, BigDecimal> variables) {
        String normalized = expression == null ? "" : expression.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        if (normalized.length() > MAX_EXPRESSION_LENGTH) {
            throw new IllegalArgumentException("表达式过长");
        }
        return new ExpressionParser(normalized, variables).parse();
    }

    BigDecimal integrateExpression(String expression, double lower, double upper) {
        double step = (upper - lower) / SIMPSON_INTERVALS;
        double sum = evaluateAsDouble(expression, lower) + evaluateAsDouble(expression, upper);
        for (int i = 1; i < SIMPSON_INTERVALS; i++) {
            double weight = (i % 2 == 1) ? 4 : 2;
            sum += weight * evaluateAsDouble(expression, lower + step * i);
        }
        return fromDouble(sum * step / 3);
    }

    BigDecimal differentiateExpression(String expression, double point) {
        double step = Math.max(Math.abs(point), 1.0) * 1e-6;
        double slope = (evaluateAsDouble(expression, point + step)
                - evaluateAsDouble(expression, point - step)) / (2 * step);
        return fromDouble(slope);
    }

    private double evaluateAsDouble(String expression, double x) {
        BigDecimal result = evaluateExpression(expression, Map.of("x", BigDecimal.valueOf(x)));
        double value = result.doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("表达式在 x=" + x + " 处的结果超出范围");
        }
        return value;
    }

    private String formatNumber(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0, RoundingMode.UNNECESSARY);
        }
        return normalized.toPlainString();
    }

    private static BigDecimal fromDouble(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("计算结果超出范围");
        }
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal factorial(int n) {
        BigDecimal result = BigDecimal.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigDecimal.valueOf(i));
        }
        return result;
    }

    private static BigDecimal combinations(int n, int k) {
        if (k > n) {
            return BigDecimal.ZERO;
        }
        int effectiveK = Math.min(k, n - k);
        BigDecimal result = BigDecimal.ONE;
        for (int i = 1; i <= effectiveK; i++) {
            // 每步中间结果恒为整数 C(n-effectiveK+i, i)，可精确整除
            result = result.multiply(BigDecimal.valueOf(n - effectiveK + i))
                    .divide(BigDecimal.valueOf(i));
        }
        return result;
    }

    private static BigDecimal permutations(int n, int k) {
        if (k > n) {
            return BigDecimal.ZERO;
        }
        BigDecimal result = BigDecimal.ONE;
        for (int i = n - k + 1; i <= n; i++) {
            result = result.multiply(BigDecimal.valueOf(i));
        }
        return result;
    }

    private static int requireCombinatorialInteger(BigDecimal value) {
        if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("fact/comb/perm 参数必须是非负整数");
        }
        try {
            int n = value.intValueExact();
            if (n > MAX_COMBINATORIAL_INPUT) {
                throw new IllegalArgumentException("fact/comb/perm 参数过大，最大支持 " + MAX_COMBINATORIAL_INPUT);
            }
            return n;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("fact/comb/perm 参数过大，最大支持 " + MAX_COMBINATORIAL_INPUT);
        }
    }

    private static final class ExpressionParser {

        private final String expression;
        private final Map<String, BigDecimal> variables;
        private int position;

        private ExpressionParser(String expression, Map<String, BigDecimal> variables) {
            this.expression = expression;
            this.variables = variables;
        }

        private BigDecimal parse() {
            BigDecimal result = parseExpression();
            skipWhitespace();
            if (!isAtEnd()) {
                throw new IllegalArgumentException("存在无法识别的字符: " + peek());
            }
            return result;
        }

        private BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value = value.add(parseTerm(), MATH_CONTEXT);
                    continue;
                }
                if (match('-')) {
                    value = value.subtract(parseTerm(), MATH_CONTEXT);
                    continue;
                }
                return value;
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parsePower();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value = value.multiply(parsePower(), MATH_CONTEXT);
                    continue;
                }
                if (match('/')) {
                    BigDecimal divisor = parsePower();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("除数不能为 0");
                    }
                    value = value.divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP);
                    continue;
                }
                if (match('%')) {
                    BigDecimal divisor = parsePower();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("取模除数不能为 0");
                    }
                    value = value.remainder(divisor, MATH_CONTEXT);
                    continue;
                }
                return value;
            }
        }

        private BigDecimal parsePower() {
            BigDecimal base = parseUnary();
            skipWhitespace();
            if (match('^')) {
                BigDecimal exponent = parsePower();
                return fromDouble(Math.pow(base.doubleValue(), exponent.doubleValue()));
            }
            return base;
        }

        private BigDecimal parseUnary() {
            skipWhitespace();
            if (match('+')) {
                return parseUnary();
            }
            if (match('-')) {
                return parseUnary().negate(MATH_CONTEXT);
            }
            return parsePrimary();
        }

        private BigDecimal parsePrimary() {
            skipWhitespace();
            if (match('(')) {
                BigDecimal value = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw new IllegalArgumentException("缺少右括号");
                }
                return value;
            }

            if (isNumberStart(peek())) {
                return parseNumber();
            }

            if (Character.isLetter(peek())) {
                String identifier = parseIdentifier();
                skipWhitespace();
                if (match('(')) {
                    List<BigDecimal> args = parseFunctionArguments();
                    return applyFunction(identifier, args);
                }
                return resolveConstant(identifier);
            }

            if (isAtEnd()) {
                throw new IllegalArgumentException("表达式不完整");
            }
            throw new IllegalArgumentException("存在无法识别的字符: " + peek());
        }

        private List<BigDecimal> parseFunctionArguments() {
            List<BigDecimal> args = new ArrayList<>();
            skipWhitespace();
            if (match(')')) {
                return args;
            }

            do {
                args.add(parseExpression());
                skipWhitespace();
            } while (match(','));

            if (!match(')')) {
                throw new IllegalArgumentException("函数参数缺少右括号");
            }
            return args;
        }

        private BigDecimal applyFunction(String identifier, List<BigDecimal> args) {
            String name = identifier.toLowerCase(Locale.ROOT);
            return switch (name) {
                case "sqrt" -> {
                    requireArgumentCount(name, args, 1);
                    BigDecimal value = args.get(0);
                    if (value.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("sqrt 参数不能为负数");
                    }
                    yield fromDouble(Math.sqrt(value.doubleValue()));
                }
                case "abs" -> {
                    requireArgumentCount(name, args, 1);
                    yield args.get(0).abs(MATH_CONTEXT);
                }
                case "round" -> {
                    requireArgumentCount(name, args, 1);
                    yield args.get(0).setScale(0, RoundingMode.HALF_UP);
                }
                case "floor" -> {
                    requireArgumentCount(name, args, 1);
                    yield args.get(0).setScale(0, RoundingMode.FLOOR);
                }
                case "ceil" -> {
                    requireArgumentCount(name, args, 1);
                    yield args.get(0).setScale(0, RoundingMode.CEILING);
                }
                case "pow" -> {
                    requireArgumentCount(name, args, 2);
                    yield fromDouble(Math.pow(args.get(0).doubleValue(), args.get(1).doubleValue()));
                }
                case "max" -> {
                    requireAtLeastOneArgument(name, args);
                    BigDecimal result = args.get(0);
                    for (int i = 1; i < args.size(); i++) {
                        result = result.max(args.get(i));
                    }
                    yield result;
                }
                case "min" -> {
                    requireAtLeastOneArgument(name, args);
                    BigDecimal result = args.get(0);
                    for (int i = 1; i < args.size(); i++) {
                        result = result.min(args.get(i));
                    }
                    yield result;
                }
                case "sin" -> unaryDouble(name, args, Math::sin);
                case "cos" -> unaryDouble(name, args, Math::cos);
                case "tan" -> unaryDouble(name, args, Math::tan);
                case "atan" -> unaryDouble(name, args, Math::atan);
                case "sinh" -> unaryDouble(name, args, Math::sinh);
                case "cosh" -> unaryDouble(name, args, Math::cosh);
                case "tanh" -> unaryDouble(name, args, Math::tanh);
                case "exp" -> unaryDouble(name, args, Math::exp);
                case "asin", "acos" -> {
                    requireArgumentCount(name, args, 1);
                    if (args.get(0).abs(MATH_CONTEXT).compareTo(BigDecimal.ONE) > 0) {
                        throw new IllegalArgumentException(name + " 参数必须在 -1 到 1 之间");
                    }
                    yield fromDouble(name.equals("asin")
                            ? Math.asin(args.get(0).doubleValue())
                            : Math.acos(args.get(0).doubleValue()));
                }
                case "ln", "log" -> {
                    requireArgumentCount(name, args, 1);
                    if (args.get(0).signum() <= 0) {
                        throw new IllegalArgumentException(name + " 参数必须为正数");
                    }
                    yield fromDouble(Math.log(args.get(0).doubleValue()));
                }
                case "log10" -> {
                    requireArgumentCount(name, args, 1);
                    if (args.get(0).signum() <= 0) {
                        throw new IllegalArgumentException("log10 参数必须为正数");
                    }
                    yield fromDouble(Math.log10(args.get(0).doubleValue()));
                }
                case "log2" -> {
                    requireArgumentCount(name, args, 1);
                    if (args.get(0).signum() <= 0) {
                        throw new IllegalArgumentException("log2 参数必须为正数");
                    }
                    yield fromDouble(Math.log(args.get(0).doubleValue()) / Math.log(2));
                }
                case "radians" -> unaryDouble(name, args, Math::toRadians);
                case "degrees" -> unaryDouble(name, args, Math::toDegrees);
                case "fact" -> {
                    requireArgumentCount(name, args, 1);
                    yield factorial(requireCombinatorialInteger(args.get(0)));
                }
                case "comb" -> {
                    requireArgumentCount(name, args, 2);
                    yield combinations(requireCombinatorialInteger(args.get(0)),
                            requireCombinatorialInteger(args.get(1)));
                }
                case "perm" -> {
                    requireArgumentCount(name, args, 2);
                    yield permutations(requireCombinatorialInteger(args.get(0)),
                            requireCombinatorialInteger(args.get(1)));
                }
                case "sum" -> {
                    requireAtLeastOneArgument(name, args);
                    BigDecimal result = BigDecimal.ZERO;
                    for (BigDecimal arg : args) {
                        result = result.add(arg, MATH_CONTEXT);
                    }
                    yield result;
                }
                case "avg", "mean" -> {
                    requireAtLeastOneArgument(name, args);
                    BigDecimal sum = BigDecimal.ZERO;
                    for (BigDecimal arg : args) {
                        sum = sum.add(arg, MATH_CONTEXT);
                    }
                    yield sum.divide(BigDecimal.valueOf(args.size()), DIVISION_SCALE, RoundingMode.HALF_UP);
                }
                case "median" -> {
                    requireAtLeastOneArgument(name, args);
                    List<BigDecimal> sorted = new ArrayList<>(args);
                    sorted.sort(BigDecimal::compareTo);
                    int middle = sorted.size() / 2;
                    if (sorted.size() % 2 == 1) {
                        yield sorted.get(middle);
                    }
                    yield sorted.get(middle - 1).add(sorted.get(middle))
                            .divide(BigDecimal.valueOf(2), DIVISION_SCALE, RoundingMode.HALF_UP);
                }
                case "var", "std" -> {
                    requireAtLeastOneArgument(name, args);
                    double mean = 0;
                    for (BigDecimal arg : args) {
                        mean += arg.doubleValue();
                    }
                    mean /= args.size();
                    double squaredDiffSum = 0;
                    for (BigDecimal arg : args) {
                        double diff = arg.doubleValue() - mean;
                        squaredDiffSum += diff * diff;
                    }
                    double variance = squaredDiffSum / args.size();
                    if (name.equals("std")) {
                        yield fromDouble(Math.sqrt(variance));
                    }
                    yield fromDouble(variance);
                }
                default -> throw new IllegalArgumentException("不支持的函数: " + identifier);
            };
        }

        private void requireArgumentCount(String functionName, List<BigDecimal> args, int expectedCount) {
            if (args.size() != expectedCount) {
                throw new IllegalArgumentException(functionName + " 需要 " + expectedCount + " 个参数");
            }
        }

        private void requireAtLeastOneArgument(String functionName, List<BigDecimal> args) {
            if (args.isEmpty()) {
                throw new IllegalArgumentException(functionName + " 至少需要 1 个参数");
            }
        }

        private BigDecimal unaryDouble(String name, List<BigDecimal> args, DoubleUnaryOperator operator) {
            requireArgumentCount(name, args, 1);
            return fromDouble(operator.applyAsDouble(args.get(0).doubleValue()));
        }

        private BigDecimal resolveConstant(String identifier) {
            String name = identifier.toLowerCase(Locale.ROOT);
            if (variables != null) {
                BigDecimal bound = variables.get(name);
                if (bound != null) {
                    return bound;
                }
            }
            return switch (name) {
                case "pi" -> fromDouble(Math.PI);
                case "e" -> fromDouble(Math.E);
                default -> throw new IllegalArgumentException("不支持的常量: " + identifier);
            };
        }

        private BigDecimal parseNumber() {
            // 0x/0b/0o 进制字面量，如 0xff、0b1010、0o17
            if (peek() == '0' && position + 1 < expression.length()) {
                char next = expression.charAt(position + 1);
                if (next == 'x' || next == 'X') {
                    return parseRadixLiteral(16);
                }
                if (next == 'b' || next == 'B') {
                    return parseRadixLiteral(2);
                }
                if (next == 'o' || next == 'O') {
                    return parseRadixLiteral(8);
                }
            }
            int start = position;
            boolean hasDot = false;
            while (!isAtEnd()) {
                char current = expression.charAt(position);
                if (Character.isDigit(current)) {
                    position++;
                    continue;
                }
                if (current == '.') {
                    if (hasDot) {
                        throw new IllegalArgumentException("数字格式错误");
                    }
                    hasDot = true;
                    position++;
                    continue;
                }
                break;
            }

            String numberText = expression.substring(start, position);
            if (".".equals(numberText)) {
                throw new IllegalArgumentException("数字格式错误");
            }
            try {
                return new BigDecimal(numberText, MATH_CONTEXT);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("数字格式错误");
            }
        }

        private BigDecimal parseRadixLiteral(int radix) {
            position += 2;
            int start = position;
            while (!isAtEnd() && Character.digit(expression.charAt(position), radix) >= 0) {
                position++;
            }
            if (position == start) {
                throw new IllegalArgumentException("进制字面量格式错误");
            }
            try {
                return new BigDecimal(new BigInteger(expression.substring(start, position), radix));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("进制字面量格式错误");
            }
        }

        private String parseIdentifier() {
            int start = position;
            while (!isAtEnd()) {
                char current = expression.charAt(position);
                if (Character.isLetterOrDigit(current) || current == '_') {
                    position++;
                    continue;
                }
                break;
            }
            return expression.substring(start, position);
        }

        private boolean isNumberStart(char current) {
            return Character.isDigit(current) || current == '.';
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(expression.charAt(position))) {
                position++;
            }
        }

        private boolean match(char expected) {
            if (peek() != expected) {
                return false;
            }
            position++;
            return true;
        }

        private char peek() {
            if (isAtEnd()) {
                return '\0';
            }
            return expression.charAt(position);
        }

        private boolean isAtEnd() {
            return position >= expression.length();
        }
    }
}
